package com.zob.recorder

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zob.recorder.navigation.AppNavHost
import com.zob.recorder.navigation.HomeRoute
import com.zob.recorder.navigation.SceneEditorRoute
import com.zob.recorder.navigation.SettingsRoute

@Composable
fun ZobApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == HomeRoute::class.qualifiedName,
                    onClick = {
                        navController.navigate(HomeRoute) {
                            popUpTo(HomeRoute) { inclusive = true }
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute?.startsWith("com.zob.recorder.navigation.SceneEditorRoute") == true,
                    onClick = {
                        navController.navigate(SceneEditorRoute(sceneId = "default"))
                    },
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Scenes") },
                    label = { Text("Scenes") }
                )
                NavigationBarItem(
                    selected = currentRoute == SettingsRoute::class.qualifiedName,
                    onClick = { navController.navigate(SettingsRoute) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}
