package com.example.zengchubao.ui.screens.management

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.zengchubao.model.*
import com.example.zengchubao.storage.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

import com.example.zengchubao.model.PRESET_BANK_COLORS

// ── 管理子页面枚举 ──

enum class ManageSubPage { MAIN, DATA_MGMT, BANK_PRODUCT, DISPLAY_SETTINGS, REMINDER_SETTINGS, DATA_REPORT, FORMULA_CALC }

// ── 主管理页面 ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManagementScreen(
    deposits: List<Deposit>,
    banks: List<Bank>,
    products: List<Product>,
    settings: AppSettings,
    storage: LocalFileManager,
    onBanksChanged: (List<Bank>) -> Unit,
    onProductsChanged: (List<Product>) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onDepositsChanged: (List<Deposit>) -> Unit,
    onArchiveRecordsChanged: (List<ArchiveRecord>) -> Unit
) {
    var currentPage by remember { mutableStateOf(ManageSubPage.MAIN) }
    val scope = rememberCoroutineScope()

    // 系统返回键/滑动手势处理
    BackHandler(enabled = currentPage != ManageSubPage.MAIN) {
        currentPage = ManageSubPage.MAIN
    }

    // 子页面切换动画（SoloChef 风格：淡入淡出 + 轻微水平位移）
    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally { (it * 0.25 * direction).toInt() } + fadeIn(tween(300))) togetherWith
            (slideOutHorizontally { (-it * 0.25 * direction).toInt() } + fadeOut(tween(300)))
        },
        label = "managePageTransition"
    ) { page ->
        when (page) {
            ManageSubPage.MAIN -> ManageMainPage(onNavigate = { currentPage = it })
            ManageSubPage.DATA_MGMT -> DataMgmtScreen(
                onBack = { currentPage = ManageSubPage.MAIN },
                deposits = deposits, storage = storage,
                onDepositsChanged = onDepositsChanged,
                onBanksChanged = onBanksChanged,
                onProductsChanged = onProductsChanged,
                onArchiveRecordsChanged = onArchiveRecordsChanged,
                onSettingsChanged = onSettingsChanged)
            ManageSubPage.BANK_PRODUCT -> BankProductScreen(
                onBack = { currentPage = ManageSubPage.MAIN },
                banks = banks, deposits = deposits, products = products, storage = storage,
                onBanksChanged = onBanksChanged, onProductsChanged = onProductsChanged)
            ManageSubPage.REMINDER_SETTINGS -> ReminderSettingsScreen(
                onBack = { currentPage = ManageSubPage.MAIN },
                settings = settings, onSettingsChanged = onSettingsChanged)
            ManageSubPage.DATA_REPORT -> DataReportScreen(
                onBack = { currentPage = ManageSubPage.MAIN },
                settings = settings, onSettingsChanged = onSettingsChanged)
            ManageSubPage.DISPLAY_SETTINGS -> ManageMainPage(onNavigate = { currentPage = it })
            ManageSubPage.FORMULA_CALC -> FormulaCalcScreen(onBack = { currentPage = ManageSubPage.MAIN })
        }
    }
}

// ═════════════════════════ 一级主页面（4+1模块入口） ═════════════════════════

@Composable
private fun ManageMainPage(onNavigate: (ManageSubPage) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        Text("管理", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp))

        // 模块列表项
        ModuleItem(icon = Icons.Outlined.CloudUpload, title = "数据管理", subtitle = "导出 / 导入 备份数据",
            iconBg = Color(0xFFEFF6FF), iconTint = Color(0xFF3B82F6)) { onNavigate(ManageSubPage.DATA_MGMT) }
        ModuleItem(icon = Icons.Outlined.AccountBalance, title = "银行与产品", subtitle = "配置银行与产品",
            iconBg = Color(0xFFFEF3C7), iconTint = Color(0xFFD97706)) { onNavigate(ManageSubPage.BANK_PRODUCT) }
        ModuleItem(icon = Icons.Outlined.Notifications, title = "到期提醒", subtitle = "提前到期提醒天数",
            iconBg = Color(0xFFFCE7F3), iconTint = Color(0xFFDB2777)) { onNavigate(ManageSubPage.REMINDER_SETTINGS) }
        ModuleItem(icon = Icons.Outlined.BarChart, title = "资产管理", subtitle = "报表排序与展示开关",
            iconBg = Color(0xFFF0FDF4), iconTint = Color(0xFF10B981)) { onNavigate(ManageSubPage.DATA_REPORT) }
        ModuleItem(icon = Icons.Outlined.Calculate, title = "公式计算", subtitle = "查看全部指标计算公式",
            iconBg = Color(0xFFFAF5FF), iconTint = Color(0xFF8B5CF6)) { onNavigate(ManageSubPage.FORMULA_CALC) }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ModuleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    iconBg: Color, iconTint: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(19.dp), tint = iconTint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B))
                Text(subtitle, fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFFCBD5E1))
        }
    }
}

