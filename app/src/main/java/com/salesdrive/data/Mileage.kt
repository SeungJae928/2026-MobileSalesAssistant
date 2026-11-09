package com.salesdrive.data

import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.Trip

/** 운행기록부 거리 계산. 홈·기록부 화면과 CSV 내보내기가 같은 값을 쓰도록 한 곳에 둔다 */
object Mileage {

    /** 끝나지 않은 주행은 0 */
    fun distanceKm(trip: Trip): Double =
        trip.odoEndKm?.let { (it - trip.odoStartKm).coerceAtLeast(0.0) } ?: 0.0

    /** 주행 후 계기판 값이 주행 전보다 작음(시연 주행을 실제 신호로 끝낸 경우 등). 실제 거리를 알 수 없다 */
    fun isOdometerReversed(trip: Trip): Boolean = trip.odoEndKm?.let { it < trip.odoStartKm } ?: false

    /** 화면과 기록부에 적는 주행 후 계기판 값. 뒤로 간 값은 주행 전 값으로 적어 주행거리 0과 맞춘다. DB에는 받은 값 그대로 둔다 */
    fun endReadingKm(trip: Trip): Double? = trip.odoEndKm?.coerceAtLeast(trip.odoStartKm)

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
