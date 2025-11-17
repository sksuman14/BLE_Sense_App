package com.blesense.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

// Singleton object to manage app-wide theme state
object ThemeManager {
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private var isInitialized = false

    fun toggleDarkMode(value: Boolean) {
        _isDarkMode.value = value
        isInitialized = true
    }

    fun initializeWithSystemTheme(isSystemDark: Boolean) {
        if (!isInitialized) {
            _isDarkMode.value = isSystemDark
            isInitialized = true
        }
    }
}

// Main settings screen composable
@Composable
fun ModernSettingsScreen(
    viewModel: AuthViewModel = viewModel(),
    onSignOut: () -> Unit,
    navController: NavHostController
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF2F2F7)
    val cardBackground = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray
    val dividerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
    val iconTint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF007AFF)

    val currentUser = viewModel.checkCurrentUser()

    Scaffold(
        backgroundColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontFamily = helveticaFont,
                        style = MaterialTheme.typography.h5.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("intermediate_screen") }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                backgroundColor = backgroundColor,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            val context = LocalContext.current

            // User profile card
            UserProfileCard(
                cardBackground = cardBackground,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                iconTint = iconTint,
                userName = when {
                    currentUser?.isAnonymous == true -> "Guest User"
                    currentUser != null -> currentUser.email?.substringBefore('@') ?: "User"
                    else -> "Not Signed In"
                },
                userEmail = when {
                    currentUser?.isAnonymous == true -> "Anonymous User"
                    currentUser != null -> currentUser.email ?: ""
                    else -> ""
                },
                profilePictureUrl = currentUser?.photoUrl?.toString(),
                onLogout = {
                    viewModel.signOut(context)
                    onSignOut()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Settings options list
            SettingsOptionsList(
                cardBackground = cardBackground,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                dividerColor = dividerColor,
                iconTint = iconTint,
                isDarkMode = isDarkMode,
                onDarkModeToggle = { newValue ->
                    ThemeManager.toggleDarkMode(newValue)
                },
                navController = navController
            )

            // Privacy policy button
            PrivacyPolicyButton()
        }
    }
}

// Composable for privacy policy button
@Composable
fun PrivacyPolicyButton() {
    val context = LocalContext.current

    Button(
        onClick = {
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sumankumar891.github.io/privacy_policy_blesense/"))
            context.startActivity(urlIntent)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text("Privacy Policy")
    }
}

// Helper function to generate a random color for the avatar
fun generateRandomColor(): Color {
    val random = Random
    return Color(
        red = random.nextInt(256),
        green = random.nextInt(256),
        blue = random.nextInt(256)
    ).copy(alpha = 0.8f)
}

// Composable for user profile card
@Composable
fun UserProfileCard(
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    userName: String,
    userEmail: String,
    profilePictureUrl: String? = null,
    onLogout: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardBackground,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profilePictureUrl != null) {
                AsyncImage(
                    model = profilePictureUrl,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    error = painterResource(R.drawable.error),
                    contentScale = ContentScale.Crop
                )
            } else {
                val avatarColor = remember { generateRandomColor() }
                val initials = userName.take(2).uppercase()

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontFamily = helveticaFont,
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = userName,
                    fontFamily = helveticaFont,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                Text(
                    text = userEmail,
                    fontFamily = helveticaFont,
                    style = MaterialTheme.typography.body2.copy(
                        color = secondaryTextColor
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = "Logout",
                    tint = iconTint
                )
            }
        }
    }
}

