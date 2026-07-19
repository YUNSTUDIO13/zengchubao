package com.example.zengchubao.ui.screens.breakdown

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
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
import java.text.NumberFormat
import java.util.Locale

private val CN_2 = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

data class DailyIncomeEntry(val date: String, val deposits: List<Pair<Deposit, Double>>) {
    val total: Double get() = deposits.sumOf { it.second }
    fun hasIncome(): Boolean = deposits.isNotEmpty()
}

data class MonthlyIncome(val year: Int, val month: Int, val byDate: Map<String, DailyIncomeEntry>) {
    val monthTotal: Double get() = byDate.values.sumOf { it.total }
    val activeDays: Int get() = byDate.values.count { it.hasIncome() }
}

fun dailyIncomeForDepositOnDate(dep: Deposit, date: String, today: String): Double {
    if (dep.status != DepositStatus.HOLDING) return 0.0
    if (date < dep.startDate || date > dep.endDate) return 0.0
    if (date > today) return 0.0
    val basis = yearBasis(dep.calcMethod).toDouble()
    return dep.principal * (dep.annualRate / 100.0) / basis
}

fun buildMonthlyIncome(holding: List<Deposit>, year: Int, month: Int, today: String): MonthlyIncome {
    val daysInMonth = daysInMonth(year, month)
    val byDate = mutableMapOf<String, DailyIncomeEntry>()
    for (d in 1..daysInMonth) {
        val date = "%04d-%02d-%02d".format(year, month, d)
        if (date > today) {
            byDate[date] = DailyIncomeEntry(date, emptyList())
            continue
        }
        val entries = holding.mapNotNull { dep ->
            val income = dailyIncomeForDepositOnDate(dep, date, today)
            if (income > 0.0) dep to income else null
        }
        byDate[date] = DailyIncomeEntry(date, entries)
    }
    return MonthlyIncome(year, month, byDate)
}

fun daysInMonth(year: Int, month: Int): Int {
    val nextMonth = if (month == 12) Pair(year + 1, 1) else Pair(year, month + 1)
    val firstOfNext = "%04d-%02d-01".format(nextMonth.first, nextMonth.second)
    val lastOfThis = addDays(firstOfNext, -1)
    return lastOfThis.substring(8, 10).toInt()
}

fun firstDayOfMonthOffset(year: Int, month: Int): Int {
    val ymd = "%04d-%02d-01".format(year, month)
    val parts = ymd.split("-").map { it.toInt() }
    val cal = java.util.GregorianCalendar(parts[0], parts[1] - 1, parts[2])
    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
    return if (dow == java.util.Calendar.SUNDAY) 0 else dow - 1
}

fun collectActiveMonths(holding: List<Deposit>): List<Pair<Int, Int>> {
    if (holding.isEmpty()) return emptyList()
    val months = mutableSetOf<Pair<Int, Int>>()
    val today = todayString()
    for (dep in holding) {
        val startY = dep.startDate.substring(0, 4).toInt()
        val startM = dep.startDate.substring(5, 7).toInt()
        val endY = (if (dep.endDate < today) dep.endDate else today).substring(0, 4).toInt()
        val endM = (if (dep.endDate < today) dep.endDate else today).substring(5, 7).toInt()
        var y = startY
        var m = startM
        while (y < endY || (y == endY && m <= endM)) {
            months.add(y to m)
            m++
            if (m > 12) { m = 1; y++ }
        }
    }
    return months.toList().sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
}

