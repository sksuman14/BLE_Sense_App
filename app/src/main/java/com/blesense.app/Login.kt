@file:OptIn(ExperimentalMaterial3Api::class)

package com.blesense.app

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay

// Define custom Helvetica font family for consistent typography
val helveticaFont = FontFamily(
    Font(R.font.helvetica),
    Font(R.font.helvetica_bold, weight = FontWeight.Bold)
)

// Object to store app-specific colors for theming
object AppColors {
    val PrimaryColor = Color(0xFF007AFF)
    val TextFieldBackgroundColor = Color(0xFFFFFFFF)
    val SecondaryTextColor = Color(0xFF8E8E93)
}

// Main composable for the login screen
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Theme-based colors
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB0B0B0) else AppColors.SecondaryTextColor
    val textFieldBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else AppColors.TextFieldBackgroundColor
    val buttonBackgroundColor = if (isDarkMode) Color(0xFFBB86FC) else AppColors.PrimaryColor
    val buttonTextColor = if (isDarkMode) Color.Black else Color.White
    val dividerColor = if (isDarkMode) Color(0xFFB0B0B0) else Color.LightGray
    val borderColor = if (isDarkMode) Color(0xFFB0B0B0) else Color.LightGray
    val errorColor = if (isDarkMode) Color(0xFFCF6679) else MaterialTheme.colorScheme.error
    val dialogBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }

    val isFormValid by remember(email, password) {
        derivedStateOf { isValidEmail(email) && isValidPassword(password) }
    }

    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { viewModel.signInWithGoogle(it) }
            } catch (e: ApiException) {
                errorMessage = "Google Sign-In failed: ${e.statusCode} - ${e.message}"
                showErrorDialog = true
            }
        } else {
            errorMessage = "Google Sign-In cancelled"
            showErrorDialog = true
        }
    }

    val googleSignInClient = remember { GoogleSignInHelper.getGoogleSignInClient(context) }
    LaunchedEffect(Unit) {
        viewModel.setGoogleSignInClient(googleSignInClient)
    }

    if (authState is AuthState.Loading) {
        LoadingDialog(onDismissRequest = {})
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            delay(5000)
            errorMessage = null
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> onNavigateToHome()
            is AuthState.Error -> {
                errorMessage = (authState as AuthState.Error).message
                showErrorDialog = true
            }
            is AuthState.PasswordResetEmailSent -> {
                showForgotPasswordDialog = false
                errorMessage = "Password reset email sent! Please check your inbox."
                showErrorDialog = true
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "Welcome Back",
            style = TextStyle(
                fontSize = 34.sp,
                fontFamily = helveticaFont,
                fontWeight = FontWeight.Bold,
                color = textColor
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Sign in to continue",
            style = TextStyle(
                fontSize = 17.sp,
                color = secondaryTextColor,
                fontFamily = helveticaFont,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(90.dp))

        EmailTextField(
            email = email,
            onEmailChange = {
                email = it
                errorMessage = null
            },
            isError = !isValidEmail(email) && email.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            placeholder = "Email",
            invalidMessage = "Please enter a valid email address",
            backgroundColor = textFieldBackgroundColor,
            textColor = textColor,
            borderColor = borderColor,
            errorColor = errorColor,
            buttonBackgroundColor = buttonBackgroundColor
        )

        PasswordTextField(
            password = password,
            onPasswordChange = {
                password = it
                errorMessage = null
            },
            passwordVisible = passwordVisible,
            onPasswordVisibilityChange = { passwordVisible = it },
            isError = !isValidPassword(password) && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Password",
            invalidMessage = "Password must be at least 8 characters",
            backgroundColor = textFieldBackgroundColor,
            textColor = textColor,
            borderColor = borderColor,
            errorColor = errorColor,
            buttonBackgroundColor = buttonBackgroundColor
        )

        TextButton(
            onClick = { showForgotPasswordDialog = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp)
        ) {
            Text(
                text = "Forgot Password?",
                style = TextStyle(
                    fontSize = 15.sp,
                    color = buttonBackgroundColor,
                    fontFamily = helveticaFont,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isFormValid) {
                    viewModel.loginUser(email.trim(), password)
                }
            },
            enabled = isFormValid && authState !is AuthState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonBackgroundColor,
                disabledContainerColor = buttonBackgroundColor.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = buttonTextColor
                )
            } else {
                Text(
                    text = "Sign In",
                    color = buttonTextColor,
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = helveticaFont
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
            Text(
                text = "Or continue with",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = TextStyle(
                    fontSize = 15.sp,
                    color = secondaryTextColor,
                    fontFamily = helveticaFont,
                    fontWeight = FontWeight.Bold
                )
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SocialLoginButton(
                icon = R.drawable.google_g,
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                backgroundColor = textFieldBackgroundColor,
                borderColor = borderColor
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Don't have an account?",
                style = TextStyle(
                    fontSize = 15.sp,
                    color = textColor,
                    fontFamily = helveticaFont,
                    fontWeight = FontWeight.SemiBold
                )
            )
            TextButton(onClick = onNavigateToRegister) {
                Text(
                    text = "Register Now",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = buttonBackgroundColor,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = helveticaFont
                    )
                )
            }
        }

        if (showErrorDialog && errorMessage != null) {
            AlertDialog(
                onDismissRequest = {
                    showErrorDialog = false
                    errorMessage = null
                },
                title = {
                    Text(
                        text = "Error",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontFamily = helveticaFont,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )
                },
                text = {
                    Text(
                        text = errorMessage ?: "An unknown error occurred",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontFamily = helveticaFont,
                            color = textColor
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showErrorDialog = false
                            errorMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = buttonBackgroundColor)
                    ) {
                        Text("OK", color = buttonTextColor)
                    }
                },
                containerColor = dialogBackgroundColor
            )
        }

        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = {
                    Text(
                        text = "Reset Password",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontFamily = helveticaFont,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your email address and we'll send you a link to reset your password.",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = helveticaFont,
                                color = textColor
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextField(
                            value = forgotPasswordEmail,
                            onValueChange = { forgotPasswordEmail = it },
                            placeholder = { Text("Email", color = secondaryTextColor) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            colors = TextFieldDefaults.textFieldColors(
                                containerColor = textFieldBackgroundColor,
                                unfocusedIndicatorColor = borderColor,
                                focusedIndicatorColor = buttonBackgroundColor,
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 17.sp, fontFamily = helveticaFont, color = textColor)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isValidEmail(forgotPasswordEmail)) {
                                viewModel.sendPasswordResetEmail(forgotPasswordEmail.trim())
                            }
                        },
                        enabled = isValidEmail(forgotPasswordEmail),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonBackgroundColor,
                            disabledContainerColor = buttonBackgroundColor.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("Send Reset Link", color = buttonTextColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("Cancel", color = buttonBackgroundColor)
                    }
                },
                containerColor = dialogBackgroundColor
            )
        }
    }
}

