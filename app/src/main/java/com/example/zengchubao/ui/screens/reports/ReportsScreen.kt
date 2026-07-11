package com.example.zengchubao.ui.screens.reports

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

private val CN = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
private val CN_I = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 0; maximumFractionDigits = 0 }
private fun fmt(v: Double) = "¥${CN.format(v)}"
private fun fmtI(v: Double) = "¥${CN_I.format(v)}"

private val FALLBACK_BANK_COLORS = listOf(
    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6),
    Color(0xFFEF4444), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16),
    Color(0xFFF97316), Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFEAB308)
)

private fun getBankColor(bankName: String, bankColorMap: Map<String, Color>, fallbackIndex: Int): Color {
    return bankColorMap[bankName] ?: FALLBACK_BANK_COLORS[fallbackIndex % FALLBACK_BANK_COLORS.size]
}

private fun buildBankColorMap(bankNames: List<String>, banks: List<Bank>): Map<String, Color> {
    val result = mutableMapOf<String, Color>()
    var fallbackIndex = 0
    bankNames.forEach { name ->
        val bank = banks.find { it.name == name }
        val color = when {
            bank != null && bank.colorHex.isNotEmpty() -> parseColor(bank.colorHex)
            else -> {
                val c = FALLBACK_BANK_COLORS[fallbackIndex % FALLBACK_BANK_COLORS.size]
                fallbackIndex++
                c
            }
        }
        result[name] = color
    }
    return result
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFCBD5E1)
    }
}

