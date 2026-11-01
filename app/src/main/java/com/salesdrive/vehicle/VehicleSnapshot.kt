package com.salesdrive.vehicle

enum class Gear { P, R, N, D, UNKNOWN }

data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 한 시점의 차량 상태. SDK·AAOS 속성·Fake 어디서 왔든 이 형태로 통일한다.
 * 읽을 수 없는 신호는 null(또는 [Gear.UNKNOWN])로 두고, 상태머신이 대체 판단한다.
 */
data class VehicleSnapshot(
    val timestampMs: Long,
    val ignitionOn: Boolean?,
    val speedKmh: Float?,
    val gear: Gear,
    val parkingBrakeOn: Boolean?,
    val odometerKm: Double,
    val location: GeoPoint?,
)
