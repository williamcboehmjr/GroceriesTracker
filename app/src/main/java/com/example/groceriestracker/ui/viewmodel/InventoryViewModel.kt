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
    ) { key, _ ->
        key.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val knownItems: StateFlow<List<Item>> = repository.knownItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shoppingList: StateFlow<List<Item>> = repository.shoppingListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scannerUiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val scannerUiState: StateFlow<ScannerUiState> = _scannerUiState

    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState

    private val _spaces = MutableStateFlow(repository.getSpaces().toList().sorted())
    val spaces: StateFlow<List<String>> = _spaces

    fun addSpace(space: String) {
        repository.addSpace(space)
        _spaces.value = repository.getSpaces().toList().sorted()
    }

    fun removeSpace(space: String) {
        repository.removeSpace(space)
        _spaces.value = repository.getSpaces().toList().sorted()
        viewModelScope.launch {
            val items = repository.getKnownItemsList()
            val defaultSpace = repository.getSpaces().firstOrNull() ?: "Pantry"
            for (item in items) {
                if (item.space == space) {
                    repository.updateItem(item.copy(space = defaultSpace))
                }
            }
        }
    }

    fun deleteItem(item: Item, context: Context) {
        viewModelScope.launch {
            repository.deleteItem(item)
            triggerTasksSync(context)
        }
    }

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

    fun addItemManually(name: String, category: String, quantity: Int, isInShoppingList: Boolean, space: String, context: Context) {
        viewModelScope.launch {
            val existing = repository.getItemByName(name)
            if (existing != null) {
                val updated = existing.copy(
                    category = category,
                    currentQuantity = quantity,
                    isInShoppingList = isInShoppingList,
                    space = space,
                    isKnown = true
                )
                repository.updateItem(updated)
            } else {
                val newItem = Item(
                    name = name,
                    category = category,
                    currentQuantity = quantity,
                    isInShoppingList = isInShoppingList,
                    space = space,
                    isKnown = true
                )
                repository.insertItem(newItem)
            }
            triggerTasksSync(context)
        }
    }

    fun auditFridgePantry(bitmaps: List<Bitmap>, selectedSpace: String, context: Context) {
        val apiKey = _geminiApiKey.value
        if (apiKey.isEmpty()) {
            _scannerUiState.value = ScannerUiState.Error("API Key is missing")
            return
        }

        _scannerUiState.value = ScannerUiState.Auditing
        viewModelScope.launch {
            try {
                val allKnown = repository.getKnownItemsList()
                val spaceKnown = allKnown.filter { it.space.equals(selectedSpace, ignoreCase = true) }
                
                val results = geminiManager.auditPantry(bitmaps, apiKey, spaceKnown)
                
                val foundNames = results.map { it.itemName.lowercase() }
                
                for (knownItem in spaceKnown) {
                    if (knownItem.name.lowercase() !in foundNames) {
                        val updated = knownItem.copy(
                            currentQuantity = 0,
                            isInShoppingList = true
                        )
                        repository.updateItem(updated)
                    }
                }
                
                for (res in results) {
                    val existingItem = repository.getItemByName(res.itemName)
                    val category = existingItem?.category ?: "Grocery"
                    
                    val isMissing = res.status == "missing"
                    val isLowStock = res.status == "low_stock"
                    
                    val finalQuantity = if (existingItem == null) {
                        if (res.quantity > 0) res.quantity else 1
                    } else {
                        if (isMissing) 0 else (if (res.quantity > 0) res.quantity else 1)
                    }
                    
                    val finalIsInShoppingList = if (existingItem == null) {
                        false
                    } else {
                        isMissing || isLowStock || res.action == "add_to_shopping_list"
                    }
                    
                    if (existingItem != null) {
                        val updated = existingItem.copy(
                            currentQuantity = finalQuantity,
                            isInShoppingList = finalIsInShoppingList,
                            space = selectedSpace,
                            isKnown = true
                        )
                        repository.updateItem(updated)
                    } else {
                        val newItem = Item(
                            name = res.itemName,
                            category = category,
                            currentQuantity = finalQuantity,
                            isInShoppingList = finalIsInShoppingList,
                            space = selectedSpace,
                            isKnown = true
                        )
                        repository.insertItem(newItem)
                    }
                }
                
                _scannerUiState.value = ScannerUiState.Success("Audit completed for $selectedSpace. Found ${results.size} items.")
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
