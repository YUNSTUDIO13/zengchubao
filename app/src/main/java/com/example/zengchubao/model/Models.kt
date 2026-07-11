package com.example.zengchubao.model

import kotlinx.serialization.Serializable

// ── 枚举 ──

enum class DepositStatus(val label: String) {
    HOLDING("持有中"),
    MATURED("已到期"),
    EARLY_WITHDRAWN("已提前支取"),
    ARCHIVED("已归档")
}

enum class ProductType(val label: String) {
    FIXED_DEPOSIT("定期存款"),
    WEALTH_MGMT("理财产品"),
    OTHER("其他")
}

enum class CalcMethod(val label: String) {
    ANNUAL_MATCH("对年对月对日法"),
    ACTUAL_DAYS("实际天数法")
}

// ── 存期选项 ──

@Serializable
data class TermOption(
    val label: String,
    val days: Int
)

val TERM_OPTIONS = listOf(
    TermOption("7天", 7),
    TermOption("1个月", 30),
    TermOption("3个月", 90),
    TermOption("6个月", 180),
    TermOption("1年", 360),
    TermOption("2年", 720),
    TermOption("3年", 1080),
    TermOption("5年", 1800)
)

// ── 利率档位 (提前支取用) ──

@Serializable
data class RateTier(
    val minDays: Int,       // 存满此天数
    val label: String,      // 档位描述
    val tierRate: Double    // 该档活期利率（%）
)

val EARLY_WITHDRAWAL_TIERS = listOf(
    RateTier(0, "不满3个月", 0.3),
    RateTier(90, "满3个月", 0.3),
    RateTier(180, "满6个月", 0.3),
    RateTier(360, "满1年", 0.3),
    RateTier(720, "满2年", 0.3),
    RateTier(1080, "满3年", 0.3),
    RateTier(1800, "满5年", 0.3)
)

// ── 银行 ──

@Serializable
data class Bank(
    val id: String,
    val name: String,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0,
    val colorHex: String = ""  // e.g. "#3B82F6"; empty = use fallback by index
)

// ── 产品模板 ──

@Serializable
data class Product(
    val id: String,
    val bankId: String,
    val name: String,
    val defaultTerm: Int? = null,
    val defaultCalcMethod: CalcMethod? = null,
    val productType: ProductType = ProductType.FIXED_DEPOSIT
)

// ── 存单（核心实体） ──

@Serializable
data class Deposit(
    val id: String,
    val bankId: String,
    val bankName: String,
    val productId: String = "",
    val productName: String,
    val productType: ProductType = ProductType.FIXED_DEPOSIT,
    val principal: Double,          // 本金
    val annualRate: Double,         // 年化利率 %
    val startDate: String,          // YYYY-MM-DD 起存日
    val endDate: String,            // YYYY-MM-DD 到期日
    val termDays: Int,              // 存期天数
    val termLabel: String = "",     // 存期描述（如"3年"）
    val calcMethod: CalcMethod = CalcMethod.ACTUAL_DAYS,
    val maturityAmount: Double = 0.0, // 到期本息
    val status: DepositStatus = DepositStatus.HOLDING,
    val note: String = "",          // 备注
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ── 归档记录 ──

@Serializable
data class ArchiveRecord(
    val id: String,
    val periodLabel: String,        // 周期名称，如"2026年Q2"
    val startDate: String,
    val endDate: String,
    val totalPrincipal: Double,     // 投入本金
    val totalYield: Double,         // 总收益
    val weightedAnnualRate: Double, // 综合年化率 %
    val maxRate: Double,
    val minRate: Double,
    val productCount: Int,
    val depositIds: List<String>,   // 包含的存单ID列表
    val createdAt: Long = System.currentTimeMillis()
)

// ── 备份载荷 ──

@Serializable
data class BackupPayload(
    val version: String = "1.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val deposits: List<Deposit>,
    val banks: List<Bank>,
    val products: List<Product>,
    val archiveRecords: List<ArchiveRecord>,
    val settings: AppSettings
)

// ── 报表设置项 ──

@Serializable
data class ReportItemSetting(
    val id: String,
    val title: String,
    val enabled: Boolean = true
)

val DEFAULT_REPORT_ITEMS = listOf(
    ReportItemSetting("assetOverview", "资产收益", true),
    ReportItemSetting("bankDistribution", "按银行资产分布", true),
    ReportItemSetting("assetCategory", "资产分类明细", true),
    ReportItemSetting("assetTrend", "资产趋势图", true)
)

// ── 应用设置 ──

@Serializable
data class AppSettings(
    val reminderDays: Int = 7,
    val showTotalDeposited: Boolean = true,
    val showAssetBalance: Boolean = true,
    val showTotalYield: Boolean = true,
    val showExpectedAnnual: Boolean = true,
    val showMaturityYield: Boolean = true,
    // 报表展示开关（保留旧字段兼容）
    val showBankDistribution: Boolean = true,
    val showNetWorthTrend: Boolean = true,
    val showAssetCategoryDetail: Boolean = true,
    // 报表顺序与可见性（v2.0）
    val reportItems: List<ReportItemSetting> = DEFAULT_REPORT_ITEMS
) {
    /**
     * 兼容旧版本备份：
     * - 移除 DEFAULT_REPORT_ITEMS 中已废弃的 id
     * - 用 DEFAULT 中的最新 title 覆写（防止重命名后旧 title 残留）
     * - 将 DEFAULT_REPORT_ITEMS 中新增的条目补入 reportItems
     * 保持既有顺序不变。
     */
    fun migrated(): AppSettings {
        val defaultsById = DEFAULT_REPORT_ITEMS.associateBy { it.id }
        val validIds = defaultsById.keys
        val kept = reportItems.filter { it.id in validIds }
            .map { item -> defaultsById[item.id] ?: item }
        val existingIds = kept.map { it.id }.toSet()
        val missing = DEFAULT_REPORT_ITEMS.filter { it.id !in existingIds }
        if (kept.size == reportItems.size && missing.isEmpty()) return this
        return copy(reportItems = kept + missing)
    }
}
