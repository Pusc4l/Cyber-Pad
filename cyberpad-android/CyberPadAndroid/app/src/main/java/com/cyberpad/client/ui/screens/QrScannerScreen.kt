package com.cyberpad.client.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyberpad.client.network.ConnectionInfo
import com.cyberpad.client.network.QrPayloadParser
import com.cyberpad.client.ui.components.CyberpunkButton
import com.cyberpad.client.ui.components.cutCornerPath
import com.cyberpad.client.ui.components.cyberGrid
import com.cyberpad.client.ui.components.neonStrokePath
import com.cyberpad.client.ui.theme.CarbonCard
import com.cyberpad.client.ui.theme.CyberFont
import com.cyberpad.client.ui.theme.CyberYellow
import com.cyberpad.client.ui.theme.ElectricPink
import com.cyberpad.client.ui.theme.NeonCyan
import com.cyberpad.client.ui.theme.ObsidianDark
import com.cyberpad.client.ui.theme.TextMuted
import com.cyberpad.client.ui.theme.TextPrimary
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val HINT_DEFAULT = "ARAHKAN KAMERA KE QR CODE DI LAYAR PC"
private const val HINT_INVALID_QR = "BUKAN QR CYBERPAD - COBA LAGI"
private const val HINT_INVALID_IP = "ALAMAT IP TIDAK VALID (CONTOH 192.168.1.100)"

/**
 * Layar pemindai QR. Setelah QR CyberPad valid terbaca, koneksi langsung dibuat otomatis
 * lewat [onConnect] dengan slot pemain yang sedang dipilih (P1..P4).
 */
@Composable
fun QrScannerScreen(onConnect: (info: ConnectionInfo, playerId: Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var playerId by rememberSaveable { mutableIntStateOf(1) }
    var hint by remember { mutableStateOf(HINT_DEFAULT) }
    var manualIp by rememberSaveable { mutableStateOf("") }
    val currentPlayer by rememberUpdatedState(playerId)
    val currentOnConnect by rememberUpdatedState(onConnect)

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(hasPermission, lifecycleOwner) {
        if (!hasPermission) {
            return@DisposableEffect onDispose { }
        }
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val handled = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor, QrAnalyzer(scanner, handled) { raw ->
                    val info = QrPayloadParser.parse(raw)
                    if (info == null) {
                        hint = HINT_INVALID_QR
                    } else if (handled.compareAndSet(false, true)) {
                        currentOnConnect(info, currentPlayer)
                    }
                })
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                hint = "KAMERA GAGAL DIBUKA: ${e.message ?: "tidak diketahui"}"
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    fun connectManual() {
        val info = QrPayloadParser.fromManual(manualIp)
        if (info == null) hint = HINT_INVALID_IP else onConnect(info, playerId)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .cyberGrid()
            .safeDrawingPadding()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Area kamera ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    val path = cutCornerPath(0f, 0f, size.width, size.height, 18.dp.toPx())
                    drawPath(path, CarbonCard)
                    neonStrokePath(path, NeonCyan, 1.5.dp.toPx(), 0.8f)
                },
            contentAlignment = Alignment.Center
        ) {
            if (hasPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
                ScanOverlay()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "IZIN KAMERA DIPERLUKAN UNTUK MEMINDAI QR",
                        color = ElectricPink,
                        fontFamily = CyberFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    CyberpunkButton(
                        label = "IZINKAN KAMERA",
                        modifier = Modifier.size(width = 200.dp, height = 44.dp),
                        accent = ElectricPink,
                        cut = 8.dp,
                        fontSize = 13.sp
                    ) { pressed ->
                        if (pressed) permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }

        // ---- Panel kanan ----
        Column(
            modifier = Modifier
                .width(264.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CYBERPAD",
                color = NeonCyan,
                fontFamily = CyberFont,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = 3.sp
            )
            Text(
                text = hint,
                color = if (hint == HINT_DEFAULT) TextMuted else ElectricPink,
                fontFamily = CyberFont,
                fontSize = 11.sp
            )

            PanelLabel("SLOT PEMAIN")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (slot in 1..4) {
                    SlotChip(
                        label = "P$slot",
                        selected = playerId == slot,
                        modifier = Modifier.weight(1f),
                        onClick = { playerId = slot }
                    )
                }
            }

            PanelLabel("MANUAL (JIKA KAMERA TIDAK BISA)")
            ManualIpField(
                value = manualIp,
                onValueChange = { manualIp = it },
                onGo = ::connectManual
            )
            SlotChip(
                label = "HUBUNGKAN",
                selected = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = ::connectManual
            )
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text = text, color = CyberYellow, fontFamily = CyberFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
}

@Composable
private fun SlotChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = if (selected) CyberYellow else NeonCyan
    Box(
        modifier = modifier
            .height(40.dp)
            .drawBehind {
                val path = cutCornerPath(0f, 0f, size.width, size.height, 8.dp.toPx())
                drawPath(path, if (selected) CyberYellow.copy(alpha = 0.85f) else CarbonCard)
                neonStrokePath(path, accent, 1.5.dp.toPx(), if (selected) 1.6f else 0.9f)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) ObsidianDark else accent,
            fontFamily = CyberFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ManualIpField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextPrimary, fontFamily = CyberFont, fontSize = 15.sp),
        cursorBrush = Brush.verticalGradient(listOf(NeonCyan, NeonCyan)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .drawBehind {
                        val path = cutCornerPath(0f, 0f, size.width, size.height, 8.dp.toPx())
                        drawPath(path, CarbonCard)
                        neonStrokePath(path, NeonCyan, 1.2.dp.toPx(), 0.6f)
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text("192.168.1.100", color = TextMuted, fontFamily = CyberFont, fontSize = 15.sp)
                }
                inner()
            }
        }
    )
}

/** Bingkai sudut neon + garis pindai bergerak di atas preview kamera. */
@Composable
private fun ScanOverlay() {
    val transition = rememberInfiniteTransition(label = "scan")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanline"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height) * 0.62f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side
        val len = side * 0.18f
        val w = 3.dp.toPx()

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                moveTo(x + dx * len, y)
                lineTo(x, y)
                lineTo(x, y + dy * len)
            }
            neonStrokePath(p, NeonCyan, w)
        }
        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(left, bottom, 1f, -1f)
        corner(right, bottom, -1f, -1f)

        val y = top + side * t
        val line = Path().apply {
            moveTo(left + len * 0.3f, y)
            lineTo(right - len * 0.3f, y)
        }
        neonStrokePath(line, ElectricPink, 2.dp.toPx(), 1.2f)
    }
}

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val handled: AtomicBoolean,
    private val onRaw: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || handled.get()) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                for (code in codes) {
                    val raw = code.rawValue
                    if (raw != null) {
                        onRaw(raw)
                        break
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
