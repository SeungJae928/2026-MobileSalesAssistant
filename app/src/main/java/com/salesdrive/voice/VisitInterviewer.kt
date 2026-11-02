package com.salesdrive.voice

import android.util.Log
import com.salesdrive.trip.TripRecorder
import com.salesdrive.trip.VisitPrompt
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import javax.inject.Singleton

/** 방문 원문을 저장하는 곳. 테스트에서는 메모리 구현으로 바꾼다 */
interface VisitStore {
    suspend fun saveRaw(tripId: Long, utterance: String): Long
}

/**
 * 정차가 확정되면 먼저 방문을 기록할지 묻고([VisitPrompt.Confirm]), 운전자가 원하면 방문 내용을 받아 원문 그대로 저장한다.
 * - 확인 질문에 답이 없으면 아무것도 정하지 않고 화면 버튼에 맡긴다. 그대로 출발하면 경유 정차로 남는다.
 * - 방문 내용 질문에 무응답이면 한 번만 다시 묻고, 그래도 없으면 화면에서 입력하도록 남겨 둔다.
 * - 대화 도중 다시 출발하면([VisitPrompt.Cancel]) 진행 중인 대화를 바로 접는다.
 */
@Singleton
class VisitInterviewer @Inject constructor(
    private val recorder: TripRecorder,
    private val prompter: VoicePrompter,
    private val capture: SpeechCapture,
    private val visits: VisitStore,
) {
    /** 서비스 코루틴에서 호출. 새 요청이 오면 이전 대화는 취소된다 */
    suspend fun run() {
        recorder.visitPrompts.collectLatest { prompt ->
            when (prompt) {
                VisitPrompt.Confirm -> confirm()
                is VisitPrompt.Ask -> interview(prompt.tripId, prompt.answer, question(prompt))
                VisitPrompt.Cancel -> Unit
            }
        }
    }

    /** "응"이면 기록이 확정되어 곧 [VisitPrompt.Ask]가 오고, 이 대화는 그쪽으로 넘어간다 */
    internal suspend fun confirm() {
        prompter.speak(CONFIRM)
        val heard = capture.listen() ?: return
        when (ConfirmReply.classify(heard)) {
            ConfirmReply.YES -> recorder.confirmVisit()
            ConfirmReply.CONTENT -> recorder.confirmVisit(answer = heard)
            ConfirmReply.NO -> {
                recorder.declineVisit()
                prompter.speak(DECLINED)
            }
            null -> Unit
        }
    }

    internal suspend fun interview(tripId: Long?, answer: String? = null, question: String = QUESTION) {
        if (tripId != null) {
            val content = answer ?: askWithRetry(question)
            if (content != null) {
                val visitId = visits.saveRaw(tripId, content)
                Log.i(TAG, "visit #$visitId saved for trip #$tripId")
                prompter.speak(SAVED)
            } else {
                prompter.speak(LATER)
            }
        }
        recorder.finishVisitPrompt()
    }

    private suspend fun askWithRetry(question: String): String? {
        prompter.speak(question)
        listenForContent()?.let { return it }
        prompter.speak(RETRY)
        return listenForContent()
    }

    /** 군말·소음("어", "음")만 들렸으면 무응답과 같이 본다 */
    private suspend fun listenForContent(): String? = capture.listen()?.takeUnless(ConfirmReply::isFiller)

    internal companion object {
        private const val TAG = "VisitInterviewer"

        const val CONFIRM = "도착하셨네요. 방문 기록할까요?"
        const val DECLINED = "알겠습니다. 경유지로 보고 계속 기록할게요."

        const val QUESTION = "방문 내용 말씀해 주세요."
        const val RETRY = "방문 내용을 한 번 더 말씀해 주세요."
        const val SAVED = "기록했습니다."
        const val LATER = "나중에 화면에서 입력해 주세요."

        /** 내비가 도착을 알려 확인 질문 없이 온 경우 인사부터 한다 */
        fun question(ask: VisitPrompt.Ask): String = when {
            !ask.greet -> QUESTION
            ask.place != null -> "${ask.place} 도착하셨네요. $QUESTION"
            else -> "도착하셨네요. $QUESTION"
        }
    }
}
