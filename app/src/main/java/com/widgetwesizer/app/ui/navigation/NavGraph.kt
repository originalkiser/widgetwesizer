package com.widgetwesizer.app.ui.navigation

import androidx.compose.runtime.Composable
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

    // No permission gate: Android asks the user to allow each widget the first time.
    // The adb tip is optional and reachable from the widget picker.
    NavHost(navController = navController, startDestination = Routes.BOARD) {
        composable(Routes.PERMISSION) {
            PermissionScreen(onBack = { navController.popBackStack() })
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
                onNavigateBack = { navController.popBackStack() },
                onOpenPermissionTip = { navController.navigate(Routes.PERMISSION) }
            )
        }
    }
}
