package com.example.zengchubao.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import com.example.zengchubao.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.zengchubao.model.*
import com.example.zengchubao.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

// ── 格式化 ──
private val CN = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
private val CN_INT = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 0; maximumFractionDigits = 0 }
private fun fmtI(v: Double) = "¥${CN_INT.format(v)}"
private fun fmtD(v: Double) = CN.format(v)

// ── 首页（1:1 精细复刻） ──

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    deposits: List<Deposit>,
    listState: LazyListState,
    onNewDeposit: () -> Unit,
    onDepositClick: (String) -> Unit,
    onAccumulatedDetail: () -> Unit = {},
    onAnnualDetail: () -> Unit = {},
    onDailyDetail: () -> Unit = {},
    onRefresh: () -> Unit
) {
    val holdingDeposits = remember(deposits) {
        deposits.filter { it.status == DepositStatus.HOLDING || it.status == DepositStatus.MATURED }
    }

    val bankNames = remember(holdingDeposits) { holdingDeposits.map { it.bankName }.distinct() }
    var selectedBanks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var timeFilter by remember { mutableIntStateOf(0) } // 0=全部, 1=本月, 2=本年
    var bankFilterExpanded by remember { mutableStateOf(false) }

    val bankFiltered = remember(holdingDeposits, selectedBanks) {
        if (selectedBanks.isEmpty()) holdingDeposits
        else holdingDeposits.filter { it.bankName in selectedBanks }
    }

    val filteredList = remember(bankFiltered, timeFilter) {
        val today = todayString()
        val currentMonth = today.take(7)
        val currentYear = today.take(4)
        val timeFiltered = when (timeFilter) {
            1 -> bankFiltered.filter { it.endDate.startsWith(currentMonth) }
            2 -> bankFiltered.filter { it.endDate.startsWith(currentYear) }
            else -> bankFiltered
        }
        timeFiltered.sortedBy { it.endDate }
    }

    val assetBalance: Double = remember(bankFiltered) { calculateAssetBalance(bankFiltered) }
    val annualExpectedYield: Double = remember(deposits, selectedBanks) {
        val filtered = if (selectedBanks.isEmpty()) deposits
        else deposits.filter { it.bankName in selectedBanks }
        calculateAnnualExpectedYield(filtered)
    }
    val holdingTotalYield: Double = remember(bankFiltered) {
        bankFiltered.sumOf { calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays, it.calcMethod) }
    }
    val dailyYield: Double = remember(bankFiltered) {
        val today = todayString()
        bankFiltered.filter { it.startDate <= today }
            .sumOf { it.principal * (it.annualRate / 100.0) / yearBasis(it.calcMethod).toDouble() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB))) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 57.dp, bottom = 90.dp)
        ) {
            // ── Hero 卡片 ──
            item {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).heroGradient()
                    .padding(20.dp)) {
                    Column {
                        Text("全部持有", fontSize = 11.sp, fontWeight = FontWeight.W500,
                            color = Color(0x73FFFFFF))
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("¥", fontSize = 14.sp, fontWeight = FontWeight.Light,
                                color = Color(0xFFE8B254), modifier = Modifier.padding(bottom = 6.dp))
                            Text(CN.format(assetBalance), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = (-1).sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(thickness = 1.dp, color = Color(0x1AFFFFFF))
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            HeroMetric("日收益", "¥${fmtD(dailyYield)}", Modifier.weight(1f)
                                .clickable { onDailyDetail() })
                            HeroMetric("今年预估收益", fmtI(annualExpectedYield), Modifier.weight(1f)
                                .clickable { onAnnualDetail() })
                            HeroMetric("持有中累计收益", fmtI(holdingTotalYield), Modifier.weight(1f)
                                .clickable { onAccumulatedDetail() })
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            // ── 我的存单 标题 ──
            item {
                Text("我的存单", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), modifier = Modifier.padding(horizontal = 18.dp))
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── 时间筛选胶囊 + 银行筛选折叠按钮（同一行）──
            item {
                val today = todayString()
                val currentMonth = today.take(7)
                val currentYear = today.take(4)
                val monthCount = remember(bankFiltered) {
                    bankFiltered.count { it.endDate.startsWith(currentMonth) }
                }
                val yearCount = remember(bankFiltered) {
                    bankFiltered.count { it.endDate.startsWith(currentYear) }
                }
                Column(modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        // 左侧：时间筛选胶囊
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TimePillChip("全部${bankFiltered.size}", timeFilter == 0, Color(0xFF0E1B4D)) { timeFilter = 0 }
                            TimePillChip("本月$monthCount", timeFilter == 1, Color(0xFF9499B8)) { timeFilter = 1 }
                            TimePillChip("本年$yearCount", timeFilter == 2, Color(0xFFC8953A)) { timeFilter = 2 }
                        }
                        Spacer(Modifier.weight(1f))
                        // 右侧：银行筛选 圆形按钮
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White)
                                .border(0.6.dp, Color(0xFFE2E8F0), CircleShape)
                                .clickable { bankFilterExpanded = !bankFilterExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (bankFilterExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "银行筛选",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    // 展开：全部 + 银行名（左对齐，与时间胶囊大小一致）
                    AnimatedVisibility(
                        visible = bankFilterExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TimePillChip("全部", selectedBanks.isEmpty(), Color(0xFF64748B)) { selectedBanks = emptySet() }
                            bankNames.forEach { bank ->
                                val sel = bank in selectedBanks
                                TimePillChip(bank, sel, Color(0xFF64748B)) {
                                    selectedBanks = if (sel) selectedBanks - bank else selectedBanks + bank
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── 存单列表 / 空状态 ──
            if (filteredList.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AccountBalance, null, Modifier.size(46.dp), tint = Color(0xFFCBD5E1))
                        Spacer(Modifier.height(10.dp))
                        Text("还没有存单记录", fontSize = 14.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { deposit ->
                    RefDepositCard(deposit = deposit, onClick = { onDepositClick(deposit.id) },
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 9.dp))
                }
            }
        }

        // ── 磨砂顶部标题栏（冻结，半透明，让底层卡片若隐若现）──
        // ── 顶部标题栏 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF4F6FB))
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Text("财迹FinTrace", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), lineHeight = 19.sp)
            Spacer(Modifier.height(2.dp))
            Text("跨行存单，一览全迹", fontSize = 10.sp, lineHeight = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

// ── Hero 指标（值 12sp / 标签 8sp，间距 2dp） ──

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier,
                       horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = Color.White, lineHeight = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.W400,
            color = Color(0x59FFFFFF), lineHeight = 9.sp, letterSpacing = 0.3.sp)
    }
}

// ── 药丸 Chip ──

@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF1E293B) else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.W600,
            color = if (selected) Color.White else Color(0xFF64748B))
    }
}

