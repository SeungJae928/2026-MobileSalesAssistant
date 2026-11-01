package com.salesdrive.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 실차·Pleos 에뮬레이터용 신호 소스.
 *
 * - Pleos Vehicle SDK: 누적 주행거리(Odometer), 기어(Gear). 권한 pleos.car.permission.CAR_DRIVE
 * - AAOS 차량 속성: 속도, 주차브레이크, 시동. Pleos SDK에 해당 API가 없다
 *
 * 신호는 바뀔 때만 오지만, 상태머신은 "주차 30초 유지"처럼 시간 경과로도 판단하므로
 * 마지막 값을 [tickMs]마다 현재 시각으로 다시 내보낸다. 주행거리를 받기 전에는 아무것도 내보내지 않는다.
 */
class PleosVehicleSource(
    private val context: Context,
    private val tickMs: Long = 1_000,
) : VehicleSignalSource {

    private data class Latest(
        val odometerKm: Double? = null,
        val gear: Gear = Gear.UNKNOWN,
        val speedKmh: Float? = null,
        val parkingBrakeOn: Boolean? = null,
        val ignitionOn: Boolean? = null,
    )

    override val snapshots: Flow<VehicleSnapshot> = callbackFlow {
        val latest = MutableStateFlow(Latest())

        val vehicle = Vehicle(context).apply { initialize() }
        val odometer = vehicle.getOdometer()
        val gear = vehicle.getGear()

        // 단위는 km로 가정. 에뮬레이터에서 계기판 값과 대조해 확인할 것
        val odometerListener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                if (value != null) latest.update { it.copy(odometerKm = value.toDouble()) }
            }
            override fun onFailed(e: Exception) = Log.w(TAG, "odometer", e).let {}
        }
        val gearListener = object : GearSelectionListener {
            override fun onSelected(value: VehicleGear?) = latest.update { it.copy(gear = value.toGear()) }
            override fun onFailed(e: Exception) = Log.w(TAG, "gear", e).let {}
        }
        odometer.getCurrentDistanceDriven(
            { value -> if (value != null) latest.update { it.copy(odometerKm = value.toDouble()) } },
            { e -> Log.w(TAG, "odometer get", e) },
        )
        gear.getCurrentGearSelectionState(
            { value -> latest.update { it.copy(gear = value.toGear()) } },
            { e -> Log.w(TAG, "gear get", e) },
        )
        odometer.registerDistanceDriven(odometerListener)
        gear.registerGearSelection(gearListener)

        val car = Car.createCar(context)
        val properties = car?.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager?
        val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                        latest.update { it.copy(speedKmh = (value.value as Float) * MS_TO_KMH) }
                    VehiclePropertyIds.PARKING_BRAKE_ON ->
                        latest.update { it.copy(parkingBrakeOn = value.value as Boolean) }
                    VehiclePropertyIds.IGNITION_STATE ->
                        latest.update { it.copy(ignitionOn = (value.value as Int).toIgnitionOn()) }
                }
            }
            override fun onErrorEvent(propertyId: Int, zone: Int) {
                Log.w(TAG, "car property error ${VehiclePropertyIds.toString(propertyId)}")
            }
        }
        // 권한이 없거나 지원하지 않는 속성은 null로 남고, 상태머신이 대체 판단한다
        for (id in listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.PARKING_BRAKE_ON, VehiclePropertyIds.IGNITION_STATE)) {
            try {
                properties?.registerCallback(propertyCallback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            } catch (e: SecurityException) {
                Log.w(TAG, "no permission for ${VehiclePropertyIds.toString(id)}", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "unsupported ${VehiclePropertyIds.toString(id)}", e)
            }
        }

        launch {
            while (isActive) {
                val l = latest.value
                if (l.odometerKm != null) {
                    send(
                        VehicleSnapshot(
                            timestampMs = System.currentTimeMillis(),
                            ignitionOn = l.ignitionOn,
                            speedKmh = l.speedKmh,
                            gear = l.gear,
                            parkingBrakeOn = l.parkingBrakeOn,
                            odometerKm = l.odometerKm,
                            location = null, // TODO: FusedLocation SDK
                        ),
                    )
                }
                delay(tickMs)
            }
        }

        awaitClose {
            odometer.unregisterDistanceDriven(odometerListener)
            gear.unregisterGearSelection(gearListener)
            properties?.unregisterCallback(propertyCallback)
            car?.disconnect()
            vehicle.release()
        }
    }

    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        VehicleGear.GEAR_UNKNOWN, null -> Gear.UNKNOWN
        else -> Gear.D // GEAR_DRIVE, 수동 1~9단
    }

    /** ACC(전장만 켜짐)는 엔진이 꺼진 상태라 시동 꺼짐으로 본다 */
    private fun Int.toIgnitionOn(): Boolean? = when (this) {
        VehicleIgnitionState.ON, VehicleIgnitionState.START -> true
        VehicleIgnitionState.OFF, VehicleIgnitionState.LOCK, VehicleIgnitionState.ACC -> false
        else -> null
    }

    private companion object {
        const val TAG = "PleosVehicleSource"
        const val MS_TO_KMH = 3.6f
    }
}
