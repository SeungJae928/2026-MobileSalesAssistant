package com.salesdrive.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 운전자의 한 마디를 받아 텍스트로 돌려준다. 말이 없거나 인식에 실패하면 null */
interface SpeechCapture {
    suspend fun listen(): String?
}

@Singleton
class PleosSpeechCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechCapture {

    private val initLock = Mutex()
    private var engine: SpeechToText? = null

    /** 인식 한 번의 진행 상황. 리스너는 한 번만 등록하고 지금 세션에만 결과를 넣는다 */
    private class Session {
        val heard = CompletableDeferred<Unit>()
        val result = CompletableDeferred<String?>()
        var partial: String? = null
    }

    private var session: Session? = null

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            val s = session ?: return
            if (text.isNotBlank()) {
                s.partial = text
                s.heard.complete(Unit)
            }
            if (isFinal) s.result.complete(s.partial)
        }
        override fun onEndedRecognition() { session?.let { it.result.complete(it.partial) } }
        override fun onError(error: ErrorType) {
            Log.w(TAG, "recognition error $error")
            session?.let { it.result.complete(it.partial) }
        }
        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    /**
     * [NO_SPEECH_MS] 안에 말이 시작되지 않으면 무응답(null).
     * 말이 시작되면 최종 결과를 [MAX_LISTEN_MS]까지 기다리고, 그래도 안 끝나면 마지막 중간 결과를 쓴다.
     */
    override suspend fun listen(): String? = withContext(Dispatchers.Main) {
        val stt = ready() ?: return@withContext null
        val s = Session().also { session = it }
        try {
            stt.request()
            val started = withTimeoutOrNull(NO_SPEECH_MS) {
                select<Unit> {
                    s.heard.onAwait { }
                    s.result.onAwait { }
                }
            }
            if (started == null) {
                null
            } else {
                withTimeoutOrNull(MAX_LISTEN_MS) { s.result.await() } ?: s.partial
            }
        } finally {
            stt.stop()
            session = null
        }
    }

    private suspend fun ready(): SpeechToText? = initLock.withLock {
        engine?.let { return it }
        val stt = SpeechToText(context, if (PleosCredentials.isSet) Mode.HYBRID else Mode.ON_DEVICE)
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            val result = CompletableDeferred<Boolean>()
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() { result.complete(true) }
                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "init failed $error")
                    result.complete(false)
                }
            })
            result.await()
        } == true
        if (!initialized) {
            stt.release()
            return null
        }
        stt.setLocale(Locale.KOREA)
        stt.addListener(results)
        if (PleosCredentials.isSet) registerApp(stt)
        engine = stt
        stt
    }

    private suspend fun registerApp(stt: SpeechToText) {
        val connected = CompletableDeferred<Boolean>()
        stt.registerApp(PleosCredentials.clientId, PleosCredentials.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() { connected.complete(true) }
            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "registerApp failed $errorType")
                connected.complete(false)
            }
        })
        withTimeoutOrNull(INIT_TIMEOUT_MS) { connected.await() }
    }

    private companion object {
        const val TAG = "SpeechCapture"
        const val INIT_TIMEOUT_MS = 5_000L
        const val NO_SPEECH_MS = 8_000L
        const val MAX_LISTEN_MS = 20_000L
    }
}