// ═════════════════════════ 二级：数据管理 ═════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataMgmtScreen(onBack: () -> Unit,
    deposits: List<Deposit>, storage: LocalFileManager,
    onDepositsChanged: (List<Deposit>) -> Unit, onBanksChanged: (List<Bank>) -> Unit,
    onProductsChanged: (List<Product>) -> Unit, onArchiveRecordsChanged: (List<ArchiveRecord>) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportMsg by remember { mutableStateOf<String?>(null) }
    var showImportMsg by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val input = context.contentResolver.openInputStream(it)
                    val temp = File(context.cacheDir, "import.zip")
                    input?.use { i -> temp.outputStream().use { o -> i.copyTo(o) } }
                    val result = withContext(Dispatchers.IO) { storage.importFromZip(temp, LocalFileManager.ImportMode.OVERWRITE) }
                    showImportMsg = if (result.success) "导入成功" else result.message
                    if (result.success) {
                        onDepositsChanged(storage.getAllDeposits())
                        onBanksChanged(storage.getAllBanks())
                        onProductsChanged(storage.getAllProducts())
                        onArchiveRecordsChanged(storage.getAllArchiveRecords())
                        onSettingsChanged(storage.getSettings())
                    }
                    temp.delete()
                } catch (e: Exception) { showImportMsg = "导入失败: ${e.message}" }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        SubPageTopBar("数据管理", onBack)

        Card(Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column {
                SubListItem(Icons.Outlined.CloudUpload, "数据打包导出", "导出全部存单和配置数据",
                    Color(0xFFEFF6FF), Color(0xFF3B82F6)) {
                    scope.launch {
                        val f = File(context.cacheDir, "FinTrace_${todayString()}.zip")
                        val ok = withContext(Dispatchers.IO) { storage.exportToZip(f) }
                        if (ok) {
                            showExportMsg = "导出成功"
                            val u = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "分享备份"))
                        } else showExportMsg = "导出失败"
                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                SubListItem(Icons.Outlined.CloudDownload, "数据打包导入", "从备份文件恢复数据",
                    Color(0xFFECFDF5), Color(0xFF10B981)) { importLauncher.launch("application/zip") }
            }
        }
        showExportMsg?.let { MsgText(it) }
        showImportMsg?.let { MsgText(it) }
        Spacer(Modifier.height(30.dp))
    }
}

