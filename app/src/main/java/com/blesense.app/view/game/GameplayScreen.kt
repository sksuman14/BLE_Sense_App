package com.blesense.app.view.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.blesense.app.model.game.HeroRegistry
import com.blesense.app.viewmodel.game.BleGameViewModel
import kotlin.math.sin

@Composable
fun GameplayScreen(
    navController: NavController,
    gameViewModel: BleGameViewModel,
    deviceName: String,
    deviceAddress: String
) {
    val uiState by gameViewModel.uiState.collectAsState()
    val archivoFont = FontFamily(Font(R.font.archivo_semibold))

    LaunchedEffect(deviceAddress) {
        gameViewModel.startScan()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Cute Chibi Heroes Sunset Background
        Image(
            painter = painterResource(id = R.drawable.cute_heroes_sunset),
            contentDescription = "Cute Superhero Squad Sunset",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Soft gradient scrim for legibility of road and texts
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070F1C).copy(alpha = 0.55f),
                            Color(0xFF0C1929).copy(alpha = 0.70f),
                            Color(0xFF070F1C).copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // 2. Back button
        IconButton(
            onClick = {
                gameViewModel.clearSelectedDevice()
                navController.popBackStack()
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 14.dp, top = 14.dp)
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

        // 3. Central Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Superhero HUD Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0xFF0F1B2B).copy(alpha = 0.90f),
                border = BorderStroke(1.5.dp, Color(0xFF00B0FF).copy(alpha = 0.6f)),
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceName.replace("_", " "),
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFFFFD600)
                        )
                        Text(
                            text = "MAC: $deviceAddress",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "RSSI: ${uiState.liveRssi} dBm",
                            fontFamily = archivoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (uiState.liveRssi >= -55) Color(0xFF00FF88) else Color(0xFFFF9100)
                        )

                        // CheckBox for Device Found
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.isDeviceFoundChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF00FF88),
                                    checkmarkColor = Color(0xFF0A1526),
                                    uncheckedColor = Color.White
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Found",
                                fontFamily = archivoFont,
                                color = if (uiState.isDeviceFoundChecked) Color(0xFF00FF88) else Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Cartoon Road and Superhero Animated Tracking Area
            CartoonRoadTracker(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                heroName = deviceName,
                liveRssi = uiState.liveRssi,
                desiredTargetRssi = -40
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Proximity State Banner with Blinking & Dynamic RSSI Color
            val rssi = uiState.liveRssi
            val isFound = uiState.isDeviceFoundChecked || (rssi >= -40 && rssi != -99)
            val stateColor = when {
                isFound -> Color(0xFF00FF88)        // Glowing Emerald Green
                rssi in -55..-41 -> Color(0xFF00E5FF) // Electric Cyan
                rssi in -70..-56 -> Color(0xFFFFD600) // Bright Gold/Yellow
                rssi in -80..-71 -> Color(0xFFFF9100) // Vivid Orange
                else -> Color(0xFFFF5252)             // Alert Red
            }

            val stateDisplayText = when {
                isFound -> "🎯 DEVICE IS FOUND!"
                rssi in -55..-41 -> "⚡ DEVICE IS VERY NEAR!"
                rssi in -70..-56 -> "🔥 DEVICE IS NEAR"
                rssi in -80..-71 -> "🏃 DEVICE IS APPROACHING"
                else -> "📡 DEVICE IS FAR"
            }

            // Blinking animation for text
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
                    .padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF091422).copy(alpha = 0.92f))
                    .border(2.dp, stateColor.copy(alpha = blinkAlpha), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
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
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = stateColor.copy(alpha = 0.6f),
                            offset = Offset(0f, 0f),
                            blurRadius = 10f
                        )
                    )
                )
            }
        }

        // 5. Target Secured Celebration Overlay (With "You won")
        if (uiState.showFoundPopup) {
            val heroName = uiState.foundDeviceName ?: deviceName
            val heroImageRes = HeroRegistry.getVictoryPopupImage(heroName)

            Dialog(
                onDismissRequest = { gameViewModel.dismissPopup() }
            ) {
                HeroRevealOverlayCompose(
                    heroName = heroName,
                    heroImageRes = heroImageRes,
                    onDismiss = {
                        gameViewModel.dismissPopup()
                    }
                )
            }
        }
    }
}

/**
 * Cartoon Proximity Tag Information Model
 */
private data class ProximityTagInfo(
    val title: String,
    val color: Color,
    val subtitle: String,
    val backgroundColor: Color
)

