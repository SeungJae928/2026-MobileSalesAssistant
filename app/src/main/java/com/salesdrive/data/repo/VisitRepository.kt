package com.salesdrive.data.repo

import android.util.Log
import com.salesdrive.data.db.PurposeSource
import com.salesdrive.data.db.TripDao
import com.salesdrive.data.db.Visit
import com.salesdrive.data.db.VisitDao
import com.salesdrive.voice.VisitParser
import com.salesdrive.voice.VisitStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitRepository @Inject constructor(
    private val visitDao: VisitDao,
    private val tripDao: TripDao,
    private val parser: VisitParser,
) : VisitStore {

    /** 음성 대화가 끝나도 파싱은 계속되도록 대화 코루틴과 따로 돈다 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** STT 원문을 먼저 저장하고, 거래처·요약 같은 구조화는 뒤에서 채운다 */
    override suspend fun saveRaw(tripId: Long, utterance: String): Long {
        val id = visitDao.insert(Visit(tripId = tripId, rawUtterance = utterance))
        scope.launch { organize(id) }
        return id
    }

    /**
     * 원문을 파싱해 방문 항목을 채운다. 발화에서 목적(출퇴근·개인)이 드러나면 주행 목적도 고친다.
     * 운전자가 화면에서 직접 고른 목적은 덮어쓰지 않는다.
     */
    suspend fun organize(visitId: Long) {
        val visit = visitDao.find(visitId) ?: return
        val trip = tripDao.find(visit.tripId)
        val parsed = parser.parse(visit.rawUtterance, trip?.naviDestination, LocalDate.now()) ?: return
        Log.i(TAG, "visit #$visitId parsed $parsed")
        visitDao.update(
            visit.copy(
                client = parsed.client,
                contact = parsed.contact,
                summary = parsed.summary,
                followUp = parsed.followUp,
                followUpDate = parsed.followUpDate,
            ),
        )
        val purpose = parsed.purpose ?: return
        if (trip != null && trip.purposeSource != PurposeSource.USER_TAP && trip.purpose != purpose) {
            tripDao.update(trip.copy(purpose = purpose, purposeSource = PurposeSource.VOICE))
        }
    }

    private companion object {
        const val TAG = "VisitRepository"
    }
}
