package com.salesdrive.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.salesdrive.navi.NaviSource
import com.salesdrive.voice.VisitInterviewer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 앱 화면이 닫혀도 주행 기록과 도착 시 음성 질문을 계속하는 포그라운드 서비스 */
@AndroidEntryPoint
class TripService : LifecycleService() {

    @Inject lateinit var recorder: TripRecorder
    @Inject lateinit var interviewer: VisitInterviewer
    @Inject lateinit var navi: NaviSource

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        navi.start()
        lifecycleScope.launch { recorder.run() }
        lifecycleScope.launch { interviewer.run() }
    }

    override fun onDestroy() {
        navi.stop()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "주행 기록", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("영업 이동 비서")
            .setContentText("주행을 자동으로 기록하고 있습니다")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, TripService::class.java))
    }
}