// Social login button
@Composable
fun SocialLoginButton(
    icon: Int,
    onClick: () -> Unit,
    backgroundColor: Color,
    borderColor: Color
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )
    }
}

// Email text field
@Composable
private fun EmailTextField(
    email: String,
    onEmailChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String,
    invalidMessage: String,
    backgroundColor: Color,
    textColor: Color,
    borderColor: Color,
    errorColor: Color,
    buttonBackgroundColor: Color
) {
    TextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = borderColor) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        isError = isError,
        supportingText = { if (isError) Text(invalidMessage, color = errorColor) },
        colors = TextFieldDefaults.textFieldColors(
            containerColor = backgroundColor,
            unfocusedIndicatorColor = borderColor,
            focusedIndicatorColor = buttonBackgroundColor,
            errorIndicatorColor = errorColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
        ),
        shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(fontSize = 17.sp, fontFamily = helveticaFont, color = textColor)
    )
}

// Password text field with visibility toggle
@Composable
private fun PasswordTextField(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String,
    invalidMessage: String,
    backgroundColor: Color,
    textColor: Color,
    borderColor: Color,
    errorColor: Color,
    buttonBackgroundColor: Color
) {
    TextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = borderColor) },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        isError = isError,
        supportingText = { if (isError) Text(invalidMessage, color = errorColor) },
        trailingIcon = {
            IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }, modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = painterResource(id = if (passwordVisible) R.drawable.invisible else R.drawable.show),
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = Color.Unspecified
                )
            }
        },
        colors = TextFieldDefaults.textFieldColors(
            containerColor = backgroundColor,
            unfocusedIndicatorColor = borderColor,
            focusedIndicatorColor = buttonBackgroundColor,
            errorIndicatorColor = errorColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
        ),
        shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(fontSize = 17.sp, fontFamily = helveticaFont, color = textColor)
    )
}

// Validation helpers
private fun isValidEmail(email: String): Boolean {
    return email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

private fun isValidPassword(password: String): Boolean {
    return password.length >= 8
}

// Preview
@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(onNavigateToRegister = {}, onNavigateToHome = {}, viewModel = AuthViewModel())
}