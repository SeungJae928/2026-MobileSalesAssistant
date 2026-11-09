package com.salesdrive.trip

import com.salesdrive.data.db.Trip
import com.salesdrive.vehicle.VehicleSnapshot

/** [TripRecorder]가 주행을 저장하는 곳. 테스트에서는 메모리 구현으로 바꾼다 */
interface TripStore {
    /** 끝나지 않은 주행. 앱 재시작 시 이어서 기록할지 판단한다 */
    suspend fun openTrip(): Trip?

    suspend fun startTrip(at: VehicleSnapshot): Long

    /** 닫은 주행의 id. 열린 주행이 없으면 아무것도 하지 않고 null. [destination]은 내비 목적지명 */
    suspend fun endTrip(at: VehicleSnapshot, destination: String?): Long?

    /**
     * 날을 넘겨 버려진 주행을 닫는다. 도착 시각을 알 수 없어 출발 시각으로 닫으므로 오늘의 주행으로 잡히지 않는다.
     * 계기판 값은 [at]으로 적되, 그 사이 기록되지 않은 주행이 섞였을 수 있어 비고에 남긴다.
     */
    suspend fun closeAbandoned(at: VehicleSnapshot)

    /** 열린 주행에 경유 정차를 남긴다. 열린 주행이 없으면 아무것도 하지 않는다 */
    suspend fun addStop(arrived: VehicleSnapshot, departed: VehicleSnapshot, name: String?)

    /** 가장 최근에 끝난 주행. 대기 중 방문 기록 버튼을 누르면 여기에 방문을 붙인다 */
    suspend fun lastEndedTripId(): Long?
}