/**
 * Cartoon Road and Superhero Animated Tracker
 * Draws a cartoon landscape with rolling hills, drifting clouds, cartoon trees,
 * a winding cartoon road with kerb stripes, and an animated superhero that moves
 * towards the target destination based on live RSSI (from -90 dBm to target -40 dBm).
 * Also features dynamic proximity tags ("Far", "Approaching", "Near", "Target Reached").
 */
@Composable
fun CartoonRoadTracker(
    modifier: Modifier = Modifier,
    heroName: String,
    liveRssi: Int,
    desiredTargetRssi: Int = -40
) {
    val density = LocalDensity.current
    val minRssi = -90f
    val targetRssi = desiredTargetRssi.toFloat()

    // Normalized progress: 0.05f (Far/Start) to 1.0f (Target Reached)
    // Strictly computed from live RSSI so the superhero moves smoothly on every RSSI change
    val rawProgress = ((liveRssi.toFloat() - minRssi) / (targetRssi - minRssi)).coerceIn(0.05f, 1.0f)

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "heroProgress"
    )

    // Infinite animations for cartoon liveliness
    val infiniteTransition = rememberInfiniteTransition(label = "cartoonLoop")

    // Running bounce / bobbing animation
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // Character tilt while running
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    // Road dashed line crawl
    val dashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dash"
    )

    // Target beacon radar pulse
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beaconPulse"
    )

    // Cartoon clouds drift
    val cloudOffset by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud"
    )

    // Dynamic Tag Info based on proximity to target RSSI
    val diffToGoal = (desiredTargetRssi - liveRssi).coerceAtLeast(0)
    val tagInfo = when {
        liveRssi >= desiredTargetRssi -> ProximityTagInfo(
            title = "🎯 TARGET REACHED!",
            color = Color(0xFF00FF88),
            subtitle = "Desired goal reached ($desiredTargetRssi dBm)",
            backgroundColor = Color(0xFF062312)
        )
        liveRssi >= -48 -> ProximityTagInfo(
            title = "🔥 VERY NEAR TARGET",
            color = Color(0xFFFF9100),
            subtitle = "$diffToGoal dBm away from goal ($desiredTargetRssi dBm)",
            backgroundColor = Color(0xFF2C1604)
        )
        liveRssi >= -65 -> ProximityTagInfo(
            title = "⚡ APPROACHING TARGET",
            color = Color(0xFFFFD600),
            subtitle = "$diffToGoal dBm to target ($desiredTargetRssi dBm)",
            backgroundColor = Color(0xFF2B2504)
        )
        liveRssi >= -78 -> ProximityTagInfo(
            title = "🏃 CLOSING IN (NEAR)",
            color = Color(0xFF00E5FF),
            subtitle = "$diffToGoal dBm to target ($desiredTargetRssi dBm)",
            backgroundColor = Color(0xFF041E28)
        )
        else -> ProximityTagInfo(
            title = "📡 FAR FROM TARGET",
            color = Color(0xFFFF5252),
            subtitle = "$diffToGoal dBm to target ($desiredTargetRssi dBm)",
            backgroundColor = Color(0xFF2B0707)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF142C44),
                        Color(0xFF1D4268),
                        Color(0xFF16324F)
                    )
                )
            )
            .border(2.dp, Color(0xFF00B0FF).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topMarginPx = with(density) { 52.dp.toPx() }
        val bottomMarginPx = with(density) { 52.dp.toPx() }

        // Road centerline parametric equation
        fun calcRoadPoint(t: Float): Offset {
            val usableH = (h - topMarginPx - bottomMarginPx).coerceAtLeast(10f)
            val y = (h - bottomMarginPx) - (t * usableH)
            // S-curve winding from center bottom to center top
            val angle = t * 2.0f * Math.PI.toFloat()
            val x = (w / 2f) + sin(angle) * (w * 0.22f)
            return Offset(x, y)
        }

        // Background Cartoon Scene Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Cartoon Rolling Hills in background
            val hillPath1 = Path().apply {
                moveTo(0f, h * 0.42f)
                quadraticTo(w * 0.35f, h * 0.26f, w * 0.72f, h * 0.44f)
                quadraticTo(w * 0.88f, h * 0.40f, w, h * 0.48f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = hillPath1,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
                )
            )

            val hillPath2 = Path().apply {
                moveTo(0f, h * 0.62f)
                quadraticTo(w * 0.28f, h * 0.46f, w * 0.65f, h * 0.64f)
                quadraticTo(w * 0.85f, h * 0.58f, w, h * 0.70f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = hillPath2,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF43A047), Color(0xFF2E7D32))
                )
            )

            // 2. Cartoon Drifting Clouds
            fun drawCartoonCloud(cx: Float, cy: Float, scale: Float) {
                val cloudColor = Color.White.copy(alpha = 0.82f)
                val r = 14.dp.toPx() * scale
                drawCircle(cloudColor, radius = r, center = Offset(cx, cy))
                drawCircle(cloudColor, radius = r * 1.35f, center = Offset(cx + r * 0.9f, cy - r * 0.25f))
                drawCircle(cloudColor, radius = r * 0.95f, center = Offset(cx + r * 1.8f, cy))
                drawRoundRect(
                    cloudColor,
                    topLeft = Offset(cx - r * 0.4f, cy),
                    size = Size(r * 2.6f, r * 0.9f),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            drawCartoonCloud(w * 0.18f + cloudOffset, h * 0.12f, 1.0f)
            drawCartoonCloud(w * 0.74f - cloudOffset * 0.7f, h * 0.20f, 0.85f)

            // 3. Build Road Path
            val roadPath = Path().apply {
                val steps = 80
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val pt = calcRoadPoint(t)
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }

            // Road Kerb Borders (Alternating Comic Red & White Racing Kerbs)
            val kerbWidth = 66.dp.toPx()
            drawPath(
                path = roadPath,
                color = Color(0xFFD32F2F),
                style = Stroke(width = kerbWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = roadPath,
                color = Color.White,
                style = Stroke(
                    width = kerbWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                )
            )

            // Road Asphalt Surface
            drawPath(
                path = roadPath,
                color = Color(0xFF263238),
                style = Stroke(width = 52.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Road Centerline (Animated Dashed Cartoon Yellow)
            drawPath(
                path = roadPath,
                color = Color(0xFFFFD54F),
                style = Stroke(
                    width = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), dashOffset)
                )
            )

            // 4. Cartoon Roadside Trees
            fun drawCartoonTree(x: Float, y: Float, treeScale: Float = 1f) {
                val trunkW = 7.dp.toPx() * treeScale
                val trunkH = 18.dp.toPx() * treeScale
                drawRoundRect(
                    color = Color(0xFF6D4C41),
                    topLeft = Offset(x - trunkW / 2, y),
                    size = Size(trunkW, trunkH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                val leafR = 16.dp.toPx() * treeScale
                drawCircle(Color(0xFF1B5E20), radius = leafR, center = Offset(x, y - leafR * 0.4f))
                drawCircle(Color(0xFF388E3C), radius = leafR * 0.85f, center = Offset(x - leafR * 0.3f, y - leafR * 0.6f))
                drawCircle(Color(0xFF4CAF50), radius = leafR * 0.8f, center = Offset(x + leafR * 0.3f, y - leafR * 0.6f))
                drawCircle(Color(0xFF81C784), radius = leafR * 0.55f, center = Offset(x, y - leafR * 0.95f))
            }

            drawCartoonTree(w * 0.10f, h * 0.35f, 1.1f)
            drawCartoonTree(w * 0.12f, h * 0.78f, 0.95f)
            drawCartoonTree(w * 0.88f, h * 0.42f, 1.0f)
            drawCartoonTree(w * 0.86f, h * 0.82f, 1.15f)

            // 5. Road Milestone Posts
            fun drawMilestonePost(t: Float, label: String, color: Color) {
                val pt = calcRoadPoint(t)
                val postX = if (pt.x > w / 2f) pt.x + 36.dp.toPx() else pt.x - 36.dp.toPx()
                drawCircle(color, radius = 5.dp.toPx(), center = Offset(postX, pt.y))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(postX, pt.y))
            }
            drawMilestonePost(0.35f, "FAR", Color(0xFFFF5252))
            drawMilestonePost(0.70f, "NEAR", Color(0xFFFFD600))

            // 6. Start Line at Bottom
            val startPt = calcRoadPoint(0f)
            drawLine(
                color = Color.White,
                start = Offset(startPt.x - 26.dp.toPx(), startPt.y),
                end = Offset(startPt.x + 26.dp.toPx(), startPt.y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 7. Finish Gate & Target Radar Beacon at Top
            val goalPt = calcRoadPoint(1f)

            // Pulsing sonar rings radiating from target beacon
            val r1 = 26.dp.toPx() + beaconPulse * 34.dp.toPx()
            drawCircle(
                color = Color(0xFF00FF88).copy(alpha = ((1f - beaconPulse) * 0.6f).coerceIn(0f, 1f)),
                radius = r1,
                center = goalPt,
                style = Stroke(width = 2.5.dp.toPx())
            )
            val pulse2 = (beaconPulse + 0.5f) % 1.0f
            val r2 = 14.dp.toPx() + pulse2 * 34.dp.toPx()
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = ((1f - pulse2) * 0.5f).coerceIn(0f, 1f)),
                radius = r2,
                center = goalPt,
                style = Stroke(width = 2.dp.toPx())
            )

            // Finish Line Bar
            drawLine(
                color = Color(0xFF00FF88),
                start = Offset(goalPt.x - 26.dp.toPx(), goalPt.y),
                end = Offset(goalPt.x + 26.dp.toPx(), goalPt.y),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF00FF88).copy(alpha = 0.35f),
                radius = 20.dp.toPx(),
                center = goalPt
            )
            drawCircle(
                color = Color(0xFF00FF88),
                radius = 20.dp.toPx(),
                center = goalPt,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Live Hero Position Coordinates
        val heroPos = calcRoadPoint(animatedProgress)
        val heroSizeDp = 68.dp
        val heroSizePx = with(density) { heroSizeDp.toPx() }

        // Cartoon Ground Shadow beneath Hero
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                color = Color.Black.copy(alpha = 0.38f),
                topLeft = Offset(heroPos.x - heroSizePx * 0.28f, heroPos.y + heroSizePx * 0.32f),
                size = Size(heroSizePx * 0.56f, 12.dp.toPx())
            )
        }

        val p8Px = with(density) { 8.dp.toPx() }
        val p6Px = with(density) { 6.dp.toPx() }
        val bobOffsetPx = with(density) { bobOffset.dp.toPx() }

        // Hero Character Avatar on the Road
        val heroImageRes = HeroRegistry.getCharacterImage(heroName)
        Image(
            painter = painterResource(id = heroImageRes),
            contentDescription = heroName,
            modifier = Modifier
                .size(heroSizeDp)
                .offset {
                    IntOffset(
                        x = (heroPos.x - heroSizePx / 2).toInt(),
                        y = (heroPos.y - heroSizePx / 2 + bobOffsetPx).toInt()
                    )
                }
                .graphicsLayer {
                    rotationZ = tiltAngle
                },
            contentScale = ContentScale.Fit
        )

        // Dynamic Comic Speech-Bubble Proximity Tag attached to Hero
        val tagWidthDp = 160.dp
        val tagHeightDp = 42.dp
        val tagWidthPx = with(density) { tagWidthDp.toPx() }
        val tagHeightPx = with(density) { tagHeightDp.toPx() }

        val clampedTagX = (heroPos.x - tagWidthPx / 2f).coerceIn(p8Px, w - tagWidthPx - p8Px)
        val tagY = if (animatedProgress > 0.86f) {
            // When hero is right near top goal, display tag beneath hero
            (heroPos.y + heroSizePx / 2f + p8Px).coerceAtMost(h - tagHeightPx - p8Px)
        } else {
            // Otherwise display tag floating above hero
            (heroPos.y - heroSizePx / 2f - tagHeightPx - p6Px + bobOffsetPx).coerceAtLeast(p6Px)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedTagX.toInt(), tagY.toInt()) }
                .width(tagWidthDp)
                .clip(RoundedCornerShape(10.dp))
                .background(tagInfo.backgroundColor.copy(alpha = 0.94f))
                .border(1.5.dp, tagInfo.color, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = tagInfo.title,
                    color = tagInfo.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = tagInfo.subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // Top Target HUD Banner
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0C1929).copy(alpha = 0.88f))
                .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "🎯 TARGET GOAL: $desiredTargetRssi dBm",
                color = Color(0xFF00FF88),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Bottom Start HUD Banner
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0C1929).copy(alpha = 0.82f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "🏁 START: -90 dBm",
                color = Color(0xFFB0BEC5),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Hero Reveal Victory Overlay:
 * Features circular hero artwork, target secured banner, and simply writes "You won".
 */
@Composable
fun HeroRevealOverlayCompose(
    heroName: String,
    heroImageRes: Int,
    onDismiss: () -> Unit
) {
    val cyberSurfaceDark = Color(0xFF0A1526)
    val stateFound = Color(0xFF00FF88)
    val rarityColor = Color(0xFFFF9100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onDismiss() },
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cyberSurfaceDark,
        border = BorderStroke(2.dp, rarityColor),
        elevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Victory Header: ★ TARGET SECURED ★
            Text(
                text = "★ TARGET SECURED ★",
                color = stateFound,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Artwork in glowing circular frame
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(rarityColor.copy(alpha = 0.15f))
                    .border(3.dp, rarityColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = heroImageRes),
                    contentDescription = heroName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hero Name (uppercase)
            Text(
                text = heroName.replace("_", " ").uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Replaced rarity, XP, and capture lines with "You won"
            Text(
                text = "You won",
                color = stateFound,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "(Tap anywhere to dismiss)",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}
