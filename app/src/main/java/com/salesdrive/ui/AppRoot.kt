package com.salesdrive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.ui.calendar.CalendarScreen
import com.salesdrive.ui.driving.DrivingScreen
import com.salesdrive.ui.driving.DrivingViewModel
import com.salesdrive.ui.home.HomeScreen
import com.salesdrive.ui.logbook.LogbookScreen
import com.salesdrive.ui.profile.ProfileScreen
import com.salesdrive.ui.review.TripDetailScreen
import com.salesdrive.ui.signal.SignalCheckScreen

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    LOGBOOK("운행기록부", Icons.AutoMirrored.Filled.List),
    CALENDAR("일정", Icons.Filled.DateRange),
    PROFILE("내 정보", Icons.Filled.Person),
    SIGNAL("신호 확인", Icons.Filled.Build),
}

/**
 * 왼쪽 탭 + 본문. 주행 기록을 하나 고르면 본문 자리에 방문 기록 확인 화면이 뜬다.
 * 주행 중에는 화면마다 따로 처리하지 않고 여기서 전체를 "기록 중" 화면으로 덮는다.
 */
@Composable
fun AppRoot(driving: DrivingViewModel = hiltViewModel()) {
    val restricted by driving.restricted.collectAsStateWithLifecycle(initialValue = false)
    if (restricted) {
        DrivingScreen()
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openTrip by rememberSaveable { mutableStateOf<Long?>(null) }

    Row(Modifier.fillMaxSize()) {
        SideBar(
            current = tab.takeIf { openTrip == null },
            onSelect = {
                tab = it
                openTrip = null
            },
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val trip = openTrip
            when {
                trip != null -> TripDetailScreen(hiltViewModel(), trip, onBack = { openTrip = null })
                tab == Tab.HOME -> HomeScreen(hiltViewModel(), onOpenTrip = { openTrip = it }, onOpenProfile = { tab = Tab.PROFILE })
                tab == Tab.LOGBOOK -> LogbookScreen(hiltViewModel(), onOpenTrip = { openTrip = it })
                tab == Tab.CALENDAR -> CalendarScreen(hiltViewModel())
                tab == Tab.PROFILE -> ProfileScreen(hiltViewModel())
                tab == Tab.SIGNAL -> SignalCheckScreen(hiltViewModel())
            }
        }
    }
}

/** 탭 5개가 세로 높이를 똑같이 나눠 빈 공간 없이 채운다. 고른 탭은 칸 전체를 칠한다 */
@Composable
private fun SideBar(current: Tab?, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(140.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectableGroup(),
    ) {
        Tab.entries.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SideTab(t, t == current, Modifier.weight(1f)) { onSelect(t) }
        }
    }
}

@Composable
private fun SideTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Column(
        modifier.fillMaxWidth()
            .background(if (selected) colors.primary else colors.surfaceContainer)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, Modifier.size(40.dp), tint = content)
        Text(tab.label, style = MaterialTheme.typography.titleMedium, color = content)
    }
}