@Composable
fun ReportsScreen(
    deposits: List<Deposit>,
    banks: List<Bank>,
    settings: AppSettings = AppSettings(),
    onDailyDetail: () -> Unit = {},
    onAnnualDetail: () -> Unit = {},
    onMaturityDetail: () -> Unit = {},
    onAccumulatedDetail: () -> Unit = {}
) {
    val holding = remember(deposits) { deposits.filter { it.status == DepositStatus.HOLDING } }
    val assetBalance = remember(holding) { calculateAssetBalance(holding) }
    val totalYield = remember(holding) { calculateTotalMaturityYield(holding) }
    val totalDeposited = remember(holding) { holding.sumOf { it.principal } }
    val annualExpected = remember(holding) { calculateAnnualExpectedYield(holding) }
    val weightedRate = remember(holding) { calculateWeightedRate(holding) }
    val dailyRate = remember(holding) {
        val today = todayString()
        val daily = holding
            .filter { it.startDate <= today }
            .sumOf { it.principal * (it.annualRate / 100.0) / yearBasis(it.calcMethod).toDouble() }
        "%.2f".format(daily)
    }
    val accumulatedYield = remember(holding) {
        holding.sumOf { calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays, it.calcMethod) }
    }
    val weightedRateText = remember(holding, weightedRate) {
        if (holding.isEmpty()) "---" else "${"%.2f".format(weightedRate)}%"
    }
    val archivedYield = remember(deposits) {
        deposits.filter { it.status != DepositStatus.HOLDING }
            .sumOf { it.maturityAmount - it.principal }
    }

    // 按银行分组
    val bankGroups = remember(holding) {
        holding.groupBy { it.bankName }.map { (bank, deps) ->
            val bal = deps.sumOf { it.principal + calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays, it.calcMethod) }
            Triple(bank, bal, deps)
        }.sortedByDescending { it.second }
    }
    val totalBalance = bankGroups.sumOf { it.second }

    val bankColorMap = remember(bankGroups, banks) { buildBankColorMap(bankGroups.map { it.first }, banks) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)
    ) {
        // ── Figma 头部 ──
        Text("数据报表", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp))

        // 按设置中 reportItems 的顺序与可见性渲染板块
        settings.reportItems.forEachIndexed { index, item ->
            AnimatedVisibility(
                visible = item.enabled,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
            ) {
                when (item.id) {
                    "assetOverview" -> AssetOverviewSection(
                        totalDeposited = totalDeposited,
                        assetBalance = assetBalance,
                        annualExpected = annualExpected,
                        totalYield = totalYield,
                        dailyRate = dailyRate,
                        accumulatedYield = accumulatedYield,
                        weightedRateText = weightedRateText,
                        archivedYield = archivedYield,
                        onDailyDetail = onDailyDetail,
                        onAnnualDetail = onAnnualDetail,
                        onMaturityDetail = onMaturityDetail,
                        onAccumulatedDetail = onAccumulatedDetail
                    )
                    "bankDistribution" -> BankDistributionSection(
                        bankGroups = bankGroups,
                        totalBalance = totalBalance,
                        bankColorMap = bankColorMap
                    )
                    "assetCategory" -> AssetCategorySection(
                        bankGroups = bankGroups,
                        totalBalance = totalBalance,
                        bankColorMap = bankColorMap
                    )
                    "assetTrend" -> AssetTrendSection(
                        deposits = deposits
                    )
                }
            }
            if (index < settings.reportItems.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── 资产总览（3x2网格） ──
// 布局：持有本金/资产余额 | 日收益/预期年度收益 | 持有中累计收益/到期总收益 | 归档历史收益

@Composable
private fun AssetOverviewSection(
    totalDeposited: Double,
    assetBalance: Double,
    annualExpected: Double,
    totalYield: Double,
    dailyRate: String,
    accumulatedYield: Double,
    weightedRateText: String,
    archivedYield: Double,
    onDailyDetail: () -> Unit = {},
    onAnnualDetail: () -> Unit = {},
    onMaturityDetail: () -> Unit = {},
    onAccumulatedDetail: () -> Unit = {}
) {
    val rateText = "${dailyRate}"
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("资产收益", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AssetField("持有本金", fmt(totalDeposited), Modifier.weight(1f))
                AssetField("资产余额", fmt(assetBalance), Modifier.weight(1f))
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AssetField("日收益", "¥$rateText", Modifier.weight(1f), onClick = onDailyDetail)
                AssetField("今年预估收益", fmt(annualExpected), Modifier.weight(1f), onClick = onAnnualDetail)
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AssetField("持有中累计收益", fmt(accumulatedYield), Modifier.weight(1f), onClick = onAccumulatedDetail)
                AssetField("到期总收益", fmt(totalYield), Modifier.weight(1f), onClick = onMaturityDetail)
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AssetField("综合年化率", weightedRateText, Modifier.weight(1f))
                AssetField("归档历史收益", fmt(archivedYield), Modifier.weight(1f))
            }
        }
    }
}

// ── 按银行资产分布（Donut 环形图 + 拉线 + 明细列表）──

private val OTHER_GRAY = Color(0xFFE2E8F0)
private const val MAX_DONUT_ITEMS = 7

