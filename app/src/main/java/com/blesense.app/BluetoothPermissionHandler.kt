package com.blesense.app
// Import necessary Android, Compose, and permission-related libraries
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Class to manage Bluetooth operations and permissions
class BluetoothManager(private val activity: ComponentActivity) {
    // Lazily initialize the Bluetooth adapter
    val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            // Get the BluetoothManager service and retrieve the adapter
            val bluetoothManager =
                activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } catch (e: SecurityException) {
            // Handle security exceptions if permissions are not granted
            null
        }
    }

    // Check if Bluetooth is enabled
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    // Check if location services (GPS or Network) are enabled
    fun isLocationEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Enable Bluetooth if it is not already enabled
    fun enableBluetooth() {
        try {
            if (!isBluetoothEnabled()) {
                // Create an intent to request Bluetooth enabling
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } catch (e: SecurityException) {
            // Show a toast if Bluetooth permissions are not granted
            Toast.makeText(
                activity,
                "Bluetooth permissions not granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Enable location services if they are not already enabled
    fun enableLocation() {
        if (!isLocationEnabled()) {
            // Create an intent to open location settings
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            activity.startActivityForResult(intent, REQUEST_ENABLE_LOCATION)
        }
    }

    // Companion object to hold request codes for Bluetooth and location enabling
    companion object {
        const val REQUEST_ENABLE_BT = 1 // Request code for enabling Bluetooth
        const val REQUEST_ENABLE_LOCATION = 2 // Request code for enabling location
    }
}

// Composable to handle Bluetooth and location permissions
@Composable
fun BluetoothPermissionHandler(
    onPermissionsGranted: () -> Unit
) {
    // Get the current context
    val context = LocalContext.current
    // State to control the visibility of the permission rationale dialog
    val showRationaleDialog = remember { mutableStateOf(false) }
    // State to control the visibility of the service enable dialog
    val showEnableDialog = remember { mutableStateOf(false) }
    // Initialize BluetoothManager with the current activity
    val btManager = remember { BluetoothManager(context as ComponentActivity) }
    // State to trigger service checks
    var checkingServices by remember { mutableStateOf(true) }

    // Check Bluetooth and location services initially and after resuming
    LaunchedEffect(checkingServices) {
        if (!btManager.isBluetoothEnabled() || !btManager.isLocationEnabled()) {
            // Show dialog if either Bluetooth or location is disabled
            showEnableDialog.value = true
        }
    }

    // Launcher for handling Bluetooth enable requests
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Trigger service recheck after Bluetooth enable attempt
        checkingServices = !checkingServices
    }

    // Launcher for handling location enable requests
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Trigger service recheck after location enable attempt
        checkingServices = !checkingServices
    }

    // Define required permissions based on Android version
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    // Launcher for requesting multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions were granted
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // Check if Bluetooth and location services are enabled
            if (!btManager.isBluetoothEnabled() || !btManager.isLocationEnabled()) {
                showEnableDialog.value = true
            } else {
                // Call the callback if all conditions are met
                onPermissionsGranted()
            }
        } else {
            // Show rationale dialog if any permission was denied
            showRationaleDialog.value = true
        }
    }

    // Automatically check and request permissions on composition
    LaunchedEffect(Unit) {
        checkAndRequestPermissions(context, requiredPermissions, permissionLauncher)
    }

    // Display rationale dialog if permissions are denied
    if (showRationaleDialog.value) {
        RationaleDialog(
            onConfirm = {
                // Request permissions again
                permissionLauncher.launch(requiredPermissions)
                showRationaleDialog.value = false
            },
            onDismiss = { showRationaleDialog.value = false }
        )
    }

    // Display dialog to enable Bluetooth and location services
    if (showEnableDialog.value) {
        AlertDialog(
            onDismissRequest = { showEnableDialog.value = false },
            title = { Text("Enable Required Services") },
            text = { Text("Both Bluetooth and Location services need to be enabled to scan for devices.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Enable Bluetooth if disabled
                        if (!btManager.isBluetoothEnabled()) {
                            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                        // Enable location if disabled
                        if (!btManager.isLocationEnabled()) {
                            locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                        showEnableDialog.value = false
                    }
                ) {
                    Text("Enable Services")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Composable to display a rationale dialog for permissions
@Composable
private fun RationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Permissions Required") },
        text = { Text("Bluetooth and location permissions are needed to scan and connect to devices.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Request Permissions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Check and request permissions that are not yet granted
private fun checkAndRequestPermissions(
    context: Context,
    permissions: Array<String>,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    // Filter permissions that are not granted
    val notGrantedPermissions = permissions.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    // Launch permission request if any permissions are missing
    if (notGrantedPermissions.isNotEmpty()) {
        launcher.launch(notGrantedPermissions.toTypedArray())
    }
}

// Composable to check the state of Bluetooth and location permissions
@Composable
fun rememberBluetoothPermissionsState(): Boolean {
    val context = LocalContext.current
    // State to track whether all permissions are granted
    var hasPermissions by remember { mutableStateOf(false) }

    // Check permissions on composition
    LaunchedEffect(Unit) {
        hasPermissions = checkPermissions(context)
    }

    return hasPermissions
}

// Check if all required Bluetooth permissions are granted
private fun checkPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN
    ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}