package com.zob.recorder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zob.recorder.ui.screens.home.HomeScreen
import com.zob.recorder.ui.screens.playback.RecordingPlaybackScreen
import com.zob.recorder.ui.screens.sceneeditor.SceneEditorScreen
import com.zob.recorder.ui.screens.settings.SettingsScreen
import com.zob.recorder.ui.screens.streaming.StreamingConfigScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> {
            HomeScreen(navController = navController)
        }
        composable<SceneEditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SceneEditorRoute>()
            SceneEditorScreen(sceneId = route.sceneId, navController = navController)
        }
        composable<StreamingConfigRoute> {
            StreamingConfigScreen(navController = navController)
        }
        composable<SettingsRoute> {
            SettingsScreen(navController = navController)
        }
        composable<RecordingPlaybackRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RecordingPlaybackRoute>()
            RecordingPlaybackScreen(recordingId = route.recordingId, navController = navController)
        }
    }
}
