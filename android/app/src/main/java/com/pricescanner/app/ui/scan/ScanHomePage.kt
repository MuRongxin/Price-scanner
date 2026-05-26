package com.pricescanner.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricescanner.app.ui.theme.Slate50
import com.pricescanner.app.ui.theme.Slate400

@Composable
fun ScanHomePage(onScanClick: (Offset) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Scan frame icon
            ScanFrameIcon()

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "扫码查价",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "对准商品条码即可识别",
                fontSize = 14.sp,
                color = Slate400
            )
            Spacer(modifier = Modifier.height(40.dp))

            val buttonCenter = remember { mutableStateOf<Offset?>(null) }

            // Big round scan button
            Surface(
                modifier = Modifier
                    .size(120.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        buttonCenter.value = Offset(
                            pos.x + coords.size.width / 2f,
                            pos.y + coords.size.height / 2f
                        )
                    }
                    .clickable {
                        buttonCenter.value?.let { onScanClick(it) }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 36.sp)
                        Text("扫描", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "点击按钮开始扫描",
                fontSize = 14.sp,
                color = Slate400
            )
        }

        // Swipe hint at bottom
        Text(
            "← → 滑动切换",
            fontSize = 12.sp,
            color = Slate400.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}

@Composable
fun ScanFrameIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 3.dp.toPx()
        val corner = 20.dp.toPx()
        val color = Color(0xFF2563EB)
        val margin = 4.dp.toPx()

        // Top-left corner
        drawLine(color, Offset(margin, margin + corner), Offset(margin, margin), stroke)
        drawLine(color, Offset(margin, margin), Offset(margin + corner, margin), stroke)
        // Top-right corner
        drawLine(color, Offset(w - margin - corner, margin), Offset(w - margin, margin), stroke)
        drawLine(color, Offset(w - margin, margin), Offset(w - margin, margin + corner), stroke)
        // Bottom-left corner
        drawLine(color, Offset(margin, h - margin - corner), Offset(margin, h - margin), stroke)
        drawLine(color, Offset(margin, h - margin), Offset(margin + corner, h - margin), stroke)
        // Bottom-right corner
        drawLine(color, Offset(w - margin, h - margin - corner), Offset(w - margin, h - margin), stroke)
        drawLine(color, Offset(w - margin, h - margin), Offset(w - margin - corner, h - margin), stroke)
    }
}
