package com.example.groceriestracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String, // e.g. "Grocery", "Household"
    val space: String = "Pantry",
    val isKnown: Boolean = true,
    val currentQuantity: Int = 0,
    val isInShoppingList: Boolean = false,
    val googleTaskId: String? = null
)
