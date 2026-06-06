package com.example.groceriestracker.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.groceriestracker.ai.GeminiVisionManager
import com.example.groceriestracker.data.model.Item
import com.example.groceriestracker.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScannerUiState {
    object Idle : ScannerUiState
    object Auditing : ScannerUiState
    data class Success(val message: String) : ScannerUiState
    data class Error(val message: String) : ScannerUiState
}

sealed interface SyncUiState {
    object Idle : SyncUiState
    object Syncing : SyncUiState
    object Success : SyncUiState
    data class Error(val message: String) : SyncUiState
}

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val geminiManager: GeminiVisionManager = GeminiVisionManager()
) : ViewModel() {

    private val _geminiApiKey = MutableStateFlow(repository.getGeminiApiKey() ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey

    private val _googleAccountName = MutableStateFlow(repository.getGoogleAccountName())
    val googleAccountName: StateFlow<String?> = _googleAccountName

    val isAuthAndKeySet: StateFlow<Boolean> = combine(
        _geminiApiKey,
        _googleAccountName
    ) { key, account ->
        key.isNotEmpty() && account != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val knownItems: StateFlow<List<Item>> = repository.knownItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shoppingList: StateFlow<List<Item>> = repository.shoppingListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scannerUiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val scannerUiState: StateFlow<ScannerUiState> = _scannerUiState

    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState

    fun setApiKey(key: String) {
        repository.setGeminiApiKey(key)
        _geminiApiKey.value = key
    }

    fun signInGoogle(accountName: String, context: Context) {
        repository.setGoogleAccountName(accountName)
        _googleAccountName.value = accountName
        viewModelScope.launch {
            // Fetch/create list on initial login
            repository.getOrCreateGroceriesList(context)
            triggerTasksSync(context)
        }
    }

    fun signOutGoogle() {
        repository.clearAuth()
        _googleAccountName.value = null
    }

    fun triggerTasksSync(context: Context) {
        if (_googleAccountName.value == null) return
        viewModelScope.launch {
            _syncUiState.value = SyncUiState.Syncing
            val result = repository.syncTasks(context)
            _syncUiState.value = if (result) {
                SyncUiState.Success
            } else {
                SyncUiState.Error("Sync failed or offline")
            }
        }
    }

    fun updateItemQuantity(item: Item, newQuantity: Int, context: Context) {
        viewModelScope.launch {
            val updatedItem = item.copy(
                currentQuantity = newQuantity,
                // If quantity is increased to > 0, it means it is restocked and should be removed from the shopping list
                isInShoppingList = if (newQuantity > 0) false else item.isInShoppingList
            )
            repository.updateItem(updatedItem)
            triggerTasksSync(context)
        }
    }

    fun setItemShoppingListState(item: Item, isInShoppingList: Boolean, context: Context) {
        viewModelScope.launch {
            val updatedItem = item.copy(
                isInShoppingList = isInShoppingList,
                // If we check off the item, it is completed, so we keep quantity as is or increment?
                // The prompt says: "Checking an item off here updates the Room DB and triggers the Tasks sync engine to complete the task in the cloud."
                // "unchecked locally (or recognized as restocked by the scanner), use the googleTaskId to mark the corresponding Google Task as completed"
                // So checking off is setting isInShoppingList = false.
                currentQuantity = if (!isInShoppingList && item.currentQuantity == 0) 1 else item.currentQuantity
            )
            repository.updateItem(updatedItem)
            triggerTasksSync(context)
        }
    }

    fun addItemManually(name: String, category: String, quantity: Int, isInShoppingList: Boolean, context: Context) {
        viewModelScope.launch {
            val existing = repository.getItemByName(name)
            if (existing != null) {
                val updated = existing.copy(
                    category = category,
                    currentQuantity = quantity,
                    isInShoppingList = isInShoppingList,
                    isKnown = true
                )
                repository.updateItem(updated)
            } else {
                val newItem = Item(
                    name = name,
                    category = category,
                    currentQuantity = quantity,
                    isInShoppingList = isInShoppingList,
                    isKnown = true
                )
                repository.insertItem(newItem)
            }
            triggerTasksSync(context)
        }
    }

    fun auditFridgePantry(bitmap: Bitmap, context: Context) {
        val apiKey = _geminiApiKey.value
        if (apiKey.isEmpty()) {
            _scannerUiState.value = ScannerUiState.Error("API Key is missing")
            return
        }

        _scannerUiState.value = ScannerUiState.Auditing
        viewModelScope.launch {
            try {
                val currentKnown = repository.getKnownItemsList()
                val results = geminiManager.auditPantry(bitmap, apiKey, currentKnown)
                
                for (res in results) {
                    val existingItem = repository.getItemByName(res.itemName)
                    val category = existingItem?.category ?: "Grocery"
                    
                    if (existingItem != null) {
                        val updated = existingItem.copy(
                            currentQuantity = res.quantity,
                            isInShoppingList = res.status == "missing" || res.action == "add_to_shopping_list",
                            isKnown = true
                        )
                        repository.updateItem(updated)
                    } else {
                        val newItem = Item(
                            name = res.itemName,
                            category = category,
                            currentQuantity = res.quantity,
                            isInShoppingList = res.status == "missing" || res.action == "add_to_shopping_list",
                            isKnown = true
                        )
                        repository.insertItem(newItem)
                    }
                }
                
                _scannerUiState.value = ScannerUiState.Success("Audit completed. Found ${results.size} items.")
                triggerTasksSync(context)
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Audit failed", e)
                _scannerUiState.value = ScannerUiState.Error(e.message ?: "Failed to audit pantry")
            }
        }
    }

    fun resetScannerState() {
        _scannerUiState.value = ScannerUiState.Idle
    }
}