// ═════════════════════════ 二级：银行与产品 ═════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankProductScreen(onBack: () -> Unit, banks: List<Bank>, deposits: List<Deposit>, products: List<Product>, storage: LocalFileManager,
    onBanksChanged: (List<Bank>) -> Unit, onProductsChanged: (List<Product>) -> Unit) {
    val scope = rememberCoroutineScope()
    var showAddBank by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bankToDelete by remember { mutableStateOf<Bank?>(null) }

    // 已关联存单的银行 ID 集合（不可删除）
    val banksWithDeposits = deposits.map { it.bankId }.toSet()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        SubPageTopBar("银行与产品", onBack)

        // 银行区域 — 一行一个银行，带颜色管理
        SectionLabel("已配置银行")
        Spacer(Modifier.height(6.dp))
        banks.sortedBy { it.sortOrder }.forEach { bank ->
            var showColorPicker by remember { mutableStateOf(false) }
            val bankColor = getBankDisplayColor(bank)
            val canDelete = !bank.isPreset && bank.id !in banksWithDeposits

            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(bank.name, fontSize = 14.sp, fontWeight = FontWeight.W500,
                            color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(bankColor)
                                .clickable { showColorPicker = !showColorPicker }
                        )
                        Spacer(Modifier.width(8.dp))
                        if (canDelete) {
                            IconButton(
                                onClick = { bankToDelete = bank; showDeleteDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, "删除银行",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF94A3B8))
                            }
                        }
                    }
                    // 颜色选择器 — 展开/折叠
                    AnimatedVisibility(
                        visible = showColorPicker,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(start = 14.dp, end = 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 12 色色板（从 index=1 开始，跳过 index=0 的白色占位）
                            PRESET_BANK_COLORS.drop(1).forEach { color ->
                                val selected = bankColor == color
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (selected) Modifier.border(2.5.dp, Color(0xFF1E293B), CircleShape)
                                            else Modifier.border(1.dp, Color(0xFFE2E8F0), CircleShape)
                                        )
                                        .clickable {
                                            scope.launch {
                                                val hex = colorToHexString(color)
                                                withContext(Dispatchers.IO) { storage.updateBankColor(bank.id, hex) }
                                                onBanksChanged(storage.getAllBanks())
                                                showColorPicker = false
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 添加银行按钮
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showAddBank = true },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B))
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加银行", fontSize = 14.sp)
        }

        Spacer(Modifier.height(30.dp))
    }

    if (showAddBank) {
        AlertDialog(onDismissRequest = { showAddBank = false },
            title = { Text("添加银行") },
            text = { OutlinedTextField(newBankName, { newBankName = it },
                Modifier.fillMaxWidth(), placeholder = { Text("银行名称") }, singleLine = true) },
            confirmButton = { TextButton({
                val name = newBankName.trim()
                if (name.isNotBlank()) {
                    scope.launch {
                        // 新银行默认用下一个未分配的备选颜色
                        val nextColorIdx = banks.size % (PRESET_BANK_COLORS.size - 1) + 1
                        val hex = colorToHexString(PRESET_BANK_COLORS[nextColorIdx])
                        withContext(Dispatchers.IO) {
                            storage.addBank(Bank("bank_${UUID.randomUUID()}", name, colorHex = hex))
                        }
                        onBanksChanged(storage.getAllBanks())
                    }
                    newBankName = ""; showAddBank = false
                }
            }) { Text("添加") } },
            dismissButton = { TextButton({ showAddBank = false }) { Text("取消") } })
    }

    if (showDeleteDialog && bankToDelete != null) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除银行") },
            text = { Text("确定要删除「${bankToDelete!!.name}」吗？\n删除后不可恢复。") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        withContext(Dispatchers.IO) { storage.deleteBank(bankToDelete!!.id) }
                        onBanksChanged(storage.getAllBanks())
                        showDeleteDialog = false; bankToDelete = null
                    }
                }) { Text("删除", color = Color(0xFFEF4444)) }
            },
            dismissButton = { TextButton({ showDeleteDialog = false }) { Text("取消") } })
    }
}

private fun getBankDisplayColor(bank: Bank): Color {
    if (bank.colorHex.isNotEmpty()) {
        return try { Color(android.graphics.Color.parseColor(bank.colorHex)) }
            catch (_: Exception) { PRESET_BANK_COLORS[0] }
    }
    // 预置银行按默认色板
    return when (bank.id) {
        "bank_icbc" -> PRESET_BANK_COLORS[1]
        "bank_abc" -> PRESET_BANK_COLORS[4]
        "bank_boc" -> PRESET_BANK_COLORS[3]
        "bank_ccb" -> PRESET_BANK_COLORS[9]
        "bank_bcm" -> PRESET_BANK_COLORS[10]
        "bank_psbc" -> PRESET_BANK_COLORS[6]
        "bank_cmb" -> PRESET_BANK_COLORS[5]
        else -> PRESET_BANK_COLORS[0]
    }
}

private fun colorToHexString(color: Color): String {
    val alpha = (color.alpha * 255).toInt()
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
}

// ═════════════════════════ 二级：到期提醒 ═════════════════════════

