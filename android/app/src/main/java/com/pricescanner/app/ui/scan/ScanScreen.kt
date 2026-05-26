package com.pricescanner.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.pricescanner.app.data.Product
import com.pricescanner.app.ui.theme.*
import com.pricescanner.app.util.SoundManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(
    viewModel: ScanViewModel = viewModel(),
    onBack: () -> Unit,
    onEditProduct: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // OCR state
    var ocrText by remember { mutableStateOf("") }
    var nameFocused by remember { mutableStateOf(false) }
    var ocrDelayPassed by remember { mutableStateOf(false) }
    var stopOcr by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val priceFocusRequester = remember { FocusRequester() }
    val isOcrEnabled = uiState.isNewForm && nameFocused && ocrDelayPassed && !stopOcr

    // Reset OCR state when form appears/disappears
    LaunchedEffect(uiState.isNewForm, uiState.barcode) {
        if (uiState.isNewForm) {
            ocrText = ""
            ocrDelayPassed = false
            stopOcr = false
            suggestion = null
            delay(1000)
            ocrDelayPassed = true
        } else {
            ocrDelayPassed = false
            ocrText = ""
            stopOcr = false
            suggestion = null
        }
    }

    // Barcode lookup when form appears
    LaunchedEffect(uiState.isNewForm, uiState.barcode) {
        val code = uiState.barcode
        if (uiState.isNewForm && code != null) {
            val result = com.pricescanner.app.util.BarcodeLookup.lookup(code)
            if (result != null && uiState.isNewForm) {
                suggestion = result
            }
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onToastShown()
        }
    }

    val enterAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400, delayMillis = 100)
    )
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .graphicsLayer { alpha = enterAlpha }
        .imePadding()) {
        // Camera always on
        CameraPreview(
            onBarcodeDetected = { barcode ->
                SoundManager.playBeep()
                viewModel.onBarcodeDetected(barcode)
            },
            isOcrEnabled = isOcrEnabled,
            onTextRecognized = { text -> ocrText = text }
        )
        ScanOverlay()

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对准条码扫描", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "关闭", tint = Color.White)
            }
        }

        // Result overlay — top card when product found
        if (uiState.barcode != null && uiState.existingProduct != null) {
            PriceCard(
                product = uiState.existingProduct!!,
                barcode = uiState.barcode!!,
                onEdit = { onEditProduct(uiState.existingProduct!!.barcode) },
                onBack = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
            )
        }

        // New product form — centered overlay
        if (uiState.isNewForm && uiState.barcode != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.dismissForm() }
            )
            NewProductForm(
                barcode = uiState.barcode!!,
                ocrText = ocrText,
                suggestion = suggestion,
                focusRequester = focusRequester,
                priceFocusRequester = priceFocusRequester,
                onNameFocusChanged = { nameFocused = it },
                onUserModified = {
                    stopOcr = true
                    suggestion = null
                },
                onSave = { name, price, unit ->
                    viewModel.saveNewProduct(uiState.barcode!!, name, price, unit)
                },
                onCancel = { viewModel.dismissForm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .align(Alignment.Center)
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
fun PriceCard(
    product: Product,
    barcode: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(product.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(barcode, fontSize = 11.sp, color = Slate400)
            Text(
                "¥${String.format("%.2f", product.price.toDoubleOrNull() ?: 0.0)}",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Red500,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("返回列表", fontSize = 13.sp)
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("编辑", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun NewProductForm(
    barcode: String,
    ocrText: String = "",
    suggestion: String? = null,
    focusRequester: FocusRequester? = null,
    priceFocusRequester: FocusRequester? = null,
    onNameFocusChanged: (Boolean) -> Unit = {},
    onUserModified: () -> Unit = {},
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("个") }
    var nameManuallyEdited by remember { mutableStateOf(false) }

    // Auto-focus name field after form appears
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester?.requestFocus()
    }

    // Fill name from OCR when text is recognized
    LaunchedEffect(ocrText) {
        if (ocrText.isNotBlank() && !nameManuallyEdited) {
            name = ocrText
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("新增商品", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(barcode, fontSize = 13.sp, color = Slate400, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))

            // Barcode lookup suggestion
            if (suggestion != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            name = suggestion
                            nameManuallyEdited = true
                            onUserModified()
                            priceFocusRequester?.requestFocus()
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE3F2FD),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "条码查询: $suggestion",
                            fontSize = 14.sp,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text("点击填入", fontSize = 12.sp, color = Color(0xFF64B5F6))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = name, onValueChange = {
                    name = it
                    if (!nameManuallyEdited) {
                        nameManuallyEdited = true
                        onUserModified()
                    }
                },
                label = { Text("商品名称") },
                placeholder = { Text("例如：可口可乐330ml") },
                modifier = Modifier.fillMaxWidth()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .onFocusChanged { onNameFocusChanged(it.isFocused) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("单价（元）") },
                placeholder = { Text("例如：3.50") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
                    .then(priceFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = unit, onValueChange = { unit = it },
                label = { Text("单位") },
                placeholder = { Text("个/瓶/箱...") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onSave(name, price, unit) },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank() && price.isNotBlank()
                ) { Text("保存") }
            }
        }
    }
}

@Composable
fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    isOcrEnabled: Boolean = false,
    onTextRecognized: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var hasPermission by remember { mutableStateOf(false) }

    // Use AtomicBoolean for thread-safe OCR flag access from analyzer
    val ocrEnabled = remember { AtomicBoolean(false) }
    SideEffect { ocrEnabled.set(isOcrEnabled) }

    // Text recognizer for OCR
    val textRecognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> hasPermission = true
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    post {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }

                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(
                                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                                    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                                    Barcode.FORMAT_CODABAR
                                ).build()
                            val scanner = BarcodeScanning.getClient(options)

                            var barcodeCooldown = false
                            var frameSkip = 0
                            var lastOcrTime = 0L

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also {
                                    it.setAnalyzer(executor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage == null) { imageProxy.close(); return@setAnalyzer }

                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage, imageProxy.imageInfo.rotationDegrees
                                        )
                                        val pending = java.util.concurrent.atomic.AtomicInteger(0)
                                        val onOpDone = {
                                            if (pending.decrementAndGet() == 0) imageProxy.close()
                                        }

                                        // OCR: every second when enabled
                                        val now = System.currentTimeMillis()
                                        if (ocrEnabled.get() && now - lastOcrTime >= 1500) {
                                            lastOcrTime = now
                                            pending.incrementAndGet()
                                            textRecognizer.process(inputImage)
                                                .addOnSuccessListener { result ->
                                                    val text = result.text.trim()
                                                    if (text.isNotBlank()) onTextRecognized(text)
                                                }
                                                .addOnCompleteListener { onOpDone() }
                                        }

                                        // Barcode: every 3rd frame, 1s cooldown
                                        frameSkip++
                                        if (frameSkip % 3 == 0 && !barcodeCooldown) {
                                            pending.incrementAndGet()
                                            scanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    if (barcodes.isNotEmpty() && !barcodeCooldown) {
                                                        barcodeCooldown = true
                                                        android.os.Handler(android.os.Looper.getMainLooper())
                                                            .postDelayed({ barcodeCooldown = false }, 1000)
                                                        barcodes[0].rawValue?.let { onBarcodeDetected(it) }
                                                    }
                                                }
                                                .addOnCompleteListener { onOpDone() }
                                        }

                                        if (pending.get() == 0) imageProxy.close()
                                    }
                                }

                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                                )
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要相机权限才能扫码", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }
    }
}

