package com.blesense.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------
//  ONE SINGLE Helvetica definition – replace with your real font if you have it
// ---------------------------------------------------------------------
//private val hel RnveticaFont = FontFamily.Default   // <-- change to your custom FontFamily

// ---------------------------------------------------------------------
//  ENGLISH-ONLY SCREEN (no translation, no managers)
// ---------------------------------------------------------------------
@Composable
fun AnimatedFirstScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onGuestSignIn: () -> Unit
) {
    // ---- Theme -------------------------------------------------------
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // ---- Hard-coded English strings ----------------------------------
    val appName           = "BLE Sense"
    val loginText         = "Login"
    val signUpText        = "Sign Up"
    val orText            = "OR"
    val continueAsGuest   = "Continue as Guest"

    // ---- Colors -------------------------------------------------------
    val backgroundColor      = if (isDarkMode) Color(0xFF121212) else Color.White
    val shapeBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFD9EFFF)
    val textColor            = if (isDarkMode) Color.White else Color.Black
    val dividerColor         = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray
    val buttonBgColor        = if (isDarkMode) Color(0xFFBB86FC) else colorResource(R.color.btnColor)
    val buttonTextColor      = if (isDarkMode) Color.Black else Color.White
    val loadingColor         = if (isDarkMode) Color(0xFFBB86FC) else colorResource(R.color.btnColor)

    // ---- Animations ---------------------------------------------------
    val backgroundScale = remember { Animatable(0f) }
    val iconAlpha       = remember { Animatable(0f) }
    val textAlpha       = remember { Animatable(0f) }
    val buttonAlpha     = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        backgroundScale.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        iconAlpha.animateTo(1f, tween(500, easing = LinearEasing))
        textAlpha.animateTo(1f, tween(500, easing = LinearEasing))
        buttonAlpha.animateTo(1f, tween(500, easing = LinearEasing))
    }

    var isLoading by remember { mutableStateOf(false) }

    // -----------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ---- Animated background shape --------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = backgroundScale.value,
                    scaleY = backgroundScale.value,
                    transformOrigin = TransformOrigin(0f, 1f)
                )
                .clip(GenericShape { size, _ ->
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.9f)
                        quadraticBezierTo(
                            size.width * 0.1f, size.height * 0.62f,
                            size.width * 0.55f, size.height * 0.55f
                        )
                        quadraticBezierTo(
                            size.width * 1f, size.height * 0.47f,
                            size.width, size.height * 0.4f
                        )
                        lineTo(size.width, 0f)
                        lineTo(0f, 0f)
                        close()
                    }
                    addPath(path)
                })
                .background(shapeBackgroundColor)
        )

        // ---- Main content ------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ---- Icon ----------------------------------------------------
            Image(
                painter = painterResource(id = R.drawable.bg_remove_ble),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(200.dp)
                    .alpha(iconAlpha.value)
            )

            // ---- App name ------------------------------------------------
            Text(
                text = appName,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = helveticaFont,
                color = textColor,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .padding(bottom = 140.dp)
            )

            Spacer(modifier = Modifier.height(80.dp))

            // ---- Login button --------------------------------------------
            Button(
                onClick = onNavigateToLogin,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .alpha(buttonAlpha.value)
                    .padding(vertical = 8.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = buttonBgColor),
                elevation = ButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = loginText,
                    fontSize = 18.sp,
                    color = buttonTextColor,
                    fontFamily = helveticaFont,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---- Sign-up button ------------------------------------------
            Button(
                onClick = onNavigateToSignup,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .alpha(buttonAlpha.value)
                    .padding(vertical = 8.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = buttonBgColor),
                elevation = ButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = signUpText,
                    fontSize = 18.sp,
                    color = buttonTextColor,
                    fontFamily = helveticaFont,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- OR divider -----------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .alpha(buttonAlpha.value),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(0.5f),
                    color = dividerColor,
                    thickness = 1.dp
                )
                Text(
                    text = "  $orText  ",
                    color = dividerColor,
                    fontSize = 14.sp,
                    fontFamily = helveticaFont,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Divider(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(0.5f),
                    color = dividerColor,
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Guest / Loading -----------------------------------------
            if (isLoading) {
                LoadingAnimation(
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(buttonAlpha.value),
                    color = loadingColor
                )
            } else {
                Text(
                    text = continueAsGuest,
                    fontSize = 16.sp,
                    color = textColor,
                    fontFamily = helveticaFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .alpha(buttonAlpha.value)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            isLoading = true
                            onGuestSignIn()
                        }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Loading animation (unchanged)
// ---------------------------------------------------------------------
@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    color: Color
) {
    val infinite = rememberInfiniteTransition()
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val scale by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                }
                .size(32.dp),
            color = color,
            strokeWidth = 3.dp
        )
    }
}