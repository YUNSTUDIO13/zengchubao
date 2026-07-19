package com.example.zengchubao.ui.screens.breakdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import java.text.NumberFormat
import java.util.Locale

private val CN_2 = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerScreen(
    deposits: List<Deposit>,
    initialYear: Int,
    initialMonth: Int,
    onBack: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    val holding = remember(deposits) { deposits.filter { it.status == DepositStatus.HOLDING } }
    val activeMonths = remember(holding) { collectActiveMonths(holding) }
    val byYear = activeMonths.groupBy { it.first }
    val years = activeMonths.map { it.first }.distinct().sortedDescending()

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB)).statusBarsPadding()) {
        // 顶部返回栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
            }
            Text("选择月份", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B),
                modifier = Modifier.weight(1f))
        }

        if (activeMonths.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("暂无收益记录", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)) {
                years.forEach { y ->
                    item(key = "year_$y") {
                        YearHeader(year = y, isCurrent = y == initialYear,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))
                    }
                    val months = byYear[y]?.sortedByDescending { it.second } ?: emptyList()
                    items(months, key = { (yy, mm) -> "${yy}_$mm" }) { (_, m) ->
                        MonthRow(year = y, month = m, holding = holding,
                            isCurrent = y == initialYear && m == initialMonth,
                            onClick = { onSelect(y, m) },
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeader(year: Int, isCurrent: Boolean, modifier: Modifier = Modifier) {
    Text("$year",
        fontSize = 15.sp, fontWeight = FontWeight.Bold,
        color = Color(0xFF2563EB),
        modifier = modifier
            .let { if (isCurrent) it.background(Color(0xFFEFF6FF), RoundedCornerShape(999.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp) else it })
}

@Composable
private fun MonthRow(
    year: Int,
    month: Int,
    holding: List<Deposit>,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthly = remember(holding, year, month) { buildMonthlyIncome(holding, year, month, todayString()) }
    val daysInM = daysInMonth(year, month)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
    ) {
        // 第一行：X月（左） + 月份收益值（右）
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${month}月", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                if (isCurrent) {
                    Text("当前", fontSize = 9.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium,
                        modifier = Modifier.background(Color(0xFFEFF6FF), RoundedCornerShape(999.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("+¥${CN_2.format(monthly.monthTotal)}",
                fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
        }
        // 第二行：热力图（方块）+ 年份（蓝色，11sp）
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                for (d in 1..daysInM) {
                    val date = "%04d-%02d-%02d".format(year, month, d)
                    val has = monthly.byDate[date]?.hasIncome() == true
                    Box(modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (has) Color(0xFF2563EB) else Color(0xFFE2E8F0)))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("$year", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
        }
    }
}
