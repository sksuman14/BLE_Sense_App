package com.blesense.app

// Import necessary Compose and Android UI libraries
//import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Composable function for the Create New Password screen
@Composable
fun CreateNewPasswordScreen() {
    // State to hold the new password input
    var newPassword by remember { mutableStateOf("") }
    // State to hold the confirm password input
    var confirmPassword by remember { mutableStateOf("") }
    // State to toggle visibility of the new password
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    // State to toggle visibility of the confirm password
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    // Define the primary color for UI elements (iOS blue)
    val primaryColor = Color(0xFF007AFF)

    // Main container for the screen
    Box(
        modifier = Modifier
            .fillMaxSize() // Fill the entire screen
            .background(Color.White) // Set white background
            .padding(24.dp) // Apply padding to all sides
    ) {
        // Column to organize content vertically
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween // Space content evenly
        ) {
            // Top Section: Title and description
            Column(
                modifier = Modifier.fillMaxWidth() // Fill available width
            ) {
                // Spacer to add vertical padding at the top
                Spacer(modifier = Modifier.height(70.dp))

                // Animated title with slide-in and fade-in effects
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
                ) {
                    Text(
                        text = "Create new password",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = helveticaFont, // Custom Helvetica font
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp) // Bottom padding
                    )
                }

                // Spacer for vertical spacing
                Spacer(modifier = Modifier.height(10.dp))

                // Description text for password requirements
                Text(
                    text = "Your new password must be unique from those previously used.",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f), // Semi-transparent gray
                    fontFamily = helveticaFont, // Custom Helvetica font
                    lineHeight = 24.sp, // Line height for readability
                    modifier = Modifier.padding(bottom = 32.dp) // Bottom padding
                )

                // Spacer for additional vertical spacing
                Spacer(modifier = Modifier.height(10.dp))

                // Password Fields Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth() // Fill available width
                        .padding(bottom = 24.dp) // Bottom padding
                ) {
                    // New Password input field
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it }, // Update state on input change
                        label = { Text("New Password", fontFamily = helveticaFont) },
                        modifier = Modifier
                            .fillMaxWidth() // Fill available width
                            .padding(bottom = 16.dp), // Bottom padding
                        shape = RoundedCornerShape(12.dp), // Rounded corners
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MaterialTheme.colors.primary, // Border color when focused
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f) // Border color when unfocused
                        ),
                        visualTransformation = if (isNewPasswordVisible)
                            VisualTransformation.None // Show password if visible
                        else
                            PasswordVisualTransformation(), // Hide password if not visible
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // Password keyboard
                        trailingIcon = {
                            // Icon button to toggle password visibility
                            androidx.compose.material3.IconButton(
                                onClick = { isNewPasswordVisible = !isNewPasswordVisible },
                                modifier = Modifier.size(24.dp) // Fixed icon size
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(
                                        id = if (isNewPasswordVisible) {
                                            R.drawable.visibility_on // Icon for visible password (placeholder)
                                        } else {
                                            R.drawable.visibility_off // Icon for hidden password (placeholder)
                                        }
                                    ),
                                    contentDescription = if (isNewPasswordVisible) "Hide password" else "Show password",
                                    tint = Color.Unspecified // Preserve original icon colors
                                )
                            }
                        }
                    )

                    // Confirm Password input field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it }, // Update state on input change
                        label = { Text("Confirm Password", fontFamily = helveticaFont) },
                        modifier = Modifier
                            .fillMaxWidth() // Fill available width
                            .padding(bottom = 16.dp), // Bottom padding
                        shape = RoundedCornerShape(12.dp), // Rounded corners
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MaterialTheme.colors.primary, // Border color when focused
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f) // Border color when unfocused
                        ),
                        visualTransformation = if (isConfirmPasswordVisible)
                            VisualTransformation.None // Show password if visible
                        else
                            PasswordVisualTransformation(), // Hide password if not visible
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // Password keyboard
                        trailingIcon = {
                            // Icon button to toggle password visibility
                            androidx.compose.material3.IconButton(
                                onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                modifier = Modifier.size(24.dp) // Fixed icon size
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(
                                        id = if (isConfirmPasswordVisible) {
                                            R.drawable.visibility_on // Icon for visible password (placeholder)
                                        } else {
                                            R.drawable.visibility_off // Icon for hidden password (placeholder)
                                        }
                                    ),
                                    contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password",
                                    tint = Color.Unspecified // Preserve original icon colors
                                )
                            }
                        }
                    )
                }

                // Spacer for vertical spacing
                Spacer(modifier = Modifier.height(10.dp))

                // Continue button to submit the new password
                Button(
                    onClick = { /* Handle send code action */ },
                    modifier = Modifier
                        .fillMaxWidth() // Fill available width
                        .height(56.dp) // Fixed height
                        .clip(RoundedCornerShape(12.dp)), // Rounded corners
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = primaryColor, // iOS blue background
                        contentColor = Color.White // White text
                    )
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = helveticaFont // Custom Helvetica font
                    )
                }
            }

            // Bottom Section: Link to login screen
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Fill available width
                    .padding(bottom = 32.dp), // Bottom padding
                horizontalArrangement = Arrangement.Center, // Center content horizontally
                verticalAlignment = Alignment.CenterVertically // Center content vertically
            ) {
                // Text prompting user if they remember their password
                Text(
                    text = "Remember Password? ",
                    color = Color.Gray.copy(alpha = 0.8f), // Semi-transparent gray
                    fontFamily = helveticaFont, // Custom Helvetica font
                    fontWeight = FontWeight.SemiBold
                )
                // Button to navigate to the login screen
                TextButton(onClick = { /* Navigate to login */ }) {
                    Text(
                        text = "Login",
                        color = primaryColor, // iOS blue color
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = helveticaFont // Custom Helvetica font
                    )
                }
            }
        }
    }
}

// Preview composable for the Create New Password screen
@Preview(showBackground = true)
@Composable
fun CreateNewPasswordScreenPreview() {
    CreateNewPasswordScreen()
}