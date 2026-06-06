package com.example.groceriestracker

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Scanner : NavKey
@Serializable data object Inventory : NavKey
@Serializable data object ShoppingList : NavKey
@Serializable data object Settings : NavKey
