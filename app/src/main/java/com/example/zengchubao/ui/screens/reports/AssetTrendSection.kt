package com.example.zengchubao.ui.screens.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import java.text.NumberFormat
import java.util.Locale

private enum class TrendMode { NET_INTEREST, TOTAL_ASSETS }

private val GOLD = Color(0xFFEAB308)
private val GOLD_SOFT = Color(0xFFFDE68A)
private val GRID_COLOR = Color(0xFFE2E8F0)

private val CN_I = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 0; maximumFractionDigits = 0 }
private val CN_2 = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

private data class MonthData(val totalAssets: Double, val netProfit: Double)

/**
 * 每月总资产 = 月末仍 HOLDING 存单本金 + 持有中累计收益
 * 每月净利息 = 月末持有中累计收益 + 历史所有已结清利息
 * 累计收益按 yearBasis(calcMethod) 动态分母计算
 */
private fun computeMonthlyData(deposits: List<Deposit>, year: Int): List<MonthData> {
    val today = todayString()
    val result = mutableListOf<MonthData>()
    for (m in 1..12) {
        // 非当前月 → 算到月底；当前月 → 算到今天
        val lastDay = when (m) {
            1,3,5,7,8,10,12 -> 31
            4,6,9,11 -> 30
            else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        }
        val rawEnd = String.format(Locale.CHINA, "%04d-%02d-%02d", year, m, lastDay)
        val cutoff = if (year == today.take(4).toIntOrNull() && m == today.substring(5,7).toIntOrNull()) today else rawEnd
        val monthEnd = if (cutoff <= rawEnd) cutoff else rawEnd
        val holdingPrincipal = deposits
            .filter { it.startDate <= monthEnd && it.status == DepositStatus.HOLDING }
            .sumOf { it.principal }
        val holdingAccrued = deposits.filter { it.status == DepositStatus.HOLDING }
            .sumOf {
                val elapsed = maxOf(0, minOf(daysBetween(it.startDate, monthEnd), it.termDays))
                it.principal * (it.annualRate / 100.0) * (elapsed.toDouble() / yearBasis(it.calcMethod))
            }
        val archivedYield = deposits
            .filter { it.status != DepositStatus.HOLDING && it.endDate <= monthEnd }
            .sumOf { it.maturityAmount - it.principal }
        result.add(
            MonthData(
                totalAssets = holdingPrincipal + holdingAccrued,
                netProfit = holdingAccrued + archivedYield
            )
        )
    }
    return result
}

private fun fmtSmart(v: Double): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs >= 100_000_000 -> "${"%.1f".format(v / 100_000_000)}亿"
        abs >= 1_000_000 -> "${"%.0f".format(v / 10_000.0)}w"
        abs >= 10_000 -> "${"%.1f".format(v / 10_000.0)}w"
        abs >= 1_000 -> "${"%.1f".format(v / 1_000.0)}k"
        else -> CN_I.format(v)
    }
}

private data class YAxisSpec(val min: Double, val max: Double, val step: Double)

private fun computeYAxis(values: List<Double>): YAxisSpec {
    if (values.isEmpty() || values.all { it == 0.0 }) return YAxisSpec(0.0, 1.0, 0.25)
    val rawMin = values.min()
    val rawMax = values.max()
    val range = (rawMax - rawMin).coerceAtLeast(0.0)
    val pad = range * 0.1 + 1.0
    val yMax = rawMax + pad
    val yMin = (rawMin - pad).coerceAtLeast(0.0)
    val step = (yMax - yMin) / 4.0
    if (step <= 0) return YAxisSpec(yMin, yMax, (yMax - yMin) / 4.0)
    val niceMin = (Math.floor(yMin / step) * step).coerceAtLeast(0.0)
    val niceMax = Math.ceil(yMax / step) * step
    val niceStep = (niceMax - niceMin) / 4.0
    return YAxisSpec(niceMin, niceMax, niceStep)
}

