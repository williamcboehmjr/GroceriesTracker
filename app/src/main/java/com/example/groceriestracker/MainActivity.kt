package com.example.groceriestracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.groceriestracker.data.database.AppDatabase
import com.example.groceriestracker.data.preferences.PreferencesManager
import com.example.groceriestracker.data.repository.DefaultInventoryRepository
import com.example.groceriestracker.theme.GroceriesTrackerTheme
import com.example.groceriestracker.ui.viewmodel.InventoryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val itemDao = database.itemDao()
        val prefsManager = PreferencesManager(applicationContext)
        val repository = DefaultInventoryRepository(itemDao, prefsManager)
        val viewModel = InventoryViewModel(repository)

        enableEdgeToEdge()
        setContent {
            GroceriesTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
