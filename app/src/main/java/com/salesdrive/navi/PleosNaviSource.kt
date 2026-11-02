package com.salesdrive.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos NaviHelper로 목적지·남은 거리·도착 이벤트를 받는다.
 * 조회 API(get*Info)는 값을 바로 돌려주지 않고 리스너로 응답하므로, 안내 상태가 바뀔 때마다 다시 요청해 [state]를 채운다.
 */
class PleosNaviSource(context: Context) : NaviSource {

    private val helper = NaviHelper(context)
    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()
    private var started = false

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "status $status")
            setGuiding(status == NavigationStatus.ROUTE_DRIVE)
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            // isRouting은 경로 탐색 중 여부라 안내 중에도 false다. 안내 여부는 naviMode로 본다
            Log.i(TAG, "route state routing=${info.isRouting} mode=${info.naviMode}")
            setGuiding(info.naviMode == NaviMode.ROUTE_GUIDING || info.naviMode == NaviMode.SIMULATION_GUIDING)
        }

        override fun onRouteStarted(info: RouteStartInfo) = log("route started $info")
        override fun onRoutingCompleted() = log("routing completed").also { refresh() }
        override fun onDestinationChanged() = log("destination changed").also { refresh() }
        override fun onRouteEnded() = log("route ended")
        override fun onRouteCancelled() = log("route cancelled")

        override fun onDestinationInfo(info: DestinationInfo) {
            Log.i(TAG, "destination ${info.name} remain=${info.remainDistance}m")
            // 목적지가 없으면 이름은 빈 문자열, 남은 거리는 0으로 온다. "목적지 근처"로 오인하지 않게 비운다
            val name = info.name?.takeIf(String::isNotBlank)
            _state.update { it.copy(destinationName = name, remainMeters = info.remainDistance.takeIf { name != null }) }
        }

        /** 안내 중 주기적으로 오는 남은 거리 */
        override fun onDrivingInfo(info: DrivingInfo) {
            val destination = info.destination ?: return
            Log.d(TAG, "driving info ${destination.name} ${destination.distance}m")
            _state.update { it.copy(remainMeters = destination.distance, destinationName = it.destinationName ?: destination.name) }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            Log.i(TAG, "arrived at destination ${info.name}")
            arrived(NaviArrival.Kind.DESTINATION, info.name)
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            Log.i(TAG, "arrived at waypoint ${info.name}")
            arrived(NaviArrival.Kind.WAYPOINT, info.name)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "error $errorCode")
        }
    }

    override fun start() {
        if (started) return
        started = true
        helper.initialize(
            {
                helper.addListener(listener)
                helper.getRouteStateInfo()
                refresh()
            },
            { Log.w(TAG, "init failed", it) },
        )
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching {
            helper.removeListener(listener)
            helper.release()
        }
    }

    override fun consumeArrival() = _state.update { it.copy(arrival = null) }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private fun setGuiding(guiding: Boolean) {
        _state.update { if (guiding) it.copy(guiding = true) else it.copy(guiding = false, remainMeters = null, destinationName = null) }
        if (guiding) refresh()
    }

    private fun refresh() {
        runCatching { helper.getDestinationInfo() }.onFailure { Log.w(TAG, "getDestinationInfo", it) }
    }

    private fun arrived(kind: NaviArrival.Kind, name: String?) =
        _state.update { it.copy(arrival = NaviArrival(kind, name?.takeIf(String::isNotBlank), System.currentTimeMillis())) }

    private companion object {
        const val TAG = "NaviSource"
    }
}
