package com.salesdrive.vehicle.fake

import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.GeoPoint
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 시나리오의 한 구간. 구간 동안 1초(시뮬레이션 시간)마다 스냅샷 하나를 낸다. */
data class Segment(
    val durationSec: Int,
    val speedKmh: Float,
    val gear: Gear,
    val parkingBrakeOn: Boolean,
)

/**
 * 에뮬레이터 조작 없이 같은 주행을 재생하는 소스. 시연 영상과 테스트에 쓴다.
 * [speedUp]배 빠르게 재생하지만, 스냅샷의 timestampMs는 시뮬레이션 시간이라 상태머신 판단은 실제와 같다.
 */
class FakeVehicleSource(
    private val scenario: List<Segment>,
    private val from: GeoPoint,
    private val to: GeoPoint,
    private val startOdometerKm: Double = 12_345.6,
    private val startTimeMs: Long = System.currentTimeMillis(),
    private val speedUp: Double = 60.0,
) : VehicleSignalSource {

    private val totalKm = scenario.sumOf { it.durationSec * it.speedKmh.toDouble() / 3600 }

    override val snapshots: Flow<VehicleSnapshot> = flow {
        var odometer = startOdometerKm
        var elapsedSec = 0L
        val tickMs = (1000 / speedUp).toLong()
        for (segment in scenario) {
            repeat(segment.durationSec) {
                odometer += segment.speedKmh / 3600.0
                elapsedSec++
                val progress = if (totalKm > 0) (odometer - startOdometerKm) / totalKm else 0.0
                emit(
                    VehicleSnapshot(
                        timestampMs = startTimeMs + elapsedSec * 1000,
                        ignitionOn = true,
                        speedKmh = segment.speedKmh,
                        gear = segment.gear,
                        parkingBrakeOn = segment.parkingBrakeOn,
                        odometerKm = odometer,
                        location = GeoPoint(
                            lat = from.lat + (to.lat - from.lat) * progress,
                            lng = from.lng + (to.lng - from.lng) * progress,
                        ),
                    ),
                )
                delay(tickMs)
            }
        }
    }

    companion object {
        /** 출발 → 15km → 신호 대기 40초 → 15km → 거래처 도착 → 주차 1분 */
        val DEMO_SCENARIO = listOf(
            Segment(durationSec = 10, speedKmh = 0f, gear = Gear.P, parkingBrakeOn = true),
            Segment(durationSec = 900, speedKmh = 60f, gear = Gear.D, parkingBrakeOn = false),
            Segment(durationSec = 40, speedKmh = 0f, gear = Gear.D, parkingBrakeOn = false),
            Segment(durationSec = 900, speedKmh = 60f, gear = Gear.D, parkingBrakeOn = false),
            Segment(durationSec = 5, speedKmh = 0f, gear = Gear.D, parkingBrakeOn = false),
            Segment(durationSec = 60, speedKmh = 0f, gear = Gear.P, parkingBrakeOn = true),
        )

        /** 서울역 → 판교 근처, 위치 표시용 */
        val DEMO_FROM = GeoPoint(37.5547, 126.9707)
        val DEMO_TO = GeoPoint(37.3948, 127.1112)
    }
}
