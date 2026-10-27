package com.blesense.app.view.game

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blesense.app.R
import com.blesense.app.model.game.HeroBeacon
import com.blesense.app.model.game.HeroRegistry
import com.blesense.app.viewmodel.game.BleGameViewModel

@Composable
fun MultiplayerScreen(
    navController: NavController,
    gameViewModel: BleGameViewModel
) {
    val uiState by gameViewModel.uiState.collectAsState()
    val archivoFont = FontFamily(Font(R.font.archivo_semibold))

    LaunchedEffect(Unit) {
        gameViewModel.startScan()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Cute Chibi Heroes Twilight Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_twilight),
            contentDescription = "Cute Cartoon Heroes Twilight",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Soft gradient scrim for legibility while keeping the cartoon art vibrant
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 2. Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C1929).copy(alpha = 0.90f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Nearby Game Devices",
                    fontFamily = archivoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { gameViewModel.refreshScan(clearAll = true) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C1929).copy(alpha = 0.90f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.baseline_autorenew_24),
                        contentDescription = "Refresh",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Subtitle hint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✨ Tap any device to track & decode the character! ✨",
                    color = Color(0xFFFFD600),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = archivoFont,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Device List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                items(uiState.deviceList, key = { it.address }) { device ->
                    MultiplayerDeviceCard(
                        device = device,
                        archivoFont = archivoFont,
                        onClick = {
                            gameViewModel.selectDevice(device)
                            navController.navigate("guess_character_screen/${Uri.encode(device.name)}/${Uri.encode(device.address)}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MultiplayerDeviceCard(
    device: HeroBeacon,
    archivoFont: FontFamily,
    onClick: () -> Unit
) {
    // In multiplayer: name and avatar are revealed if RSSI > -40 dBm or when tapped to decode
    val isRevealed = device.rssi > -40
    val displayName = if (isRevealed) device.name.replace("_", " ") else "Unknown Device"
    val charImageRes = if (isRevealed) {
        HeroRegistry.getCharacterImage(device.name)
    } else {
        R.drawable.notfound
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0xFFF0F4F8).copy(alpha = 0.95f),
        border = BorderStroke(2.dp, if (isRevealed) Color(0xFF00C853) else Color(0xFF00B0FF)),
        elevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Character Image / Mystery Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = charImageRes),
                    contentDescription = displayName,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0D1B2A),
                        modifier = Modifier.weight(1f)
                    )

                    // Small Decode badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRevealed) Color(0xFF00C853) else Color(0xFF0288D1))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isRevealed) "DECODED" else "DECODE ➔",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "MAC Address: ${device.address}",
                    fontFamily = archivoFont,
                    fontSize = 13.sp,
                    color = Color(0xFF37474F)
                )

                Text(
                    text = "RSSI: ${device.rssi} dBm",
                    fontFamily = archivoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (device.rssi >= -55) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
        }
    }
}
