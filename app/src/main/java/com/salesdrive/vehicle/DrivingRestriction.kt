package com.salesdrive.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 차량이 지금 주행 중 화면 제한(Distraction Optimization)을 요구하는지.
 * Business 카테고리는 주행 중 예외 대상이 아니므로, true면 최상위 화면에서 "기록 중" 화면으로 덮는다.
 */
interface DrivingRestriction {
    val requiresDistractionOptimization: StateFlow<Boolean>
}

/** AAOS CarUxRestrictionsManager 구독. 차량이 아니거나 서비스를 못 쓰면 항상 false(주행 상태로만 판단) */
class CarDrivingRestriction(context: Context) : DrivingRestriction {

    private val _required = MutableStateFlow(false)
    override val requiresDistractionOptimization: StateFlow<Boolean> = _required.asStateFlow()

    init {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            runCatching {
                val car = Car.createCar(context)
                val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                _required.value = manager.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
                manager.registerListener { restrictions ->
                    Log.i(TAG, "requiresDistractionOptimization=${restrictions.isRequiresDistractionOptimization}")
                    _required.value = restrictions.isRequiresDistractionOptimization
                }
            }.onFailure { Log.w(TAG, "ux restrictions unavailable", it) }
        }
    }

    private companion object {
        const val TAG = "DrivingRestriction"
    }
}