@Composable
fun AssetTrendSection(deposits: List<Deposit>) {
    val currentYear = todayString().take(4).toIntOrNull() ?: 2026
    var year by remember { mutableStateOf(currentYear) }
    var mode by remember { mutableStateOf(TrendMode.NET_INTEREST) }
    var hoverIdx by remember { mutableStateOf<Int?>(null) }

    val monthly = remember(deposits, year) { computeMonthlyData(deposits, year) }
    val currentValues = remember(monthly, mode) {
        monthly.map { if (mode == TrendMode.TOTAL_ASSETS) it.totalAssets else it.netProfit }
    }
    val yAxis = remember(currentValues) { computeYAxis(currentValues) }

    val todayMonth = todayString().substring(5, 7).toIntOrNull() ?: 12
    val currentMonthIdx = if (year == currentYear) (todayMonth - 1).coerceIn(0, 11) else 11

    val displayIdx = hoverIdx ?: currentMonthIdx
    val headerValue = currentValues.getOrNull(displayIdx) ?: 0.0
    val headerIsFuture = year == currentYear && displayIdx > currentMonthIdx

    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // 标题 + 年份切换（无图标）
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (mode) {
                        TrendMode.TOTAL_ASSETS -> "总资产趋势图"
                        TrendMode.NET_INTEREST -> "净利息趋势图"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)
                )
                Spacer(Modifier.weight(1f))
                YearSwitcher(
                    year = year,
                    onPrev = { year-- },
                    onNext = { if (year < currentYear) year++ }
                )
            }

            Spacer(Modifier.height(10.dp))

            // 胶囊框：当前月份 + 金额
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.background(Color(0xFFF1F5F9), RoundedCornerShape(14.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${year}年${displayIdx + 1}月 ¥${CN_2.format(headerValue)}${if (headerIsFuture) "（预测）" else ""}",
                        fontSize = 9.sp, lineHeight = 11.sp, color = Color(0xFF475569), fontWeight = FontWeight.W500
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 图表
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                TrendChartCanvas(
                    values = currentValues,
                    yAxis = yAxis,
                    currentMonthIdx = currentMonthIdx,
                    hoverIdx = displayIdx,
                    onTapAt = { frac -> hoverIdx = (frac * 11).toInt().coerceIn(0, 11) }
                )
            }

            Spacer(Modifier.height(4.dp))

            // 底部悬浮 Tab 卡片（无级滑动）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TrendSegmented(
                    options = listOf("净利息", "总资产"),
                    selectedIndex = if (mode == TrendMode.NET_INTEREST) 0 else 1,
                    onSelect = { idx ->
                        mode = if (idx == 0) TrendMode.NET_INTEREST else TrendMode.TOTAL_ASSETS
                        hoverIdx = null
                    }
                )
            }
        }
    }
}

@Composable
private fun TrendSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemWidth = 58.dp
    val indicatorHeight = 24.dp
    val inset = 2.dp
    val containerWidth = itemWidth * options.size
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val insetPx = with(density) { inset.toPx() }
    val innerH = indicatorHeight - inset * 2
    val animOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = selectedIndex * itemWidthPx,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "segOffset"
    )
    Box(
        modifier = Modifier
            .width(containerWidth)
            .height(indicatorHeight)
            .background(Color(0xFFF1F5F9), RoundedCornerShape(999.dp))
    ) {
        // 滑动指示器（与文字同宽，按比例上下缩进）
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset((animOffset + insetPx).toInt(), insetPx.toInt()) }
                .width(itemWidth)
                .height(innerH)
                .background(Color.White, RoundedCornerShape(999.dp))
        )
        // 文字层（顶层，fillMaxSize）
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { idx, label ->
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(idx) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 9.sp, lineHeight = 11.sp,
                        fontWeight = if (idx == selectedIndex) FontWeight.W600 else FontWeight.W500,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}

@Composable
private fun YearSwitcher(year: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val currentYear = todayString().take(4).toIntOrNull() ?: 2026
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.ChevronLeft, "上一年",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp).clickable { onPrev() }
        )
        Text(
            "$year",
            fontSize = 12.sp, fontWeight = FontWeight.W600, color = Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Icon(
            Icons.Filled.ChevronRight, "下一年",
            tint = if (year < currentYear) Color(0xFF475569) else Color(0xFFCBD5E1),
            modifier = Modifier.size(16.dp).let { mod ->
                if (year < currentYear) mod.clickable { onNext() } else mod
            }
        )
    }
}

