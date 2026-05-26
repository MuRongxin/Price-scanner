package com.pricescanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pricescanner.app.ui.edit.EditProductScreen
import com.pricescanner.app.ui.list.ProductListScreen
import com.pricescanner.app.ui.scan.ScanHomePage
import com.pricescanner.app.ui.scan.ScanScreen
import com.pricescanner.app.ui.theme.PriceScannerTheme
import com.pricescanner.app.ui.theme.Slate50
import kotlin.math.hypot
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PriceScannerTheme {
                var showScanner by remember { mutableStateOf(false) }
                var editingBarcode by remember { mutableStateOf<String?>(null) }
                var scanButtonCenter by remember { mutableStateOf<Offset?>(null) }
                var closingScanner by remember { mutableStateOf(false) }
                var scannerAnimating by remember { mutableStateOf(false) }
                val pagerState = rememberPagerState(
                    initialPage = Int.MAX_VALUE / 2 - 1,
                    pageCount = { Int.MAX_VALUE }
                )

                BackHandler(enabled = showScanner && !scannerAnimating) {
                    closingScanner = true
                }
                BackHandler(enabled = editingBarcode != null) { editingBarcode = null }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Layer 1: Pager — always present, never wrapped in animations
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !showScanner && editingBarcode == null,
                        modifier = Modifier.fillMaxSize().background(Slate50)
                    ) { page ->
                        when (page % 2) {
                            0 -> ScanHomePage(onScanClick = { center ->
                                scanButtonCenter = center
                                showScanner = true
                            })
                            1 -> ProductListScreen(
                                onEditProduct = { barcode -> editingBarcode = barcode },
                                onManualAdd = { editingBarcode = "" }
                            )
                        }
                    }

                    // Layer 2: Edit screen overlay
                    AnimatedContent(
                        targetState = editingBarcode != null,
                        transitionSpec = {
                            (slideInVertically(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                            (slideOutVertically(tween(250)) { it } + fadeOut(tween(200)))
                        }
                    ) { isEdit ->
                        if (isEdit) {
                            EditProductScreen(
                                productBarcode = editingBarcode ?: "",
                                onBack = { editingBarcode = null }
                            )
                        }
                    }

                    // Layer 3: Scanner on top of pager, iris‑expand on open / iris‑out on close
                    if (showScanner) {
                        val clipProgress = remember { Animatable(0f) }
                        val density = LocalDensity.current
                        val buttonRadiusPx = with(density) { 56.dp.toPx() }
                        // 随机选择展开圆心：0=按钮中心，1-4=四个角落
                        val cornerChoice = remember { Random.nextInt(5) }
                        // 随机浅色背景（相机预热期间的底色）
                        val lightColors = listOf(
                            Color(0xFFE3F2FD), Color(0xFFFCE4EC),
                            Color(0xFFE8F5E9), Color(0xFFFFF3E0),
                            Color(0xFFF3E5F5), Color(0xFFE0F7FA),
                        )
                        val bgColor = remember { lightColors.random() }

                        LaunchedEffect(closingScanner, showScanner) {
                            if (!closingScanner) {
                                scannerAnimating = true
                                clipProgress.snapTo(0f)
                                clipProgress.animateTo(
                                    1f,
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                )
                                scannerAnimating = false
                            } else {
                                scannerAnimating = true
                                clipProgress.animateTo(
                                    0f,
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                )
                                showScanner = false
                                closingScanner = false
                            }
                        }

                        val progress by clipProgress.asState()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bgColor)
                                .drawWithContent {
                                    if (progress < 0.999f) {
                                        val cx = when (cornerChoice) {
                                            0 -> scanButtonCenter?.x ?: (size.width / 2f)
                                            1 -> 0f
                                            2 -> size.width
                                            3 -> 0f
                                            4 -> size.width
                                            else -> scanButtonCenter?.x ?: (size.width / 2f)
                                        }
                                        val cy = when (cornerChoice) {
                                            0 -> scanButtonCenter?.y ?: (size.height / 2f)
                                            1 -> 0f
                                            2 -> 0f
                                            3 -> size.height
                                            4 -> size.height
                                            else -> scanButtonCenter?.y ?: (size.height / 2f)
                                        }
                                        val maxRadius = maxOf(
                                            hypot(cx, cy),
                                            hypot(size.width - cx, cy),
                                            hypot(cx, size.height - cy),
                                            hypot(size.width - cx, size.height - cy)
                                        )
                                        val radius =
                                            buttonRadiusPx + (maxRadius - buttonRadiusPx) * progress

                                        val path = Path().apply {
                                            addOval(
                                                Rect(
                                                    cx - radius, cy - radius,
                                                    cx + radius, cy + radius
                                                )
                                            )
                                        }
                                        clipPath(path) {
                                            this@drawWithContent.drawContent()
                                        }
                                    } else {
                                        drawContent()
                                    }
                                }
                        ) {
                            ScanScreen(
                                onBack = { closingScanner = true },
                                onEditProduct = { barcode ->
                                    showScanner = false
                                    editingBarcode = barcode
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