@Composable
private fun BankDistributionSection(
    bankGroups: List<Triple<String, Double, List<Deposit>>>,
    totalBalance: Double,
    bankColorMap: Map<String, Color>
) {
    val isEmpty = bankGroups.isEmpty()
    val showAnim by remember { mutableStateOf(true) }

    val donutItems = if (isEmpty || totalBalance <= 0) emptyList() else {
        val top = bankGroups.take(MAX_DONUT_ITEMS)
        val otherSum = bankGroups.drop(MAX_DONUT_ITEMS).sumOf { it.second }
        val list = mutableListOf<Triple<String, Double, Color>>()
        top.forEach { (bank, bal, _) ->
            list.add(Triple(bank, bal, bankColorMap[bank] ?: Color(0xFFCBD5E1)))
        }
        if (otherSum > 0) list.add(Triple("其他", otherSum, OTHER_GRAY))
        list
    }

    SectionCard("按银行资产分布") {
        if (isEmpty) {
            // 资产为 0 态
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DonutEmptyChart()
                    Spacer(Modifier.height(8.dp))
                    Text("暂无资产", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        } else {
            // Donut 环形图（含中心金额 + 拉线标签）
            Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
                DonutChartWithLabels(
                    items = donutItems,
                    totalBalance = totalBalance,
                    showAnim = showAnim
                )
            }

            Spacer(Modifier.height(4.dp))

            // 明细列表
            Column {
                bankGroups.sortedByDescending { it.second }.forEach { (bank, balance, deps) ->
                    val pct = if (totalBalance > 0) (balance / totalBalance * 100) else 0.0
                    val color = bankColorMap[bank] ?: Color(0xFFCBD5E1)
                    BankDetailItem(
                        bankName = bank,
                        percentage = pct,
                        balance = balance,
                        count = deps.size,
                        color = color,
                        showAnim = showAnim
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── Donut 环形图：中心金额 + 拉线标签 ──

@Composable
private fun DonutChartWithLabels(
    items: List<Triple<String, Double, Color>>,
    totalBalance: Double,
    showAnim: Boolean
) {
    val density = LocalDensity.current
    val animProgress by animateFloatAsState(
        targetValue = if (showAnim) 1f else 1f,
        animationSpec = tween(800),
        label = "donutAnim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.47f
        val outerR = minOf(w * 0.32f, 120f)
        val innerR = outerR * 0.6f
        val strokeW = outerR - innerR

        val total = items.sumOf { it.second }
        if (total <= 0) return@Canvas

        var startAngle = -90f
        items.forEach { (name, value, color) ->
            val sweep = ((value / total * 360f).coerceAtLeast(1.5)).toFloat()
            val animatedSweep = sweep * animProgress
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2, outerR * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }

        // 中心覆盖白色圆（模拟中空）
        drawCircle(Color.White, radius = innerR, center = Offset(cx, cy))
    }

    // 中心文字叠加层
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-8).dp)
        ) {
            Text("资产总额", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.W500)
            Text(fmt(totalBalance), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        }
    }

    // 拉线标签（需要 viewport 坐标，独立于 Canvas 放 Box 外侧）
    Box(modifier = Modifier.fillMaxSize()) {
        val viewW = with(density) { 360.dp.toPx() }
        val viewH = with(density) { 230.dp.toPx() }
        val cx = viewW / 2f
        val cy = viewH * 0.47f
        val outerR = minOf(viewW * 0.32f, with(density) { 120.dp.toPx() })
        val labelRadius = outerR + with(density) { 18.dp.toPx() }
        val total = items.sumOf { it.second }.coerceAtLeast(1.0)

        var start = -90f
        items.forEach { (name, value, color) ->
            val sweep = ((value / total * 360f).coerceAtLeast(1.5)).toFloat()
            val midAngle = start + sweep / 2f
            val rad = Math.toRadians(midAngle.toDouble()).toFloat()
            val lx = cx + labelRadius * cos(rad)
            val ly = cy + labelRadius * sin(rad)
            val pct = value / total * 100

            Text(
                text = "$name ${"%.1f".format(pct)}%",
                fontSize = 8.sp,
                color = Color(0xFF475569),
                fontWeight = FontWeight.W500,
                modifier = Modifier.offset(
                    x = with(density) { (lx / density.density).dp },
                    y = with(density) { (ly / density.density).dp - 4.dp }
                )
            )
            start += sweep
        }
    }
}

// ── 环形图空态 ──

@Composable
private fun DonutEmptyChart() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val outerR = minOf(cx, cy) - 4f
            val strokeW = outerR * 0.4f
            drawArc(Color(0xFFF1F5F9), 0f, 360f, false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2, outerR * 2),
                style = Stroke(strokeW, cap = StrokeCap.Butt))
            drawCircle(Color.White, radius = outerR - strokeW, center = Offset(cx, cy))
        }
        Text("¥0.00", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
    }
}

