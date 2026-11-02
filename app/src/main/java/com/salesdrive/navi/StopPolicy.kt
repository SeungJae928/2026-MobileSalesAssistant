package com.salesdrive.navi

/** 정차가 확정됐을 때 내비 정보로 내린 판단 */
sealed interface StopVerdict {
    /** 목적지에 도착함. 묻지 않고 바로 방문 내용을 받는다 */
    data class Arrival(val place: String?) : StopVerdict

    /** 목적지로 가는 도중의 정차. 묻지 않고 경유지로 둔다 */
    data class Waypoint(val place: String?) : StopVerdict

    /** 내비로는 알 수 없음. 운전자에게 확인한다 */
    data object Ask : StopVerdict
}

/**
 * 1. 정차 직전(10분 안)에 내비가 목적지·경유지 도착을 알렸으면 그대로 따른다.
 * 2. 안내 중이면 목적지까지 남은 거리로 판단한다. 가까우면 도착, 멀면 휴게소 같은 경유 정차.
 * 3. 안내 중이 아니면 운전자에게 묻는다.
 */
object StopPolicy {
    const val NEAR_DESTINATION_M = 500
    const val ARRIVAL_FRESH_MS = 10 * 60_000L

    fun classify(navi: NaviState, stopAtMs: Long): StopVerdict {
        navi.arrival?.takeIf { stopAtMs - it.atMs in 0..ARRIVAL_FRESH_MS }?.let {
            return when (it.kind) {
                NaviArrival.Kind.DESTINATION -> StopVerdict.Arrival(it.name)
                NaviArrival.Kind.WAYPOINT -> StopVerdict.Waypoint(it.name)
            }
        }
        val remain = navi.remainMeters
        if (navi.guiding && remain != null) {
            return if (remain <= NEAR_DESTINATION_M) StopVerdict.Arrival(navi.destinationName) else StopVerdict.Waypoint(null)
        }
        return StopVerdict.Ask
    }
}
