package com.blesense.app.view.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blesense.app.R

@Composable
fun StartGameScreen(
    navController: NavController
) {
    val archivoFont = FontFamily(Font(R.font.archivo_semibold))

    // Pulse animation for the start button
    val infiniteTransition = rememberInfiniteTransition(label = "startPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Cute Chibi Heroes Sunset Theme Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_sunset),
            contentDescription = "Cute Superhero Squad",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Soft gradient overlay to keep heroes visible while providing header & button contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        // 2. Standard High-Visibility Back Button
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 14.dp, top = 12.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF0C1929).copy(alpha = 0.85f))
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // 3. Top Title Banner
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFF8F00))
                    )
                )
                .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 7.dp)
        ) {
            Text(
                text = "★ SUPERHERO HUNT ★",
                fontFamily = archivoFont,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        // 4. Center Start Game Animated Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            GifImage(
                assetName = "START_GAME_COMPRESS.gif",
                contentDescription = "Start Game",
                modifier = Modifier
                    .scale(pulseScale)
                    .size(130.dp),
                onClick = {
                    navController.navigate("game_welcome_screen")
                }
            )
        }
    }
}