@Composable
fun ScanOverlay() {
    val transition = rememberInfiniteTransition()
    val scanLineY by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(animation = tween(1800), repeatMode = RepeatMode.Reverse)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val margin = w * 0.08f; val top = h * 0.22f; val bottom = h * 0.78f
        val cornerLen = 24.dp.toPx(); val red = Color(0xFFE63232); val strokeW = 3.dp.toPx()
        val dark = Color.Black.copy(alpha = 0.45f)

        drawRect(dark, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, top))
        drawRect(dark, Offset(0f, bottom), androidx.compose.ui.geometry.Size(w, h - bottom))
        drawRect(dark, Offset(0f, top), androidx.compose.ui.geometry.Size(margin, bottom - top))
        drawRect(dark, Offset(w - margin, top), androidx.compose.ui.geometry.Size(margin, bottom - top))

        drawLine(red, Offset(margin, top), Offset(margin + cornerLen, top), strokeW)
        drawLine(red, Offset(margin, top), Offset(margin, top + cornerLen), strokeW)
        drawLine(red, Offset(w - margin, top), Offset(w - margin - cornerLen, top), strokeW)
        drawLine(red, Offset(w - margin, top), Offset(w - margin, top + cornerLen), strokeW)
        drawLine(red, Offset(margin, bottom), Offset(margin + cornerLen, bottom), strokeW)
        drawLine(red, Offset(margin, bottom), Offset(margin, bottom - cornerLen), strokeW)
        drawLine(red, Offset(w - margin, bottom), Offset(w - margin - cornerLen, bottom), strokeW)
        drawLine(red, Offset(w - margin, bottom), Offset(w - margin, bottom - cornerLen), strokeW)

        val lineY = top + (bottom - top) * scanLineY
        drawLine(Color.Red.copy(alpha = 0.9f), Offset(margin, lineY), Offset(w - margin, lineY), 2.dp.toPx())
    }
}