// ── 时间筛选 小号药丸 Chip（比 PillChip 略小）──

@Composable
fun TimePillChip(label: String, selected: Boolean, activeTextColor: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF1E293B) else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.W600,
            color = if (selected) Color.White else activeTextColor)
    }
}

// ── 统计小卡片（居中，内边距 2dp） ──

@Composable
private fun StatMiniCard(title: String, value: String, selected: Boolean = false,
    textColor: Color = Color(0xFF0E1B4D), modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val borderColor = if (selected) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick ?: {}) {
        Column(Modifier.padding(2.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor, lineHeight = 22.sp)
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.W500, color = Color(0xFF9499B8), lineHeight = 9.sp)
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ═════════════════════════ 存单卡片 ═════════════════════════

@Composable
fun RefDepositCard(deposit: Deposit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val remainingDays = daysUntilMaturity(deposit.endDate)
    val isExpired = deposit.status == DepositStatus.MATURED || remainingDays < 0
    val isExpiringSoon = remainingDays in 0..30

    val dateColor = when {
        isExpired -> Color(0xFFF87171)
        isExpiringSoon -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }
    val interestAmount = calculateMaturityInterest(deposit.principal, deposit.annualRate, deposit.termDays, deposit.calcMethod)

    Card(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {

        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // ══ L1: 产品名（左） | +¥利息 · ¥本金（右，底部基线对齐） ══
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(deposit.productName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                Text("+¥${CN_INT.format(interestAmount)}", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626), modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(6.dp))
                Text("¥${CN_INT.format(deposit.principal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), modifier = Modifier.alignByBaseline())
            }

            // ══ L2: 银行 · 利率（左） | 倒计时（右） ══
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(deposit.bankName, fontSize = 10.sp, color = Color(0xFF64748B))
                    Text("·", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text("${"%.2f".format(deposit.annualRate)}%", fontSize = 10.sp, color = Color(0xFF64748B))
                }
                    val countdownText = when {
                        isExpired -> "已过期${-remainingDays}天"
                        remainingDays == 0 -> "今日到期"
                        else -> "剩${remainingDays}天"
                    }
                Text(countdownText, fontSize = 10.sp, color = dateColor)
            }
        }
    }
}

// ── 银行首字图标 ──

@Composable
private fun BankFirstCharIcon(bankName: String) {
    val char = bankName.take(1).ifEmpty { "?" }
    Box(
        modifier = Modifier.size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFDBEAFE))
            .border(1.5.dp, Color(0xFFBFDBFE), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(char, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
    }
}

// ── 迷你 Badge ──

@Composable
fun MiniBadge(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.W500, color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            lineHeight = 11.sp)
    }
}

// ── 钱包图标（wallet_2_line） ──

@Composable
fun WalletIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF1E293B)) {
    Icon(
        painter = painterResource(id = R.drawable.ic_wallet),
        contentDescription = "到期利息",
        modifier = modifier,
        tint = tint
    )
}

// ── 动态利率图标 ──

@Composable
fun RateIcon(rate: Double, modifier: Modifier = Modifier, color: Color = rateColorFor(rate)) {
    val sweep = ((rate / 5.0).coerceIn(0.0, 1.0) * 360).toFloat()
    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val diameter = size.minDimension
        val radius = (diameter - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

fun rateColorFor(rate: Double): Color = when {
    rate >= 3.0 -> Color(0xFFD4A853)
    rate >= 2.0 -> Color(0xFFE8C070)
    rate >= 1.0 -> Color(0xFFF59E0B)
    else -> Color(0xFF94A3B8)
}

// ── Hero 渐变 + 装饰图形 ──

fun Modifier.heroGradient() = this.then(
    Modifier.drawBehind {
        // Figma: linear-gradient(145deg, #0E1B4D 0%, #1A2F7A 55%, #0E1B4D 100%)
        drawRect(brush = Brush.linearGradient(
            colors = listOf(Color(0xFF0E1B4D), Color(0xFF1A2F7A), Color(0xFF0E1B4D)),
            start = Offset(0f, this.size.height), end = Offset(this.size.width, 0f)))

        // 右上白色大圆 rgba(255,255,255,0.04), 160x160, top:-40 right:-30
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            color = Color(0x0AFFFFFF),
            radius = w * 0.22f,
            center = Offset(w * 1.06f, -w * 0.02f))

        // 右下金色圆 rgba(200,149,58,0.08), 120x120, bottom:-50 right:40
        drawCircle(
            color = Color(0x1AC8953A),
            radius = w * 0.16f,
            center = Offset(w * 0.76f, h * 0.95f))

        // Figma 金色弧线：已移除
    })
