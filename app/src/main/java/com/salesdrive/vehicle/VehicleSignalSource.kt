package com.salesdrive.vehicle

import kotlinx.coroutines.flow.Flow

/** 앱이 차량 신호를 받는 유일한 통로. Pleos SDK 구현과 Fake 구현이 이것을 구현한다. */
interface VehicleSignalSource {
    val snapshots: Flow<VehicleSnapshot>
}
