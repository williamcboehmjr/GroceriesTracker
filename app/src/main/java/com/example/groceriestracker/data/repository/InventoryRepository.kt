package com.example.groceriestracker.data.repository

import android.content.Context
import com.example.groceriestracker.data.database.ItemDao
import com.example.groceriestracker.data.model.Item
import com.example.groceriestracker.data.preferences.PreferencesManager
import com.example.groceriestracker.sync.GoogleTasksSyncManager
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    val allItemsFlow: Flow<List<Item>>
    val knownItemsFlow: Flow<List<Item>>
    val shoppingListFlow: Flow<List<Item>>

    suspend fun getKnownItemsList(): List<Item>
    suspend fun getShoppingListItemsList(): List<Item>
    suspend fun getItemByName(name: String): Item?
    suspend fun getItemById(id: Int): Item?
    suspend fun insertItem(item: Item): Long
    suspend fun updateItem(item: Item): Int
    suspend fun deleteItem(item: Item)

    // Sync
    suspend fun syncTasks(context: Context): Boolean
    suspend fun getOrCreateGroceriesList(context: Context): String?

    // Preferences
    fun getGeminiApiKey(): String?
    fun setGeminiApiKey(key: String)
    fun getGoogleAccountName(): String?
    fun setGoogleAccountName(name: String?)
    fun getGoogleTasksListId(): String?
    fun setGoogleTasksListId(id: String?)
    fun clearAuth()
}

class DefaultInventoryRepository(
    private val itemDao: ItemDao,
    private val prefsManager: PreferencesManager,
    private val syncManager: GoogleTasksSyncManager = GoogleTasksSyncManager()
) : InventoryRepository {

    override val allItemsFlow: Flow<List<Item>> = itemDao.getAllItemsFlow()
    override val knownItemsFlow: Flow<List<Item>> = itemDao.getKnownItemsFlow()
    override val shoppingListFlow: Flow<List<Item>> = itemDao.getShoppingListItemsFlow()

    override suspend fun getKnownItemsList(): List<Item> = itemDao.getKnownItemsList()
    override suspend fun getShoppingListItemsList(): List<Item> = itemDao.getShoppingListItemsList()
    override suspend fun getItemByName(name: String): Item? = itemDao.getItemByName(name)
    override suspend fun getItemById(id: Int): Item? = itemDao.getItemById(id)
    override suspend fun insertItem(item: Item): Long = itemDao.insert(item)
    override suspend fun updateItem(item: Item): Int = itemDao.update(item)
    override suspend fun deleteItem(item: Item) = itemDao.delete(item)

    override suspend fun syncTasks(context: Context): Boolean {
        return syncManager.syncAll(context, itemDao, prefsManager)
    }

    override suspend fun getOrCreateGroceriesList(context: Context): String? {
        return syncManager.getOrCreateGroceriesList(context, prefsManager)
    }

    override fun getGeminiApiKey(): String? = prefsManager.getGeminiApiKey()
    override fun setGeminiApiKey(key: String) = prefsManager.setGeminiApiKey(key)
    override fun getGoogleAccountName(): String? = prefsManager.getGoogleAccountName()
    override fun setGoogleAccountName(name: String?) = prefsManager.setGoogleAccountName(name)
    override fun getGoogleTasksListId(): String? = prefsManager.getGoogleTasksListId()
    override fun setGoogleTasksListId(id: String?) = prefsManager.setGoogleTasksListId(id)
    override fun clearAuth() = prefsManager.clearAuth()
}
