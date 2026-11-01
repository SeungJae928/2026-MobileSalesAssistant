package com.salesdrive.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 운전자에게 한 문장을 읽어 준다. 다 읽거나 실패하면 돌아온다 (실패해도 예외를 던지지 않는다) */
interface VoicePrompter {
    suspend fun speak(text: String)
}

@Singleton
class PleosVoicePrompter @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoicePrompter {

    private val initLock = Mutex()
    private var engine: TextToSpeech? = null

    /** 지금 읽고 있는 문장의 완료 신호. 리스너는 한 번만 등록하고 이 값을 바꿔 가며 쓴다 */
    private var speaking: CompletableDeferred<Unit>? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { speaking?.complete(Unit) }
        override fun onStop() { speaking?.complete(Unit) }
        override fun onError(error: ErrorType) {
            Log.w(TAG, "speak error $error")
            speaking?.complete(Unit)
        }
    }

    override suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        val tts = ready() ?: return@withContext
        val done = CompletableDeferred<Unit>().also { speaking = it }
        try {
            tts.speak(text, true)
            if (withTimeoutOrNull(SPEAK_TIMEOUT_MS) { done.await() } == null) Log.w(TAG, "speak timeout")
        } catch (e: CancellationException) {
            tts.stop()
            throw e
        } finally {
            speaking = null
        }
    }

    /** 처음 부를 때 엔진을 초기화한다. 실패하면 null이고 다음 호출 때 다시 시도한다 */
    private suspend fun ready(): TextToSpeech? = initLock.withLock {
        engine?.let { return it }
        val tts = TextToSpeech(context, if (PleosCredentials.isSet) Mode.HYBRID else Mode.ON_DEVICE)
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            val result = CompletableDeferred<Boolean>()
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() { result.complete(true) }
                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "init failed $error")
                    result.complete(false)
                }
            })
            result.await()
        } == true
        if (!initialized) {
            tts.release()
            return null
        }
        tts.setLocale(Locale.KOREA)
        tts.addEventListener(events)
        if (PleosCredentials.isSet) registerApp(tts)
        engine = tts
        tts
    }

    /** 서버 합성용 앱 등록. 실패해도 HYBRID 모드가 On-device로 대신 읽으므로 기다리기만 한다 */
    private suspend fun registerApp(tts: TextToSpeech) {
        val connected = CompletableDeferred<Boolean>()
        tts.registerApp(PleosCredentials.clientId, PleosCredentials.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() { connected.complete(true) }
            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "registerApp failed $errorType")
                connected.complete(false)
            }
        })
        withTimeoutOrNull(INIT_TIMEOUT_MS) { connected.await() }
    }

    private companion object {
        const val TAG = "VoicePrompter"
        const val INIT_TIMEOUT_MS = 5_000L
        const val SPEAK_TIMEOUT_MS = 15_000L
    }
}
