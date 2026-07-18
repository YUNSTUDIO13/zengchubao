package com.example.zengchubao.ui.screens.detail

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import com.example.zengchubao.storage.LocalFileManager
import com.example.zengchubao.ui.screens.home.heroGradient
import com.example.zengchubao.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.util.Locale

private val BrandBlue = Color(0xFF1B4FD8)
private val BrandBlueBg = Color(0xFFEFF6FF)
private val EmeraldBg = Color(0xFFECFDF5)
private val EmeraldText = Color(0xFF059669)
private val AmberBg = Color(0xFFFFFBEB)
private val RedBg = Color(0xFFFEF2F2)
private val BgPage = Color(0xFFF4F6FB)

private val CN_NUMBER = NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}
private fun fmt(amount: Double): String = "¥${CN_NUMBER.format(amount)}"
private fun fmtRate(rate: Double): String = "${"%.2f".format(rate)}%"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositDetailScreen(
    depositId: String,
    storage: LocalFileManager,
    deposits: List<Deposit>,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onEdit: (Deposit) -> Unit = {}
) {
    val deposit = remember(deposits, depositId) {
        deposits.find { it.id == depositId }
    } ?: run { onBack(); return }

    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEarlyWithdrawalSheet by remember { mutableStateOf(false) }
    var showEditNote by remember { mutableStateOf(false) }
    var noteText by remember(deposit.note) { mutableStateOf(deposit.note) }
    var isEditingNote by remember { mutableStateOf(false) }
    var tempNote by remember { mutableStateOf(deposit.note) }

    val remainingDays = daysUntilMaturity(deposit.endDate)
    val totalDays = daysBetween(deposit.startDate, deposit.endDate)
    val accruedInterest = calculateAccruedInterest(
        deposit.principal, deposit.annualRate, deposit.startDate, deposit.termDays, deposit.calcMethod
    )
    val assetBalance = deposit.principal + accruedInterest

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 44.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowBack, "返回", tint = Gray700, modifier = Modifier.size(20.dp))
                }
                Text("存单详情", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                Spacer(Modifier.weight(1f))
                // 银行名称 + 状态标签（半尺寸，右对齐）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(shape = RoundedCornerShape(999.dp), color = BrandBlueBg,
                        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))) {
                        Text(deposit.bankName, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                            color = BrandBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    val (statusLabel, statusBg, statusColor) = when (deposit.status) {
                        DepositStatus.HOLDING -> Triple("持有中", EmeraldBg, EmeraldText)
                        DepositStatus.MATURED -> Triple("已到期", RedBg, Red500)
                        DepositStatus.EARLY_WITHDRAWN -> Triple("已支取", AmberBg, Amber600)
                        DepositStatus.ARCHIVED -> Triple("已归档", Gray100, Gray500)
                    }
                    Surface(shape = RoundedCornerShape(999.dp), color = statusBg,
                        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))) {
                        Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        },
        containerColor = BgPage
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Hero：渐变卡片（参照首页hero）──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .heroGradient()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deposit.productName, fontSize = 13.sp, fontWeight = FontWeight.W500,
                            color = Color(0x73FFFFFF))
                        Spacer(Modifier.height(4.dp))
                        Text(fmt(deposit.principal), fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, letterSpacing = (-0.5).sp)
                    }
                    if (deposit.status == DepositStatus.HOLDING && totalDays > 0) {
                        DaysRing(daysLeft = remainingDays.coerceAtLeast(0), totalDays = totalDays)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Highlight 条 ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(0.6.dp, Color(0xFFE2E8F0))
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("当前资产余额", fontSize = 11.sp, color = Gray400)
                        Spacer(Modifier.height(4.dp))
                        Text(fmt(assetBalance), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray900)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Gray50,
                    border = BorderStroke(0.6.dp, Color(0xFFE2E8F0))
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("已累计收益", fontSize = 11.sp, color = Gray400)
                        Spacer(Modifier.height(4.dp))
                        Text(fmt(accruedInterest), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray800)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 详情卡片 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Gray100)
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    DetailRow("存期", deposit.termLabel.ifEmpty { "${deposit.termDays}天" })
                    DetailDivider()
                    DetailRow("年利率", fmtRate(deposit.annualRate))
                    DetailDivider()
                    DetailRow("计息规则", deposit.calcMethod.label)
                    DetailDivider()
                    DetailRow("起存日期", deposit.startDate)
                    DetailDivider()
                    DetailRow("到期日期", deposit.endDate)
                    DetailDivider()
                    DetailRow("到期本息", fmt(deposit.maturityAmount))
                    DetailDivider()
                    DetailRow("到期利息", fmt(deposit.maturityAmount - deposit.principal))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 备注 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Gray100)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isEditingNote) { isEditingNote = true; tempNote = noteText } }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notes, null, tint = Gray400, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("备注", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray400)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Outlined.Edit, null, tint = Gray300, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    if (isEditingNote) {
                        OutlinedTextField(
                            value = tempNote,
                            onValueChange = { tempNote = it },
                            placeholder = { Text("添加备注…", fontSize = 14.sp, color = Gray300) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Gray700),
                            singleLine = false,
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        // 保存 / 取消
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { isEditingNote = false }) {
                                Text("取消", fontSize = 13.sp, color = Gray500)
                            }
                            TextButton(onClick = {
                                noteText = tempNote
                                isEditingNote = false
                                showEditNote = false
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        storage.saveDeposit(deposit.copy(note = tempNote, updatedAt = System.currentTimeMillis()))
                                    }
                                }
                            }) {
                                Text("保存", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BrandBlue)
                            }
                        }
                    } else {
                        Text(
                            noteText.ifEmpty { "添加备注…" },
                            fontSize = 14.sp,
                            color = if (noteText.isEmpty()) Gray300 else Gray700
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 操作按钮 ──
            // 提前支取（仅持有中）
            if (deposit.status == DepositStatus.HOLDING) {
                ActionButton(
                    onClick = { showEarlyWithdrawalSheet = true },
                    bgColor = Amber50, borderColor = Amber500.copy(alpha = 0.25f), contentColor = Amber600,
                    icon = { Icon(Icons.Outlined.FlashOn, null, tint = Amber600, modifier = Modifier.size(16.dp)) },
                    text = "提前支取模拟器"
                )
                Spacer(Modifier.height(12.dp))
            }

            // 编辑（非归档期）
            if (deposit.status != DepositStatus.ARCHIVED) {
                ActionButton(
                    onClick = { onEdit(deposit) },
                    bgColor = BrandBlueBg, borderColor = Blue100, contentColor = BrandBlue,
                    icon = { Icon(Icons.Outlined.Edit, null, tint = BrandBlue, modifier = Modifier.size(16.dp)) },
                    text = "编辑存单"
                )
                Spacer(Modifier.height(12.dp))
            }

            // 删除
            ActionButton(
                onClick = { showDeleteDialog = true },
                bgColor = Red50, borderColor = Red500.copy(alpha = 0.25f), contentColor = Red500,
                icon = { Icon(Icons.Outlined.Delete, null, tint = Red500, modifier = Modifier.size(16.dp)) },
                text = "删除存单"
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // ── 删除确认 ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后将无法恢复，确定要删除「${deposit.productName}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) { storage.deleteDeposit(depositId) }
                            onDeleted()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Red500)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 提前支取模拟器 ──
    AnimatedVisibility(
        visible = showEarlyWithdrawalSheet,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        if (showEarlyWithdrawalSheet) {
            BackHandler { showEarlyWithdrawalSheet = false }
            EarlyWithdrawalSheet(
                deposit = deposit,
                onDismiss = { showEarlyWithdrawalSheet = false },
                onConfirm = {
                    showEarlyWithdrawalSheet = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val updated = deposit.copy(
                                status = DepositStatus.EARLY_WITHDRAWN,
                                updatedAt = System.currentTimeMillis()
                            )
                            storage.saveDeposit(updated)
                        }
                        onDeleted()
                    }
                }
            )
        }
    }

    // ── 编辑备注对话框（仅弹窗编辑）──
    if (showEditNote) {
        var temp by remember { mutableStateOf(noteText) }
        AlertDialog(
            onDismissRequest = { showEditNote = false },
            title = { Text("编辑备注") },
            text = {
                OutlinedTextField(
                    value = temp,
                    onValueChange = { temp = it },
                    placeholder = { Text("添加备注信息...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteText = temp
                    showEditNote = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            storage.saveDeposit(deposit.copy(note = temp, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNote = false }) { Text("取消") }
            }
        )
    }
}

// ── 距到期圆环（参考图：环+数字在上 + "距到期"标签在底部）──
@Composable
private fun DaysRing(daysLeft: Int, totalDays: Int) {
    val pct = if (totalDays > 0) (daysLeft.toFloat() / totalDays).coerceIn(0f, 1f) else 1f
    val ringSizeDp = 64.dp
    val density = LocalDensity.current
    val strokeW = with(density) { 4.dp.toPx() }
    val r = with(density) { ringSizeDp.toPx() / 2f } - strokeW / 2f
    Box(modifier = Modifier.size(ringSizeDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ringSizeDp)) {
            val cx = size.width / 2; val cy = size.height / 2
            drawCircle(Color(0x33FFFFFF), r, Offset(cx, cy), style = Stroke(strokeW, cap = StrokeCap.Round))
            drawArc(Color(0xFFE8B254), -90f, 360f * pct, false,
                Offset(cx - r, cy - r), Size(r * 2, r * 2),
                style = Stroke(strokeW, cap = StrokeCap.Round))
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 20.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                }
                // 文字居中：基线在 cy + textSize/3 附近
                val fm = p.fontMetrics
                drawText("$daysLeft", cx, cy - (fm.ascent + fm.descent) / 2, p)
            }
        }
    }
}

