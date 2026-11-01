package com.salesdrive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.salesdrive.ui.signal.SignalCheckScreen
import com.salesdrive.ui.signal.SignalCheckViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val signalCheck: SignalCheckViewModel by viewModels()

    // 속도는 런타임 권한. 거부해도 기어로 대체 판단하므로 결과와 상관없이 시작한다
    private val requestSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        signalCheck.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestSpeed.launch(CAR_SPEED)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SignalCheckScreen(signalCheck)
                }
            }
        }
    }

    private companion object {
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
