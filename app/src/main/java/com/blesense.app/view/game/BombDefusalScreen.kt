package com.blesense.app.view.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.blesense.app.R
import com.blesense.app.model.game.BombDefusalStatus
import com.blesense.app.model.game.HeroRegistry
import com.blesense.app.viewmodel.game.BleGameViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BombDefusalScreen(
    navController: NavController,
    gameViewModel: BleGameViewModel,
    deviceName: String,
    deviceAddress: String
) {
    val uiState by gameViewModel.uiState.collectAsState()
    val archivoFont = FontFamily(Font(R.font.archivo_semibold))

    // Ensure bomb mission is running for this target
    LaunchedEffect(deviceAddress) {
        if (uiState.bombStatus != BombDefusalStatus.DEFUSING ||
            uiState.bombTargetDeviceAddress != deviceAddress
        ) {
            gameViewModel.startBombMission(deviceName, deviceAddress)
        }
    }

    // Format time mm:ss
    val minutes = uiState.bombTimeRemainingSeconds / 60
    val seconds = uiState.bombTimeRemainingSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val isCriticalTime = uiState.bombTimeRemainingSeconds <= 30
    val isWarningTime = uiState.bombTimeRemainingSeconds in 31..60

    // Pulse animation for countdown timer
    val infiniteTransition = rememberInfiniteTransition(label = "bombPulse")
    val timerPulseScale by infiniteTransition.animateFloat(
        initialValue = if (isCriticalTime) 0.92f else 0.97f,
        targetValue = if (isCriticalTime) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isCriticalTime) 350 else if (isWarningTime) 650 else 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerScale"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Cute Heroes Sunset Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_sunset),
            contentDescription = "Superhero Bomb Defusal Sunset",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Danger dark scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF150505).copy(alpha = 0.70f),
                            Color(0xFF0C1929).copy(alpha = 0.80f),
                            Color(0xFF150505).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // 2. Main Gameplay Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Standard High-Visibility Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        gameViewModel.stopBombMission()
                        navController.popBackStack()
                    },
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

                Spacer(modifier = Modifier.weight(1f))

                // Title Banner
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFD50000), Color(0xFFFF6D00))
                            )
                        )
                        .border(1.5.dp, Color(0xFFFFD54F), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "💣 TIME-BOMB DEFUSAL 💣",
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(42.dp))
            }

            // Target Device HUD Card (HIDDEN IDENTITY UNTIL DEFUSED!)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0xFF0F1B2B).copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, Color(0xFFFF3D00).copy(alpha = 0.8f)),
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mystery avatar thumbnail (Masked before defusal)
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0F18))
                            .border(1.5.dp, Color(0xFFFF1744), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.question_mark2),
                            contentDescription = "Encrypted Threat",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mystery Bomb #${deviceAddress.takeLast(5)}",
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFFFD54F)
                        )
                        Text(
                            text = "🔒 Encrypted Superhero Signal",
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "MAC: $deviceAddress",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Live Signal Badge
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "RSSI: ${uiState.bombLiveRssi} dBm",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (uiState.bombLiveRssi >= -40 && uiState.bombLiveRssi != -99) Color(0xFF00FF88) else Color(0xFFFF9100)
                        )
                        Text(
                            text = "GOAL: > -40 dBm",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Central Interactive Animated Time Bomb & Timer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color(0xFF101B2B).copy(alpha = 0.94f),
                border = BorderStroke(
                    2.dp,
                    if (isCriticalTime) Color(0xFFFF1744) else if (isWarningTime) Color(0xFFFF9100) else Color(0xFF00B0FF).copy(alpha = 0.6f)
                ),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Big Digital Countdown Display
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isCriticalTime) "🚨 DETONATION IMMINENT 🚨" else if (isWarningTime) "⚠️ RUN! TIME IS RUNNING OUT ⚠️" else "⏱ DETONATION COUNTDOWN",
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = if (isCriticalTime) Color(0xFFFF1744) else if (isWarningTime) Color(0xFFFF9100) else Color(0xFF00E5FF)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Large Digital Clock
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF060B12))
                                .border(
                                    2.dp,
                                    if (isCriticalTime) Color(0xFFFF1744) else if (isWarningTime) Color(0xFFFF9100) else Color(0xFF00E5FF),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formattedTime,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 42.sp,
                                letterSpacing = 2.sp,
                                color = if (isCriticalTime) Color(0xFFFF1744) else if (isWarningTime) Color(0xFFFF9100) else Color(0xFF00FF88)
                            )
                        }
                    }

                    // Tactical Map UI: Phone location is FIXED, Bomb moves closer based on RSSI with live hints
                    BombTacticalMapTracker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        liveRssi = uiState.bombLiveRssi,
                        isCritical = isCriticalTime,
                        archivoFont = archivoFont
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Proximity Defusal Meter towards -40 dBm
                    DefusalProximityGauge(
                        liveRssi = uiState.bombLiveRssi,
                        targetRssi = -40,
                        archivoFont = archivoFont
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(8.dp)
            )
        }

        // ==================== VICTORY OVERLAY (BOMB DEFUSED! WIN) ====================
        // REVEALS HERO IDENTITY ONLY HERE!
        if (uiState.bombStatus == BombDefusalStatus.DEFUSED) {
            Dialog(
                onDismissRequest = { /* user must tap action button */ }
            ) {
                BombDefusedVictoryDialog(
                    heroName = deviceName,
                    timeRemainingFormatted = formattedTime,
                    finalRssi = uiState.bombLiveRssi,
                    archivoFont = archivoFont,
                    onPlayAgain = {
                        gameViewModel.restartBombMission()
                    },
                    onBackToList = {
                        gameViewModel.stopBombMission()
                        navController.popBackStack()
                    }
                )
            }
        }

        // ==================== ACTUAL EXPLOSIVE BOMB BLAST ANIMATION & DEFEAT ====================
        if (uiState.bombStatus == BombDefusalStatus.EXPLODED) {
            BombBlastExplosionOverlay(
                heroName = deviceName,
                archivoFont = archivoFont,
                onTryAgain = {
                    gameViewModel.restartBombMission()
                },
                onExit = {
                    gameViewModel.stopBombMission()
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * REAL GOOGLE MAPS NAVIGATION VIEW
 * - Styled after authentic Google Maps (Image 3):
 *   - Google Maps light terrain (#F4F3F0) with green parks (Serra Park, Las Palmas Park, McClellan Preserve)
 *   - Golden arterial highways (State Route 85 & Interstate 280) with amber borders and interchange ramps
 *   - Residential white street grid and secondary avenues (Stevens Creek Blvd, W Fremont Ave, Sunnyvale Saratoga Rd)
 *   - Silicon Valley landmarks (Apple Infinite Loop, Target, De Anza College, Cupertino)
 *   - Route shields ((85), (280))
 *   - Google watermark logo at bottom-left and GPS location crosshair FAB at bottom-right
 * - FIXED PHONE LOCATION at Cupertino destination (Stevens Creek Blvd) with Google Blue Location Dot & accuracy pulse
 * - DYNAMIC MOVING BOMB DEVICE with classic Google Maps Red Teardrop Pin Marker moving along the street navigation route
 * - Vibrant Google Navigation Blue polyline connecting moving Bomb to Phone with traveling direction pulses
 * - Turn-by-Turn Google Navigation Green banner at top with real-time Hot/Cold directional hints
 */
/**
 * REAL INDOOR BLE TRACKING MAP UI
 * - Streets removed: BLE signal indoor tracking uses a direct line-of-sight signal pathway.
 * - BOMB LOCATION IS FIXED: Placed at a fixed position with a prominent animated BOMB SYMBOL,
 *   concentric distance range rings (< 0.5m Defusal Zone, 2m, 5m, 10m+), and threat warning waves.
 * - PHONE IS MOVING: As the user walks, the Google Blue Location Dot ("You (Phone)") dynamically
 *   moves closer to the fixed Bomb based on live RSSI signal strength.
 * - DIRECT SIGNAL PATHWAY: A live, reactive signal vector links Phone -> Bomb with traveling energy pulses
 *   and real-time Hot/Cold (Warmer in Green / Colder in Red) directional feedback.
 * - Google Maps / Find My style controls: Top Navigation Banner, Google Watermark, and GPS Recenter FAB.
 */
@Composable
fun BombTacticalMapTracker(
    modifier: Modifier = Modifier,
    liveRssi: Int,
    isCritical: Boolean,
    archivoFont: FontFamily
) {
    val density = LocalDensity.current
    val minRssi = -90f
    val goalRssi = -40f

    // Calculate signal trend (Warmer vs Colder)
    var prevRssi by remember { mutableStateOf(liveRssi) }
    var trend by remember { mutableStateOf("SCANNING") }
    var deltaDb by remember { mutableStateOf(0) }

    LaunchedEffect(liveRssi) {
        if (liveRssi != -99 && prevRssi != -99) {
            val diff = liveRssi - prevRssi
            deltaDb = diff
            trend = when {
                diff >= 2 -> "WARMER"
                diff <= -2 -> "COLDER"
                else -> "STABLE"
            }
        }
        if (liveRssi != -99) {
            prevRssi = liveRssi
        }
    }

    // Distance estimation
    val (estDistanceText, estDistanceDetail) = when {
        liveRssi >= -40 && liveRssi != -99 -> Pair("< 0.5m", "AT BOMB! Within Defusal Range!")
        liveRssi in -50..-41 -> Pair("~ 1 - 2 m", "Immediate Area! Move closer to defuse!")
        liveRssi in -65..-51 -> Pair("~ 2 - 5 m", "In this room / walking towards bomb!")
        liveRssi in -78..-66 -> Pair("~ 5 - 10 m", "Searching area / follow signal beam!")
        else -> Pair("> 10 m", "Far away! Walk in different directions to locate!")
    }

    // Directional hint based on trend and distance
    val (hintText, hintManeuver, pathColor) = when {
        liveRssi >= -40 && liveRssi != -99 -> Triple("🎯 POINT BLANK! Bomb is within arm's reach!", "🎯", Color(0xFF00FF88))
        trend == "WARMER" -> Triple("🔥 GETTING WARMER! Signal rising (+${deltaDb.coerceAtLeast(1)} dBm) • Keep moving this way!", "↑", Color(0xFF00FF88))
        trend == "COLDER" -> Triple("❄️ GETTING COLDER! Signal dropping (${deltaDb} dBm) • Turn around!", "↩", Color(0xFFFF5252))
        liveRssi >= -55 -> Triple("⚡ VERY CLOSE! Step directly towards the bomb!", "↑", Color(0xFF00E5FF))
        else -> Triple("📡 Follow signal vector towards fixed bomb", "↑", Color(0xFF4285F4))
    }

    // Distance fraction from Bomb: 0.0f = directly at Bomb, 1.0f = far away at map edge
    val rawDistanceFraction = if (liveRssi == -99) 0.95f else {
        ((goalRssi - liveRssi.toFloat()) / (goalRssi - minRssi)).coerceIn(0.04f, 0.95f)
    }

    val animatedDistFraction by animateFloatAsState(
        targetValue = rawDistanceFraction,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "mapBombDist"
    )

    // Infinite animations for live map dynamics
    val infiniteTransition = rememberInfiniteTransition(label = "trackingMapAnim")

    // Blue dot location accuracy pulse for Phone
    val locationPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "locPulse"
    )

    // Traveling energy pulse along direct signal pathway
    val beamPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (liveRssi >= -55) 550 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beamPulse"
    )

    // Threat radar warning waves radiating from fixed Bomb
    val threatPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCritical) 500 else 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "threatPulse"
    )

    // Rotating sparks on bomb fuse
    val sparkAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkAngle"
    )

    // Reusable text paint for canvas distance labels
    val labelPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F8FA))
            .border(1.5.dp, Color(0xFFDADCE0), RoundedCornerShape(16.dp))
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // 1. FIXED BOMB LOCATION (Upper-Center of the Arena)
        val bombX = w / 2f
        val bombY = h * 0.38f

        // 2. DYNAMIC MOVING PHONE LOCATION
        // Moves closer to the fixed Bomb as RSSI increases towards -40 dBm
        val maxTravelDist = (h - with(density) { 52.dp.toPx() }) - bombY
        val phoneY = bombY + maxTravelDist * animatedDistFraction
        // Gentle realistic lateral walking path curvature
        val phoneX = bombX + sin(animatedDistFraction * Math.PI.toFloat() * 1.5f) * (w * 0.10f)

        // 3. CANVAS DRAWING (Concentric Range Rings, Direct Signal Pathway, Fixed Bomb Symbol, Moving Phone)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxDensity = density.density

            // A. Real Google Maps Terrain (like Image 2, without street pavement)
            drawRect(Color(0xFFF4F3F0))

            // B. Beautiful Green Parks & Preserves (from Image 2)
            // 1. Las Palmas Park (Upper Right)
            val lasPalmasPath = Path().apply {
                moveTo(w * 0.62f, h * 0.05f)
                lineTo(w * 0.90f, h * 0.05f)
                lineTo(w * 0.90f, h * 0.18f)
                lineTo(w * 0.74f, h * 0.20f)
                lineTo(w * 0.62f, h * 0.15f)
                close()
            }
            drawPath(lasPalmasPath, Color(0xFFCEEAD6))
            drawPath(lasPalmasPath, Color(0xFFA5D6A7), style = Stroke(width = 1.dp.toPx()))

            // 2. Serra Park (Center Right)
            val serraPath = Path().apply {
                moveTo(w * 0.50f, h * 0.28f)
                lineTo(w * 0.76f, h * 0.28f)
                lineTo(w * 0.78f, h * 0.42f)
                lineTo(w * 0.52f, h * 0.42f)
                close()
            }
            drawPath(serraPath, Color(0xFFCEEAD6))
            drawPath(serraPath, Color(0xFFA5D6A7), style = Stroke(width = 1.dp.toPx()))

            // 3. Ortega Park (Far Right)
            val ortegaPath = Path().apply {
                moveTo(w * 0.86f, h * 0.30f)
                lineTo(w, h * 0.30f)
                lineTo(w, h * 0.44f)
                lineTo(w * 0.86f, h * 0.44f)
                close()
            }
            drawPath(ortegaPath, Color(0xFFCEEAD6))

            // 4. McClellan Ranch Preserve (Lower Left)
            val preservePath = Path().apply {
                moveTo(w * 0.05f, h * 0.72f)
                lineTo(w * 0.32f, h * 0.70f)
                lineTo(w * 0.34f, h * 0.94f)
                lineTo(w * 0.05f, h * 0.94f)
                close()
            }
            drawPath(preservePath, Color(0xFFCEEAD6))
            drawPath(preservePath, Color(0xFFA5D6A7), style = Stroke(width = 1.dp.toPx()))

            // 5. Memorial Park / Cupertino Civic Green (Center Left)
            val memorialPath = Path().apply {
                moveTo(w * 0.48f, h * 0.60f)
                lineTo(w * 0.68f, h * 0.60f)
                lineTo(w * 0.68f, h * 0.70f)
                lineTo(w * 0.48f, h * 0.70f)
                close()
            }
            drawPath(memorialPath, Color(0xFFD4ECD5))

            // C. Green Circular Park Badges with Pine Tree Icons (Image 2)
            fun drawParkBadge(center: Offset) {
                drawCircle(Color(0xFF34A853), radius = 8.dp.toPx(), center = center)
                val treePath = Path().apply {
                    moveTo(center.x, center.y - 5.dp.toPx())
                    lineTo(center.x + 4.dp.toPx(), center.y + 1.dp.toPx())
                    lineTo(center.x + 2.dp.toPx(), center.y + 1.dp.toPx())
                    lineTo(center.x + 4.dp.toPx(), center.y + 4.dp.toPx())
                    lineTo(center.x - 4.dp.toPx(), center.y + 4.dp.toPx())
                    lineTo(center.x - 2.dp.toPx(), center.y + 1.dp.toPx())
                    lineTo(center.x - 4.dp.toPx(), center.y + 1.dp.toPx())
                    close()
                }
                drawPath(treePath, Color.White)
                drawLine(Color.White, Offset(center.x, center.y + 4.dp.toPx()), Offset(center.x, center.y + 6.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            }

            fun drawPoiBadge(center: Offset, color: Color) {
                drawCircle(color, radius = 7.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = center)
            }

            drawParkBadge(Offset(w * 0.74f, h * 0.11f)) // Las Palmas Park
            drawParkBadge(Offset(w * 0.64f, h * 0.35f)) // Serra Park
            drawParkBadge(Offset(w * 0.92f, h * 0.36f)) // Ortega Park

            // D. Landmark Badges (Apple Infinite Loop, Target, De Anza College)
            drawPoiBadge(Offset(w * 0.72f, h * 0.50f), Color(0xFF1967D2)) // Apple Infinite Loop
            drawPoiBadge(Offset(w * 0.66f, h * 0.64f), Color(0xFF1967D2)) // Target
            drawPoiBadge(Offset(w * 0.44f, h * 0.68f), Color(0xFF5F6368)) // De Anza College

            // E. Map Text Labels (Parks, POIs, Cupertino - Native Canvas Text)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                // Park Labels
                labelPaint.color = android.graphics.Color.rgb(46, 125, 50)
                labelPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                labelPaint.textSize = 8.5.sp.toPx()
                labelPaint.textAlign = Paint.Align.LEFT
                native.drawText("Las Palmas Park", w * 0.62f, h * 0.15f, labelPaint)
                native.drawText("Serra Park", w * 0.52f, h * 0.38f, labelPaint)
                native.drawText("Ortega Park", w * 0.86f, h * 0.34f, labelPaint)
                native.drawText("McClellan Ranch", w * 0.08f, h * 0.80f, labelPaint)

                // Landmark / POI Labels
                labelPaint.color = android.graphics.Color.rgb(25, 103, 210)
                labelPaint.textSize = 8.5.sp.toPx()
                native.drawText("Apple Infinite Loop", w * 0.54f, h * 0.53f, labelPaint)
                native.drawText("Target", w * 0.68f, h * 0.65f, labelPaint)
                native.drawText("De Anza College", w * 0.30f, h * 0.70f, labelPaint)

                // City Label
                labelPaint.color = android.graphics.Color.rgb(60, 64, 67)
                labelPaint.textSize = 11.sp.toPx()
                labelPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                native.drawText("Cupertino", w * 0.66f, h * 0.74f, labelPaint)
            }

            // F. Concentric Sonar Distance Range Rings Centered on FIXED BOMB
            // Ring 4: Far Zone (> 10m)
            drawCircle(
                color = Color(0xFFFF5252).copy(alpha = 0.28f),
                radius = 180.dp.toPx(),
                center = Offset(bombX, bombY),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f))
            )

            // Ring 3: Mid Zone (~ 5m)
            drawCircle(
                color = Color(0xFFFFB300).copy(alpha = 0.32f),
                radius = 125.dp.toPx(),
                center = Offset(bombX, bombY),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
            )

            // Ring 2: Near Proximity Zone (~ 2m)
            drawCircle(
                color = Color(0xFF00B0FF).copy(alpha = 0.38f),
                radius = 78.dp.toPx(),
                center = Offset(bombX, bombY),
                style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
            )

            // Ring 1: DEFUSAL TARGET ZONE (< 0.5m / Target RSSI > -40 dBm)
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = 0.14f),
                radius = 36.dp.toPx(),
                center = Offset(bombX, bombY)
            )
            drawCircle(
                color = Color(0xFF00C853),
                radius = 36.dp.toPx(),
                center = Offset(bombX, bombY),
                style = Stroke(width = 2.dp.toPx())
            )

            // D. DIRECT SIGNAL PATHWAY (Actual line-of-sight vector from Moving Phone to Fixed Bomb)
            val pathTrace = Path().apply {
                moveTo(phoneX, phoneY)
                quadraticTo(
                    (phoneX + bombX) / 2f + 8.dp.toPx(),
                    (phoneY + bombY) / 2f,
                    bombX,
                    bombY
                )
            }

            // Soft glow casing around the signal beam
            drawPath(
                path = pathTrace,
                color = pathColor.copy(alpha = 0.22f),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )

            // Core signal vector line
            drawPath(
                path = pathTrace,
                color = pathColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            // Traveling energy pulse dot flowing along the pathway from Phone -> Bomb
            val pulseT = beamPulse
            val pulseX = phoneX + (bombX - phoneX) * pulseT
            val pulseY = phoneY + (bombY - phoneY) * pulseT
            drawCircle(color = pathColor, radius = 4.dp.toPx(), center = Offset(pulseX, pulseY))
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(pulseX, pulseY))

            // E. Threat Warning Radar Waves Radiating from FIXED BOMB
            val threatMaxR = 40.dp.toPx()
            val threatR = threatMaxR * threatPulse
            val threatAlpha = ((1f - threatPulse) * 0.7f).coerceIn(0f, 1f)
            val ringWaveColor = if (isCritical) Color(0xFFFF1744) else if (liveRssi >= -50) Color(0xFF00FF88) else Color(0xFFFF9100)

            drawCircle(
                color = ringWaveColor.copy(alpha = threatAlpha),
                radius = threatR,
                center = Offset(bombX, bombY),
                style = Stroke(width = 2.dp.toPx())
            )

            // F. PROMINENT FIXED BOMB SYMBOL AT (bombX, bombY)
            val bRadius = 18.dp.toPx()

            // 1. Bomb Shadow on the floor
            drawOval(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(bombX - bRadius * 0.9f, bombY + bRadius * 0.8f),
                size = Size(bRadius * 1.8f, 7.dp.toPx())
            )

            // 2. 3D Bomb Body Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF546E7A), Color(0xFF263238), Color(0xFF10171D)),
                    center = Offset(bombX - bRadius * 0.35f, bombY - bRadius * 0.35f),
                    radius = bRadius * 1.3f
                ),
                radius = bRadius,
                center = Offset(bombX, bombY)
            )

            // 3. Bomb Outer Rim
            drawCircle(
                color = if (isCritical) Color(0xFFFF1744) else Color(0xFFFFD54F),
                radius = bRadius,
                center = Offset(bombX, bombY),
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Hazard Stripe Belt Across Bomb
            val beltW = bRadius * 1.7f
            val beltH = 6.dp.toPx()
            drawRoundRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(bombX - beltW / 2, bombY - beltH / 2),
                size = Size(beltW, beltH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // 5. Burning Fuse Cord
            val fuseTopX = bombX + 11.dp.toPx()
            val fuseTopY = bombY - bRadius - 9.dp.toPx()
            val fuseCurve = Path().apply {
                moveTo(bombX, bombY - bRadius)
                quadraticTo(bombX + 6.dp.toPx(), bombY - bRadius - 6.dp.toPx(), fuseTopX, fuseTopY)
            }
            drawPath(
                path = fuseCurve,
                color = Color(0xFF8D6E63),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 6. Fuse Spark and Rotating Fire Rays
            drawCircle(
                color = Color(0xFFFFEB3B).copy(alpha = 0.7f),
                radius = 6.dp.toPx(),
                center = Offset(fuseTopX, fuseTopY)
            )
            drawCircle(
                color = Color(0xFFFF5722),
                radius = 3.dp.toPx(),
                center = Offset(fuseTopX, fuseTopY)
            )

            val rayCount = 4
            for (i in 0 until rayCount) {
                val angle = Math.toRadians((sparkAngle + (i * 360f / rayCount)).toDouble())
                val rayLen = 6.dp.toPx()
                val rx = fuseTopX + (cos(angle) * rayLen).toFloat()
                val ry = fuseTopY + (sin(angle) * rayLen).toFloat()
                drawLine(
                    color = Color(0xFFFFD54F),
                    start = Offset(fuseTopX, fuseTopY),
                    end = Offset(rx, ry),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // G. DYNAMIC MOVING PHONE: Google Maps Blue Location Dot at (phoneX, phoneY)
            // 1. Translucent expanding accuracy pulse ring
            val pulseR = with(density) { (12.dp + 16.dp * locationPulse).toPx() }
            val pulseA = ((1f - locationPulse) * 0.35f).coerceIn(0f, 1f)
            drawCircle(Color(0xFF4285F4).copy(alpha = pulseA), radius = pulseR, center = Offset(phoneX, phoneY))

            // 2. Pure white crisp outer ring
            drawCircle(Color.White, radius = 10.5.dp.toPx(), center = Offset(phoneX, phoneY))

            // 3. Solid Google Blue inner core
            drawCircle(Color(0xFF1A73E8), radius = 8.dp.toPx(), center = Offset(phoneX, phoneY))

            // 4. White directional heading pointer towards the fixed Bomb
            val dx = bombX - phoneX
            val dy = bombY - phoneY
            val angleToBomb = kotlin.math.atan2(dy, dx)
            val arrLen = 5.dp.toPx()
            drawLine(
                Color.White,
                Offset(phoneX, phoneY),
                Offset(phoneX + (cos(angleToBomb) * arrLen).toFloat(), phoneY + (sin(angleToBomb) * arrLen).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 4. FLOATING BOMB LABEL ABOVE FIXED BOMB SYMBOL
        val bombTagWidthDp = 120.dp
        val bombTagWidthPx = with(density) { bombTagWidthDp.toPx() }
        val clampedBombTagX = (bombX - bombTagWidthPx / 2f).coerceIn(8f, w - bombTagWidthPx - 8f)
        val clampedBombTagY = (bombY - with(density) { 50.dp.toPx() }).coerceIn(with(density) { 46.dp.toPx() }, h * 0.4f)

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedBombTagX.toInt(), clampedBombTagY.toInt()) }
                .width(bombTagWidthDp)
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.5.dp, Color(0xFFFF1744), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "💣 BOMB (FIXED TARGET)",
                    color = Color(0xFFC62828),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = archivoFont,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Defuse Zone: > -40 dBm",
                    color = Color(0xFF2E7D32),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 5. FLOATING PHONE BADGE BELOW MOVING PHONE DOT
        val phonePillWidthDp = 96.dp
        val phonePillWidthPx = with(density) { phonePillWidthDp.toPx() }
        val phonePillX = (phoneX - phonePillWidthPx / 2f).coerceIn(6f, w - phonePillWidthPx - 6f)
        val phonePillY = (phoneY + with(density) { 14.dp.toPx() }).coerceAtMost(h - with(density) { 32.dp.toPx() })

        Box(
            modifier = Modifier
                .offset { IntOffset(phonePillX.toInt(), phonePillY.toInt()) }
                .width(phonePillWidthDp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.2.dp, Color(0xFF1A73E8), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A73E8))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "You: $estDistanceText",
                    fontFamily = archivoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color(0xFF202124)
                )
            }
        }

        // 6. GOOGLE WATERMARK LOGO (Bottom Left)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp)
                .shadow(2.dp, RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(text = "o", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(text = "o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(text = "g", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(text = "l", color = Color(0xFF34A853), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(text = "e", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }

        // 7. GOOGLE MAPS GPS RE-CENTER FAB (Bottom Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 8.dp)
                .size(34.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val r = size.width * 0.32f
                drawCircle(
                    color = Color(0xFF5F6368),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF5F6368),
                    radius = 2.dp.toPx(),
                    center = center
                )
                val tick = 3.dp.toPx()
                drawLine(Color(0xFF5F6368), Offset(center.x, center.y - r - tick), Offset(center.x, center.y - r + 1f), 1.5.dp.toPx())
                drawLine(Color(0xFF5F6368), Offset(center.x, center.y + r - 1f), Offset(center.x, center.y + r + tick), 1.5.dp.toPx())
                drawLine(Color(0xFF5F6368), Offset(center.x - r - tick, center.y), Offset(center.x - r + 1f, center.y), 1.5.dp.toPx())
                drawLine(Color(0xFF5F6368), Offset(center.x + r - 1f, center.y), Offset(center.x + r + tick, center.y), 1.5.dp.toPx())
            }
        }

        // 8. GOOGLE MAPS TURN-BY-TURN NAVIGATION BANNER (Top of Map)
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF0F9D58), // Google Navigation Emerald Green
            elevation = 5.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Maneuver Icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B8043)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hintManeuver,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hintText,
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = estDistanceDetail,
                        fontFamily = archivoFont,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Distance Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0B8043))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = estDistanceText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = Color(0xFFFFEB3B)
                    )
                }
            }
        }
    }
}

