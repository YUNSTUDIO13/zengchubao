package com.example.zengchubao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.zengchubao.ui.theme.ZengChuBaoTheme
import com.example.zengchubao.ui.theme.AppTab
import com.example.zengchubao.ui.theme.BottomNavBar
import com.example.zengchubao.ui.screens.home.HomeScreen
import com.example.zengchubao.ui.screens.reports.ReportsScreen
import com.example.zengchubao.ui.screens.archive.ArchiveScreen
import com.example.zengchubao.ui.screens.management.ManagementScreen
import com.example.zengchubao.ui.screens.newdeposit.NewDepositScreen
import com.example.zengchubao.ui.screens.detail.DepositDetailScreen
import com.example.zengchubao.ui.screens.breakdown.EarningsBreakdownScreen
import com.example.zengchubao.ui.screens.breakdown.BreakdownMode
import com.example.zengchubao.storage.LocalFileManager
import com.example.zengchubao.notification.NotificationHelper
import com.example.zengchubao.model.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.initChannel(this@MainActivity)
        setContent {
            ZengChuBaoTheme {
                ZengChuBaoApp()
            }
        }
    }
}

// ── 导航状态 ──

sealed class Screen {
    data object Main : Screen()
    data object NewDeposit : Screen()
    data object Archive : Screen()
    data class DepositDetail(val depositId: String) : Screen()
    data class EditDeposit(val depositId: String) : Screen()
    data class EarningsBreakdown(val mode: String) : Screen()  // "daily" | "annual" | "accumulated"
}

