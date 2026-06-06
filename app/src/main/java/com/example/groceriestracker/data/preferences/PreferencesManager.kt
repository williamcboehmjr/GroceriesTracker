package com.example.groceriestracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {
    private val sharedPrefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPrefs = EncryptedSharedPreferences.create(
            context,
            "groceries_tracker_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getGeminiApiKey(): String? {
        return sharedPrefs.getString(KEY_GEMINI_API_KEY, null)
    }

    fun setGeminiApiKey(key: String) {
        sharedPrefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun getGoogleAccountName(): String? {
        return sharedPrefs.getString(KEY_GOOGLE_ACCOUNT_NAME, null)
    }

    fun setGoogleAccountName(name: String?) {
        sharedPrefs.edit().putString(KEY_GOOGLE_ACCOUNT_NAME, name).apply()
    }

    fun getGoogleTasksListId(): String? {
        return sharedPrefs.getString(KEY_GOOGLE_TASKS_LIST_ID, null)
    }

    fun setGoogleTasksListId(id: String?) {
        sharedPrefs.edit().putString(KEY_GOOGLE_TASKS_LIST_ID, id).apply()
    }

    fun clearAuth() {
        sharedPrefs.edit()
            .remove(KEY_GOOGLE_ACCOUNT_NAME)
            .remove(KEY_GOOGLE_TASKS_LIST_ID)
            .apply()
    }

    fun getSpaces(): Set<String> {
        return sharedPrefs.getStringSet(KEY_SPACES, setOf("Fridge", "Freezer", "Pantry", "Cupboard")) ?: setOf("Fridge", "Freezer", "Pantry", "Cupboard")
    }

    fun addSpace(space: String) {
        val current = getSpaces().toMutableSet()
        current.add(space)
        sharedPrefs.edit().putStringSet(KEY_SPACES, current).apply()
    }

    fun removeSpace(space: String) {
        val current = getSpaces().toMutableSet()
        current.remove(space)
        sharedPrefs.edit().putStringSet(KEY_SPACES, current).apply()
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GOOGLE_ACCOUNT_NAME = "google_account_name"
        private const val KEY_GOOGLE_TASKS_LIST_ID = "google_tasks_list_id"
        private const val KEY_SPACES = "custom_spaces"
    }
}
