package com.salesdrive.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salesdrive.ui.home.HomeScreen
import com.salesdrive.ui.logbook.LogbookScreen
import com.salesdrive.ui.review.TripDetailScreen
import com.salesdrive.ui.signal.SignalCheckScreen

enum class Tab(val label: String, val icon: String) {
    HOME("홈", "🏠"),
    LOGBOOK("운행기록부", "📒"),
    SIGNAL("신호 확인", "🛠"),
}

/** 왼쪽 탭 + 본문. 주행 기록을 하나 고르면 본문 자리에 방문 기록 확인 화면이 뜬다 */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openTrip by rememberSaveable { mutableStateOf<Long?>(null) }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(Modifier.fillMaxHeight().width(140.dp).padding(top = 24.dp)) {
            Tab.entries.forEach { t ->
                NavigationRailItem(
                    selected = t == tab && openTrip == null,
                    onClick = {
                        tab = t
                        openTrip = null
                    },
                    icon = { Text(t.icon, style = MaterialTheme.typography.headlineMedium) },
                    label = { Text(t.label, style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        val trip = openTrip
        when {
            trip != null -> TripDetailScreen(hiltViewModel(), trip, onBack = { openTrip = null })
            tab == Tab.HOME -> HomeScreen(hiltViewModel(), onOpenTrip = { openTrip = it })
            tab == Tab.LOGBOOK -> LogbookScreen(hiltViewModel(), onOpenTrip = { openTrip = it })
            tab == Tab.SIGNAL -> SignalCheckScreen(hiltViewModel())
        }
    }
}
