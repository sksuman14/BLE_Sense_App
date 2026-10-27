package com.blesense.app.view.game

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
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
fun BombDeviceListScreen(
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
        // Cute Superhero Twilight Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_twilight),
            contentDescription = "Superhero Bomb Squad Twilight Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark gradient scrim for strong text & card contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.50f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top Bar with standard back button
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
                    text = "💣 Bomb Squad Targets",
                    fontFamily = archivoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { gameViewModel.refreshScan(clearAll = false) },
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

            // 2. Mission Rules Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = Color(0xFF1B0C0C).copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, Color(0xFFFF3D00)),
                elevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🚨 MISSION OBJECTIVE 🚨",
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = Color(0xFFFF5252),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF1744))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⏱ 3:00 LIMIT",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "A superhero BLE device is acting as a ticking time-bomb! You have exactly 3 MINUTES to track it down. Bring your phone within DEFUSAL PROXIMITY (RSSI > -40 dBm) to defuse it! If time runs out, the bomb BLASTS and you lose!",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = archivoFont
                    )
                }
            }

            // 3. Subtitle Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a Superhero Device to Defuse 👇",
                    color = Color(0xFFFFD600),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = archivoFont
                )
            }

            // 4. Detected Beacons List
            if (uiState.deviceList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📡 Searching for Superhero Devices...",
                            color = Color.White,
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ensure your Superhero BLE beacon is turned on and advertising (Thor, Iron_Man, Hulk, etc.)",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                ) {
                    items(uiState.deviceList, key = { it.address }) { device ->
                        BombTargetCard(
                            device = device,
                            archivoFont = archivoFont,
                            onClick = {
                                gameViewModel.startBombMission(device.name, device.address)
                                navController.navigate("game_bomb_defusal_screen/${Uri.encode(device.name)}/${Uri.encode(device.address)}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BombTargetCard(
    device: HeroBeacon,
    archivoFont: FontFamily,
    onClick: () -> Unit
) {
    // Identity is hidden before defusal!
    val maskedName = "Mystery Threat #${device.address.takeLast(5)}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF141F2D).copy(alpha = 0.94f),
        border = BorderStroke(2.dp, Color(0xFFFF3D00)),
        elevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mystery Bomb Icon with glowing red hazard border
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0F18))
                        .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.question_mark2),
                        contentDescription = "Encrypted Threat",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Device details (Encrypted identity)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = maskedName,
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFFFD54F),
                            modifier = Modifier.weight(1f)
                        )

                        // Bomb Threat Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF1744))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "💣 ACTIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = "🔒 Encrypted Superhero Signal",
                        fontFamily = archivoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00E5FF)
                    )

                    Text(
                        text = "MAC: ${device.address}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // RSSI badge
                        val isStrong = device.rssi >= -55
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F2537))
                                .border(1.dp, if (isStrong) Color(0xFF00E5FF) else Color(0xFFFF9100), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "📶 ${device.rssi} dBm",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isStrong) Color(0xFF00E5FF) else Color(0xFFFF9100)
                            )
                        }

                        // Action prompt
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFD50000), Color(0xFFFF6D00))
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "DEFUSE ➔",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                fontFamily = archivoFont
                            )
                        }
                    }
                }
            }
        }
    }
}