@Composable
private fun ReminderSettingsScreen(onBack: () -> Unit, settings: AppSettings, onSettingsChanged: (AppSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 通知权限
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33)
        android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
    else true

    // 电池优化权限
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    val hasBatteryExemption = pm.isIgnoringBatteryOptimizations(context.packageName)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.zengchubao.notification.NotificationHelper.scheduleAll(context, settings)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        SubPageTopBar("到期提醒", onBack)

        Card(Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(Modifier.padding(14.dp)) {
                // ── 开关行 ──
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("通知提醒", fontSize = 14.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B))
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChanged(settings.copy(reminderEnabled = enabled))
                            if (enabled && !hasNotificationPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2563EB),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE2E8F0)
                        )
                    )
                }

                // ── 展开内容 ──
                AnimatedVisibility(visible = settings.reminderEnabled) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(Modifier.height(12.dp))

                        // 到期前提醒 + 天数
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("到期前提醒", fontSize = 12.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 3, 7).forEach { d ->
                                    FilterChip(selected = settings.reminderDays == d,
                                        onClick = { onSettingsChanged(settings.copy(reminderDays = d)) },
                                        label = { Text("${d}天", fontSize = 11.sp, fontWeight = FontWeight.W600) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF2563EB),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0xFFF1F5F9),
                                            labelColor = Color(0xFF64748B)))
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 提醒时间（点击弹出选择器）
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("提醒时间", fontSize = 12.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B))
                            // 时间显示按钮
                            var showPicker by remember { mutableStateOf(false) }
                            Text(
                                "${settings.reminderHour}:${String.format("%02d", settings.reminderMinute)}",
                                fontSize = 14.sp, fontWeight = FontWeight.W600, color = Color(0xFF2563EB),
                                modifier = Modifier.clickable { showPicker = true }
                            )
                            if (showPicker) {
                                TimePickerDialog(
                                    initialHour = settings.reminderHour,
                                    initialMinute = settings.reminderMinute,
                                    onConfirm = { h, m ->
                                        onSettingsChanged(settings.copy(reminderHour = h, reminderMinute = m))
                                        showPicker = false
                                    },
                                    onDismiss = { showPicker = false }
                                )
                            }
                        }
                    }
                }
            }
        }
        // 电池优化提示
        if (settings.reminderEnabled && !hasBatteryExemption) {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("关闭电池优化以保障提醒准时", fontSize = 11.sp, fontWeight = FontWeight.W600, color = Color(0xFF92400E))
                        Text("开启后即使App未运行也能准时通知", fontSize = 9.sp, color = Color(0xFFA16207))
                    }
                    TextButton(onClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) { Text("去设置", fontSize = 11.sp, color = Color(0xFFD97706)) }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ═════ 时间选择弹框 ═════
@Composable
private fun TimePickerDialog(
    initialHour: Int, initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hours = (0..23).toList()
    val minutes = (0..55 step 5).toList()

    // 滚动视觉状态（与确认解耦）
    val hourListState = rememberLazyListState(initialFirstVisibleItemIndex = (initialHour - 2).coerceAtLeast(0))
    val minuteListState = rememberLazyListState(initialFirstVisibleItemIndex = (minutes.indexOf(initialMinute) - 2).coerceAtLeast(0))

    val itemHPx = with(LocalDensity.current) { 40.dp.toPx() }
    val visualHour by remember {
        derivedStateOf {
            (hourListState.firstVisibleItemIndex +
                (hourListState.firstVisibleItemScrollOffset / itemHPx + 0.5f).toInt()
            ).coerceIn(0, 23)
        }
    }
    val visualMinuteIdx by remember {
        derivedStateOf {
            (minuteListState.firstVisibleItemIndex +
                (minuteListState.firstVisibleItemScrollOffset / itemHPx + 0.5f).toInt()
            ).coerceIn(0, minutes.size - 1)
        }
    }

    // 确认时使用的值（仅按钮点击时更新）
    var selectedHour by rememberSaveable { mutableStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("选择提醒时间", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(160.dp)
            ) {
                // 小时滚轮
                WheelPickerVisual(
                    items = hours.map { "${it}时" },
                    listState = hourListState,
                    centerIndex = visualHour,
                    modifier = Modifier.width(80.dp)
                )
                Text("：", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B),
                    modifier = Modifier.padding(horizontal = 4.dp))
                // 分钟滚轮
                WheelPickerVisual(
                    items = minutes.map { String.format("%02d分", it) },
                    listState = minuteListState,
                    centerIndex = visualMinuteIdx,
                    modifier = Modifier.width(80.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selectedHour = visualHour
                selectedMinute = minutes[visualMinuteIdx]
                onConfirm(selectedHour, selectedMinute)
            }) { Text("确认", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF94A3B8)) }
        }
    )
}

// ═════ 纯视觉滚轮（无状态回调，不触发父级重组）═════
@Composable
private fun WheelPickerVisual(
    items: List<String>,
    listState: LazyListState,
    centerIndex: Int,
    modifier: Modifier = Modifier
) {
    val itemH = 40.dp
    val visibleCount = 5

    Box(modifier = modifier.height(itemH * visibleCount)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = true
        ) {
            item { Spacer(Modifier.height(itemH * (visibleCount / 2))) }
            items(items.size) { idx ->
                val isCenter = idx == centerIndex
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(items[idx],
                        fontSize = if (isCenter) 18.sp else 14.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) Color(0xFF1E293B) else Color(0xFF94A3B8))
                }
            }
            item { Spacer(Modifier.height(itemH * (visibleCount / 2))) }
        }

        Box(Modifier.fillMaxWidth().height(itemH * 2).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.White, Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(itemH * 2).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White))))
    }
}

