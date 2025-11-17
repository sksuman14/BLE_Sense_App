package com.blesense.app

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* -------------------------------------------------------------
   3-D helpers (unchanged)
   ------------------------------------------------------------- */
data class Point3D(val x: Float, val y: Float, val z: Float) {
    fun rotateX(a: Double) = Point3D(
        x,
        (y * cos(a) - z * sin(a)).toFloat(),
        (y * sin(a) + z * cos(a)).toFloat()
    )
    fun rotateY(a: Double) = Point3D(
        (x * cos(a) + z * sin(a)).toFloat(),
        y,
        (-x * sin(a) + z * cos(a)).toFloat()
    )
    fun rotateZ(a: Double) = Point3D(
        (x * cos(a) - y * sin(a)).toFloat(),
        (x * sin(a) + y * cos(a)).toFloat(),
        z
    )
}

/* -------------------------------------------------------------
   Main screen – English only, no translation
   ------------------------------------------------------------- */
@Composable
fun ChartScreen(navController: NavController, deviceAddress: String? = null) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val factory = remember { BluetoothScanViewModelFactory(app) }
    val vm: BluetoothScanViewModel<Any?> = viewModel(factory = factory)

    val dark by ThemeManager.isDarkMode.collectAsState()

    val sensorData by remember(deviceAddress) {
        vm.devices.map { it.find { d -> d.address == deviceAddress }?.sensorData }
    }.collectAsState(initial = null)

    // ---- raw sensor values -------------------------------------------------
    val temp   = (sensorData as? BluetoothScanViewModel.SensorData.SHT40Data)?.temperature?.toFloatOrNull()
    val hum    = (sensorData as? BluetoothScanViewModel.SensorData.SHT40Data)?.humidity?.toFloatOrNull()
    val speed  = (sensorData as? BluetoothScanViewModel.SensorData.SDTData)?.speed?.toFloatOrNull()
    val dist   = (sensorData as? BluetoothScanViewModel.SensorData.SDTData)?.distance?.toFloatOrNull()
    val accX   = (sensorData as? BluetoothScanViewModel.SensorData.LIS2DHData)?.x?.toFloatOrNull()
    val accY   = (sensorData as? BluetoothScanViewModel.SensorData.LIS2DHData)?.y?.toFloatOrNull()
    val accZ   = (sensorData as? BluetoothScanViewModel.SensorData.LIS2DHData)?.z?.toFloatOrNull()
    val soilM  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.moisture?.toFloatOrNull()
    val soilT  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.temperature?.toFloatOrNull()
    val soilN  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.nitrogen?.toFloatOrNull()
    val soilP  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.phosphorus?.toFloatOrNull()
    val soilK  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.potassium?.toFloatOrNull()
    val soilEC = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.ec?.toFloatOrNull()
    val soilPH = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.pH?.toFloatOrNull()

    // ---- history buffers ---------------------------------------------------
    val tempH   = remember { mutableStateListOf<Float>() }
    val humH    = remember { mutableStateListOf<Float>() }
    val speedH  = remember { mutableStateListOf<Float>() }
    val distH   = remember { mutableStateListOf<Float>() }
    val accXH   = remember { mutableStateListOf<Float>() }
    val accYH   = remember { mutableStateListOf<Float>() }
    val accZH   = remember { mutableStateListOf<Float>() }
    val soilMH  = remember { mutableStateListOf<Float>() }
    val soilTH  = remember { mutableStateListOf<Float>() }
    val soilNH  = remember { mutableStateListOf<Float>() }
    val soilPHist = remember { mutableStateListOf<Float>() }
    val soilKHist = remember { mutableStateListOf<Float>() }
    val soilECHist = remember { mutableStateListOf<Float>() }
    val soilPHHist = remember { mutableStateListOf<Float>() }
    val timestamps = remember { mutableStateListOf<String>() }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // ---- hard-coded English strings ----------------------------------------
    val titleGraphs            = "Graphs"
    val tempLabel              = "Temperature (°C)"
    val humLabel               = "Humidity (%)"
    val speedLabel             = "Speed (m/s)"
    val distLabel              = "Distance (m)"
    val xLabel                 = "X Axis (g)"
    val yLabel                 = "Y Axis (g)"
    val zLabel                 = "Z Axis (g)"
    val soilTitle              = "Soil Sensor Data (Click for detailed view)"
    val soilMoistLabel         = "Soil Moisture (%)"
    val soilTempLabel          = "Soil Temperature (°C)"
    val soilNLabel             = "Soil Nitrogen (ppm)"
    val soilPLabel             = "Soil Phosphorus (ppm)"
    val soilKLabel             = "Soil Potassium (ppm)"
    val soilECLabel            = "Soil EC (µS/cm)"
    val soilPHLabel            = "Soil pH"
    val clickAll               = "Click to view all soil parameters and table view"
    val waitingData            = "Waiting for data..."          // <-- FIXED
    val waitingSensor          = "Waiting for sensor data..."
    val ensureConnected        = "Make sure the device is connected and sending data"
    val currentTxt             = "Current"
    val naTxt                  = "N/A"
    val tabGraphs              = "Graphs"
    val tabSoilTable           = "Soil Data Table"
    val backToSensors          = "Back to All Sensors"

    // ---- theme colours ------------------------------------------------------
    val bgGrad = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF121212), Color(0xFF424242)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0A74DA), Color(0xFFADD8E6)))
    }
    val cardBg   = if (dark) Color(0xFF1E1E1E) else Color.White
    val txt      = if (dark) Color.White else Color.Black
    val txt2     = if (dark) Color(0xFFFFFFFF) else Color(0xFF2A2626)
    val accent   = if (dark) Color(0xFFBB86FC) else Color(0xFF0A74DA)
    val tabBg    = if (dark) Color(0xFF2A2A2A) else Color.Transparent
    val appBarBg = if (dark) Color(0xFF121212) else Color.White

    // ---- UI state -----------------------------------------------------------
    val receiving   = remember { mutableStateOf(false) }
    val hasSoil     = remember { mutableStateOf(false) }
    var soilClicked by remember { mutableStateOf(false) }
    var tabIdx      by remember { mutableStateOf(0) }
    val tabs        = listOf(tabGraphs, tabSoilTable)
    val flowState   = remember { mutableStateOf("Waiting for data...") }

    // ---- start scanning -----------------------------------------------------
    LaunchedEffect(Unit) { vm.startScan(ctx as Activity) }

    // ---- data-received flags ------------------------------------------------
    LaunchedEffect(sensorData) {
        receiving.value = temp != null || hum != null || speed != null || dist != null ||
                accX != null || accY != null || accZ != null ||
                soilM != null || soilT != null || soilN != null ||
                soilP != null || soilK != null || soilEC != null || soilPH != null

        hasSoil.value = soilM != null || soilT != null || soilN != null ||
                soilP != null || soilK != null || soilEC != null || soilPH != null
    }

    // ---- update histories ---------------------------------------------------
    LaunchedEffect(temp, hum, speed, dist, accX, accY, accZ,
        soilM, soilT, soilN, soilP, soilK, soilEC, soilPH) {

        temp?.let   { updateHistory(tempH,   it) }
        hum?.let    { updateHistory(humH,    it) }
        speed?.let  { updateHistory(speedH,  it) }
        dist?.let   { updateHistory(distH,   it) }

        val addTs = soilM != null || soilT != null || soilN != null ||
                soilP != null || soilK != null || soilEC != null || soilPH != null
        if (addTs) {
            if (timestamps.size >= 20) timestamps.removeAt(0)
            timestamps.add(fmt.format(Date()))
        }

        soilM?.let  { updateHistory(soilMH,  it) }
        soilT?.let  { updateHistory(soilTH,  it) }
        soilN?.let  { updateHistory(soilNH,  it) }
        soilP?.let  { updateHistory(soilPHist, it) }
        soilK?.let  { updateHistory(soilKHist, it) }
        soilEC?.let { updateHistory(soilECHist, it) }
        soilPH?.let { updateHistory(soilPHHist, it) }
    }

    LaunchedEffect(accX, accY, accZ) {
        val now = System.currentTimeMillis()
        flowState.value = "Data received at: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))}"
        accX?.let { if (accXH.size >= 50) accXH.removeAt(0); accXH.add(it) }
        accY?.let { if (accYH.size >= 50) accYH.removeAt(0); accYH.add(it) }
        accZ?.let { if (accZH.size >= 50) accZH.removeAt(0); accZH.add(it) }
    }

    // -------------------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titleGraphs,
                        fontFamily = helveticaFont,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = txt
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (dark) Color.White else Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { /* export */ }) {
                        Icon(Icons.Default.TableChart, "Export", tint = if (dark) Color.White else Color.Black)
                    }
                    IconButton(onClick = { /* options */ }) {
                        Icon(Icons.AutoMirrored.Filled.List, "Options", tint = if (dark) Color.White else Color.Black)
                    }
                },
                backgroundColor = appBarBg,
                elevation = 0.dp
            )
        },
        backgroundColor = Color.Transparent
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGrad)
                .padding(pad)
        ) {
            Column {
                // ----- TAB ROW (only when soil card is expanded) -----
                if (soilClicked && hasSoil.value) {
                    TabRow(
                        selectedTabIndex = tabIdx,
                        backgroundColor = tabBg,
                        contentColor = accent
                    ) {
                        tabs.forEachIndexed { i, t ->
                            Tab(
                                text = { Text(t, color = txt) },
                                selected = tabIdx == i,
                                onClick = { tabIdx = i }
                            )
                        }
                    }
                }

                // ----- MAIN CONTENT -----
                if (!soilClicked || (soilClicked && tabIdx == 0)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ---- SHT40 -------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.SHT40Data) {
                            item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                        }
                        // ---- SDT ---------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.SDTData) {
                            item { SensorGraphCard(speedLabel, speed, speedH, Color(0xFF43A047), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(distLabel, dist, distH, Color(0xFFFFB300), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                        }
                        // ---- LIS2DH ------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.LIS2DHData) {
                            item { SensorGraphCard(xLabel, accX, accXH, Color(0xFFE91E63), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(yLabel, accY, accYH, Color(0xFF9C27B0), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(zLabel, accZ, accZH, Color(0xFF009688), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item {
                                Accelerometer3DVisualization(accX, accY, accZ, cardBg, txt, dark)
                            }
                        }
                        // ---- SOIL --------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.SoilSensorData) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { soilClicked = true },
                                    elevation = 2.dp,
                                    backgroundColor = if (soilClicked) accent.copy(alpha = 0.1f) else cardBg
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(soilTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(bottom = 8.dp))
                                        SensorGraphCard(soilMoistLabel, soilM, soilMH, Color(0xFF6200EA), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                        Spacer(Modifier.height(16.dp))
                                        SensorGraphCard(soilTempLabel, soilT, soilTH, Color(0xFFFF6D00), cardBg, txt, txt2, currentTxt, naTxt, dark)

                                        if (!soilClicked) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                                horizontalArrangement = Arrangement.Center
                                            ) { Text(clickAll, color = accent) }
                                        } else {
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilNLabel, soilN, soilNH, Color(0xFF00897B), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilPLabel, soilP, soilPHist, Color(0xFFC2185B), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilKLabel, soilK, soilKHist, Color(0xFF7B1FA2), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilECLabel, soilEC, soilECHist, Color(0xFFF57C00), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilPHLabel, soilPH, soilPHHist, Color(0xFFD32F2F), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                        }
                                    }
                                }
                            }
                        }

                        // ---- data-flow monitor ----------------------------------------
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                backgroundColor = if (dark) Color(0xFF2A2A2A) else Color(0xFFE3F2FD)
                            ) {
                                Text(
                                    text = flowState.value,
                                    color = if (dark) Color.White else Color.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // ---- no data yet ---------------------------------------------
                        if (!receiving.value) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(waitingSensor, modifier = Modifier.padding(vertical = 32.dp), color = txt)
                                    Text(ensureConnected, fontSize = 14.sp, color = txt2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                } else if (soilClicked && tabIdx == 1) {
                    SoilSensorDataTable(
                        soilMoistureHistory = soilMH,
                        soilTemperatureHistory = soilTH,
                        soilNitrogenHistory = soilNH,
                        soilPhosphorusHistory = soilPHist,
                        soilPotassiumHistory = soilKHist,
                        soilEcHistory = soilECHist,
                        soilPhHistory = soilPHHist,
                        timestamps = timestamps,
                        isReceivingData = receiving.value && (soilMH.isNotEmpty() || soilTH.isNotEmpty() ||
                                soilNH.isNotEmpty() || soilPHist.isNotEmpty() || soilKHist.isNotEmpty() ||
                                soilECHist.isNotEmpty() || soilPHHist.isNotEmpty()),
                        soilMoistureLabel = soilMoistLabel,
                        soilTemperatureLabel = soilTempLabel,
                        soilNitrogenLabel = soilNLabel,
                        soilPhosphorusLabel = soilPLabel,
                        soilPotassiumLabel = soilKLabel,
                        soilEcLabel = soilECLabel,
                        soilPhLabel = soilPHLabel,
                        waitingForSensorData = waitingSensor,
                        textColor = txt,
                        secondaryTextColor = txt2,
                        cardBackground = cardBg
                    )
                }

                // ---- back button when soil view is expanded --------------------
                if (soilClicked) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accent, RoundedCornerShape(8.dp))
                                .clickable { soilClicked = false }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(backToSensors, color = if (dark) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------
   Graph card (touch-enabled)
   ------------------------------------------------------------- */
@Composable
fun SensorGraphCard(
    title: String,
    cur: Float?,
    hist: List<Float>,
    lineCol: Color,
    cardBg: Color,
    txtCol: Color,
    txt2Col: Color,
    curLabel: String,
    naLabel: String,
    dark: Boolean
) {
    var tapPos by remember { mutableStateOf<Offset?>(null) }
    var tapVal by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(tapPos) {
        if (tapPos != null) {
            kotlinx.coroutines.delay(1000)
            tapPos = null
            tapVal = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        elevation = 4.dp,
        backgroundColor = cardBg
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = txtCol, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("${curLabel}: ${cur?.toString() ?: naLabel}", fontSize = 16.sp, color = txt2Col, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            if (hist.isNotEmpty()) {
                var canvasSize by remember { mutableStateOf(Size.Zero) }

                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { tapPos = it } }
                    ) {
                        canvasSize = size
                        val pts = hist
                        if (pts.isNotEmpty()) {
                            val lm = 50f; val rm = 20f; val tm = 20f; val bm = 30f
                            val w = size.width - lm - rm
                            val h = size.height - tm - bm
                            val sx = lm
                            val sy = tm
                            val ex = sx + w
                            val ey = sy + h

                            val yMin = -10f; val yMax = 50f; val yRng = yMax - yMin
                            val stepX = w / (pts.size.coerceAtLeast(2) - 1).toFloat()

                            // ---- grid -------------------------------------------------
                            drawRect(if (dark) Color(0x0AFFFFFF) else Color(0x0A000000), Offset(sx, sy), Size(w, h))

                            val grid = txt2Col.copy(alpha = 0.15f)
                            for (v in yMin.toInt()..yMax.toInt() step 2) {
                                val y = sy + h * (1 - (v - yMin) / yRng)
                                drawLine(color = grid, start = Offset(sx, y), end = Offset(ex, y), strokeWidth = 0.5f)   // <-- FIXED
                            }
                            for (i in 0..pts.size step 2) {
                                if (i < pts.size) {
                                    val x = sx + i * stepX
                                    drawLine(color = grid, start = Offset(x, sy), end = Offset(x, ey), strokeWidth = 0.5f)   // <-- FIXED
                                }
                            }

                            val bold = txt2Col.copy(alpha = 0.3f)
                            listOf(-10f, 0f, 10f, 20f, 30f, 40f, 50f).forEach {
                                val y = sy + h * (1 - (it - yMin) / yRng)
                                drawLine(color = bold, start = Offset(sx, y), end = Offset(ex, y), strokeWidth = 1f)       // <-- FIXED
                            }
                            for (i in 0..pts.size step 10) {
                                if (i < pts.size) {
                                    val x = sx + i * stepX
                                    drawLine(color = bold, start = Offset(x, sy), end = Offset(x, ey), strokeWidth = 1f)       // <-- FIXED
                                }
                            }

                            drawRect(txt2Col.copy(alpha = 0.4f), Offset(sx, sy), Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))

                            // ---- Y labels -------------------------------------------------
                            listOf(-10f, 0f, 10f, 20f, 30f, 40f, 50f).forEach {
                                val y = sy + h * (1 - (it - yMin) / yRng)
                                val p = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(
                                        (txt2Col.alpha * 255).toInt(),
                                        (txt2Col.red * 255).toInt(),
                                        (txt2Col.green * 255).toInt(),
                                        (txt2Col.blue * 255).toInt()
                                    )
                                    textSize = 20f
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                }
                                drawContext.canvas.nativeCanvas.drawText("%.0f".format(it), sx - 35f, y + 5f, p)
                            }

                            // ---- X labels -------------------------------------------------
                            listOf(0, 10, 20, 30, 40).forEach { idx ->
                                if (idx < pts.size) {
                                    val x = sx + idx * stepX
                                    val p = android.graphics.Paint().apply {
                                        color = android.graphics.Color.argb(
                                            (txt2Col.alpha * 255).toInt(),
                                            (txt2Col.red * 255).toInt(),
                                            (txt2Col.green * 255).toInt(),
                                            (txt2Col.blue * 255).toInt()
                                        )
                                        textSize = 18f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }
                                    drawContext.canvas.nativeCanvas.drawText("%.0f".format(idx.toFloat()), x, ey + 15f, p)
                                }
                            }

                            // ---- line ----------------------------------------------------
                            val path = androidx.compose.ui.graphics.Path().apply {
                                val firstY = sy + h * (1 - (pts[0] - yMin) / yRng)
                                moveTo(sx, firstY)
                                for (i in 1 until pts.size) {
                                    val x = sx + i * stepX
                                    val y = sy + h * (1 - (pts[i] - yMin) / yRng)
                                    lineTo(x, y)
                                }
                            }
                            drawPath(
                                path,
                                lineCol,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.5f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )

                            // ---- latest point --------------------------------------------
                            if (pts.isNotEmpty()) {
                                val li = pts.size - 1
                                val lx = sx + li * stepX
                                val ly = sy + h * (1 - (pts.last() - yMin) / yRng)
                                drawCircle(lineCol, 4f, Offset(lx, ly))
                            }

                            // ---- tap indicator -------------------------------------------
                            tapPos?.let { tp ->
                                val step = w / (pts.size.coerceAtLeast(2) - 1).toFloat()
                                val idx = ((tp.x - lm) / step).toInt().coerceIn(0, pts.size - 1)
                                val tx = sx + idx * stepX
                                val ty = sy + h * (1 - (pts[idx] - yMin) / yRng)
                                tapVal = pts[idx]

                                drawLine(
                                    color = lineCol.copy(alpha = 0.5f),
                                    start = Offset(tx, sy),
                                    end = Offset(tx, ey),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                                )   // <-- FIXED
                                drawCircle(lineCol, 6f, Offset(tx, ty))

                                val p = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(
                                        (lineCol.alpha * 255).toInt(),
                                        (lineCol.red * 255).toInt(),
                                        (lineCol.green * 255).toInt(),
                                        (lineCol.blue * 255).toInt()
                                    )
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawContext.canvas.nativeCanvas.drawText("%.2f".format(pts[idx]), tx, ty - 15f, p)
                            }
                        }
                    }
                }

                // ---- tapped value -------------------------------------------------
                tapVal?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Touched value: %.2f".format(it),
                        fontSize = 14.sp,
                        color = lineCol,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            //else
//            {
//                Text(waitingData, modifier = Modifier.padding(vertical = 32.dp), color = txtCol)   // <-- FIXED
//            }
        }
    }
}

