package com.example.zengchubao.ui.screens.reports

import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import java.text.NumberFormat
import java.util.Locale

private val CN = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 0; maximumFractionDigits = 0 }
private fun fmt(v: Double) = "¥${CN.format(v)}"

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
    settings: AppSettings = AppSettings()
) {
    val holding = remember(deposits) { deposits.filter { it.status == DepositStatus.HOLDING } }
    val assetBalance = remember(holding) { calculateAssetBalance(holding) }
    val totalYield = remember(holding) { calculateTotalMaturityYield(holding) }
    val totalDeposited = remember(holding) { holding.sumOf { it.principal } }
    val annualExpected = remember(holding) { calculateAnnualExpectedYield(holding) }
    val weightedRate = remember(holding) { calculateWeightedRate(holding) }
    val dailyRate = remember(annualExpected) {
        val rate = annualExpected / 365.0
        "%.2f".format(kotlin.math.round(rate * 100) / 100)  // 四舍五入保留2位小数
    }

    // 按银行分组
    val bankGroups = remember(holding) {
        holding.groupBy { it.bankName }.map { (bank, deps) ->
            val bal = deps.sumOf { it.principal + calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays) }
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
                        weightedRate = weightedRate
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
                }
            }
            if (index < settings.reportItems.lastIndex) {
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── 资产总览（3x2网格） ──
// 布局：持有本金/资产余额 | 日收益率/预期年度收益 | 到期总收益/综合年化率

@Composable
private fun AssetOverviewSection(
    totalDeposited: Double,
    assetBalance: Double,
    annualExpected: Double,
    totalYield: Double,
    dailyRate: String,
    weightedRate: Double
) {
    val rateText = "${dailyRate}"
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("资产收益", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                AssetField("持有本金", fmt(totalDeposited), Modifier.weight(1f))
                AssetField("资产余额", fmt(assetBalance), Modifier.weight(1f))
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                AssetField("日收益率", "¥$rateText", Modifier.weight(1f))
                AssetField("预期年度收益", fmt(annualExpected), Modifier.weight(1f))
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                AssetField("到期总收益", fmt(totalYield), Modifier.weight(1f))
                AssetField("综合年化率", "${"%.2f".format(weightedRate)}%", Modifier.weight(1f))
            }
        }
    }
}

// ── 按银行资产分布（饼图） ──

@Composable
private fun BankDistributionSection(
    bankGroups: List<Triple<String, Double, List<Deposit>>>,
    totalBalance: Double,
    bankColorMap: Map<String, Color>
) {
    SectionCard("按银行资产分布") {
        if (bankGroups.isEmpty()) {
            Text("暂无数据", color = Color(0xFF94A3B8), fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            CanvasPieChart(
                data = bankGroups.map { it.second },
                colors = bankGroups.map { bankColorMap[it.first] ?: Color(0xFFCBD5E1) },
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            bankGroups.forEach { (bank, balance, _) ->
                val pct = if (totalBalance > 0) (balance / totalBalance * 100) else 0.0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(bankColorMap[bank] ?: Color(0xFFCBD5E1), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(bank, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Text("${"%.1f".format(pct)}%  ${fmt(balance)}", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                }
            }
        }
    }
}

// ── 资产分类明细表 ──

@Composable
private fun AssetCategorySection(
    bankGroups: List<Triple<String, Double, List<Deposit>>>,
    totalBalance: Double,
    bankColorMap: Map<String, Color>
) {
    SectionCard("资产分类明细") {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("类型", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.W600, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Text("余额", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.W600, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Text("占比", Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.W600, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Text("笔数", Modifier.weight(0.6f), fontSize = 10.sp, fontWeight = FontWeight.W600, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
        }
        HorizontalDivider(color = Color(0xFFF8FAFC))
        bankGroups.forEachIndexed { i, (bank, bal, deps) ->
            val pct = if (totalBalance > 0) (bal / totalBalance * 100) else 0.0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(8.dp).background(bankColorMap[bank] ?: Color(0xFFCBD5E1), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(bank, fontSize = 11.sp, color = Color(0xFF475569))
                }
                Text(fmt(bal), Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.W600, color = Color(0xFF334155), textAlign = TextAlign.Center)
                Text("${"%.1f".format(pct)}%", Modifier.weight(0.8f), fontSize = 11.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                Text("${deps.size}笔", Modifier.weight(0.6f), fontSize = 11.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            }
            if (i < bankGroups.size - 1) HorizontalDivider(color = Color(0xFFF8FAFC))
        }
    }
}
// ── 资产收益网格字段（透明圆角背景） ──

@Composable
private fun AssetField(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC)
    ) {
        Column(Modifier.padding(3.dp)) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.W500, color = Color(0xFF94A3B8), lineHeight = 10.sp)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), lineHeight = 14.sp)
        }
    }
}

// ── Figma 分区卡片容器 ──

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── 饼图 ──

@Composable
private fun CanvasPieChart(data: List<Double>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = data.sum()
    Canvas(modifier = modifier) {
        if (total <= 0) return@Canvas
        val r = minOf(size.width, size.height) / 2.5f
        val cx = size.width / 2; val cy = size.height / 2
        var start = -90f
        data.forEachIndexed { i, v ->
            val sweep = (v / total * 360).toFloat()
            drawArc(colors[i], start, sweep, true, Offset(cx - r, cy - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
            start += sweep
        }
        drawCircle(Color.White, r * 0.55f, Offset(cx, cy))
    }
}