// ── 银行明细列表项 ──

@Composable
private fun BankDetailItem(
    bankName: String,
    percentage: Double,
    balance: Double,
    count: Int,
    color: Color,
    showAnim: Boolean
) {
    val barAnim by animateFloatAsState(
        targetValue = if (showAnim) (percentage.toFloat() / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(600, delayMillis = 200),
        label = "barAnim"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 银行圆形 Icon（首字）
        BankIcon(bankName = bankName, color = color)

        Spacer(Modifier.width(10.dp))

        // 中间：银行名 + 占比 + 能量条
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bankName, fontSize = 11.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B))
                Spacer(Modifier.width(6.dp))
                Text("${"%.1f".format(percentage)}%", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
            Spacer(Modifier.height(4.dp))
            // 能量条
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(2.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barAnim)
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // 右侧：余额 + 笔数
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt(balance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text("${count}笔", fontSize = 9.sp, color = Color(0xFF94A3B8))
        }
    }
}

// ── 银行圆形 Icon（微立体感）──

@Composable
private fun BankIcon(bankName: String, color: Color) {
    val firstChar = bankName.firstOrNull()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(34.dp)
            .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(firstChar, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── 资产分类明细表（1:1 复刻参考图） ──

@Composable
private fun AssetCategorySection(
    bankGroups: List<Triple<String, Double, List<Deposit>>>,
    totalBalance: Double,
    bankColorMap: Map<String, Color>
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("资产分类明细", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Spacer(Modifier.height(8.dp))

            // 内层灰色圆角卡片（2层结构：白卡→灰卡）
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
            ) {
                // 表头（浅灰色背景）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("类型", Modifier.weight(1.2f), fontSize = 9.sp, fontWeight = FontWeight.W600, color = Color(0xFF475569), textAlign = TextAlign.Start)
                    Text("余额", Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.W600, color = Color(0xFF475569), textAlign = TextAlign.Center)
                    Text("占比", Modifier.weight(0.8f), fontSize = 9.sp, fontWeight = FontWeight.W600, color = Color(0xFF475569), textAlign = TextAlign.Center)
                    Text("笔数", Modifier.weight(0.6f).padding(end = 6.dp), fontSize = 9.sp, fontWeight = FontWeight.W600, color = Color(0xFF475569), textAlign = TextAlign.Center)
                }

                // 数据行（浅浅灰色：内卡片底色，仅用分割线分隔）
                Column(Modifier.padding(horizontal = 8.dp)) {
                    bankGroups.forEachIndexed { i, (bank, bal, deps) ->
                        val pct = if (totalBalance > 0) (bal / totalBalance * 100) else 0.0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bank, Modifier.weight(1.2f), fontSize = 11.sp, color = Color(0xFF1E293B), textAlign = TextAlign.Start)
                            Text(fmtI(bal), Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B), textAlign = TextAlign.Center)
                            Text("${"%.1f".format(pct)}%", Modifier.weight(0.8f), fontSize = 11.sp, color = Color(0xFF475569), textAlign = TextAlign.Center)
                            Text("${deps.size}笔", Modifier.weight(0.6f).padding(end = 6.dp), fontSize = 11.sp, color = Color(0xFF475569), textAlign = TextAlign.Center)
                        }
                        if (i < bankGroups.size - 1) {
                            HorizontalDivider(color = Color(0xFFEEF2F6), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}
// ── 资产收益网格字段（透明圆角背景） ──

@Composable
private fun AssetField(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC)
    ) {
        Column(Modifier.padding(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.W500, color = Color(0xFF94A3B8), lineHeight = 10.sp)
                if (onClick != null) {
                    Icon(Icons.Filled.ChevronRight, null, Modifier.size(10.dp), tint = Color(0xFF94A3B8))
                }
            }
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), lineHeight = 14.sp)
        }
    }
}

// ── Figma 分区卡片容器 ──

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}