/**
 * Defusal Proximity Progress Gauge towards target RSSI (-40 dBm)
 */
@Composable
fun DefusalProximityGauge(
    liveRssi: Int,
    targetRssi: Int = -40,
    archivoFont: FontFamily
) {
    val minRssi = -90f
    val goalRssi = targetRssi.toFloat()

    val rawProgress = if (liveRssi == -99) 0.05f else ((liveRssi.toFloat() - minRssi) / (goalRssi - minRssi)).coerceIn(0.05f, 1.0f)

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "defuseMeter"
    )

    val isPointBlank = liveRssi >= -48
    val isNear = liveRssi in -70..-49

    val statusText = when {
        liveRssi >= targetRssi && liveRssi != -99 -> "🎯 DEFUSED! TARGET REACHED!"
        isPointBlank -> "🔥 POINT BLANK! STEP CLOSER TO DEFUSE!"
        isNear -> "⚡ APPROACHING BOMB... SIGNAL STRONG!"
        else -> "📡 FAR! MOVE CLOSER QUICKLY!"
    }

    val statusColor = when {
        liveRssi >= targetRssi && liveRssi != -99 -> Color(0xFF00FF88)
        isPointBlank -> Color(0xFFFF9100)
        isNear -> Color(0xFF00E5FF)
        else -> Color(0xFFFF5252)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontFamily = archivoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF08101A))
                .border(1.5.dp, Color(0xFF1E3A5F), RoundedCornerShape(9.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF1744),
                                Color(0xFFFF9100),
                                Color(0xFF00E5FF),
                                Color(0xFF00FF88)
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "START: -90 dBm",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "DEFUSE: $targetRssi dBm",
                color = Color(0xFF00FF88),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Victory Dialog when user wins (Device found with RSSI >= -40 dBm within 3 minutes)
 * HERO IDENTITY IS TRIUMPHANTLY REVEALED HERE!
 */
@Composable
fun BombDefusedVictoryDialog(
    heroName: String,
    timeRemainingFormatted: String,
    finalRssi: Int,
    archivoFont: FontFamily,
    onPlayAgain: () -> Unit,
    onBackToList: () -> Unit
) {
    val heroImageRes = HeroRegistry.getVictoryPopupImage(heroName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0xFF0A1526),
        border = BorderStroke(2.5.dp, Color(0xFF00FF88)),
        elevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "★ MISSION ACCOMPLISHED ★",
                color = Color(0xFF00FF88),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Hero victory image - REVEALED!
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00FF88).copy(alpha = 0.15f))
                    .border(3.dp, Color(0xFF00FF88), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = heroImageRes),
                    contentDescription = heroName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "BOMB SAFELY DEFUSED! 🎉",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = archivoFont,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // REVEALED IDENTITY BADGE
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF00FF88).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF00FF88), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "IDENTITY REVEALED: ${heroName.replace("_", " ").uppercase()}",
                    color = Color(0xFF00FF88),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = archivoFont,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "You defused the ticking threat and rescued ${heroName.replace("_", " ")}!",
                color = Color(0xFFFFD54F),
                fontSize = 12.sp,
                fontFamily = archivoFont,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stats box
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF060D17),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TIME LEFT",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = timeRemainingFormatted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF88),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FINAL RSSI",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$finalRssi dBm",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Button(
                onClick = onPlayAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00C853))
            ) {
                Text(
                    text = "PLAY AGAIN (RESTART 3 MIN)",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    fontFamily = archivoFont
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onBackToList,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = Color.Transparent)
            ) {
                Text(
                    text = "DEFUSE ANOTHER BOMB",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = archivoFont
                )
            }
        }
    }
}

