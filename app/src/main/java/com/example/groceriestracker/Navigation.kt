package com.example.groceriestracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.groceriestracker.ui.screens.InventoryView
import com.example.groceriestracker.ui.screens.ScannerView
import com.example.groceriestracker.ui.screens.SettingsView
import com.example.groceriestracker.ui.screens.ShoppingListView
import com.example.groceriestracker.ui.viewmodel.InventoryViewModel

@Composable
fun MainNavigation(viewModel: InventoryViewModel) {
    val backStack = rememberNavBackStack(Scanner)
    val currentKey = backStack.lastOrNull() ?: Scanner

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentKey == Scanner,
                    onClick = {
                        if (currentKey != Scanner) {
                            backStack.clear()
                            backStack.add(Scanner)
                        }
                    },
                    icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Scanner") },
                    label = { Text("Scanner") }
                )
                NavigationBarItem(
                    selected = currentKey == Inventory,
                    onClick = {
                        if (currentKey != Inventory) {
                            backStack.clear()
                            backStack.add(Inventory)
                        }
                    },
                    icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Inventory") },
                    label = { Text("Inventory") }
                )
                NavigationBarItem(
                    selected = currentKey == ShoppingList,
                    onClick = {
                        if (currentKey != ShoppingList) {
                            backStack.clear()
                            backStack.add(ShoppingList)
                        }
                    },
                    icon = { Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Shopping List") },
                    label = { Text("Shopping List") }
                )
                NavigationBarItem(
                    selected = currentKey == Settings,
                    onClick = {
                        if (currentKey != Settings) {
                            backStack.clear()
                            backStack.add(Settings)
                        }
                    },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Scanner> {
                    ScannerView(
                        viewModel = viewModel,
                        onGoToSettings = {
                            backStack.clear()
                            backStack.add(Settings)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                entry<Inventory> {
                    InventoryView(
                        viewModel = viewModel,
                        onGoToSettings = {
                            backStack.clear()
                            backStack.add(Settings)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                entry<ShoppingList> {
                    ShoppingListView(
                        viewModel = viewModel,
                        onGoToSettings = {
                            backStack.clear()
                            backStack.add(Settings)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                entry<Settings> {
                    SettingsView(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        )
    }
}
