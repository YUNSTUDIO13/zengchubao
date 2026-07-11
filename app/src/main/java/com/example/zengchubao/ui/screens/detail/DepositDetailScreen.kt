package com.example.zengchubao.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.zengchubao.storage.LocalFileManager
import com.example.zengchubao.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.util.Locale

private val CN_NUMBER = NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}
private fun fmt(amount: Double): String = "¥${CN_NUMBER.format(amount)}"
private fun fmtRate(rate: Double): String = "${"%.2f".format(rate)}%"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val remainingDays = daysUntilMaturity(deposit.endDate)
    val accruedInterest = calculateAccruedInterest(
        deposit.principal, deposit.annualRate, deposit.startDate, deposit.termDays, deposit.calcMethod
    )
    val assetBalance = deposit.principal + accruedInterest

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存单详情", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color(0xFFF0F4F8)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ── 银行 & 状态标签 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getBankColor(deposit.bankName).copy(alpha = 0.1f)
                ) {
                    Text(
                        deposit.bankName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = getBankColor(deposit.bankName),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                val typeLabel = when (deposit.productType) {
                    ProductType.FIXED_DEPOSIT -> "定期存款"
                    ProductType.WEALTH_MGMT -> "理财产品"
                    ProductType.OTHER -> "其他"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Blue50
                ) {
                    Text(
                        typeLabel,
                        fontSize = 12.sp,
                        color = Blue600,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                // 状态标签
                val statusLabel = when (deposit.status) {
                    DepositStatus.HOLDING -> "持有中"
                    DepositStatus.MATURED -> "已到期"
                    DepositStatus.EARLY_WITHDRAWN -> "已支取"
                    DepositStatus.ARCHIVED -> "已归档"
                }
                val statusColor = when (deposit.status) {
                    DepositStatus.HOLDING -> Emerald500
                    DepositStatus.MATURED -> Red500
                    DepositStatus.EARLY_WITHDRAWN -> Amber500
                    DepositStatus.ARCHIVED -> Gray500
                }
                Text(statusLabel, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(20.dp))

            // ── 产品名 + 本金 ──
            Text(deposit.productName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900)

            Spacer(Modifier.height(8.dp))

            Text(fmt(deposit.principal), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Blue600)

            Spacer(Modifier.height(24.dp))

            // ── 详情卡片 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                    DetailRow("距到期", if (remainingDays > 0) "${remainingDays}天" else if (remainingDays == 0) "今日到期" else "已到期${-remainingDays}天")
                    DetailDivider()
                    DetailRow("到期本息", fmt(deposit.maturityAmount))
                    DetailDivider()
                    DetailRow("到期利息", fmt(deposit.maturityAmount - deposit.principal))
                    DetailDivider()
                    DetailRow("已累计收益", fmt(accruedInterest))
                    DetailDivider()
                    DetailRow("当前资产余额", fmt(assetBalance), valueColor = Blue600)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 备注 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notes, null, tint = Gray400, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        if (noteText.isNotEmpty()) {
                            Text(noteText, fontSize = 14.sp, color = Gray700)
                        } else {
                            Text("添加备注...", fontSize = 14.sp, color = Gray400)
                        }
                    }
                    IconButton(onClick = { showEditNote = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Edit, "编辑备注", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 操作按钮 ──
            // 提前支取（仅持有中可操作）
            if (deposit.status == DepositStatus.HOLDING) {
                OutlinedButton(
                    onClick = { showEarlyWithdrawalSheet = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber600)
                ) {
                    Icon(Icons.Outlined.MoneyOff, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("提前支取模拟器", fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
            }

            // 编辑按钮 — 已归档存单不显示
            if (deposit.status != DepositStatus.ARCHIVED) {
                OutlinedButton(
                    onClick = { onEdit(deposit) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue600)
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑存单", fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
            }

            // 删除按钮
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500)
            ) {
                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除存单", fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── 删除确认对话框 ──
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

    // ── 提前支取模拟器 BottomSheet ──
    if (showEarlyWithdrawalSheet) {
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

    // ── 编辑备注对话框 ──
    if (showEditNote) {
        var tempNote by remember { mutableStateOf(noteText) }
        AlertDialog(
            onDismissRequest = { showEditNote = false },
            title = { Text("编辑备注") },
            text = {
                OutlinedTextField(
                    value = tempNote,
                    onValueChange = { tempNote = it },
                    placeholder = { Text("添加备注信息...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteText = tempNote
                    showEditNote = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            storage.saveDeposit(deposit.copy(note = tempNote, updatedAt = System.currentTimeMillis()))
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

// ── 详情行 ──

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Gray900) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Gray500)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(color = Gray100, thickness = 0.5.dp)
}

// ── 提前支取模拟器底部弹窗 ──

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EarlyWithdrawalSheet(
    deposit: Deposit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var withdrawalDate by remember { mutableStateOf(todayString()) }
    var demandRate by remember { mutableStateOf("0.3") }
    var showDatePicker by remember { mutableStateOf(false) }

    val rate = try { demandRate.toDoubleOrNull() ?: 0.3 } catch (_: Exception) { 0.3 }
    val result = try {
        calculateEarlyWithdrawalInterest(
            principal = deposit.principal,
            startDate = deposit.startDate,
            withdrawalDate = withdrawalDate,
            demandRate = rate
        )
    } catch (_: Exception) {
        EarlyWithdrawalResult(
            interest = 0.0, totalAmount = deposit.principal, actualDays = 0,
            demandRate = rate, description = "日期格式无效，请重新选择"
        )
    }

    val normalMaturityInterest = deposit.maturityAmount - deposit.principal
    val lossAmount = normalMaturityInterest - result.interest

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 拖拽指示器
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Gray300)
        )
        Spacer(Modifier.height(20.dp))

        Text("提前支取模拟器", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))
        Text("传统标准：定期存款提前支取部分一律按支取日活期挂牌利率计息，采用实际天数计算。算头不算尾。",
            fontSize = 12.sp, color = Gray500)

        Spacer(Modifier.height(20.dp))

        // 支取日期（支持DatePicker）
        Text("支取日期", fontSize = 13.sp, color = Gray600)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = withdrawalDate,
            onValueChange = { withdrawalDate = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Outlined.CalendarToday, "选择日期", modifier = Modifier.size(20.dp))
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // 活期利率
        Text("活期挂牌利率 (%)", fontSize = 13.sp, color = Gray600)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = demandRate,
            onValueChange = { demandRate = it.filter { c -> c.isDigit() || c == '.' } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            suffix = { Text("%") }
        )

        Spacer(Modifier.height(20.dp))

        // ── 计算结果 ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Blue50)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ResultRow("实际计息天数", "${result.actualDays}天", "算头不算尾")
                ResultRow("活期利率", "${result.demandRate}%", "")
                ResultRow("提前支取利息", fmt(result.interest), result.tierLabel)
                ResultRow("可收回总额", fmt(result.totalAmount), "本金+利息")
                HorizontalDivider(color = Blue200, modifier = Modifier.padding(vertical = 8.dp))
                ResultRow("正常到期利息", fmt(normalMaturityInterest), "")
                ResultRow("利息损失", fmt(lossAmount), "", Red500)
            }
        }

        Spacer(Modifier.height(10.dp))

        // 规则说明
        Text(
            result.description,
            fontSize = 11.sp,
            color = Gray500,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(20.dp))

        // 按钮
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消") }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
            ) { Text("确认支取") }
        }

        Spacer(Modifier.height(16.dp))
    }

    // 日期选择器
    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton({
                state.selectedDateMillis?.let { millis ->
                    withdrawalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                }
                showDatePicker = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun ResultRow(label: String, value: String, hint: String, valueColor: Color = Gray900) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Gray600)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
            if (hint.isNotEmpty()) {
                Text("  $hint", fontSize = 11.sp, color = Gray400)
            }
        }
    }
}
