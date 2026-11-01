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
 * 도착이 확정되면 방문 내용을 한 번 묻고, 답을 원문 그대로 저장한다.
 * 무응답이면 한 번만 다시 묻고, 그래도 없으면 정차 중 화면에서 입력하도록 남겨 둔다.
 * 질문 도중 다시 출발하면([VisitPrompt.Cancel]) 진행 중인 질문을 바로 접는다.
 */
@Singleton
class VisitInterviewer @Inject constructor(
    private val recorder: TripRecorder,
    private val prompter: VoicePrompter,
    private val capture: SpeechCapture,
    private val visits: VisitStore,
) {
    /** 서비스 코루틴에서 호출. 새 요청이 오면 이전 질문은 취소된다 */
    suspend fun run() {
        recorder.visitPrompts.collectLatest { prompt ->
            if (prompt is VisitPrompt.Ask) interview(prompt.tripId)
        }
    }

    internal suspend fun interview(tripId: Long?) {
        if (tripId != null) {
            val answer = askWithRetry()
            if (answer != null) {
                val visitId = visits.saveRaw(tripId, answer)
                Log.i(TAG, "visit #$visitId saved for trip #$tripId")
                prompter.speak(SAVED)
            } else {
                prompter.speak(LATER)
            }
        }
        recorder.finishVisitPrompt()
    }

    private suspend fun askWithRetry(): String? {
        prompter.speak(QUESTION)
        capture.listen()?.let { return it }
        prompter.speak(RETRY)
        return capture.listen()
    }

    internal companion object {
        private const val TAG = "VisitInterviewer"

        // TODO: NaviHelper 목적지가 있으면 "OO빌딩 도착하셨네요. 방문 내용 말씀해 주세요."
        const val QUESTION = "도착하셨네요. 방문 내용 말씀해 주세요."
        const val RETRY = "방문 내용을 한 번 더 말씀해 주세요."
        const val SAVED = "기록했습니다."
        const val LATER = "나중에 화면에서 입력해 주세요."
    }
}