@Composable
private fun TrendChartCanvas(
    values: List<Double>,
    yAxis: YAxisSpec,
    currentMonthIdx: Int,
    hoverIdx: Int,
    onTapAt: (Float) -> Unit
) {
    val months = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")
    val plotLeftPad = 60f
    val plotRightPad = 12f
    val plotTopPad = 4f
    val plotBottomPad = 28f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width
                    val innerPad = 8f
                    val plotWInner = w - plotLeftPad - plotRightPad - 2 * innerPad
                    val frac = ((offset.x - plotLeftPad - innerPad) / plotWInner).coerceIn(0f, 1f)
                    onTapAt(frac)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val plotW = w - plotLeftPad - plotRightPad
        val plotH = h - plotTopPad - plotBottomPad

        // Y 网格 + 标签（顶部4个，跳过最底格避免和 X 月份重叠）
        for (i in 0..4) {
            val y = plotTopPad + plotH * (1f - i / 4f)
            val drawLabel = i in 1..4
            val labelValue = yAxis.min + yAxis.step * i
            drawLine(
                color = GRID_COLOR,
                start = Offset(plotLeftPad, y),
                end = Offset(w - plotRightPad, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
            )
            if (!drawLabel) continue
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 7.5.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                val label = fmtSmart(labelValue)
                drawText(label, plotLeftPad - 4f, y + 3f, paint)
            }
        }

        if (values.isEmpty()) return@Canvas

        val n = values.size.coerceAtMost(12)
        val safeIdx = currentMonthIdx.coerceIn(0, n - 1)
        val innerPad = 8f
        val plotWInner = plotW - 2 * innerPad
        val xs = (0 until n).map { plotLeftPad + innerPad + plotWInner * (it / 11f) }

        val yRange = (yAxis.max - yAxis.min).coerceAtLeast(0.0001)
        val yScale = plotH / yRange.toFloat()
        fun toY(v: Double): Float {
            return plotTopPad + plotH - ((v - yAxis.min) * yScale).toFloat()
        }
        val ys = values.take(n).map { toY(it.coerceIn(yAxis.min, yAxis.max)) }
        val currentY = ys[safeIdx]

        // 渐变面积
        val areaPath = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1..safeIdx) {
                val px = xs[i - 1]; val py = ys[i - 1]
                val cx = xs[i]; val cy = ys[i]
                val mid = (px + cx) / 2f
                cubicTo(mid, py, mid, cy, cx, cy)
            }
            lineTo(xs[safeIdx], plotTopPad + plotH)
            lineTo(xs[0], plotTopPad + plotH)
            close()
        }
        drawPath(
            areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    GOLD.copy(alpha = 0.55f),
                    GOLD.copy(alpha = 0.22f),
                    GOLD.copy(alpha = 0.04f)
                ),
                startY = plotTopPad,
                endY = plotTopPad + plotH
            )
        )

        // 实线段
        val solid = Path()
        solid.moveTo(xs[0], ys[0])
        for (i in 1..safeIdx) {
            val px = xs[i - 1]; val py = ys[i - 1]
            val cx = xs[i]; val cy = ys[i]
            val mid = (px + cx) / 2f
            solid.cubicTo(mid, py, mid, cy, cx, cy)
        }
        drawPath(solid, color = GOLD, style = Stroke(width = 2.8f))

        // 未来月虚线拉平
        if (safeIdx < n - 1) {
            val dash = Path().apply {
                moveTo(xs[safeIdx], currentY)
                lineTo(xs[n - 1], currentY)
            }
            drawPath(
                dash, color = GOLD_SOFT,
                style = Stroke(width = 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            )
        }

        // hover 位置竖线
        if (hoverIdx in 0..(n - 1)) {
            drawLine(
                color = Color(0xFF94A3B8).copy(alpha = 0.5f),
                start = Offset(xs[hoverIdx], plotTopPad),
                end = Offset(xs[hoverIdx], plotTopPad + plotH),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
            )
        }

        // 当前月数据点（仅当无 hover）
        if (hoverIdx == currentMonthIdx) {
            drawCircle(color = GOLD, radius = 5f, center = Offset(xs[safeIdx], currentY))
            drawCircle(color = Color.White, radius = 2.2f, center = Offset(xs[safeIdx], currentY))
        } else if (hoverIdx in 0..(n - 1)) {
            drawCircle(color = GOLD, radius = 5f, center = Offset(xs[hoverIdx], ys[hoverIdx]))
            drawCircle(color = Color.White, radius = 2.2f, center = Offset(xs[hoverIdx], ys[hoverIdx]))
        } else {
            drawCircle(color = GOLD, radius = 5f, center = Offset(xs[safeIdx], currentY))
            drawCircle(color = Color.White, radius = 2.2f, center = Offset(xs[safeIdx], currentY))
        }

        // X 轴月份
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = 8.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            for (i in 0 until n) {
                drawText(months[i], xs[i], h - 6f, paint)
            }
        }
    }
}
