package com.salesdrive.navi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 내비가 알려 준 도착 이벤트. [atMs]는 이벤트를 받은 시각(System.currentTimeMillis) */
data class NaviArrival(val kind: Kind, val name: String?, val atMs: Long) {
    enum class Kind { DESTINATION, WAYPOINT }
}

data class NaviState(
    /** 목적지 안내 중 */
    val guiding: Boolean = false,
    val destinationName: String? = null,
    /** 목적지까지 남은 거리(m). 모르면 null */
    val remainMeters: Int? = null,
    /** 아직 정차 판단에 쓰지 않은 가장 최근 도착 이벤트 */
    val arrival: NaviArrival? = null,
)

/** 내비 목적지와 도착 이벤트. 정차가 경유인지 도착인지 판단하는 데 쓴다 */
interface NaviSource {
    val state: StateFlow<NaviState>

    fun start()
    fun stop()

    /** 도착 이벤트를 정차 판단에 썼으면 지운다. 다음 정차에서 다시 쓰지 않도록 */
    fun consumeArrival()
}

/** 내비가 없는 기기(폰 AVD)와 테스트용. 항상 안내 없음 */
class NoNaviSource(initial: NaviState = NaviState()) : NaviSource {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    override fun start() = Unit
    override fun stop() = Unit
    override fun consumeArrival() {
        _state.value = _state.value.copy(arrival = null)
    }
}
