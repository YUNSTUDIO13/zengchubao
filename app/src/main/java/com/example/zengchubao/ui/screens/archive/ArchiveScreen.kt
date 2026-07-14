package com.example.zengchubao.ui.screens.archive

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.zengchubao.ui.screens.home.heroGradient
import java.text.NumberFormat
import java.util.Locale

private val CN_INT = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
private val CN = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
private fun fmtI(v: Double) = "¥${CN_INT.format(v)}"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    archiveRecords: List<ArchiveRecord>,
    deposits: List<Deposit>,
    onBack: () -> Unit = {},
    onDepositClick: (String) -> Unit = {}
) {
    val archived = remember(deposits) { deposits.filter { it.status == DepositStatus.ARCHIVED } }

    // 归档统计
    val totalPrincipal = remember(archived) { archived.sumOf { it.principal } }
    val totalYield = remember(archived) { archived.sumOf { it.maturityAmount - it.principal } }
    val momText = remember(archiveRecords) {
        if (archiveRecords.size < 2) "---"
        else {
            val sorted = archiveRecords.sortedByDescending { it.createdAt }
            val latest = sorted[0].totalYield
            val prev = sorted[1].totalYield
            if (prev <= 0) "---"
            else {
                val mom = ((latest - prev) / prev * 100)
                val arrow = if (mom >= 0) "↑" else "↓"
                "$arrow 环比 ${if (mom >= 0) "+" else ""}${"%.1f".format(mom)}%"
            }
        }
    }
    val weightedRate = remember(archived) {
        if (totalPrincipal > 0) archived.sumOf { it.principal * it.annualRate } / totalPrincipal else 0.0
    }

    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB))) {
        // ── 顶部标题栏（带返回）──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFF1E293B),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("归档", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), lineHeight = 19.sp)
                Spacer(Modifier.height(2.dp))
                Text("历史收益回顾", fontSize = 10.sp, lineHeight = 10.sp, color = Color(0xFF94A3B8))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {

        // ── Figma Hero 卡片 ──
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .heroGradient()
                .padding(20.dp)
        ) {
            Column {
                Text("周期内总收益", fontSize = 9.sp, fontWeight = FontWeight.W500,
                    color = Color(0xFFBFDBFE), letterSpacing = 1.sp)
                Text(fmtI(totalYield), fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, lineHeight = 30.sp)
                Text(momText, fontSize = 10.sp, color = Color(0xFFBFDBFE).copy(alpha = 0.8f))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(0.8f)) {
                        Text("综合年化率%", fontSize = 9.sp, fontWeight = FontWeight.W500,
                            color = Color(0xFFBFDBFE).copy(alpha = 0.7f), lineHeight = 9.sp)
                        Text("${"%.2f".format(weightedRate)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, lineHeight = 20.sp)
                    }
                    Column(Modifier.weight(1.2f)) {
                        Text("投入本金", fontSize = 9.sp, fontWeight = FontWeight.W500,
                            color = Color(0xFFBFDBFE).copy(alpha = 0.7f), lineHeight = 9.sp)
                        Text(fmtI(totalPrincipal), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, lineHeight = 20.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Figma 历史记录标题 ──
        Text("历史记录", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B), modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

        // ── Figma 可展开归档卡片 ──
        if (archived.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("暂无归档记录", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            // 按银行分组展示
            val grouped = archived.groupBy { it.bankName }
            grouped.forEach { (bankName, deps) ->
                val groupTotalPrincipal = deps.sumOf { it.principal }
                val groupTotalYield = deps.sumOf { it.maturityAmount - it.principal }
                val groupWeightedRate = if (groupTotalPrincipal > 0) deps.sumOf { it.principal * it.annualRate } / groupTotalPrincipal else 0.0
                val isExpanded = expandedId == bankName

                Card(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        // 头部（可点击）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedId = if (isExpanded) null else bankName }
                                .padding(16.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(bankName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Text("${deps.size} 笔存单", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                                Text("+${fmtI(groupTotalYield)}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("最高 ${"%.2f".format(deps.maxOf { it.annualRate })}%", fontSize = 10.sp,
                                    color = Color(0xFF94A3B8))
                                Text("综合年化 ${"%.2f".format(groupWeightedRate)}%", fontSize = 10.sp,
                                    color = Color(0xFF475569), fontWeight = FontWeight.W600)
                            }
                        }

                        // Figma 展开详情
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                        ) {
                            Column(
                                Modifier
                                    .background(Color(0xFFF8FAFC).copy(alpha = 0.6f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("产品明细", fontSize = 9.sp, fontWeight = FontWeight.W600,
                                    color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                                Spacer(Modifier.height(8.dp))
                                deps.sortedByDescending { it.maturityAmount - it.principal }.forEach { dep ->
                                    val yieldAmount = dep.maturityAmount - dep.principal
                                    val periodText = "${dep.startDate.take(7).replace("-", "/")}～${dep.endDate.take(7).replace("-", "/")}"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onDepositClick(dep.id) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(dep.productName, fontSize = 12.sp, color = Color(0xFF334155),
                                                fontWeight = FontWeight.W500, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false))
                                            Text(periodText, fontSize = 10.sp, color = Color(0xFF94A3B8))
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                "+${CN_INT.format(yieldAmount)}",
                                                fontSize = 12.sp, fontWeight = FontWeight.W600, color = Color(0xFF2563EB)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = Color(0xFFCBD5E1),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        }
    }
}
