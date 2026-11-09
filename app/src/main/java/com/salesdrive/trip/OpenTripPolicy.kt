package com.salesdrive.trip

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 끝나지 않은 주행을 이어 갈지 판단한다. 앱이 주행 중 죽은 채로 날을 넘겼다면 그 주행은 이미 끝난 것이다 */
object OpenTripPolicy {
    /** 자정을 넘겨 달리는 중에 앱이 다시 켜진 경우는 같은 주행으로 본다 */
    private val OVERNIGHT_DRIVE_LIMIT: Duration = Duration.ofHours(6)

    fun isAbandoned(startedAt: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        now.atZone(zone).toLocalDate().isAfter(startedAt.atZone(zone).toLocalDate()) &&
            Duration.between(startedAt, now) >= OVERNIGHT_DRIVE_LIMIT
}
