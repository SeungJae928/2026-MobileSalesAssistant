package com.salesdrive.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** 차량 위치. 출발·도착·경유 지점 좌표를 남기는 데만 쓴다(촘촘한 경로는 저장하지 않는다) */
interface LocationSource {
    /** 위치가 바뀔 때마다. 권한이 없거나 위치를 쓸 수 없으면 아무것도 내보내지 않는다 */
    val locations: Flow<GeoPoint>
}

object NoLocationSource : LocationSource {
    override val locations: Flow<GeoPoint> = emptyFlow()
}

/**
 * Android 표준 LocationManager. Pleos 차량에서는 Pleos 퓨전 위치 서비스(GNSS + 차량 신호)가
 * `fused` 제공자로 등록돼 있어 그 값을 그대로 받는다. 없으면 GPS 제공자로 대신한다.
 */
class AndroidLocationSource(
    private val context: Context,
    private val intervalMs: Long = 5_000,
) : LocationSource {

    @SuppressLint("MissingPermission") // granted()로 확인한다
    override val locations: Flow<GeoPoint> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !granted()) {
            Log.w(TAG, "위치 권한이 없거나 LocationManager가 없어 위치 없이 기록합니다")
            awaitClose { }
            return@callbackFlow
        }
        val provider = pickProvider(manager)
        if (provider == null) {
            Log.w(TAG, "사용할 수 있는 위치 제공자가 없습니다")
            awaitClose { }
            return@callbackFlow
        }
        val listener = LocationListener { trySend(it.toGeoPoint()) }
        manager.getLastKnownLocation(provider)?.let { trySend(it.toGeoPoint()) }
        manager.requestLocationUpdates(provider, intervalMs, 0f, listener, Looper.getMainLooper())
        Log.i(TAG, "위치 수신 시작: $provider")
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun granted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pickProvider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.firstOrNull { it in manager.allProviders && manager.isProviderEnabled(it) }
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val TAG = "LocationSource"
    }
}