/** 大数缩写：<100 保留 2 位小数；<1000 取整；<10000 → k；≥10000 → w。 */
fun formatProfit(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    val absValue = kotlin.math.abs(value)
    return when {
        absValue < 100 -> "$sign${"%.2f".format(value)}"          // +51.68 / -9.37
        absValue < 1000 -> "$sign${value.toInt()}"                 // 过百取整 +201
        absValue < 10000 -> "$sign${"%.2fk".format(value / 1000)}" // +1.65k
        else -> "$sign${"%.2fw".format(value / 10000)}"            // +2.08w
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBreakdownScreen(
    deposits: List<Deposit>,
    onBack: () -> Unit
) {
    val holding = remember(deposits) {
        deposits.filter { it.status == DepositStatus.HOLDING }
    }

    val today = todayString()
    val todayYear = today.substring(0, 4).toInt()
    val todayMonth = today.substring(5, 7).toInt()
    val todayDay = today.substring(8, 10).toInt()

    var currentYear by remember { mutableStateOf(todayYear) }
    var currentMonth by remember { mutableStateOf(todayMonth) }
    var selectedDate by remember { mutableStateOf(today) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val currentMonthly = remember(holding, currentYear, currentMonth, today) {
        buildMonthlyIncome(holding, currentYear, currentMonth, today)
    }
    val selectedEntry = currentMonthly.byDate[selectedDate] ?: DailyIncomeEntry(selectedDate, emptyList())

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB)).statusBarsPadding()) {
        // 顶部返回栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
            }
            Text("日收益明细", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
            // 日历卡片
            item(key = "calendar") {
                CalendarPager(
                    holding = holding,
                    today = today,
                    todayDay = if (currentYear == todayYear && currentMonth == todayMonth) todayDay else null,
                    currentYear = currentYear,
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    onSelectDate = { date ->
                        selectedDate = date
                        val parts = date.split("-")
                        if (parts.size == 3) {
                            currentYear = parts[0].toInt()
                            currentMonth = parts[1].toInt()
                        }
                    },
                    onCurrentMonthChange = { y, m ->
                        currentYear = y; currentMonth = m
                        selectedDate = if (y == todayYear && m == todayMonth) today
                        else "%04d-%02d-01".format(y, m)
                    },
                    onTapYearMonth = { showMonthPicker = true },
                    onPrevMonth = {
                        val prev = if (currentMonth == 1) Pair(currentYear - 1, 12) else Pair(currentYear, currentMonth - 1)
                        currentYear = prev.first; currentMonth = prev.second
                        selectedDate = if (prev.first == todayYear && prev.second == todayMonth) today
                        else "%04d-%02d-01".format(prev.first, prev.second)
                    },
                    onNextMonth = {
                        val next = if (currentMonth == 12) Pair(currentYear + 1, 1) else Pair(currentYear, currentMonth + 1)
                        currentYear = next.first; currentMonth = next.second
                        selectedDate = if (next.first == todayYear && next.second == todayMonth) today
                        else "%04d-%02d-01".format(next.first, next.second)
                    },
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp)
                )
            }

            // 下方明细行：仅日期·日收益 8sp（去合计）
            item(key = "summary_row") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateLabel = formatDateLong(selectedDate)
                    Text("${dateLabel} · 日收益", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                    Text("${selectedEntry.deposits.size}单", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8))
                }
            }

            if (selectedEntry.deposits.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("当前日暂无收益", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                }
            } else {
                items(selectedEntry.deposits, key = { it.first.id }) { (dep, income) ->
                    DailyIncomeCard(
                        deposit = dep,
                        income = income,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 7.dp)
                    )
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerScreen(
            deposits = deposits,
            initialYear = currentYear,
            initialMonth = currentMonth,
            onBack = { showMonthPicker = false },
            onSelect = { y, m ->
                currentYear = y; currentMonth = m
                selectedDate = if (y == todayYear && m == todayMonth) today
                else "%04d-%02d-01".format(y, m)
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun CalendarPager(
    holding: List<Deposit>,
    today: String,
    todayDay: Int?,
    currentYear: Int,
    currentMonth: Int,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onCurrentMonthChange: (Int, Int) -> Unit,
    onTapYearMonth: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayYear = today.substring(0, 4).toInt()
    val todayMonth = today.substring(5, 7).toInt()
    val pagerState = rememberPagerState(initialPage = 500) { 1000 }

    // Pager 同步外部年月变化（如选择月份页面跳转）
    LaunchedEffect(currentYear, currentMonth) {
        val diff = (currentYear - todayYear) * 12 + (currentMonth - todayMonth)
        val targetPage = 500 + diff
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val diff = page - 500
            val newY = todayYear + diff / 12
            val newM = todayMonth + diff % 12
            val normalizedM = when {
                newM > 12 -> newM - 12
                newM < 1 -> newM + 12
                else -> newM
            }
            val normalizedY = if (newM > 12) newY + 1 else if (newM < 1) newY - 1 else newY
            onCurrentMonthChange(normalizedY, normalizedM)
        }
    }

    HorizontalPager(state = pagerState, pageSpacing = 16.dp, modifier = modifier) { page ->
        val diff = page - 500
        var y = todayYear + diff / 12
        var m = todayMonth + diff % 12
        if (m > 12) { m -= 12; y++ }
        if (m < 1) { m += 12; y-- }
        val monthly = remember(holding, y, m, today) {
            buildMonthlyIncome(holding, y, m, today)
        }
        CalendarCard(
            year = y, month = m,
            monthly = monthly,
            today = today,
            todayDay = if (y == todayYear && m == todayMonth) todayDay else null,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            onTapYearMonth = onTapYearMonth,
            onPrevMonth = onPrevMonth,
            onNextMonth = onNextMonth
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CalendarCard(
    year: Int,
    month: Int,
    monthly: MonthlyIncome,
    today: String,
    todayDay: Int?,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onTapYearMonth: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val daysCount = daysInMonth(year, month)
    val firstOffset = firstDayOfMonthOffset(year, month)
    // 上月日期填充：6 行固定 × 7 列 = 42 格
    val prevMonthDays = daysInMonth(if (month == 1) year - 1 else year, if (month == 1) 12 else month - 1)
    val leadingCount = firstOffset
    val trailingCount = 42 - leadingCount - daysCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
    ) {
        // 顶部：左右箭头 + 年月标题 + 月合计
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .clickable { onPrevMonth() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronLeft, "上月", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f).clickable { onTapYearMonth() },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${year}年${month}月", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("月合计 +¥${CN_2.format(monthly.monthTotal)}", fontSize = 10.sp,
                    color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .clickable { onNextMonth() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronRight, "下月", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六").forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 6 行固定
        val cells = mutableListOf<Triple<Int, String, Boolean>>()  // (day, date, isCurrentMonth)
        // 上月填充
        for (i in 0 until leadingCount) {
            val d = prevMonthDays - leadingCount + 1 + i
            val py = if (month == 1) year - 1 else year
            val pm = if (month == 1) 12 else month - 1
            val date = "%04d-%02d-%02d".format(py, pm, d)
            cells.add(Triple(d, date, false))
        }
        // 当月
        for (d in 1..daysCount) {
            val date = "%04d-%02d-%02d".format(year, month, d)
            cells.add(Triple(d, date, true))
        }
        // 下月填充
        val ny = if (month == 12) year + 1 else year
        val nm = if (month == 12) 1 else month + 1
        for (i in 1..trailingCount) {
            val date = "%04d-%02d-%02d".format(ny, nm, i)
            cells.add(Triple(i, date, false))
        }

        // 6 行固定 + 行间 2dp 缝隙
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until 7) {
                    val idx = row * 7 + col
                    val (d, date, isCurrent) = cells[idx]
                    val isSelected = date == selectedDate
                    val entry = monthly.byDate[date]
                    val income = entry?.total ?: 0.0
                    val isToday = isCurrent && d == todayDay && date == today
                    val isFuture = isCurrent && date > today
                    DayCell(
                        day = d,
                        income = income,
                        isCurrentMonth = isCurrent,
                        isSelected = isSelected,
                        isToday = isToday,
                        isFuture = isFuture,
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    income: Double,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasIncome = income > 0.0
    val bgColor = when {
        isSelected -> Color(0xFFFFF9E6)            // 淡黄（更浅）
        hasIncome && isCurrentMonth -> Color(0xFFFEF2F2)  // 浅粉（更浅）
        isFuture -> Color(0xFFF8FAFC)
        !isCurrentMonth -> Color(0xFFFAFBFC)        // 邻月浅
        else -> Color.White
    }
    val textColor = when {
        isSelected -> Color(0xFF1E293B)
        !isCurrentMonth -> Color(0xFFCBD5E1)
        isFuture -> Color(0xFFCBD5E1)
        isToday -> Color(0xFFDC2626)
        else -> Color(0xFF1E293B)
    }
    val incomeColor = when {
        isSelected -> Color(0xFFDC2626)
        hasIncome -> Color(0xFFDC2626)
        else -> Color(0xFF94A3B8)
    }
    Box(
        modifier = modifier
            .requiredHeight(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.5.dp, bgColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()) {
            Text("$day", fontSize = 12.sp, lineHeight = 13.sp, color = textColor,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium)
            if (isCurrentMonth && (hasIncome || isSelected)) {
                Spacer(Modifier.height(2.dp))
                Text(formatProfit(income), fontSize = 8.sp, lineHeight = 9.sp,
                    color = incomeColor,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DailyIncomeCard(
    deposit: Deposit,
    income: Double,
    modifier: Modifier = Modifier
) {
    val remainingDays = daysUntilMaturity(deposit.endDate)
    val isExpired = remainingDays < 0
    val isExpiringSoon = remainingDays in 0..30
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            // L1: 产品名 (左，weight 1 填充) | 收益金额 (右，自然宽)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deposit.productName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text("+¥${CN_2.format(income)}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626))
            }
            // L2: 银行名 · 利率 (左) | 本金 (右)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(deposit.bankName, fontSize = 10.sp, color = Color(0xFF94A3B8))
                Text(" · ", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                RateIcon(rate = deposit.annualRate, modifier = Modifier.size(10.dp), color = Color(0xFF94A3B8))
                Text(" ${"%.2f".format(deposit.annualRate)}%", fontSize = 10.sp, color = Color(0xFF94A3B8))
                Spacer(Modifier.weight(1f))
                Text("¥${CN_2.format(deposit.principal)}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B))
            }
            // L3: 起投日期-到期日期 | 预计到期时间
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${deposit.startDate} ~ ${deposit.endDate}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                val countdownText = when {
                    isExpired -> "已过期${-remainingDays}天"
                    isExpiringSoon -> "${remainingDays}天后到期"
                    remainingDays == 0 -> "今日到期"
                    else -> "${remainingDays}天后到期"
                }
                Text(countdownText, fontSize = 9.sp,
                    color = if (isExpired) Color(0xFFF87171)
                    else if (isExpiringSoon) Color(0xFFF59E0B)
                    else Color(0xFF94A3B8))
            }
        }
    }
}

private fun formatDateLong(date: String): String {
    val parts = date.split("-")
    if (parts.size != 3) return date
    return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
}
