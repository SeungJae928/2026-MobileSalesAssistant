package com.salesdrive.data

import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.Trip

/** 운행기록부 거리 계산. 홈·기록부 화면과 CSV 내보내기가 같은 값을 쓰도록 한 곳에 둔다 */
object Mileage {

    /** 끝나지 않은 주행은 0 */
    fun distanceKm(trip: Trip): Double =
        trip.odoEndKm?.let { (it - trip.odoStartKm).coerceAtLeast(0.0) } ?: 0.0

    data class Totals(
        val totalKm: Double = 0.0,
        val businessKm: Double = 0.0,
        val commuteKm: Double = 0.0,
        val personalKm: Double = 0.0,
    ) {
        /** 업무사용비율 = (출퇴근 + 일반업무) ÷ 총주행거리. 주행이 없으면 null */
        val businessRatio: Double?
            get() = if (totalKm > 0) (businessKm + commuteKm) / totalKm else null
    }

    fun totals(trips: List<Trip>): Totals = trips.fold(Totals()) { acc, trip ->
        val km = distanceKm(trip)
        when (trip.purpose) {
            Purpose.BUSINESS -> acc.copy(totalKm = acc.totalKm + km, businessKm = acc.businessKm + km)
            Purpose.COMMUTE -> acc.copy(totalKm = acc.totalKm + km, commuteKm = acc.commuteKm + km)
            Purpose.PERSONAL -> acc.copy(totalKm = acc.totalKm + km, personalKm = acc.personalKm + km)
        }
    }
}
