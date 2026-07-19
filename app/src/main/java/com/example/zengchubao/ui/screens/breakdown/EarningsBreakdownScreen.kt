package com.example.zengchubao.ui.screens.breakdown

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import com.example.zengchubao.ui.screens.home.RateIcon
import com.example.zengchubao.ui.screens.home.WalletIcon
import com.example.zengchubao.ui.screens.home.MiniBadge
import java.text.NumberFormat
import java.util.Locale

private val CN_INT = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
private val CN_2 = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

enum class BreakdownMode(val title: String, val metricLabel: String) {
    DAILY("日收益", "日利息"),
    ANNUAL("今年预估收益", "今年预估收益"),
    ACCUMULATED("持有中累计收益", "历史已累计收益"),
    MATURITY("到期总收益", "到期利息")
}

@Composable
fun EarningsBreakdownScreen(
    deposits: List<Deposit>,
    mode: BreakdownMode,
    onBack: () -> Unit
) {
    if (mode == BreakdownMode.DAILY) {
        DailyBreakdownScreen(deposits = deposits, onBack = onBack)
        return
    }
    val holding = remember(deposits, mode) {
        deposits
            .filter {
                if (mode == BreakdownMode.ANNUAL) true  // 今年预估：含已归档
                else it.status != DepositStatus.ARCHIVED  // 持有中累计/到期：不含已归档
            }
            .sortedBy { it.endDate }
    }

    fun metricValue(dep: Deposit): Double {
        val basis = yearBasis(dep.calcMethod).toDouble()
        return when (mode) {
            BreakdownMode.DAILY -> dep.principal * (dep.annualRate / 100.0) / basis
            BreakdownMode.ANNUAL -> {
                val today = todayString()
                val yearStart = "${today.take(4)}-01-01"
                val yearEnd = "${today.take(4)}-12-31"
                val start = if (dep.startDate > yearStart) addDays(dep.startDate, 1) else yearStart
                val end = if (dep.endDate < yearEnd) dep.endDate else yearEnd
                if (start >= end) 0.0
                else dep.principal * (dep.annualRate / 100.0) / basis * (daysBetween(start, end) + 1)
            }
            BreakdownMode.ACCUMULATED -> {
                val today = todayString()
                val elapsed = maxOf(0, minOf(daysBetween(dep.startDate, today), dep.termDays))
                dep.principal * (dep.annualRate / 100.0) / basis * elapsed
            }
            BreakdownMode.MATURITY -> {
                dep.principal * (dep.annualRate / 100.0) * (dep.termDays.toDouble() / 360.0)
            }
        }
    }

    val totalMetric = holding.sumOf { metricValue(it) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB)).statusBarsPadding()) {
        // 顶部返回栏 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
            }
            Text(mode.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B),
                modifier = Modifier.weight(1f))
            Text("¥${CN_2.format(totalMetric)}", fontSize = 13.sp, fontWeight = FontWeight.W600,
                color = Color(0xFF2563EB))
        }
        if (holding.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("暂无存单", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                items(holding, key = { it.id }) { dep ->
                    BreakdownDepositCard(
                        deposit = dep,
                        metricLabel = mode.metricLabel,
                        metricValue = metricValue(dep),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownDepositCard(
    deposit: Deposit,
    metricLabel: String,
    metricValue: Double,
    modifier: Modifier = Modifier
) {
    val remainingDays = daysUntilMaturity(deposit.endDate)
    val isArchived = deposit.status == DepositStatus.ARCHIVED
    val isExpired = deposit.status == DepositStatus.MATURED || remainingDays < 0
    val isExpiringSoon = remainingDays in 0..30

    val dateColor = when {
        isArchived -> Color(0xFF94A3B8)
        isExpired -> Color(0xFFF87171)
        isExpiringSoon -> Color(0xFFF59E0B)
        else -> Color(0xFF94A3B8)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // L1: 产品名 (左) | 利息 (右，红加粗)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(deposit.productName, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                Text("+¥${CN_2.format(metricValue)}", fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626))
            }

            // L2: 银行名 · 利率 (左) | 本金 (右，加粗)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(deposit.bankName, fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xFF94A3B8))
                Text(" · ", fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xFFCBD5E1))
                RateIcon(rate = deposit.annualRate, modifier = Modifier.size(10.dp), color = Color(0xFF94A3B8))
                Text(" ${"%.2f".format(deposit.annualRate)}%", fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xFF94A3B8))
                Spacer(Modifier.weight(1f))
                Text("¥${CN_2.format(deposit.principal)}", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B))
            }

            // L3: 起止日期 (左) | 倒计时 (右)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${deposit.startDate} ~ ${deposit.endDate}", fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xFF94A3B8))
                val countdownText = when {
                    isArchived -> "已归档"
                    isExpired -> "已过期${-remainingDays}天"
                    isExpiringSoon -> "${remainingDays}天后到期"
                    remainingDays == 0 -> "今日到期"
                    else -> "${remainingDays}天后到期"
                }
                Text(countdownText, fontSize = 9.sp, lineHeight = 11.sp, color = dateColor)
            }
        }
    }
}