/* -------------------------------------------------------------
   Helper – keep history at max 50 points
   ------------------------------------------------------------- */
private fun updateHistory(list: MutableList<Float>, v: Float) {
    if (list.size >= 50) list.removeAt(0)
    list.add(v)
}

/* -------------------------------------------------------------
   Soil table (tab 1)
   ------------------------------------------------------------- */
@Composable
fun SoilSensorDataTable(
    soilMoistureHistory: List<Float>,
    soilTemperatureHistory: List<Float>,
    soilNitrogenHistory: List<Float>,
    soilPhosphorusHistory: List<Float>,
    soilPotassiumHistory: List<Float>,
    soilEcHistory: List<Float>,
    soilPhHistory: List<Float>,
    timestamps: List<String>,
    isReceivingData: Boolean,
    soilMoistureLabel: String,
    soilTemperatureLabel: String,
    soilNitrogenLabel: String,
    soilPhosphorusLabel: String,
    soilPotassiumLabel: String,
    soilEcLabel: String,
    soilPhLabel: String,
    waitingForSensorData: String,
    textColor: Color,
    secondaryTextColor: Color,
    cardBackground: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = 4.dp,
        backgroundColor = cardBackground
    ) {
        Column(Modifier.padding(16.dp)) {
            if (isReceivingData) {
                LazyColumn {
                    items(timestamps.size) { i ->
                        Column {
                            Text("Timestamp: ${timestamps.getOrNull(i) ?: "-"}", color = textColor, fontWeight = FontWeight.Bold)
                            Text("$soilMoistureLabel: ${soilMoistureHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilTemperatureLabel: ${soilTemperatureHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilNitrogenLabel: ${soilNitrogenHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilPhosphorusLabel: ${soilPhosphorusHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilPotassiumLabel: ${soilPotassiumHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilEcLabel: ${soilEcHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilPhLabel: ${soilPhHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Divider(color = secondaryTextColor.copy(alpha = 0.2f), thickness = 1.dp)
                        }
                    }
                }
            } else {
                Text(waitingForSensorData, modifier = Modifier.padding(vertical = 32.dp), color = textColor)
            }
        }
    }
}

