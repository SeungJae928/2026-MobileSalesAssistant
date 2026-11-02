package com.salesdrive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.salesdrive.trip.TripService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 속도는 런타임 권한. 거부해도 기어로 대체 판단하므로 결과와 상관없이 기록을 시작한다.
    // 신호 구독은 서비스가 시작될 때 한 번 하므로, 권한 결과가 나온 뒤에 시작해야 속도가 반영된다
    private val requestSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        TripService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestSpeed.launch(CAR_SPEED)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    private companion object {
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
