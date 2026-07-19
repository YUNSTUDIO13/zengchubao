package com.example.zengchubao.model

import kotlinx.datetime.*

// ── 基础换算常量 ──
const val DAYS_PER_MONTH = 30
const val DAYS_PER_YEAR = 360

/** 到期利息计算用：对年对月→360(TERM_OPTIONS用360进制)，实际天数→365 */
fun yearBasis(calcMethod: CalcMethod): Int =
    if (calcMethod == CalcMethod.ANNUAL_MATCH) 360 else 365

/** 每日收益计算用：统一365天基准 */
private const val DAILY_BASIS = 365

// ── 获取今天日期字符串 ──
fun todayString(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return now.toString() // YYYY-MM-DD
}

// ── 日期工具 ──

/** 字符串 -> LocalDate */
fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

/** 计算两个日期间的天数（实际日历天数） */
fun daysBetween(start: String, end: String): Int {
    val s = start.toLocalDate()
    val e = end.toLocalDate()
    return s.until(e, DateTimeUnit.DAY).toInt()
}

/** 起始日期 + 天数 = 结束日期 */
fun addDays(dateStr: String, days: Int): String {
    val date = dateStr.toLocalDate()
    return date.plus(days.toLong(), DateTimeUnit.DAY).toString()
}

// ── 对年对月对日法 日期计算 ──

/**
 * 对年对月对日法：按年+月+日分段加
 * 例：2024-01-15 + 1年 + 3个月 + 10天 -> 2025-04-25
 */
fun addByAnnualMatch(startDate: String, years: Int, months: Int, extraDays: Int): String {
    val d = startDate.toLocalDate()
    var result = d
    // 加年
    result = result.plus(years.toLong(), DateTimeUnit.YEAR)
    // 加月
    result = result.plus(months.toLong(), DateTimeUnit.MONTH)
    // 加天
    result = result.plus(extraDays.toLong(), DateTimeUnit.DAY)
    return result.toString()
}

/**
 * 将存期天数分解为 年 + 月 + 日
 */
fun decomposeTermDays(totalDays: Int): Triple<Int, Int, Int> {
    val years = totalDays / DAYS_PER_YEAR
    val remainder = totalDays % DAYS_PER_YEAR
    val months = remainder / DAYS_PER_MONTH
    val days = remainder % DAYS_PER_MONTH
    return Triple(years, months, days)
}

// ── 到期日期计算 ──

fun calculateEndDate(startDate: String, termDays: Int, calcMethod: CalcMethod): String {
    return when (calcMethod) {
        CalcMethod.ACTUAL_DAYS -> {
            // 实际天数法：直接加天数
            addDays(startDate, termDays)
        }
        CalcMethod.ANNUAL_MATCH -> {
            // 对年对月对日法
            val (years, months, days) = decomposeTermDays(termDays)
            addByAnnualMatch(startDate, years, months, days)
        }
    }
}

// ── 存期天数计算（用于计息） ──

fun calculateActualTermDays(startDate: String, endDate: String, calcMethod: CalcMethod): Int {
    return when (calcMethod) {
        CalcMethod.ACTUAL_DAYS -> daysBetween(startDate, endDate)
        CalcMethod.ANNUAL_MATCH -> daysBetween(startDate, endDate)
    }
}

// ── 到期本息计算 ──

/**
 * 到期本息 = 本金 × (1 + 年利率% × 存期天数 ÷ 360)
 */
fun calculateMaturityAmount(principal: Double, annualRate: Double, termDays: Int, calcMethod: CalcMethod = CalcMethod.ACTUAL_DAYS): Double {
    val basis = yearBasis(calcMethod).toDouble()
    val interest = principal * (annualRate / 100.0) * (termDays.toDouble() / basis)
    return principal + interest
}

/**
 * 到期利息
 */
fun calculateMaturityInterest(principal: Double, annualRate: Double, termDays: Int, calcMethod: CalcMethod = CalcMethod.ACTUAL_DAYS): Double {
    val basis = yearBasis(calcMethod).toDouble()
    return principal * (annualRate / 100.0) * (termDays.toDouble() / basis)
}

// ── 已累计收益计算 ──

/**
 * 计算截止今天已产生的收益
 * 持有天数 = min(今天 - 起存日, 存期天数)
 */
fun calculateAccruedInterest(
    principal: Double,
    annualRate: Double,
    startDate: String,
    termDays: Int,
    calcMethod: CalcMethod
): Double {
    val today = todayString()
    val elapsed = maxOf(0, minOf(daysBetween(startDate, today), termDays))
    return principal * (annualRate / 100.0) * (elapsed.toDouble() / yearBasis(calcMethod).toDouble())
}

// ── 提前支取计息（传统标准：活期利率 × 实际天数） ──

/**
 * 提前支取利息计算
 *
 * 传统标准：定期存款全部或部分提前支取，支取部分按支取日活期挂牌利率计息
 * 采用实际天数（公式 B）计算
 * 算头不算尾：存入当天计息，支取当天不计息
 *
 * 例：6月1日存入，6月11日支取，计息天数 = 11-1 = 10天
 */
