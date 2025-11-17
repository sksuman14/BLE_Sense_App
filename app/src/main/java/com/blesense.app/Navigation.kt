package com.blesense.app

import android.app.Activity
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun AppNavigation(navController: NavHostController) {
    // Initialize AuthViewModel
    val authViewModel: AuthViewModel = viewModel()

    // Observe authentication state
    val authState by authViewModel.authState.collectAsState()

    // Get context, application & activity
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val activity = context as ComponentActivity

    // Initialize BluetoothScanViewModel
    val bluetoothViewModel: BluetoothScanViewModel<Any?> by activity.viewModels {
        BluetoothScanViewModelFactory(application)
    }

    // Handle authentication state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                navController.navigate("intermediate_screen") {
                    popUpTo("first_screen") { inclusive = true }
                    popUpTo("splash_screen") { inclusive = true }
                }
            }
            is AuthState.Error -> {
                // Optionally handle error
            }
            is AuthState.Idle -> {
                if (navController.currentDestination?.route !in listOf("first_screen", "splash_screen")) {
                    navController.navigate("first_screen") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    // Navigation graph
    NavHost(
        navController = navController,
        startDestination = if (authViewModel.isUserAuthenticated()) "intermediate_screen" else "splash_screen"
    ) {
        // Splash screen
        composable("splash_screen") {
            SplashScreen(
                onNavigateToLogin = {
                    if (!authViewModel.isUserAuthenticated()) {
                        navController.navigate("first_screen") {
                            popUpTo("splash_screen") { inclusive = true }
                        }
                    }
                }
            )
        }

        // First screen
        composable("first_screen") {
            AnimatedFirstScreen(
                onNavigateToLogin = {
                    navController.navigate("login")
                },
                onNavigateToSignup = {
                    navController.navigate("register")
                },
                onGuestSignIn = {
                    authViewModel.signInAsGuest()
                }
            )
        }

        // Login screen
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate("register")
                },
                onNavigateToHome = {
                    navController.navigate("intermediate_screen") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Register screen
        composable("register") {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate("intermediate_screen") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Intermediate screen
        composable("intermediate_screen") {
            IntermediateScreen(
                navController = navController,
                isDarkMode = ThemeManager.isDarkMode.collectAsState().value
            )
        }

        // Home screen
        composable("home_screen") {
            MainScreen(
                navController = navController,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        // Robot control screen (safe back handling)
        composable("robot_screen") {
            val act = LocalContext.current as? Activity
            RobotControlScreen(
                onBackPressed = {
                    act?.finish()
                }
            )
        }

        // Settings screen
        composable("settings_screen") {
            ModernSettingsScreen(
                viewModel = authViewModel,
                onSignOut = {
                    navController.navigate("first_screen") {
                        popUpTo("intermediate_screen") { inclusive = true }
                    }
                },
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
            val deviceName = backStackEntry.arguments?.getString("deviceName") ?: ""
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
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

        // Data logger screen
        composable(
            route = "data_logger/{deviceName}/{deviceAddress}/{deviceId}",
            arguments = listOf(
                navArgument("deviceName") { type = NavType.StringType },
                navArgument("deviceAddress") { type = NavType.StringType },
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceName = backStackEntry.arguments?.getString("deviceName") ?: ""
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            DataLoggerScreen(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                navController = navController,
                deviceId = deviceId,
                viewModel = bluetoothViewModel as BluetoothScanViewModel<Any>
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
                deviceAddress = backStackEntry.arguments?.getString("deviceAddress")
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
    }
}