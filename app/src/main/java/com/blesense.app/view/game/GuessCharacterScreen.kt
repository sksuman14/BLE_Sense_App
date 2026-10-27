package com.blesense.app.view.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
import com.blesense.app.model.game.HeroRegistry
import com.blesense.app.viewmodel.game.BleGameViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GuessCharacterScreen(
    navController: NavController,
    gameViewModel: BleGameViewModel,
    realHeroName: String,
    deviceAddress: String
) {
    val uiState by gameViewModel.uiState.collectAsState()
    val archivoFont = FontFamily(Font(R.font.archivo_semibold))

    // Start BLE scan for this device
    LaunchedEffect(deviceAddress) {
        gameViewModel.startScan()
    }

    var showVictoryDialog by remember { mutableStateOf(false) }

    // Hero is decoded when in target proximity (-40 dBm or found)
    val isDecoded = uiState.isDeviceFoundChecked || (uiState.liveRssi >= -40 && uiState.liveRssi != -99)

    // Trigger victory when reached
    LaunchedEffect(isDecoded) {
        if (isDecoded && !showVictoryDialog) {
            showVictoryDialog = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Cute Chibi Heroes Twilight Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_twilight),
            contentDescription = "Cute Superhero Twilight Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Translucent gradient scrim for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color(0xFF040C16).copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // 2. Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Standard Properly Visible Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        gameViewModel.clearSelectedDevice()
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1B2B).copy(alpha = 0.90f))
                        .border(1.5.dp, Color(0xFFFFD600), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isDecoded) "🎉 HERO DECODED! 🎉" else "📡 RADAR DECODER",
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDecoded) Color(0xFF00FF88) else Color(0xFFFFD600)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(42.dp))
            }

            // Beacon Signal Info Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101F33).copy(alpha = 0.88f))
                    .border(1.dp, Color(0xFF00B0FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isDecoded) realHeroName.replace("_", " ") else "Unknown Superhero Signal",
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isDecoded) Color(0xFF00FF88) else Color.White
                    )
                    Text(
                        text = "MAC: $deviceAddress",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "RSSI: ${uiState.liveRssi} dBm",
                        fontFamily = archivoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (uiState.liveRssi >= -55) Color(0xFF00FF88) else Color(0xFFFF9100)
                    )
                    Text(
                        text = "TARGET: > -40 dBm",
                        fontSize = 10.sp,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. HIGHLY EFFICIENT MODERN RADAR TRACKING UI
            ModernRadarTrackingUi(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                realHeroName = realHeroName,
                isDecoded = isDecoded,
                liveRssi = uiState.liveRssi,
                desiredTargetRssi = -40,
                archivoFont = archivoFont
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Large Blinking Dynamic RSSI Status Banner
            val rssi = uiState.liveRssi
            val stateColor = when {
                isDecoded -> Color(0xFF00FF88)
                rssi in -55..-41 -> Color(0xFF00E5FF)
                rssi in -70..-56 -> Color(0xFFFFD600)
                rssi in -80..-71 -> Color(0xFFFF9100)
                else -> Color(0xFFFF5252)
            }

            val stateDisplayText = when {
                isDecoded -> "🎯 HERO DECODED!"
                rssi in -55..-41 -> "⚡ DEVICE IS VERY NEAR!"
                rssi in -70..-56 -> "🔥 DEVICE IS NEAR"
                rssi in -80..-71 -> "🏃 DEVICE IS APPROACHING"
                else -> "📡 DEVICE IS FAR"
            }

            val blinkTransition = rememberInfiniteTransition(label = "proximityBlink")
            val blinkAlpha by blinkTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = if (rssi >= -55) 400 else 750, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blinkAlpha"
            )

            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp, start = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF091422).copy(alpha = 0.92f))
                    .border(2.dp, stateColor.copy(alpha = blinkAlpha), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stateDisplayText,
                    fontFamily = archivoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = stateColor.copy(alpha = blinkAlpha),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = stateColor.copy(alpha = 0.6f),
                            offset = Offset(0f, 0f),
                            blurRadius = 10f
                        )
                    )
                )
            }
        }

        // 5. Decoded Victory Popup
        if (showVictoryDialog) {
            Dialog(
                onDismissRequest = { showVictoryDialog = false }
            ) {
                HeroRevealOverlayCompose(
                    heroName = realHeroName,
                    heroImageRes = HeroRegistry.getVictoryPopupImage(realHeroName),
                    onDismiss = {
                        showVictoryDialog = false
                    }
                )
            }
        }
    }
}

/**
 * HIGHLY EFFICIENT MODERN RADAR TRACKING UI
 * Features 360-degree continuous rotating radar sweep with phosphor persistence trail,
 * concentric tactical range rings, polar degree calibrations, live distance estimation,
 * cryptographic decryption progress gauge, and dynamic target ping reticle.
 */
