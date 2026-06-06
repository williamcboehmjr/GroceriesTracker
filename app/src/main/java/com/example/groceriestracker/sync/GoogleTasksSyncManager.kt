package com.example.groceriestracker.sync

import android.accounts.Account
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.groceriestracker.data.database.ItemDao
import com.example.groceriestracker.data.preferences.PreferencesManager
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleTasksSyncManager {

    companion object {
        private const val TAG = "GoogleTasksSync"
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getTasksService(context: Context, accountName: String): Tasks? {
        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context.applicationContext,
                listOf(TasksScopes.TASKS)
            ).apply {
                selectedAccount = Account(accountName, "com.google")
            }

            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            Tasks.Builder(transport, jsonFactory, credential)
                .setApplicationName("GroceriesTracker")
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Tasks service", e)
            null
        }
    }

    suspend fun getOrCreateGroceriesList(context: Context, prefsManager: PreferencesManager): String? = withContext(Dispatchers.IO) {
        val accountName = prefsManager.getGoogleAccountName() ?: return@withContext null
        val service = getTasksService(context, accountName) ?: return@withContext null

        // If list ID is already saved, verify it exists
        val savedId = prefsManager.getGoogleTasksListId()
        if (savedId != null) {
            try {
                val existingList = service.tasklists().get(savedId).execute()
                if (existingList != null) {
                    return@withContext savedId
                }
            } catch (e: Exception) {
                Log.w(TAG, "Saved list ID $savedId no longer accessible. Fetching/creating anew.")
            }
        }

        // Try to find a list named "Groceries"
        try {
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            val existingGroceriesList = taskLists.find { it.title.equals("Groceries", ignoreCase = true) }
            if (existingGroceriesList != null) {
                prefsManager.setGoogleTasksListId(existingGroceriesList.id)
                return@withContext existingGroceriesList.id
            }

            // Create a new "Groceries" list
            val newList = TaskList().apply { title = "Groceries" }
            val createdList = service.tasklists().insert(newList).execute()
            prefsManager.setGoogleTasksListId(createdList.id)
            return@withContext createdList.id
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching/creating Groceries list", e)
            return@withContext null
        }
    }

    suspend fun syncAll(context: Context, itemDao: ItemDao, prefsManager: PreferencesManager): Boolean = withContext(Dispatchers.IO) {
        val accountName = prefsManager.getGoogleAccountName() ?: return@withContext false
        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "Device is offline. Skipping sync.")
            return@withContext false
        }

        val service = getTasksService(context, accountName) ?: return@withContext false
        val listId = getOrCreateGroceriesList(context, prefsManager) ?: return@withContext false

        var syncSucceeded = true

        // 1. Process items to create: Local has isInShoppingList = true but no googleTaskId
        val itemsToCreate = itemDao.getShoppingListItemsList().filter { it.googleTaskId == null }
        for (item in itemsToCreate) {
            try {
                val newTask = Task().apply {
                    title = item.name
                    notes = "Category: ${item.category}"
                    status = "needsAction"
                }
                val createdTask = service.tasks().insert(listId, newTask).execute()
                val updatedItem = item.copy(googleTaskId = createdTask.id)
                itemDao.update(updatedItem)
                Log.d(TAG, "Successfully synced new task to Google Tasks for: ${item.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing item ${item.name} to Google Tasks", e)
                syncSucceeded = false
            }
        }

        // 2. Process items to complete: Local has isInShoppingList = false but still has googleTaskId
        val itemsToComplete = itemDao.getItemsToCompleteList()
        for (item in itemsToComplete) {
            val taskId = item.googleTaskId ?: continue
            try {
                // Fetch current status, modify to complete
                val task = service.tasks().get(listId, taskId).execute()
                task.status = "completed"
                service.tasks().update(listId, taskId, task).execute()
                
                // Clear the googleTaskId locally as sync is complete
                val updatedItem = item.copy(googleTaskId = null)
                itemDao.update(updatedItem)
                Log.d(TAG, "Successfully marked task completed in Google Tasks for: ${item.name}")
            } catch (e: Exception) {
                // If it is 404/not found, it was deleted in cloud, so we can clear the ID locally
                if (e.message?.contains("404") == true) {
                    val updatedItem = item.copy(googleTaskId = null)
                    itemDao.update(updatedItem)
                } else {
                    Log.e(TAG, "Error completing task $taskId for item ${item.name}", e)
                    syncSucceeded = false
                }
            }
        }

        return@withContext syncSucceeded
    }
}
