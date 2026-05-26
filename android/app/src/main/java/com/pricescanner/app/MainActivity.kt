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
                    // Layer 1: Pager ↔ Edit via AnimatedContent
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
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = !showScanner,
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
                        }
                    }

                    // Layer 2: Scanner on top of pager, iris‑expand on open / iris‑out on close
                    if (showScanner) {
                        val clipProgress = remember { Animatable(0f) }
                        val density = LocalDensity.current
                        val buttonRadiusPx = with(density) { 56.dp.toPx() }

                        LaunchedEffect(closingScanner, showScanner) {
                            if (!closingScanner) {
                                // 进入：从按钮大小展开到全屏
                                scannerAnimating = true
                                clipProgress.snapTo(0f)
                                clipProgress.animateTo(
                                    1f,
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                )
                                scannerAnimating = false
                            } else {
                                // 离开：从全屏收缩到按钮大小
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
                                .drawWithContent {
                                    if (progress < 0.999f) {
                                        val cx = scanButtonCenter?.x ?: (size.width / 2f)
                                        val cy = scanButtonCenter?.y ?: (size.height / 2f)
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