@Composable
fun ModernRadarTrackingUi(
    modifier: Modifier = Modifier,
    realHeroName: String,
    isDecoded: Boolean,
    liveRssi: Int,
    desiredTargetRssi: Int = -40,
    archivoFont: FontFamily
) {
    val density = LocalDensity.current
    val minRssi = -90f
    val goalRssi = desiredTargetRssi.toFloat()

    // Normalized distance from center: 0.05f (at bullseye lock) to 0.92f (at outer boundary)
    val rawDistanceFraction = if (isDecoded) 0f else {
        ((goalRssi - liveRssi.toFloat()) / (goalRssi - minRssi)).coerceIn(0.06f, 0.94f)
    }

    val animatedDistFraction by animateFloatAsState(
        targetValue = rawDistanceFraction,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "radarDist"
    )

    // Decryption percentage: 0% to 100%
    val rawDecryption = if (isDecoded) 1.0f else {
        ((liveRssi.toFloat() - minRssi) / (goalRssi - minRssi)).coerceIn(0.05f, 1.0f)
    }
    val animatedDecryption by animateFloatAsState(
        targetValue = rawDecryption,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "decryptionMeter"
    )

    // Infinite animations for military radar
    val infiniteTransition = rememberInfiniteTransition(label = "radarLoops")

    // Continuous 360° sweeping beam angle
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    // Sonar ping ripple radiating from target blip
    val blipPulse by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blipPulse"
    )

    // Estimated distance in meters
    val estDistanceStr = when {
        isDecoded || liveRssi >= -40 -> "< 0.5 m"
        liveRssi in -50..-41 -> "~ 1.2 m"
        liveRssi in -65..-51 -> "~ 3.5 m"
        liveRssi in -78..-66 -> "~ 7.0 m"
        else -> "> 12.0 m"
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF030A14))
            .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(10.dp)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val cy = h / 2f + 8.dp.value

        val maxRadius = minOf(w * 0.44f, h * 0.40f)

        // Tactical blip coordinate (placed at 45 degree bearing in upper-right quadrant)
        val blipAngleRad = Math.toRadians(45.0)
        val blipDistancePx = maxRadius * animatedDistFraction
        val blipX = cx + (cos(blipAngleRad) * blipDistancePx).toFloat()
        val blipY = cy - (sin(blipAngleRad) * blipDistancePx).toFloat()

        // 1. Full Radar Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxDensity = this.density

            // Background Deep Radar Scope Circle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF081C30),
                        Color(0xFF04101D),
                        Color(0xFF02070E)
                    ),
                    center = Offset(cx, cy),
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = Offset(cx, cy)
            )

            // Outer Scope Bezel
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                radius = maxRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Polar Degree Tick Marks around Scope Edge
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = deg % 45 == 0
                val tickLen = (if (isMajor) 9.dp else 5.dp).toPx()
                val startX = cx + (cos(rad) * (maxRadius - tickLen)).toFloat()
                val startY = cy + (sin(rad) * (maxRadius - tickLen)).toFloat()
                val endX = cx + (cos(rad) * maxRadius).toFloat()
                val endY = cy + (sin(rad) * maxRadius).toFloat()

                drawLine(
                    color = if (isMajor) Color(0xFF00FF88) else Color(0xFF00E5FF).copy(alpha = 0.4f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = (if (isMajor) 2f else 1f) * pxDensity
                )
            }

            // Concentric Range Rings
            // Ring 1 (Far - 100%)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                radius = maxRadius * 0.90f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
            )
            // Ring 2 (Mid - 65%)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                radius = maxRadius * 0.65f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
            )
            // Ring 3 (Near - 35%)
            drawCircle(
                color = Color(0xFFFFD600).copy(alpha = 0.45f),
                radius = maxRadius * 0.35f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Center Bullseye Target Lock Ring (-40 dBm Goal)
            drawCircle(
                color = if (isDecoded) Color(0xFF00FF88) else Color(0xFF00E5FF).copy(alpha = 0.7f),
                radius = 18.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = if (isDecoded) Color(0xFF00FF88).copy(alpha = 0.25f) else Color(0xFF00E5FF).copy(alpha = 0.15f),
                radius = 18.dp.toPx(),
                center = Offset(cx, cy)
            )

            // Crosshair Reticle Axes
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.30f),
                start = Offset(cx - maxRadius, cy),
                end = Offset(cx + maxRadius, cy),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.30f),
                start = Offset(cx, cy - maxRadius),
                end = Offset(cx, cy + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Continuous Sweeping Radar Beam with Phosphor Trail
            val sweepRad = Math.toRadians(sweepAngle.toDouble())
            val beamEndX = cx + (cos(sweepRad) * maxRadius).toFloat()
            val beamEndY = cy + (sin(sweepRad) * maxRadius).toFloat()

            // Leading Beam Line
            drawLine(
                color = Color(0xFF00FF88),
                start = Offset(cx, cy),
                end = Offset(beamEndX, beamEndY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Phosphor Trailing Glow (Fanned Arc behind sweep)
            val trailSlices = 12
            for (t in 1..trailSlices) {
                val backAngleRad = Math.toRadians((sweepAngle - t * 3.5f).toDouble())
                val trailEndX = cx + (cos(backAngleRad) * maxRadius).toFloat()
                val trailEndY = cy + (sin(backAngleRad) * maxRadius).toFloat()
                val alpha = ((trailSlices - t).toFloat() / trailSlices * 0.15f).coerceIn(0f, 1f)
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                    start = Offset(cx, cy),
                    end = Offset(trailEndX, trailEndY),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Target Sonar Ping Waves expanding from blip position
            val rippleRadius = 14.dp.toPx() + blipPulse * 30.dp.toPx()
            val rippleAlpha = ((1f - blipPulse) * 0.8f).coerceIn(0f, 1f)
            drawCircle(
                color = (if (isDecoded) Color(0xFF00FF88) else Color(0xFFFF9100)).copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = Offset(blipX, blipY),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 2. Interactive Target Blip Reticle & Character Avatar
        val blipSizeDp = if (isDecoded) 52.dp else 42.dp
        val blipSizePx = with(density) { blipSizeDp.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (blipX - blipSizePx / 2f).toInt(),
                        y = (blipY - blipSizePx / 2f).toInt()
                    )
                }
                .size(blipSizeDp),
            contentAlignment = Alignment.Center
        ) {
            if (isDecoded) {
                // Decoded Hero Avatar inside Glowing Emerald Target Frame
                Box(
                    modifier = Modifier
                        .size(blipSizeDp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF88).copy(alpha = 0.25f))
                        .border(2.5.dp, Color(0xFF00FF88), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = HeroRegistry.getCharacterImage(realHeroName)),
                        contentDescription = realHeroName,
                        modifier = Modifier
                            .size(blipSizeDp - 8.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                // Tactical Mystery Reticle with Question Mark
                Box(
                    modifier = Modifier
                        .size(blipSizeDp)
                        .clip(CircleShape)
                        .background(Color(0xFF07192C).copy(alpha = 0.90f))
                        .border(2.dp, Color(0xFFFFD600), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.question_mark2),
                        contentDescription = "Unknown Signal Target",
                        modifier = Modifier.size(26.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // 3. Floating Tactical HUD Tag attached to Target Blip
        val tagWidthDp = 130.dp
        val tagWidthPx = with(density) { tagWidthDp.toPx() }
        val clampedTagX = (blipX - tagWidthPx / 2f).coerceIn(10f, w - tagWidthPx - 10f)
        val clampedTagY = if (animatedDistFraction < 0.35f) {
            (blipY + with(density) { 32.dp.toPx() }).coerceAtMost(h - 60.dp.value)
        } else {
            (blipY - with(density) { 46.dp.toPx() }).coerceAtLeast(10f)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedTagX.toInt(), clampedTagY.toInt()) }
                .width(tagWidthDp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF071424).copy(alpha = 0.92f))
                .border(1.5.dp, if (isDecoded) Color(0xFF00FF88) else Color(0xFFFFD600), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isDecoded) "🎯 DECODED TARGET" else "🔒 ENCRYPTED TARGET",
                    color = if (isDecoded) Color(0xFF00FF88) else Color(0xFFFFD600),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "DIST: $estDistanceStr | RSSI: $liveRssi",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 4. Top Decryption Matrix HUD Panel
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDecoded) "DECRYPTION COMPLETE" else "DECRYPTING SIGNAL WAVEFORM...",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (isDecoded) Color(0xFF00FF88) else Color(0xFF00E5FF)
                )

                Text(
                    text = "${(animatedDecryption * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = if (isDecoded) Color(0xFF00FF88) else Color(0xFFFFD600)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Decryption Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF081C30))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedDecryption)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF1744), Color(0xFFFFD600), Color(0xFF00FF88))
                            )
                        )
                )
            }
        }

        // 5. Tactical Radar Scope Footer Info
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BRG: 045° TAC",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF00E5FF).copy(alpha = 0.8f)
            )

            Text(
                text = "EST: $estDistanceStr",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color(0xFFFFD600)
            )

            Text(
                text = "LOCK: -40 dBm",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF00FF88).copy(alpha = 0.8f)
            )
        }
    }
}
