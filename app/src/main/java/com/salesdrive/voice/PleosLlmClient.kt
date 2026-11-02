package com.salesdrive.voice

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.OnServerConnectionListener
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pleos LLM SDK. local.properties에 클라이언트 키가 있으면 서버 모드, 없으면 On-device 모드.
 * - 서비스 연결은 initialize() 뒤 비동기로 이뤄지고 연결 전 요청은 SDK가 조용히 버리므로, 서비스 시작 때 [warmUp]으로 미리 연결한다.
 * - Pleos 에뮬레이터는 On-device LLM이 꺼져 있어 요청이 응답 없이 묻힌다. 한 번 응답이 없으면 [BACKOFF_MS] 동안은 바로 포기해
 *   규칙 기반 파서로 넘어가게 한다.
 */
@Singleton
class PleosLlmClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmClient {

    private var llm: LLM? = null
    private var unavailableUntil = 0L

    private val server: Boolean get() = PleosCredentials.isSet

    fun warmUp() {
        runCatching { engine() }.onFailure { Log.w(TAG, "init failed", it) }
    }

    override suspend fun generate(prompt: String): String? = withContext(Dispatchers.Main) {
        if (System.currentTimeMillis() < unavailableUntil) return@withContext null
        val engine = runCatching { engine() }.getOrElse {
            Log.w(TAG, "init failed", it)
            return@withContext null
        }
        request(engine, prompt).also {
            if (it == null) unavailableUntil = System.currentTimeMillis() + BACKOFF_MS
        }
    }

    private suspend fun request(engine: LLM, prompt: String): String? {
        val reply = StringBuilder()
        val done = CompletableDeferred<Boolean>()
        val started = System.currentTimeMillis()
        engine.generateContent(prompt, object : ResultListener {
            override fun onResponse(text: String, isFinal: Boolean) {
                Log.d(TAG, "response final=$isFinal len=${text.length}")
                reply.append(text)
                if (isFinal) done.complete(true)
            }

            override fun onError(error: String) {
                Log.w(TAG, "error $error")
                done.complete(false)
            }
        })
        val ok = withTimeoutOrNull(TIMEOUT_MS) { done.await() }
        Log.i(TAG, "generate (${if (server) "server" else "on-device"}) ok=$ok in ${System.currentTimeMillis() - started}ms")
        return reply.toString().takeIf { ok != false && it.isNotBlank() }
    }

    private fun engine(): LLM = llm ?: LLM(context, if (server) Mode.SERVER else Mode.ON_DEVICE).also {
        it.initialize()
        if (server) {
            it.registerApp(PleosCredentials.clientId, PleosCredentials.clientSecret, object : OnServerConnectionListener {
                override fun onConnected() { Log.i(TAG, "server connected") }
                override fun onFailed(reason: String) { Log.w(TAG, "server connection failed $reason") }
            })
        }
        llm = it
    }

    private companion object {
        const val TAG = "PleosLlm"
        const val TIMEOUT_MS = 20_000L
        const val BACKOFF_MS = 10 * 60_000L
    }
}