/* -------------------------------------------------------------
   3-D accelerometer cube
   ------------------------------------------------------------- */
@Composable
fun Accelerometer3DVisualization(
    xAxis: Float?,
    yAxis: Float?,
    zAxis: Float?,
    cardBackground: Color,
    textColor: Color,
    isDarkMode: Boolean
) {
    val x = xAxis ?: 0f
    val y = yAxis ?: 0f
    val z = zAxis ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth().height(380.dp),
        elevation = 4.dp,
        backgroundColor = cardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "3D Orientation Visualizer",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Canvas(modifier = Modifier.fillMaxWidth().height(280.dp).padding(8.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val scale = minOf(size.width, size.height) * 0.25f
                val axisLen = 300f
                val diag = axisLen / sqrt(2f)
                val edge = if (isDarkMode) Color.White else Color.Black

                // ----- cube vertices & edges (same as helper) -----
                val verts = arrayOf(
                    floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                    floatArrayOf(1f, 1f, -1f),   floatArrayOf(-1f, 1f, -1f),
                    floatArrayOf(-1f, -1f, 1f),  floatArrayOf(1f, -1f, 1f),
                    floatArrayOf(1f, 1f, 1f),   floatArrayOf(-1f, 1f, 1f)
                )
                val edges = arrayOf(
                    intArrayOf(0,1), intArrayOf(1,2), intArrayOf(2,3), intArrayOf(3,0),
                    intArrayOf(4,5), intArrayOf(5,6), intArrayOf(6,7), intArrayOf(7,4),
                    intArrayOf(0,4), intArrayOf(1,5), intArrayOf(2,6), intArrayOf(3,7)
                )

                fun rot(x: Float, y: Float, z: Float): FloatArray {
                    val rx = Math.toRadians(20.0).toFloat()
                    val ry = Math.toRadians(25.0).toFloat()
                    val rz = Math.toRadians(5.0).toFloat()

                    var yy = y * cos(rx) - z * sin(rx)
                    var zz = y * sin(rx) + z * cos(rx)
                    var xx = x

                    val zz2 = zz * cos(ry) - xx * sin(ry)
                    val xx2 = zz * sin(ry) + xx * cos(ry)

                    val xx3 = xx2 * cos(rz) - yy * sin(rz)
                    val yy3 = xx2 * sin(rz) + yy * cos(rz)
                    return floatArrayOf(xx3, yy3, zz2)
                }

                fun proj(x: Float, y: Float, z: Float): Offset {
                    val d = 5f
                    val p = d / (d - z)
                    return Offset(cx + x * scale * p, cy - y * scale * p)
                }

                val projected = verts.map {
                    val r = rot(it[0], it[1], it[2])
                    proj(r[0], r[1], r[2])
                }

                edges.forEach { (a, b) -> drawLine(color = edge, start = projected[a], end = projected[b], strokeWidth = 3f) }   // <-- FIXED
                projected.forEach { drawCircle(edge, 4f, it) }

                // ----- axes -------------------------------------------------
                val xEnd = Offset(cx + axisLen, cy)
                val yEnd = Offset(cx, cy - axisLen)
                val zEnd = Offset(cx - diag, cy + diag)

                drawLine(color = Color.Red,   start = Offset(cx, cy), end = xEnd, strokeWidth = 3f)   // <-- FIXED
                drawLine(color = Color.Green, start = Offset(cx, cy), end = yEnd, strokeWidth = 3f)   // <-- FIXED
                drawLine(color = Color.Cyan,  start = Offset(cx, cy), end = zEnd, strokeWidth = 3f)   // <-- FIXED

                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply { textSize = 36f; isAntiAlias = true }
                    p.color = android.graphics.Color.RED;   drawText("X", xEnd.x + 10f, xEnd.y, p)
                    p.color = android.graphics.Color.GREEN; drawText("Y", yEnd.x + 10f, yEnd.y, p)
                    p.color = android.graphics.Color.CYAN;  drawText("Z", zEnd.x + 20f, zEnd.y + 30f, p)
                }

                // ----- sensor dot --------------------------------------------
                val range = 10f
                val sx = (x / range).coerceIn(-1f, 1f)
                val sy = (y / range).coerceIn(-1f, 1f)
                val sz = (z / range).coerceIn(-1f, 1f)

                val r = rot(sx, sy, sz)
                val dot = proj(r[0], r[1], r[2])
                drawCircle(Color.Magenta, 10f, dot)
            }

            Text(
                "X: %.2f, Y: %.2f, Z: %.2f".format(x, y, z),
                color = textColor,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}