/**
 * ACTUAL CINEMATIC BOMB BLAST EXPLOSION OVERLAY
 * Features full-screen screen rumble/shake, blinding white flash, expanding fireballs,
 * shockwave blast rings, flying fiery particle embers, and explosive defeat card!
 */
@Composable
fun BombBlastExplosionOverlay(
    heroName: String,
    archivoFont: FontFamily,
    onTryAgain: () -> Unit,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Trigger haptic feedback immediately on blast
    LaunchedEffect(Unit) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}
    }

    // Master blast animation timeline: 0f -> 1f over 2400ms
    val blastAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        blastAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
    }

    val progress = blastAnim.value

    // Screen Shake effect: peaks in early blast, then fades out
    val shakeIntensity = ((1f - progress / 0.7f).coerceIn(0f, 1f)) * 22f
    val shakeX = sin(progress * 75f) * shakeIntensity
    val shakeY = cos(progress * 60f) * shakeIntensity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(shakeX.toInt(), shakeY.toInt()) },
        contentAlignment = Alignment.Center
    ) {
        // Fullscreen Explosive Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // 1. Black smoky background
            drawRect(color = Color.Black.copy(alpha = (progress * 1.2f).coerceAtMost(0.92f)))

            // 2. Initial blinding flash (0.0 to 0.3 progress)
            if (progress < 0.35f) {
                val flashAlpha = ((0.35f - progress) / 0.35f).coerceIn(0f, 1f)
                drawRect(color = Color.White.copy(alpha = flashAlpha))
            }

            // 3. Multi-layered Expanding Fireballs
            val maxBlastR = minOf(w, h) * 0.95f

            // Outer Fiery Crimson Shockwave
            val outerR = (maxBlastR * (progress * 1.5f)).coerceIn(10f, maxBlastR * 1.8f)
            val outerAlpha = ((1f - progress) * 0.85f).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF9100).copy(alpha = outerAlpha),
                        Color(0xFFFF1744).copy(alpha = outerAlpha * 0.8f),
                        Color(0xFF260505).copy(alpha = outerAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = outerR
                ),
                radius = outerR,
                center = Offset(cx, cy)
            )

            // Inner Blinding Core Fireball
            val innerR = (maxBlastR * 0.65f * (progress * 1.2f)).coerceIn(5f, maxBlastR * 0.75f)
            val innerAlpha = ((1f - progress * 1.1f) * 0.95f).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = innerAlpha),
                        Color(0xFFFFEA00).copy(alpha = innerAlpha),
                        Color(0xFFFF3D00).copy(alpha = innerAlpha * 0.7f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = innerR
                ),
                radius = innerR,
                center = Offset(cx, cy)
            )

            // 4. Multiple Rapid Shockwave Rings
            val ringCount = 3
            for (rIdx in 0 until ringCount) {
                val delayFactor = rIdx * 0.15f
                val ringProg = ((progress - delayFactor) / (1f - delayFactor)).coerceIn(0f, 1f)
                if (ringProg > 0f) {
                    val ringRadius = maxBlastR * ringProg * 1.4f
                    val ringAlpha = ((1f - ringProg) * 0.8f).coerceIn(0f, 1f)
                    drawCircle(
                        color = (if (rIdx % 2 == 0) Color(0xFFFFD54F) else Color(0xFFFF1744)).copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = (6f - ringProg * 4f).coerceAtLeast(1.5f).dp.toPx())
                    )
                }
            }

            // 5. 40+ Flying Fiery Embers & Shrapnel Debris Particles
            val pxDensity = this.density
            val particleCount = 42
            for (i in 0 until particleCount) {
                val angle = Math.toRadians((i * (360.0 / particleCount) + (i * 13)).toDouble())
                val speed = (180f + (i * 37 % 280).toFloat()) * pxDensity
                val pDist = speed * (progress * 1.4f)
                val px = cx + (cos(angle) * pDist).toFloat()
                // Gravity pulls particles downward over time
                val py = cy + (sin(angle) * pDist).toFloat() + (progress * progress * 80f * pxDensity)

                val pAlpha = ((1f - progress * 1.1f) * 0.9f).coerceIn(0f, 1f)
                val pRadius = ((4.5f - progress * 2.5f) * pxDensity).coerceAtLeast(1.5f)

                val pColor = when (i % 3) {
                    0 -> Color(0xFFFFEB3B)
                    1 -> Color(0xFFFF5722)
                    else -> Color(0xFFFF1744)
                }

                drawCircle(
                    color = pColor.copy(alpha = pAlpha),
                    radius = pRadius,
                    center = Offset(px, py)
                )
            }
        }

        // 6. Giant "💥 KABOOM! 💥" Impact Title (Explosion Phase)
        if (progress < 0.65f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "💥 KABOOM! 💥",
                    fontFamily = archivoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 44.sp,
                    color = Color(0xFFFF1744),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "BOMB DETONATED!",
                    fontFamily = archivoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color(0xFFFFD54F),
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 7. Defeat Result Card (Reveals smoothly once blast peak settles at progress >= 0.55f)
        if (progress >= 0.55f) {
            val cardAlpha = ((progress - 0.55f) / 0.35f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xFF1B0707).copy(alpha = cardAlpha * 0.96f),
                    border = BorderStroke(2.5.dp, Color(0xFFFF1744).copy(alpha = cardAlpha)),
                    elevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "💥 KABOOM! BOMB DETONATED! 💥",
                            color = Color(0xFFFF1744),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = archivoFont,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Big Explosion Icon
                        Text(
                            text = "💣🔥💥",
                            fontSize = 46.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "TIME EXPIRED (00:00)",
                            color = Color(0xFFFF5252),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Identity revealed after blast
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3B0D0D))
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "TARGET BEACON WAS: ${heroName.replace("_", " ").uppercase()}",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = archivoFont,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You couldn't reach the device in time (RSSI never reached -40 dBm). The bomb blasted and the mission was lost!",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontFamily = archivoFont,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Button(
                            onClick = onTryAgain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD50000))
                        ) {
                            Text(
                                text = "TRY AGAIN (RESTART 3 MIN)",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                fontFamily = archivoFont
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onExit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = Color.Transparent)
                        ) {
                            Text(
                                text = "RETURN TO GAME MENU",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = archivoFont
                            )
                        }
                    }
                }
            }
        }
    }
}
