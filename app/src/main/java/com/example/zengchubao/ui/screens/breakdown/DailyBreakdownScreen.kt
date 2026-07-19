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

/** 单日收益：日期 → 当日有收益的存单列表（每条带收益金额） */
data class DailyIncomeEntry(val date: String, val deposits: List<Pair<Deposit, Double>>) {
    val total: Double get() = deposits.sumOf { it.second }
    fun hasIncome(): Boolean = deposits.isNotEmpty()
}

/** 月度数据：年-月 → 当月每日收益 */
data class MonthlyIncome(val year: Int, val month: Int, val byDate: Map<String, DailyIncomeEntry>) {
    val monthTotal: Double get() = byDate.values.sumOf { it.total }
    val activeDays: Int get() = byDate.values.count { it.hasIncome() }
}

/** 每日单存单收益（仅持有中，仅今日及之前） */
fun dailyIncomeForDepositOnDate(dep: Deposit, date: String, today: String): Double {
    if (dep.status != DepositStatus.HOLDING) return 0.0
    if (date < dep.startDate || date > dep.endDate) return 0.0
    if (date > today) return 0.0
    val basis = yearBasis(dep.calcMethod).toDouble()
    return dep.principal * (dep.annualRate / 100.0) / basis
}

/** 构建某年某月每天的收益明细 */
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

/** 指定年-月有多少天 */
fun daysInMonth(year: Int, month: Int): Int {
    val nextMonth = if (month == 12) Pair(year + 1, 1) else Pair(year, month + 1)
    val firstOfNext = "%04d-%02d-01".format(nextMonth.first, nextMonth.second)
    val lastOfThis = addDays(firstOfNext, -1)
    return lastOfThis.substring(8, 10).toInt()
}

/** 某年某月1日是星期几 → 返回 [0..6] 偏移（日一二三四五六） */
fun firstDayOfMonthOffset(year: Int, month: Int): Int {
    val ymd = "%04d-%02d-01".format(year, month)
    val parts = ymd.split("-").map { it.toInt() }
    val cal = java.util.GregorianCalendar(parts[0], parts[1] - 1, parts[2])
    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
    return if (dow == java.util.Calendar.SUNDAY) 0 else dow - 1
}

/** 提取所有有收益的年月（按时间倒序） */
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

        // 可滚动内容：日历 + 下方明细
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
            // 日历卡片
            item(key = "calendar") {
                CalendarPager(
                    holding = holding,
                    today = today,
                    todayDay = if (currentYear == todayYear && currentMonth == todayMonth) todayDay else null,
                    selectedDate = selectedDate,
                    onSelectDate = { date ->
                        selectedDate = date
                        // 从 date 提取年月
                        val parts = date.split("-")
                        if (parts.size == 3) {
                            currentYear = parts[0].toInt()
                            currentMonth = parts[1].toInt()
                        }
                    },
                    onCurrentMonthChange = { y, m ->
                        currentYear = y; currentMonth = m
                        // 切换月后默认选中该月今日（如果存在），否则 1 号
                        selectedDate = if (y == todayYear && m == todayMonth) today
                        else "%04d-%02d-01".format(y, m)
                    },
                    onTapYearMonth = { showMonthPicker = true },
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp)
                )
            }

            // 下方明细行
            item(key = "summary_row") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateLabel = formatDateLong(selectedDate)
                    Text("${dateLabel} · 日收益", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                    Text("合计 +¥${CN_2.format(selectedEntry.total)}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626))
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
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onCurrentMonthChange: (Int, Int) -> Unit,
    onTapYearMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayYear = today.substring(0, 4).toInt()
    val todayMonth = today.substring(5, 7).toInt()
    val pagerState = rememberPagerState(initialPage = 500) { 1000 }

    // 监听 pager 切换
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

    HorizontalPager(state = pagerState, modifier = modifier) { page ->
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
            onTapYearMonth = onTapYearMonth
        )
    }
}

@Composable
private fun CalendarCard(
    year: Int,
    month: Int,
    monthly: MonthlyIncome,
    today: String,
    todayDay: Int?,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onTapYearMonth: () -> Unit
) {
    val daysCount = daysInMonth(year, month)
    val firstOffset = firstDayOfMonthOffset(year, month)
    val totalCells = firstOffset + daysCount
    val weeks = (totalCells + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(0.6.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        // 年-月 + 月合计
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Column(modifier = Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${year}年${month}月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), modifier = Modifier.clickable { onTapYearMonth() })
                Text("月合计 +¥${CN_2.format(monthly.monthTotal)}", fontSize = 11.sp,
                    color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(6.dp))

        // 日期格子
        var dayCounter = 1
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val cellIndex = week * 7 + dow
                    if (cellIndex < firstOffset || dayCounter > daysCount) {
                        Box(modifier = Modifier.weight(1f).height(62.dp))
                    } else {
                        val d = dayCounter
                        val date = "%04d-%02d-%02d".format(year, month, d)
                        val entry = monthly.byDate[date]
                        val income = entry?.total ?: 0.0
                        val isSelected = date == selectedDate
                        val isToday = d == todayDay && year.toString() + "-" +
                            (if (month < 10) "0$month" else "$month") == today.take(7)
                        val isFuture = date > today
                        DayCell(
                            day = d,
                            income = income,
                            isSelected = isSelected,
                            isToday = isToday,
                            isFuture = isFuture,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                        dayCounter++
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
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasIncome = income > 0.0
    val bgColor = when {
        isSelected -> Color(0xFFDC2626)
        hasIncome -> Color(0xFFFEE2E2)
        isFuture -> Color(0xFFF1F5F9)
        else -> Color.White
    }
    val textColor = when {
        isSelected -> Color.White
        isFuture -> Color(0xFFCBD5E1)
        else -> Color(0xFF1E293B)
    }
    val incomeColor = when {
        isSelected -> Color.White
        hasIncome -> Color(0xFFDC2626)
        else -> Color(0xFF94A3B8)
    }
    Box(
        modifier = modifier
            .height(62.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .border(
                width = if (isToday && !isSelected) 1.2.dp else 0.dp,
                color = Color(0xFFDC2626),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top,
            modifier = Modifier.padding(top = 6.dp)) {
            Text("$day", fontSize = 13.sp, color = textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            if (hasIncome || isSelected) {
                Spacer(Modifier.height(1.dp))
                Text(if (hasIncome) "+${CN_2.format(income)}" else "+0", fontSize = 8.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else incomeColor,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            // L1: 产品名 (左) | 收益金额 (右)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deposit.productName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Text("+¥${CN_2.format(income)}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626))
            }
            // L2: 银行名 · 利率
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(deposit.bankName, fontSize = 10.sp, color = Color(0xFF94A3B8))
                Text(" · ", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                RateIcon(rate = deposit.annualRate, modifier = Modifier.size(10.dp), color = Color(0xFF94A3B8))
                Text(" ${"%.2f".format(deposit.annualRate)}%", fontSize = 10.sp, color = Color(0xFF94A3B8))
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
