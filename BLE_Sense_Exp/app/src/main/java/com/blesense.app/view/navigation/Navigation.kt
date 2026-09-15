package com.blesense.app.view.navigation

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.blesense.app.repository.DataLoggerRepository
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.screens.*
import com.blesense.app.view.settings.ModernSettingsScreen
import com.blesense.app.viewmodel.BluetoothScanViewModel
import com.blesense.app.viewmodel.BluetoothScanViewModelFactory

@Composable
fun AppNavigation(navController: NavHostController) {
    // Get context, application & activity
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val activity = context as ComponentActivity

    // Initialize BluetoothScanViewModel
    val bluetoothViewModel: BluetoothScanViewModel by activity.viewModels {
        BluetoothScanViewModelFactory(application)
    }

    // Navigation graph
    NavHost(
        navController = navController,
        startDestination = "splash_screen"
    ) {
        // Splash screen (navigates to intermediate_screen automatically)
        composable("splash_screen") {
            SplashScreen(navController = navController)
        }

        // Intermediate screen (Main Dashboard)
        composable("intermediate_screen") {
            IntermediateScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel,
                isDarkMode = ThemeManager.isDarkMode.collectAsState().value
            )
        }

        // Home screen (Standard Sensor Hub)
        composable("home_screen") {
            MainScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        // AWS Scanner Screen
        composable("aws_scanner") {
            AWSScannerScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        // AWS Advertising Details
        composable(
            route = "aws_advertising/{deviceName}/{deviceAddress}",
            arguments = listOf(
                navArgument("deviceName") { type = NavType.StringType },
                navArgument("deviceAddress") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceName = Uri.decode(backStackEntry.arguments?.getString("deviceName") ?: "AWS")
            val deviceAddress = Uri.decode(backStackEntry.arguments?.getString("deviceAddress") ?: "")

            AWSAdvertisingScreen(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                navController = navController,
                viewModel = bluetoothViewModel
            )
        }

        // AWS Live Hub (Analytics/Animation)
        composable("aws_live_hub") {
            SensorDashboardScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        // Robot control screen (fixed orientation)
        composable("robot_screen") {
            RobotControlScreen(
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }



        // Settings screen
        composable("settings_screen") {
            ModernSettingsScreen(
                navController = navController
            )
        }
        
        // Advertising data screen
        composable(
            route = "advertising/{deviceName}/{deviceAddress}/{sensorType}/{deviceId}",
            arguments = listOf(
                navArgument("deviceName") { type = NavType.StringType },
                navArgument("deviceAddress") { type = NavType.StringType },
                navArgument("sensorType") { type = NavType.StringType },
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceName = Uri.decode(backStackEntry.arguments?.getString("deviceName") ?: "")
            val deviceAddress = Uri.decode(backStackEntry.arguments?.getString("deviceAddress") ?: "")
            val sensorType = backStackEntry.arguments?.getString("sensorType") ?: ""
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            AdvertisingDataScreen(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                navController = navController,
                deviceId = deviceId,
                viewModel = bluetoothViewModel
            )
        }

        // Data logger list screen (Repository)
        composable("data_logger_list") {
            DataLoggerListScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        // Scan History screen (BigAdv & BLE scan history)
        composable("scan_history") {
            ScanHistoryScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        // Data logger control screen (new architecture)
        composable(
            route = "data_logger_control/{loggerId}",
            arguments = listOf(
                navArgument("loggerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val loggerId = backStackEntry.arguments?.getString("loggerId") ?: ""

            DataLoggerScreen(
                loggerId = loggerId,
                navController = navController,
                viewModel = bluetoothViewModel
            )
        }

        // Legacy Data logger screen
        composable(
            route = "data_logger/{deviceName}/{deviceAddress}/{deviceId}",
            arguments = listOf(
                navArgument("deviceName") { type = NavType.StringType },
                navArgument("deviceAddress") { type = NavType.StringType },
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            val loggerId = DataLoggerRepository.getLoggerByDeviceId(backStackEntry.arguments?.getString("deviceId") ?: "")?.id ?: "dl1"

            DataLoggerScreen(
                loggerId = loggerId,
                deviceAddress = deviceAddress,
                navController = navController,
                viewModel = bluetoothViewModel
            )
        }

        // Chart screen
        composable(
            route = "chart_screen/{deviceAddress}",
            arguments = listOf(
                navArgument("deviceAddress") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            ChartScreen(
                navController = navController,
                deviceAddress = backStackEntry.arguments?.getString("deviceAddress"),
                viewModel = bluetoothViewModel
            )
        }

        composable(
            route = "chart_screen/{deviceAddress}?sensorType={sensorType}",
            arguments = listOf(
                navArgument("deviceAddress") { type = NavType.StringType },
                navArgument("sensorType") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            ChartScreen(
                navController = navController,
                deviceAddress = backStackEntry.arguments?.getString("deviceAddress"),
                sensorType = backStackEntry.arguments?.getString("sensorType"),
                viewModel = bluetoothViewModel
            )
        }

        // Chart screen 2
        composable(
            route = "chart_screen_2/{title}/{value}",
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("value") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title")
            val value = backStackEntry.arguments?.getString("value")

            ChartScreen2(navController = navController, title = title, value = value)
        }

        // Raw data viewer screen
        composable(
            route = "raw_data_viewer/{deviceAddress}",
            arguments = listOf(
                navArgument("deviceAddress") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            RawDataViewerScreen(
                navController = navController,
                deviceAddress = deviceAddress,
                viewModel = bluetoothViewModel
            )
        }

        composable("bigadv_scanner_screen") {
            BigAdvScannerScreen(navController = navController, viewModel = bluetoothViewModel)
        }
    }
}