@Composable
fun ZengChuBaoApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storage = remember { LocalFileManager(context) }
    val scope = rememberCoroutineScope()

    // ── 全局数据状态 ──
    var deposits by remember { mutableStateOf<List<Deposit>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Bank>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var archiveRecords by remember { mutableStateOf<List<ArchiveRecord>>(emptyList()) }
    var settings by remember { mutableStateOf(AppSettings()) }
    var isInitialized by remember { mutableStateOf(false) }

    // ── 导航状态 ──
    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    // ── 初始化 ──
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            storage.initialize()
            storage.autoArchiveExpired()
        }
        deposits = storage.getAllDeposits()
        banks = storage.getAllBanks()
        products = storage.getAllProducts()
        archiveRecords = storage.getAllArchiveRecords()
        settings = storage.getSettings()
        // 初始调度通知
        NotificationHelper.scheduleAll(context, settings)
        isInitialized = true
    }

    // 设置或存单变化时重新调度通知
    LaunchedEffect(settings, deposits) {
        if (isInitialized) {
            NotificationHelper.scheduleAll(context, settings)
        }
    }

    // ── 刷新数据（不阻塞主线程） ──
    suspend fun refreshData() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            storage.autoArchiveExpired()
        }
        deposits = storage.getAllDeposits()
        archiveRecords = storage.getAllArchiveRecords()
    }

    if (!isInitialized) {
        // 加载中状态
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    // ── 主界面 ──
    // 系统返回键/滑动手势处理
    BackHandler(enabled = currentScreen !is Screen.Main) {
        currentScreen = Screen.Main
    }

    // 屏幕切换动画（SoloChef 风格：淡入淡出 + 轻微水平位移）
    fun screenOrder(screen: Screen): Int = when (screen) {
        Screen.Main -> 0
        is Screen.NewDeposit -> 1
        is Screen.EditDeposit -> 2
        is Screen.DepositDetail -> 3
        is Screen.EarningsBreakdown -> 4
        Screen.Archive -> 5
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val direction = if (screenOrder(targetState) > screenOrder(initialState)) 1 else -1
                (slideInHorizontally { (it * 0.25 * direction).toInt() } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally { (-it * 0.25 * direction).toInt() } + fadeOut(tween(300)))
            },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                is Screen.Main -> {
                    Scaffold(
                        bottomBar = {
                            BottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = { tab ->
                                    currentTab = tab
                                    // 切换到首页时自动触发归档检查
                                    if (tab == AppTab.HOME) {
                                        scope.launch { refreshData() }
                                    }
                                },
                                onNewDeposit = { currentScreen = Screen.NewDeposit }
                            )
                        },
                        containerColor = androidx.compose.ui.graphics.Color(0xFFF0F4F8)
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier.padding(
                                top = paddingValues.calculateTopPadding(),
                                start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                                end = paddingValues.calculateEndPadding(LayoutDirection.Ltr)
                            )
                        ) {
                            when (currentTab) {
                                AppTab.HOME -> HomeScreen(
                                    deposits = deposits,
                                    onNewDeposit = { currentScreen = Screen.NewDeposit },
                                    onDepositClick = { depositId ->
                                        currentScreen = Screen.DepositDetail(depositId)
                                    },
                                    onAccumulatedDetail = { currentScreen = Screen.EarningsBreakdown("accumulated") },
                                    onAnnualDetail = { currentScreen = Screen.EarningsBreakdown("annual") },
                                    onDailyDetail = { currentScreen = Screen.EarningsBreakdown("daily") },
                                    onRefresh = { scope.launch { refreshData() } }
                                )
                                AppTab.REPORTS -> ReportsScreen(
                                    deposits = deposits,
                                    banks = banks,
                                    settings = settings,
                                    onDailyDetail = { currentScreen = Screen.EarningsBreakdown("daily") },
                                    onAnnualDetail = { currentScreen = Screen.EarningsBreakdown("annual") },
                                    onMaturityDetail = { currentScreen = Screen.EarningsBreakdown("maturity") },
                                    onAccumulatedDetail = { currentScreen = Screen.EarningsBreakdown("accumulated") },
                                    onArchiveClick = { currentScreen = Screen.Archive }
                                )
                                AppTab.MANAGE -> ManagementScreen(
                                    deposits = deposits,
                                    banks = banks,
                                    products = products,
                                    settings = settings,
                                    storage = storage,
                                    onBanksChanged = { banks = it },
                                    onProductsChanged = { products = it },
                                    onSettingsChanged = {
                                        settings = it
                                        scope.launch { withContext(Dispatchers.IO) { storage.saveSettings(it) } }
                                    },
                                    onDepositsChanged = {
                                        deposits = it
                                    },
                                    onArchiveRecordsChanged = { archiveRecords = it }
                                )
                            }
                        }
                    }
                }
                is Screen.NewDeposit -> {
                    NewDepositScreen(
                        banks = banks,
                        products = products.filter { it.bankId == banks.firstOrNull()?.id },
                        deposits = deposits,
                        storage = storage,
                        onSave = {
                            scope.launch { refreshData() }
                            currentScreen = Screen.Main
                        },
                        onCancel = { currentScreen = Screen.Main },
                        onBanksChanged = { banks = it },
                        onProductsChanged = { products = it }
                    )
                }
                is Screen.DepositDetail -> {
                    val depositId = screen.depositId
                    DepositDetailScreen(
                        depositId = depositId,
                        storage = storage,
                        deposits = deposits,
                        onBack = { currentScreen = Screen.Main },
                        onDeleted = {
                            scope.launch { refreshData() }
                            currentScreen = Screen.Main
                        },
                        onEdit = { deposit ->
                            currentScreen = Screen.EditDeposit(deposit.id)
                        }
                    )
                }
                is Screen.EditDeposit -> {
                    val editId = screen.depositId
                    val editDeposit = deposits.find { it.id == editId }
                    if (editDeposit != null) {
                        NewDepositScreen(
                            banks = banks,
                            products = products.filter { it.bankId == editDeposit.bankId || it.bankId.isEmpty() },
                            deposits = deposits,
                            editDeposit = editDeposit,
                            storage = storage,
                            onSave = {
                                scope.launch { refreshData() }
                                currentScreen = Screen.Main
                            },
                            onCancel = { currentScreen = Screen.Main },
                            onBanksChanged = { banks = it },
                            onProductsChanged = { products = it }
                        )
                    }
                }
                is Screen.EarningsBreakdown -> {
                    val mode = when (screen.mode) {
                        "daily" -> BreakdownMode.DAILY
                        "annual" -> BreakdownMode.ANNUAL
                        "accumulated" -> BreakdownMode.ACCUMULATED
                        "maturity" -> BreakdownMode.MATURITY
                        else -> BreakdownMode.DAILY
                    }
                    EarningsBreakdownScreen(
                        deposits = deposits,
                        mode = mode,
                        onBack = { currentScreen = Screen.Main }
                    )
                }
                Screen.Archive -> {
                    ArchiveScreen(
                        archiveRecords = archiveRecords,
                        deposits = deposits,
                        onBack = { currentScreen = Screen.Main },
                        onDepositClick = { depositId ->
                            currentScreen = Screen.DepositDetail(depositId)
                        }
                    )
                }
            }
        }
    }
}
