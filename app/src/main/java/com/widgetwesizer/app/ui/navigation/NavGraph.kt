package com.widgetwesizer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.widgetwesizer.app.ui.screens.BoardScreen
import com.widgetwesizer.app.ui.screens.PermissionScreen
import com.widgetwesizer.app.ui.screens.WidgetPickerScreen
import com.widgetwesizer.app.ui.viewmodel.WidgetBoardViewModel
import com.widgetwesizer.app.widget.WidgetManager

object Routes {
    const val BOARD = "board"
    const val PICKER = "picker"
    const val PERMISSION = "permission"
}

@Composable
fun NavGraph(
    viewModel: WidgetBoardViewModel,
    widgetManager: WidgetManager
) {
    val navController = rememberNavController()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()

    val startDestination = if (isPermissionGranted) Routes.BOARD else Routes.PERMISSION

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.PERMISSION) {
            PermissionScreen(
                onPermissionGranted = {
                    viewModel.refreshPermission()
                    navController.navigate(Routes.BOARD) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.BOARD) {
            BoardScreen(
                viewModel = viewModel,
                widgetManager = widgetManager,
                onNavigateToPicker = { navController.navigate(Routes.PICKER) }
            )
        }
        composable(Routes.PICKER) {
            WidgetPickerScreen(
                viewModel = viewModel,
                widgetManager = widgetManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