// ═════════════════════════ 二级：数据报表（排序+开关） ═════════════════════════

@Composable
private fun DataReportScreen(onBack: () -> Unit, settings: AppSettings, onSettingsChanged: (AppSettings) -> Unit) {
    var items by remember(settings.reportItems) { mutableStateOf(settings.reportItems) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        SubPageTopBar("资产管理", onBack)

        Text("拖拽右侧手柄可调整顺序，开关控制是否展示",
            fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))

        items.forEachIndexed { index, item ->
            ReportSortItem(
                item = item,
                onToggle = { enabled ->
                    items = items.toMutableList().apply { this[index] = item.copy(enabled = enabled) }
                    onSettingsChanged(settings.copy(reportItems = items))
                },
                onReorder = { fromIndex, toIndex ->
                    if (fromIndex != toIndex) {
                        items = items.toMutableList().apply {
                            val removed = removeAt(fromIndex)
                            add(toIndex.coerceIn(0, size), removed)
                        }
                        onSettingsChanged(settings.copy(reportItems = items))
                    }
                },
                index = index,
                totalCount = items.size
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ReportSortItem(
    item: ReportItemSetting,
    index: Int,
    totalCount: Int,
    onToggle: (Boolean) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val itemHeight = 56.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 8f else 0.5f,
        label = "cardElevation"
    )

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .zIndex(if (isDragging) 1f else 0f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄（三条横线）
            Column(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { isDragging = true },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetY += dragAmount.y
                            },
                            onDragEnd = {
                                isDragging = false
                                val move = (offsetY / itemHeightPx).roundToInt()
                                val newIndex = (index + move).coerceIn(0, totalCount - 1)
                                onReorder(index, newIndex)
                                offsetY = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                offsetY = 0f
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(18.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFD1D5DB)))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.size(14.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFD1D5DB)))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.size(18.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFD1D5DB)))
            }

            Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.W500,
                color = if (item.enabled) Color(0xFF1E293B) else Color(0xFF94A3B8),
                modifier = Modifier.weight(1f))

            // 开关（圆形 knob，padding 上下=3dp 左右=3dp）
            val knobSize = 18.dp
            val trackWidth = 44.dp
            val trackHeight = 24.dp
            val knobOffset = 3.dp
            Box(Modifier.width(trackWidth).height(trackHeight).clip(RoundedCornerShape(999.dp))
                .background(if (item.enabled) Color(0xFF3B82F6) else Color(0xFFE2E8F0))
                .clickable { onToggle(!item.enabled) }) {
                Box(Modifier.offset(x = if (item.enabled) trackWidth - knobSize - knobOffset else knobOffset, y = knobOffset).size(knobSize)
                    .clip(CircleShape).background(Color.White))
            }
        }
    }
}

// ═════════════════════════ 二级：公式计算 ═════════════════════════

