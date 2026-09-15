package com.blesense.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.AdjustStatusBarIconsForTheme
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.navigation.AppNavigation
import com.blesense.app.viewmodel.BluetoothScanViewModel
import com.blesense.app.viewmodel.BluetoothScanViewModelFactory

// Main entry point for the app, extending ComponentActivity for Compose support
class MainActivity : ComponentActivity() {

    private val bluetoothViewModel: BluetoothScanViewModel by viewModels {
        BluetoothScanViewModelFactory(application)
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) {
            // App is being closed
            bluetoothViewModel.clearAllDeviceData()
            clearCache(this)
        }
    }

    private fun clearCache(context: Context) {
        try {
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        // Prevent activity recreation on orientation changes for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        super.onCreate(savedInstanceState)
        
        // Pre-initialize theme based on saved preference or system theme to avoid flicker
        val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        ThemeManager.initializeWithSystemTheme(this, isSystemDark)

        lifecycle.addObserver(lifecycleObserver)
        
        // Keep the screen on as long as the app is in the foreground
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val isDarkMode by ThemeManager.isDarkMode.collectAsState()
            val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = bgColor
            ) {
                AdjustStatusBarIconsForTheme()
                val context = LocalContext.current

                // 1. Define required Bluetooth and Location permissions based on Android API level
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                } else {
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }

                // 2. Permission launcher for automatic runtime permission prompt on app startup
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allGranted = permissions.values.all { it }
                    if (!allGranted) {
                        Toast.makeText(
                            context,
                            "Bluetooth and Location permissions are required for BLE features",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 3. Automatically check and request missing permissions on app launch
                LaunchedEffect(Unit) {
                    val missingPermissions = permissionsToRequest.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missingPermissions.isNotEmpty()) {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }

                    // 4. Prompt user to turn on Bluetooth if disabled on device
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                    val bluetoothAdapter = bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        context.startActivity(enableBtIntent)
                    }
                }

                val navController = rememberNavController()
                AppNavigation(navController)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(lifecycleObserver)
        clearCache(this)
    }
}