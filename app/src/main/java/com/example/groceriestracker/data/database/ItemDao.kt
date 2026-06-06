package com.example.groceriestracker.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.groceriestracker.data.model.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItemsFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE isKnown = 1 ORDER BY category ASC, name ASC")
    fun getKnownItemsFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE isKnown = 1 ORDER BY category ASC, name ASC")
    suspend fun getKnownItemsList(): List<Item>

    @Query("SELECT * FROM items WHERE isInShoppingList = 1 ORDER BY name ASC")
    fun getShoppingListItemsFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE isInShoppingList = 1 ORDER BY name ASC")
    suspend fun getShoppingListItemsList(): List<Item>

    @Query("SELECT * FROM items WHERE isInShoppingList = 0 AND googleTaskId IS NOT NULL")
    suspend fun getItemsToCompleteList(): List<Item>

    @Query("SELECT * FROM items WHERE name = :name LIMIT 1")
    suspend fun getItemByName(name: String): Item?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): Item?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Update
    suspend fun update(item: Item): Int

    @Delete
    suspend fun delete(item: Item)
}
