package com.blesense.app.view.game

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
fun GameDeviceListScreen(
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
        // 1. Cute Cartoon Park Heroes Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_park),
            contentDescription = "Cute Cartoon Park Heroes",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Soft gradient overlay to keep cartoon background vibrant while making cards readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
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

            // 3. Device List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(uiState.deviceList, key = { it.address }) { device ->
                    ItemDeviceCard(
                        device = device,
                        archivoFont = archivoFont,
                        onClick = {
                            gameViewModel.selectDevice(device)
                            navController.navigate("game_play_screen/${device.name}/${device.address}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ItemDeviceCard(
    device: HeroBeacon,
    archivoFont: FontFamily,
    onClick: () -> Unit
) {
    val charImageRes = HeroRegistry.getCharacterImage(device.name)
    val cardBorderColor = if (device.isFound) Color(0xFF00C853) else Color(0xFF0288D1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFFF8FAFC).copy(alpha = 0.95f),
        border = BorderStroke(2.dp, cardBorderColor),
        elevation = 6.dp
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
                // Character Image with clean white backing
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = charImageRes),
                        contentDescription = device.name,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = device.name.replace("_", " "),
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )

                    Text(
                        text = "MAC: ${device.address}",
                        fontFamily = archivoFont,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    // RSSI Pill Badge
                    val isStrong = device.rssi >= -55
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isStrong) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                                .border(1.dp, if (isStrong) Color(0xFF00C853) else Color(0xFFFF9800), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "📶 RSSI: ${device.rssi} dBm",
                                fontFamily = archivoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isStrong) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            // Top-right "FOUND" Badge
            if (device.isFound) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00C853))
                        .border(1.dp, Color(0xFF00FF88), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "FOUND 🎯",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