// ── 详情行 ──
@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Gray800) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Gray500)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(color = Gray100, thickness = 0.5.dp)
}

// ── 操作按钮 ──
@Composable
private fun ActionButton(
    onClick: () -> Unit,
    bgColor: Color, borderColor: Color, contentColor: Color,
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = contentColor)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = contentColor.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
    }
}

// ── 提前支取模拟器 BottomSheet ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarlyWithdrawalSheet(
    deposit: Deposit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var withdrawalDate by remember { mutableStateOf(todayString()) }
    var demandRate by remember { mutableStateOf("0.3") }
    var showDatePicker by remember { mutableStateOf(false) }

    val rate = demandRate.toDoubleOrNull() ?: 0.3
    val result = try {
        calculateEarlyWithdrawalInterest(
            principal = deposit.principal,
            startDate = deposit.startDate,
            withdrawalDate = withdrawalDate,
            demandRate = rate
        )
    } catch (_: Exception) {
        EarlyWithdrawalResult(0.0, deposit.principal, 0, rate, "日期格式无效，请重新选择")
    }

    val normalMaturityInterest = deposit.maturityAmount - deposit.principal
    val lossAmount = normalMaturityInterest - result.interest

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题栏（与存单详情头部一致风格）
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowBack, "关闭", tint = Gray700, modifier = Modifier.size(20.dp))
                }
                Text("提前支取模拟器", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            }

            Text("传统标准：定期存款提前支取部分一律按支取日活期挂牌利率计息，采用实际天数计算。算头不算尾。",
                fontSize = 12.sp, color = Gray400, lineHeight = 18.sp)

            Spacer(Modifier.height(20.dp))

            // 支取日期
            Text("支取日期", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray500)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = withdrawalDate,
                onValueChange = { withdrawalDate = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD", fontSize = 14.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = Gray200
                ),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Outlined.DateRange, "选择日期", modifier = Modifier.size(20.dp))
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // 活期利率
            Text("活期挂牌利率 (%)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray500)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = demandRate,
                onValueChange = { demandRate = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                suffix = { Text("%", color = Gray400) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = Gray200
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(20.dp))

            // 计算结果
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BrandBlueBg.copy(alpha = 0.6f))
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ResultRow("实际计息天数", "${result.actualDays}天", "算头不算尾")
                    ResultRow("活期利率", "${result.demandRate}%", "")
                    ResultRow("提前支取利息", fmt(result.interest), result.tierLabel)
                    ResultRow("可收回总额", fmt(result.totalAmount), "本金+利息")
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color(0xFFBFDBFE).copy(alpha = 0.4f))
                    Spacer(Modifier.height(4.dp))
                    ResultRow("正常到期利息", fmt(normalMaturityInterest), "")
                    ResultRow("利息损失", fmt(lossAmount), "", valueColor = Red500)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(result.description, fontSize = 11.sp, color = Gray400, lineHeight = 16.sp)

            Spacer(Modifier.height(24.dp))

            // 按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Gray200),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray600)
                ) { Text("取消", fontSize = 14.sp) }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("确认支取", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton({
                state.selectedDateMillis?.let { millis ->
                    withdrawalDate = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                }
                showDatePicker = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

// ── 模拟器结果行 ──
@Composable
private fun ResultRow(label: String, value: String, hint: String, valueColor: Color = Gray800) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Gray500)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
            if (hint.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(hint, fontSize = 11.sp, color = Gray400)
            }
        }
    }
}
