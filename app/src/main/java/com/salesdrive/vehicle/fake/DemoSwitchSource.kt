package com.salesdrive.vehicle.fake

import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

/**
 * 평소에는 실제 차량 신호를 그대로 넘기고, 시연을 켜면 Fake 시나리오로 바꿔 끼운다.
 * 시연 주행은 마지막 실제 주행거리에서 이어 달리고, 시연을 끄면 다시 실제 신호를 받는다.
 * 기록기는 이 소스 하나만 구독하므로 상태머신·음성·화면이 실제와 똑같이 동작한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoSwitchSource(
    private val real: VehicleSignalSource,
    private val demo: (startOdometerKm: Double) -> VehicleSignalSource,
) : VehicleSignalSource {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var lastOdometerKm = 0.0

    fun start() {
        _running.value = true
    }

    fun stop() {
        _running.value = false
    }

    override val snapshots: Flow<VehicleSnapshot> = _running.flatMapLatest { demoOn ->
        if (demoOn) {
            demo(lastOdometerKm).snapshots
        } else {
            real.snapshots.onEach { lastOdometerKm = it.odometerKm }
        }
    }
}
