package com.example.zengchubao.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// ── Tab 定义 ──

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("存单", Icons.Filled.Home, Icons.Outlined.Home),
    REPORTS("报表", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    MANAGE("管理", Icons.Filled.Settings, Icons.Outlined.Settings)
}

// ═════════════════════════ 悬浮白色底部导航栏 v2.3 ═════════════════════════
// 特征：
//   - 浮丸容器为纯白实底（左侧约 75% 宽度），承载 3 个 Tab
//   - 右侧独立圆形「新建」按钮（带图标）
//   - 保留灰色滑动选中胶囊

@Composable
fun BottomNavBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    onNewDeposit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = 22.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 左侧：浮丸容器（3 Tab）──
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(26.dp), ambientColor = Color(0x28000000)),
                color = Color.White,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                ) {
                    val tabCount = AppTab.entries.size
                    val tabWidth = maxWidth / tabCount
                    val indicatorHorizontalPadding = 4.dp
                    val indicatorWidth = tabWidth - indicatorHorizontalPadding * 2
                    val selectedIndex = AppTab.entries.indexOf(currentTab)

                    val indicatorOffset by animateDpAsState(
                        targetValue = when (selectedIndex) {
                            0 -> indicatorHorizontalPadding
                            1 -> indicatorHorizontalPadding + tabWidth
                            2 -> indicatorHorizontalPadding + tabWidth * 2
                            else -> indicatorHorizontalPadding
                        },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "indicatorOffset"
                    )

                    // 滑动灰色胶囊（与容器同弧度）
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(indicatorWidth)
                            .height(44.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFE5E7EB))
                            .align(Alignment.CenterStart)
                    )

                    // Tab icons
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onTabSelected(tab) },
                                contentAlignment = Alignment.Center
                            ) {
                                val tint = if (selected) Color(0xFF374151) else Color(0xFF9CA3AF)
                                Crossfade(
                                    targetState = selected,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "iconCrossfade"
                                ) { sel ->
                                    Icon(
                                        imageVector = if (sel) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 右侧：独立「新建」圆形按钮（与其他 tab 按钮同色）──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(26.dp), ambientColor = Color(0x28000000))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNewDeposit() }
            ) {
                Icon(
                    imageVector = Icons.Filled.RestaurantMenu,
                    contentDescription = "新建存单",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
