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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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
    val allDeposits = remember(deposits) { deposits }
    val activeMonths = remember(allDeposits) { collectActiveMonths(allDeposits) }
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
            Text("本月", fontSize = 12.sp, color = Color(0xFF2563EB),
                modifier = Modifier.clickable {
                    onSelect(todayString().substring(0,4).toInt(), todayString().substring(5,7).toInt())
                })
        }

        if (activeMonths.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("暂无收益记录", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)) {
                years.forEach { y ->
                    item(key = "year_$y") {
                        YearHeader(year = y,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))
                    }
                    val allMonths = byYear[y]?.sortedBy { it.second } ?: emptyList()
                    val rows = allMonths.chunked(4)
                    rows.forEach { row ->
                        item(key = "row_${y}_${row.firstOrNull()?.second ?: 0}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { (_, m) ->
                                    MonthCard(
                                        year = y, month = m, holding = allDeposits,
                                        isCurrent = y == initialYear && m == initialMonth,
                                        onClick = { onSelect(y, m) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // 填充不足 4 格的空位
                                repeat(4 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCard(
    year: Int, month: Int, holding: List<Deposit>,
    isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val monthly = remember(holding, year, month) { buildMonthlyIncome(holding, year, month, todayString()) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFAFBFC))
            .clickable { onClick() }
            .padding(vertical = 12.dp)
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("${month}月", fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) Color(0xFF2563EB) else Color(0xFF1E293B),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
            Text("+¥${CN_2.format(monthly.monthTotal)}", fontSize = 8.sp, lineHeight = 9.sp,
                color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold, maxLines = 1,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
    }
}

@Composable
private fun YearHeader(year: Int, modifier: Modifier = Modifier) {
    Text("$year",
        fontSize = 15.sp, fontWeight = FontWeight.Bold,
        color = Color(0xFF2563EB),
        modifier = modifier)
}