@Composable
private fun FormulaCalcScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        SubPageTopBar("公式计算", onBack)

        // ── 计息法说明（最顶） ──
        FormulaCard(
            title = "计息法",
            formula = "对年对月对日法 · 实际天数法",
            desc = "对年对月对日法：存期按年×360+月×30+日（30/360银行基准），分母=360\n" +
                    "实际天数法：存期按实际日历天数（含闰年），分母=365\n" +
                    "日收益/累计/年预估/到期利息 全线统一按 yearBasis 切换分母"
        )
        FormulaCard(
            title = "日收益",
            formula = "Σ(持有中本金 × 年利率% ÷ yearBasis)",
            desc = "yearBasis：对年对月法=360，实际天数法=365\n" +
                    "仅统计已起息（startDate≤今天）的 HOLDING 存单"
        )
        FormulaCard(
            title = "持有中累计收益",
            formula = "Σ(本金 × 年利率% ÷ yearBasis × 已持有天数)",
            desc = "已持有天数 = today − 起存日，上限≤存期天数\n" +
                    "对年对月法：天数按 30/360 银行基准（整年×360+整月×30）\n" +
                    "实际天数法：天数按实际日历天数\n" +
                    "yearBasis 同上：对年对月=360，实际天数=365"
        )
        FormulaCard(
            title = "资产余额",
            formula = "Σ(持有中本金 + 持有中累计收益)",
            desc = "仅统计HOLDING存单。\n已到期/已归档/已支取 不计入。\n反映当前在仓资产的实际价值"
        )
        FormulaCard(
            title = "今年预估收益",
            formula = "Σ(本金 × 年利率% ÷ yearBasis × 今年有效天数)",
            desc = "单笔今年有效天数 = min(12-31, 到期日) − max(1-1, 起存日次日) + 1\n" +
                    "前年存的→从1月1日起算（含1月1日当天）\n" +
                    "今年新存→从起存日次日起算\n" +
                    "对年对月法：天数按 30/360 银行基准\n" +
                    "实际天数法：天数按实际日历天数\n" +
                    "yearBasis：对年对月=360，实际天数=365"
        )
        FormulaCard(
            title = "到期总收益",
            formula = "Σ(本金 × 年利率% ÷ yearBasis × 存期天数)",
            desc = "所有持有中/已到期（非归档）存单到期时预计产生的总利息。\n" +
                    "存期天数 = termDays（对年对月法=年×360+月×30+日，实际天数法=日历天数）\n" +
                    "yearBasis：对年对月=360，实际天数=365"
        )
        FormulaCard(
            title = "到期利息（单笔）",
            formula = "本金 × 年利率% ÷ yearBasis × termDays",
            desc = "新建存单时的预计到期利息，也是到期总收益明细中每笔的值。\n" +
                    "对年对月法：整年直接简化为 本金 × 年利率% × 年数\n" +
                    "实际天数法：含闰年真实日历天数"
        )
        FormulaCard(
            title = "综合年化率（时间加权）",
            formula = "Σ(本金 × 年利率 × 存期天数) ÷ Σ(本金 × 存期天数)",
            desc = "按本金×存期加权平均的年化利率。\n仅统计HOLDING存单，3年期4%权重是1年期4%的3倍"
        )
        FormulaCard(
            title = "归档历史收益",
            formula = "Σ(已归档存单 本金 × 年利率% ÷ yearBasis × 存期天数)",
            desc = "所有已归档存单（ARCHIVED）的到期利息，动态计算。\n" +
                    "不再使用存入时的 maturityAmount 快照值。\n" +
                    "yearBasis：对年对月=360，实际天数=365"
        )
        FormulaCard(
            title = "累计收益（全量）",
            formula = "持有中 → 持有中累计收益（动态计算）\n已到期/已归档/提前支取 → 到期利息（动态计算）",
            desc = "全生命周期统计，所有状态存单统一用动态计算而非 DB 快照值。\n含已结算历史收益 + 当前持仓累计收益"
        )
        FormulaCard(
            title = "累计存入",
            formula = "Σ(本金)",
            desc = "所有存单（含已到期、已归档）的本金总和。\n反映历史总投入规模"
        )
        FormulaCard(
            title = "总资产趋势图",
            formula = "每月末总资产 = 持有中且未到期本金 + 持有中累计收益",
            desc = "仅统计持有中且 endDate > 当月月末的存单。\n持有中累计收益按当月月末节点计算：\n 对年对月法 → 30/360 银行日 ÷ 360\n 实际天数法 → 日历日 ÷ 365\n预测月（未来月份）按持有存单照常计算"
        )
        FormulaCard(
            title = "净利息趋势图",
            formula = "每月末净利息 = 持有中累计收益 + 已到期未归档收益 + 已到期已归档收益 + 已归档收益",
            desc = "持有中累计收益 = 按当月月末节点，对年法→银行日÷360，实际天法→日历日÷365\n" +
                    "已到期/已归档收益 = 动态计算到期利息（不再用 maturityAmount 快照）\n" +
                    "预测月（未来月份）按持有存单照常计算"
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FormulaCard(title: String, formula: String, desc: String) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(Modifier.height(8.dp))
            Text(formula, fontSize = 13.sp, fontWeight = FontWeight.W600, color = Color(0xFF8B5CF6),
                lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            Text(desc, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 17.sp)
        }
    }
}

// ═════════════════════════ 通用组件 ═════════════════════════

@Composable
private fun SubPageTopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
    }
}

@Composable
private fun SubListItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String,
    bg: Color, tint: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(bg),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.W600, color = Color(0xFF1E293B))
            Text(desc, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun MsgText(msg: String) {
    Text(msg, Modifier.padding(horizontal = 16.dp, vertical = 3.dp), fontSize = 11.sp,
        color = if (msg.contains("成功")) Color(0xFF10B981) else Color(0xFFEF4444))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp), fontSize = 9.sp, fontWeight = FontWeight.W600,
        color = Color(0xFF94A3B8), letterSpacing = 2.sp)
}