@Composable
fun SettingsOptionsList(
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color,
    dividerColor: Color,
    iconTint: Color,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    navController: NavHostController
) {
    val settingsOptions = listOf(
        SettingsItem(Icons.Outlined.DarkMode, "Dark Mode", SettingsItemType.SWITCH),
        SettingsItem(Icons.AutoMirrored.Outlined.Help, "Help", SettingsItemType.DETAIL),
        SettingsItem(Icons.Outlined.AccountCircle, "Accounts", SettingsItemType.DETAIL),
        SettingsItem(Icons.Outlined.Info, "About BLE", SettingsItemType.DETAIL),
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardBackground,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            settingsOptions.forEachIndexed { index, item ->
                if (item.title == "Dark Mode") {
                    SettingsItemRow(
                        item = item,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = iconTint,
                        initialSwitchState = isDarkMode,
                        onSwitchChange = onDarkModeToggle,
                        navController = navController
                    )
                } else {
                    SettingsItemRow(
                        item = item,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = iconTint,
                        initialSwitchState = isDarkMode,
                        navController = navController
                    )
                }

                if (index < settingsOptions.size - 1) {
                    Divider(
                        color = dividerColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

// Composable for individual settings item row
@Composable
fun SettingsItemRow(
    item: SettingsItem,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    initialSwitchState: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    navController: NavHostController,
) {
    var switchState by remember { mutableStateOf(initialSwitchState) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAccountsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isAboutItem = item.icon == Icons.Outlined.Info
    val isHelpItem = item.icon == Icons.AutoMirrored.Outlined.Help
    val isAccountsItem = item.icon == Icons.Outlined.AccountCircle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.type == SettingsItemType.DETAIL) {
                when {
                    isAboutItem -> showAboutDialog = true
                    isHelpItem -> showHelpDialog = true
                    isAccountsItem -> showAccountsDialog = true
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = item.title,
            fontFamily = helveticaFont,
            style = MaterialTheme.typography.body1.copy(
                fontWeight = FontWeight.Medium,
                color = textColor
            ),
            modifier = Modifier.weight(1f)
        )

        when (item.type) {
            SettingsItemType.SWITCH -> {
                Switch(
                    checked = switchState,
                    onCheckedChange = { newValue ->
                        switchState = newValue
                        onSwitchChange?.invoke(newValue)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = iconTint,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f)
                    )
                )
            }
            SettingsItemType.DETAIL -> {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Navigate",
                    tint = secondaryTextColor
                )
            }
        }
    }

    // About BLE dialog
    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .heightIn(max = 600.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (initialSwitchState) Color(0xFF1E1E1E) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "BLE Info",
                                tint = iconTint,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(top = 16.dp, bottom = 16.dp)
                            )

                            Text(
                                text = "Bluetooth Low Energy",
                                fontFamily = helveticaFont,
                                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                color = if (initialSwitchState) Color.White else Color.Black,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )

                            Text(
                                text = "Bluetooth Low Energy (BLE) is a wireless personal area network technology " +
                                        "designed for low power consumption while maintaining a similar communication " +
                                        "range to classic Bluetooth.",
                                fontFamily = helveticaFont,
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Justify,
                                color = if (initialSwitchState) Color.White else Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                            )

                            Text(
                                text = "Key Features:",
                                fontFamily = helveticaFont,
                                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                color = if (initialSwitchState) Color.White else Color.Black,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 8.dp)
                            )
                        }

                        items(listOf(
                            "Low power consumption",
                            "Small data packets",
                            "Quick connection times",
                            "Secure communication",
                            "Wide device compatibility"
                        )) { feature ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Check",
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    fontFamily = helveticaFont,
                                    style = MaterialTheme.typography.body2,
                                    color = if (initialSwitchState) Color.White else Color.Black,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { showAboutDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        Text("Close", color = iconTint)
                    }
                }
            }
        }
    }

    // Accounts dialog
    if (showAccountsDialog) {
        val viewModel: AuthViewModel = viewModel()
        val currentUser = viewModel.checkCurrentUser()

        AccountsDialog(
            isDarkMode = initialSwitchState,
            currentUserId = currentUser?.uid,
            onDismiss = { showAccountsDialog = false },
            navController = navController,
            viewModel = viewModel
        )
    }

    // Help dialog
    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (initialSwitchState) Color(0xFF1E1E1E) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Help,
                        contentDescription = "Help",
                        tint = iconTint,
                        modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                    )

                    Text(
                        text = "For any help or to report bugs:",
                        fontFamily = helveticaFont,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = if (initialSwitchState) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:awadhropar@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Help/Support Request")
                                putExtra(Intent.EXTRA_TEXT, "Please describe your issue or question here:")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send Email"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                            }
                            showHelpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Contact Developer",
                            color = iconTint,
                            fontFamily = helveticaFont,
                            style = MaterialTheme.typography.button
                        )
                    }

                    TextButton(
                        onClick = { showHelpDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text("Close", color = iconTint, fontFamily = helveticaFont, style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

// Composable for accounts dialog
@Composable
fun AccountsDialog(
    isDarkMode: Boolean,
    currentUserId: String?,
    onDismiss: () -> Unit,
    navController: NavHostController,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val users = UserRepository.users
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserData?>(null) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                accountToDelete = null
            },
            title = { Text("Delete Account", color = if (isDarkMode) Color.White else Color.Black) },
            text = {
                Column {
                    Text("Are you sure you want to delete this account?", color = if (isDarkMode) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (accountToDelete?.id == currentUserId) {
                        Text(
                            text = "This is your currently signed-in account. It will be deleted and you'll be signed in as a guest.",
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountToDelete?.let { user ->
                            if (user.id == currentUserId) {
                                showDeleteConfirmation = false
                                accountToDelete = null
                                onDismiss()

                                viewModel.deleteAccountAndSignInAsGuest { success, message ->
                                    if (success) {
                                        Toast.makeText(context, "Account deleted. Guest session started.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                UserRepository.removeUser(user.id)
                                Toast.makeText(context, "Account removed", Toast.LENGTH_SHORT).show()
                                showDeleteConfirmation = false
                                accountToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    accountToDelete = null
                }) { Text("Cancel") }
            },
            backgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White,
            contentColor = if (isDarkMode) Color.White else Color.Black
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Saved Accounts",
                    style = MaterialTheme.typography.h6,
                    color = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (users.isEmpty()) {
                    Text(
                        text = "No saved accounts found",
                        color = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(users) { user ->
                            SavedAccountItem(
                                user = user,
                                isCurrentUser = user.id == currentUserId,
                                isDarkMode = isDarkMode,
                                onDeleteAccount = {
                                    accountToDelete = user
                                    showDeleteConfirmation = true
                                }
                            )
                            Divider(color = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0), thickness = 1.dp)
                        }
                    }
                }

                Button(
                    onClick = {
                        val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sumankumar891.github.io/Delete_account_BLESense/"))
                        context.startActivity(urlIntent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Account Deletion Request")
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF007AFF))
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

// Composable for saved account item
@Composable
fun SavedAccountItem(
    user: UserData,
    isCurrentUser: Boolean,
    isDarkMode: Boolean,
    onDeleteAccount: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (user.profilePictureUrl != null) {
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = "Profile",
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(generateRandomColor()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                color = if (isDarkMode) Color.White else Color.Black,
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = if (user.isAnonymous) "Anonymous User" else user.email,
                color = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onDeleteAccount, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete account",
                tint = if (isDarkMode) Color(0xFFFF6B6B) else Color(0xFFE53935)
            )
        }
    }
}

// Data classes for settings items
data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val type: SettingsItemType
)

enum class SettingsItemType {
    SWITCH,
    DETAIL
}