fun calculateEarlyWithdrawalInterest(
    principal: Double,
    startDate: String,
    withdrawalDate: String,
    demandRate: Double = 0.3  // 活期利率 0.3%
): EarlyWithdrawalResult {
    // 算头不算尾：实际计息天数
    val actualDays = daysBetween(startDate, withdrawalDate)
    if (actualDays <= 0) {
        return EarlyWithdrawalResult(
            interest = 0.0,
            totalAmount = principal,
            actualDays = 0,
            demandRate = demandRate,
            description = "起存日不能晚于支取日"
        )
    }

    // 利息 = 本金 × 活期利率% × 实际天数 ÷ 年基准
    val basis = yearBasis(CalcMethod.ACTUAL_DAYS).toDouble()
    val interest = principal * (demandRate / 100.0) * (actualDays.toDouble() / basis)
    val totalAmount = principal + interest

    // 判断所在档位
    val tier = EARLY_WITHDRAWAL_TIERS.lastOrNull { actualDays >= it.minDays }
        ?: EARLY_WITHDRAWAL_TIERS.first()

    return EarlyWithdrawalResult(
        interest = interest,
        totalAmount = totalAmount,
        actualDays = actualDays,
        demandRate = demandRate,
        tierLabel = tier.label,
        description = "按${tier.label}活期利率${demandRate}%计息，实际计息${actualDays}天（算头不算尾）"
    )
}

// ── 提前支取结果 ──

data class EarlyWithdrawalResult(
    val interest: Double,
    val totalAmount: Double,
    val actualDays: Int,
    val demandRate: Double,
    val tierLabel: String = "",
    val description: String = ""
)

// ── 距到期天数 ──

fun daysUntilMaturity(endDate: String): Int {
    val today = todayString()
    return daysBetween(today, endDate)
}

// ── 年化收益预估 ──

/**
 * 今年预估收益 = Σ(本金 × 年利率% ÷ 365 × 单笔今年有效天数)
 *
 * 单笔今年有效天数 = min(今年12-31, 到期日) - max(今年1-1, 起存日)
 * 起存日早于今年 → 从1月1日起算；起存日今年内 → 从起存日起算
 * 例：2024年存的 → 1/1~12/31 = 365天
 * 例：今年7/10新存 → 7/10~12/31 = 174天
 */
fun calculateAnnualExpectedYield(deposits: List<Deposit>): Double {
    val today = todayString()
    val yearStart = "${today.take(4)}-01-01"
    val yearEnd = "${today.take(4)}-12-31"
    return deposits
        .sumOf { dep ->
            val start = if (dep.startDate > yearStart) addDays(dep.startDate, 1) else yearStart
            val end = if (dep.endDate < yearEnd) dep.endDate else yearEnd
            if (start >= end) return@sumOf 0.0
            val days = daysBetween(start, end) + 1
            dep.principal * (dep.annualRate / 100.0) / yearBasis(dep.calcMethod).toDouble() * days
        }
}

// ── 到期总收益 ──

fun calculateTotalMaturityYield(deposits: List<Deposit>): Double {
    return deposits.filter { it.status == DepositStatus.HOLDING }
        .sumOf { calculateMaturityInterest(it.principal, it.annualRate, it.termDays, it.calcMethod) }
}

// ── 加权平均年化利率（时间加权） ──

/**
 * 综合年化率 = Σ(本金 × 年利率 × 存期天数) / Σ(本金 × 存期天数)
 * 引入时间权重：3年期4% > 1年期4%，更精准反映资金综合成本
 */
fun calculateWeightedRate(deposits: List<Deposit>): Double {
    val holding = deposits.filter { it.status == DepositStatus.HOLDING }
    val weightedSum = holding.sumOf { it.principal * it.annualRate * it.termDays }
    val totalWeight = holding.sumOf { it.principal * it.termDays.toDouble() }
    if (totalWeight <= 0) return 0.0
    return weightedSum / totalWeight
}

// ── 资产余额 ──

fun calculateAssetBalance(deposits: List<Deposit>): Double {
    return deposits.filter { it.status == DepositStatus.HOLDING }
        .sumOf { it.principal + calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays, it.calcMethod) }
}

// ── 累计存入 ──

fun calculateTotalDeposited(deposits: List<Deposit>): Double {
    return deposits.sumOf { it.principal }
}

// ── 累计收益 ──

fun calculateTotalYield(deposits: List<Deposit>): Double {
    return deposits.sumOf {
        when (it.status) {
            DepositStatus.HOLDING -> calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays, it.calcMethod)
            DepositStatus.ARCHIVED, DepositStatus.MATURED, DepositStatus.EARLY_WITHDRAWN ->
                it.maturityAmount - it.principal
        }
    }
}

// ── 存期标签 ──

fun termDaysToLabel(termDays: Int): String {
    return TERM_OPTIONS.find { it.days == termDays }?.label
        ?: if (termDays % DAYS_PER_YEAR == 0) "${termDays / DAYS_PER_YEAR}年"
        else if (termDays % DAYS_PER_MONTH == 0) "${termDays / DAYS_PER_MONTH}个月"
        else "${termDays}天"
}
