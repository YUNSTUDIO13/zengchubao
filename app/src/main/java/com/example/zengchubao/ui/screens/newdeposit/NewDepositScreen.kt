package com.example.zengchubao.ui.screens.newdeposit

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import com.example.zengchubao.storage.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

// 统一背景与输入框颜色
private val PageBg = Color(0xFFF2F4F7)
private val InputBg = Color(0xFFE8EEF4)
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val TextTertiary = Color(0xFF94A3B8)
private val Blue = Color(0xFF3B82F6)
private val BlueLight = Color(0xFFDBEAFE)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewDepositScreen(
    banks: List<Bank>,
    products: List<Product>,
    deposits: List<Deposit>,
    storage: LocalFileManager,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onBanksChanged: (List<Bank>) -> Unit,
    onProductsChanged: (List<Product>) -> Unit,
    editDeposit: Deposit? = null
) {
    val scope = rememberCoroutineScope()

    val isEdit = editDeposit != null
    var selectedBankId by remember { mutableStateOf(editDeposit?.bankId ?: banks.firstOrNull()?.id ?: "") }
    var selectedBankName by remember { mutableStateOf(editDeposit?.bankName ?: banks.firstOrNull()?.name ?: "") }
    var productName by remember { mutableStateOf(editDeposit?.productName ?: "") }
    var startDate by remember { mutableStateOf(editDeposit?.startDate ?: todayString()) }
    val banksWithDeposits = remember(banks, deposits) { banks.filter { b -> deposits.any { d -> d.bankId == b.id } }.toSet() }
    var selectedTermDays by remember { mutableIntStateOf(editDeposit?.termDays ?: 360) }
    var selectedTermLabel by remember { mutableStateOf(editDeposit?.termLabel ?: "1年") }
    var principal by remember { mutableStateOf(if (editDeposit != null) editDeposit.principal.toLong().toString() else "") }
    var annualRate by remember { mutableStateOf(if (editDeposit != null) editDeposit.annualRate.toString() else "") }
    var selectedCalcMethod by remember { mutableStateOf(editDeposit?.calcMethod ?: CalcMethod.ANNUAL_MATCH) }
    var note by remember { mutableStateOf(editDeposit?.note ?: "") }

    var showAddBankDialog by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // 实际天数法→将选中存期换算成实际日历天数
    val effectiveTermDays = remember(startDate, selectedTermDays, selectedCalcMethod) {
        if (selectedCalcMethod == CalcMethod.ACTUAL_DAYS) {
            val endDateANM = calculateEndDate(startDate, selectedTermDays, CalcMethod.ANNUAL_MATCH)
            daysBetween(startDate, endDateANM)
        } else {
            selectedTermDays
        }
    }

    val endDate = remember(startDate, effectiveTermDays, selectedCalcMethod) {
        calculateEndDate(startDate, effectiveTermDays, selectedCalcMethod)
    }
    val principalVal = principal.toDoubleOrNull() ?: 0.0
    val rateVal = annualRate.toDoubleOrNull() ?: 0.0
    val estimatedInterest = calculateMaturityInterest(principalVal, rateVal, effectiveTermDays, selectedCalcMethod)
    val estimatedTotal = principalVal + estimatedInterest

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑存单" else "新增存单", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = { TextButton(onCancel) { Text("取消", fontSize = 14.sp, color = TextTertiary) } },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val deposit = editDeposit?.copy(
                                    bankId = selectedBankId, bankName = selectedBankName,
                                    productName = productName.ifEmpty { selectedBankName + "定存" },
                                    principal = principalVal, annualRate = rateVal,
                                    startDate = startDate, endDate = endDate,
                                    termDays = effectiveTermDays, termLabel = selectedTermLabel,
                                    calcMethod = selectedCalcMethod, maturityAmount = estimatedTotal,
                                    note = note, updatedAt = System.currentTimeMillis()
                                ) ?: Deposit(
                                    id = UUID.randomUUID().toString(),
                                    bankId = selectedBankId, bankName = selectedBankName,
                                    productName = productName.ifEmpty { selectedBankName + "定存" },
                                    productType = ProductType.FIXED_DEPOSIT,
                                    principal = principalVal, annualRate = rateVal,
                                    startDate = startDate, endDate = endDate,
                                    termDays = effectiveTermDays, termLabel = selectedTermLabel,
                                    calcMethod = selectedCalcMethod, maturityAmount = estimatedTotal,
                                    note = note
                                )
                                withContext(Dispatchers.IO) { storage.saveDeposit(deposit) }
                                onSave()
                            }
                        },
                        enabled = principalVal > 0 && rateVal > 0
                    ) { Text("保存", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Blue) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = PageBg
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 起止时间 ──
            SectionCard("起止时间") {
                FormLabel("开始日期")
                Spacer(Modifier.height(8.dp))
                InputRow(
                    value = formatDateDisplay(startDate),
                    placeholder = "选择开始日期",
                    onClick = { showDatePicker = true },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp), tint = TextSecondary) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("起息", fontSize = 12.sp, fontWeight = FontWeight.W500, color = Blue)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = TextTertiary)
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                FormLabel("期限")
                Spacer(Modifier.height(8.dp))
                TermGrid(
                    selectedDays = selectedTermDays,
                    onSelect = { days, label -> selectedTermDays = days; selectedTermLabel = label }
                )

                Spacer(Modifier.height(16.dp))
                FormLabel("到期时间")
                Spacer(Modifier.height(8.dp))
                InputRow(
                    value = formatDateDisplay(endDate),
                    placeholder = "自动计算",
                    onClick = { },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp), tint = TextSecondary) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = TextTertiary) }
                )
            }

            // ── 银行名称 ──
            SectionCard("银行名称") {
                BankGrid(
                    banks = banks,
                    selectedBankId = selectedBankId,
                    banksWithDeposits = banksWithDeposits,
                    onSelect = { id, name -> selectedBankId = id; selectedBankName = name },
                    onDelete = { bank ->
                        scope.launch {
                            withContext(Dispatchers.IO) { storage.deleteBank(bank.id) }
                            onBanksChanged(storage.getAllBanks())
                            if (selectedBankId == bank.id) {
                                val newBanks = storage.getAllBanks()
                                selectedBankId = newBanks.firstOrNull()?.id ?: ""
                                selectedBankName = newBanks.firstOrNull()?.name ?: ""
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showAddBankDialog = true },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Blue)
                ) { Text("+ 添加银行", fontSize = 12.sp, fontWeight = FontWeight.W500) }
            }

            // ── 存单名称 ──
            SectionCard("存单名称") {
                FormLabel("最多8字符")
                Spacer(Modifier.height(8.dp))
                FilledTextField(
                    value = productName,
                    onValueChange = { if (it.length <= 8) productName = it },
                    placeholder = if (selectedBankName.isNotEmpty()) "${selectedBankName}定存" else "输入产品名称",
                    trailingIcon = if (productName.isNotEmpty()) {
                        { IconButton(onClick = { productName = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "清除", Modifier.size(16.dp), tint = TextTertiary)
                        } }
                    } else null
                )
            }

            // ── 本金与利率 ──
            SectionCard("本金与利率") {
                FilledNumberField(
                    value = principal,
                    onValueChange = { principal = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "5,000",
                    prefix = { Text("CNY", fontSize = 10.sp, fontWeight = FontWeight.W600, color = TextTertiary) },
                    suffix = { Text("元", fontSize = 10.sp, color = TextTertiary) }
                )
                Spacer(Modifier.height(12.dp))
                FilledNumberField(
                    value = annualRate,
                    onValueChange = { annualRate = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "2.75",
                    suffix = { Text("% 年化", fontSize = 10.sp, fontWeight = FontWeight.W600, color = TextTertiary) }
                )
            }

            // ── 计息规则 ──
            SectionCard("计息规则") {
                CalcMethod.entries.forEach { method ->
                    val sel = selectedCalcMethod == method
                    Surface(
                        onClick = { selectedCalcMethod = method },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (sel) Blue else Color(0xFFE2E8F0)),
                        color = if (sel) BlueLight else Color.White
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(999.dp)).border(2.dp, if (sel) Blue else Color(0xFFCBD5E1), RoundedCornerShape(999.dp)).background(if (sel) Blue else Color.Transparent, RoundedCornerShape(999.dp)), contentAlignment = Alignment.Center) {
                                if (sel) Box(Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(Color.White))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(method.label, fontSize = 13.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                                Text(
                                    if (method == CalcMethod.ANNUAL_MATCH) "按年/月/日分段计算，适合整月整年定存"
                                    else "按实际持有天数和360天基数计算",
                                    fontSize = 11.sp, color = TextTertiary
                                )
                            }
                        }
                    }
                    if (method != CalcMethod.entries.last()) Spacer(Modifier.height(8.dp))
                }
            }

            // ── 收益预估 ──
            if (principalVal > 0 && rateVal > 0) {
                SectionCard("收益预估") {
                    Row(Modifier.fillMaxWidth()) {
                        YieldItem("预计利息", fmtMoney(estimatedInterest), Modifier.weight(1f))
                        YieldItem("到期本息", fmtMoney(estimatedTotal), Modifier.weight(1f), Blue)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        YieldItem("存期", termDaysToLabel(selectedTermDays), Modifier.weight(1f))
                        YieldItem("年化利率", "${"%.2f".format(rateVal)}%", Modifier.weight(1f))
                    }
                }
            }

            // ── 备注 ──
            SectionCard("备注") {
                FilledTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "添加备注信息...",
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // 添加银行
    if (showAddBankDialog) {
        AlertDialog(
            onDismissRequest = { showAddBankDialog = false },
            title = { Text("添加自定义银行") },
            text = { OutlinedTextField(newBankName, { newBankName = it }, Modifier.fillMaxWidth(), placeholder = { Text("银行名称") }, singleLine = true) },
            confirmButton = { TextButton({
                if (newBankName.isNotBlank()) {
                    val b = Bank("bank_${UUID.randomUUID()}", newBankName.trim(), sortOrder = 99)
                    scope.launch { withContext(Dispatchers.IO) { storage.addBank(b) }; onBanksChanged(storage.getAllBanks()); selectedBankId = b.id; selectedBankName = b.name }
                    newBankName = ""; showAddBankDialog = false
                }
            }) { Text("添加") } },
            dismissButton = { TextButton({ showAddBankDialog = false }) { Text("取消") } }
        )
    }

    // 日期选择器
    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton({
                state.selectedDateMillis?.let { millis ->
                    startDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                }
                showDatePicker = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

// ── 通用组件 ──

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(text, fontSize = 13.sp, color = TextSecondary)
}

@Composable
private fun InputRow(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InputBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.invoke()
        leadingIcon?.let { Spacer(Modifier.width(8.dp)) }
        Text(
            text = value.ifEmpty { placeholder },
            fontSize = 14.sp,
            fontWeight = if (value.isNotEmpty()) FontWeight.W500 else FontWeight.Normal,
            color = if (value.isNotEmpty()) TextPrimary else TextTertiary,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun FilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    maxLines: Int = 1,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        placeholder = { Text(placeholder, fontSize = 14.sp, color = TextTertiary) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = InputBg,
            unfocusedContainerColor = InputBg,
            disabledContainerColor = InputBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        trailingIcon = trailingIcon
    )
}

@Composable
private fun FilledNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        placeholder = { Text(placeholder, fontSize = 14.sp, color = TextTertiary) },
        prefix = prefix,
        suffix = suffix,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = InputBg,
            unfocusedContainerColor = InputBg,
            disabledContainerColor = InputBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

@Composable
private fun TermGrid(selectedDays: Int, onSelect: (Int, String) -> Unit) {
    val rows = TERM_OPTIONS.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val sel = selectedDays == option.days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Blue else InputBg)
                            .clickable { onSelect(option.days, option.label) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option.label, fontSize = 13.sp, fontWeight = FontWeight.W500,
                            color = if (sel) Color.White else TextSecondary, textAlign = TextAlign.Center)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BankGrid(
    banks: List<Bank>,
    selectedBankId: String,
    banksWithDeposits: Set<Bank>,
    onSelect: (String, String) -> Unit,
    onDelete: (Bank) -> Unit
) {
    val rows = banks.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { bank ->
                    val sel = selectedBankId == bank.id
                    val canDelete = !bank.isPreset && bank.id !in banksWithDeposits.map { it.id }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) BlueLight else InputBg)
                            .border(1.dp, if (sel) Blue else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onSelect(bank.id, bank.name) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text(bank.name, fontSize = 10.sp, fontWeight = FontWeight.W500,
                                color = if (sel) Blue else TextSecondary, textAlign = TextAlign.Center)
                            if (canDelete) {
                                Spacer(Modifier.width(2.dp))
                                IconButton(onClick = { onDelete(bank) }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.Close, "删除", Modifier.size(12.dp), tint = TextTertiary)
                                }
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun YieldItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = TextPrimary) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextTertiary)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = if (label.contains("利息") || label.contains("本息")) 16.sp else 14.sp,
            fontWeight = FontWeight.Bold, color = valueColor)
    }
}

private fun formatDateDisplay(date: String): String {
    return date.replace("-", " / ")
}

private fun fmtMoney(value: Double): String {
    return "¥${java.text.NumberFormat.getNumberInstance(java.util.Locale.CHINA).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(value)}"
}
