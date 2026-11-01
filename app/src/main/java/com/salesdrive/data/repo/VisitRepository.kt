package com.salesdrive.data.repo

import com.salesdrive.data.db.Visit
import com.salesdrive.data.db.VisitDao
import com.salesdrive.voice.VisitStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitRepository @Inject constructor(
    private val visitDao: VisitDao,
) : VisitStore {

    /** STT 원문을 먼저 저장한다. 거래처·요약 같은 구조화는 이후 파서가 채운다 */
    override suspend fun saveRaw(tripId: Long, utterance: String): Long =
        visitDao.insert(Visit(tripId = tripId, rawUtterance = utterance))
}
