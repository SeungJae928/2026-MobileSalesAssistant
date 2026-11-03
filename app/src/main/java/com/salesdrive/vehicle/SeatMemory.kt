package com.salesdrive.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 차량 시트 메모리(운전자 프리셋) 불러오기.
 * Pleos Seat API에는 위치를 직접 지정하는 기능이 없고 메모리 저장 API도 없어서,
 * 운전자가 차량 기본 기능으로 저장해 둔 번호를 불러오기만 한다.
 */
interface SeatMemory {
    /** 운전석이 지원하는 메모리 번호 범위. 전동 시트 메모리가 없거나 권한이 없으면 null */
    suspend fun supportedSlots(): IntRange?

    /** 차량이 명령을 받았으면 true. 실제로 다 움직였는지는 알 수 없다 */
    suspend fun recall(slot: Int): Boolean
}

/** 차량이 아닌 기기(폰 에뮬레이터, 테스트) */
object NoSeatMemory : SeatMemory {
    override suspend fun supportedSlots(): IntRange? = null
    override suspend fun recall(slot: Int) = false
}

/** 국내 차량 기준 운전석(1열 왼쪽). 권한 pleos.car.permission.CAR_SEATS, CONTROL_CAR_SEATS */
class PleosSeatMemory(context: Context) : SeatMemory {

    private val seat by lazy { Vehicle(context).apply { initialize() }.getSeat() }
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)

    // SDK 연결이 비동기라 첫 호출이 응답 없이 버려질 수 있어 몇 번 다시 묻는다
    override suspend fun supportedSlots(): IntRange? = retry { done, fail ->
        seat.checkSeatMemoryCapabilities(
            { _, bounds ->
                val b = bounds[driver] ?: bounds.values.firstOrNull()
                done(b?.let { it.minValue..it.maxValue })
            },
            fail,
        )
    }

    override suspend fun recall(slot: Int): Boolean = retry<Boolean> { done, fail ->
        seat.selectSeatMemory(driver, slot) { e -> fail(e) }
        // 성공 콜백이 없는 API라, 잠시 오류가 없으면 받은 것으로 본다
        done(true)
    } ?: false

    private suspend fun <T> retry(call: (done: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): T? {
        repeat(ATTEMPTS) { attempt ->
            val result = withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Result<T?>> { cont ->
                    runCatching {
                        call({ if (cont.isActive) cont.resume(Result.success(it)) }, { if (cont.isActive) cont.resume(Result.failure(it)) })
                    }.onFailure { if (cont.isActive) cont.resume(Result.failure(it)) }
                }
            }
            when {
                result == null -> Log.w(TAG, "no response (attempt ${attempt + 1})")
                result.isFailure -> { Log.w(TAG, "failed: ${result.exceptionOrNull()?.message}"); return null }
                else -> return result.getOrNull()
            }
            delay(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val TAG = "SeatMemory"
        const val ATTEMPTS = 3
        const val TIMEOUT_MS = 3_000L
        const val RETRY_DELAY_MS = 1_000L
    }
}
