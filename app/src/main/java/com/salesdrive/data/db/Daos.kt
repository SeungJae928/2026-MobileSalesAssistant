package com.salesdrive.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface TripDao {
    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip)

    /** 앱이 죽었다 살아났을 때 이어서 기록할 진행 중 주행 */
    @Query("SELECT * FROM Trip WHERE id = :id")
    suspend fun find(id: Long): Trip?

    @Query("SELECT * FROM Trip WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun findOpen(): Trip?

    @Query("SELECT id FROM Trip WHERE endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastEndedId(): Long?

    @Query("SELECT * FROM Trip WHERE startedAt >= :from AND startedAt < :until ORDER BY startedAt")
    fun observeBetween(from: Instant, until: Instant): Flow<List<Trip>>

    @Query("SELECT * FROM Trip ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Trip>>

    @Query("SELECT * FROM Trip WHERE id = :id")
    fun observe(id: Long): Flow<Trip?>

    /** 운행기록부 내보내기: 기간 안에 끝난 주행 */
    @Query("SELECT * FROM Trip WHERE startedAt >= :from AND startedAt < :until AND endedAt IS NOT NULL ORDER BY startedAt")
    suspend fun endedBetween(from: Instant, until: Instant): List<Trip>

    @Query(
        "SELECT Trip.*, " +
            "(SELECT COUNT(*) FROM TripStop WHERE TripStop.tripId = Trip.id) AS stopCount, " +
            "(SELECT COALESCE(summary, rawUtterance) FROM Visit WHERE Visit.tripId = Trip.id ORDER BY id LIMIT 1) AS visitNote, " +
            "(SELECT client FROM Visit WHERE Visit.tripId = Trip.id AND client IS NOT NULL ORDER BY id LIMIT 1) AS client " +
            "FROM Trip ORDER BY startedAt DESC LIMIT :limit",
    )
    fun observeRecentSummaries(limit: Int): Flow<List<TripSummary>>

    @Query(
        "SELECT Trip.*, " +
            "(SELECT COUNT(*) FROM TripStop WHERE TripStop.tripId = Trip.id) AS stopCount, " +
            "(SELECT COALESCE(summary, rawUtterance) FROM Visit WHERE Visit.tripId = Trip.id ORDER BY id LIMIT 1) AS visitNote, " +
            "(SELECT client FROM Visit WHERE Visit.tripId = Trip.id AND client IS NOT NULL ORDER BY id LIMIT 1) AS client " +
            "FROM Trip WHERE startedAt >= :from AND startedAt < :until ORDER BY startedAt DESC",
    )
    fun observeSummariesBetween(from: Instant, until: Instant): Flow<List<TripSummary>>
}

/** 목록 한 줄: 주행, 경유 정차 횟수, 첫 방문의 요약(없으면 원문)과 거래처 */
data class TripSummary(
    @Embedded val trip: Trip,
    val stopCount: Int,
    val visitNote: String? = null,
    val client: String? = null,
)

@Dao
interface StopDao {
    @Insert suspend fun insert(stop: TripStop): Long

    @Query("SELECT * FROM TripStop WHERE tripId = :tripId ORDER BY arrivedAt")
    suspend fun findByTrip(tripId: Long): List<TripStop>

    @Query("SELECT * FROM TripStop WHERE tripId = :tripId ORDER BY arrivedAt")
    fun observeByTrip(tripId: Long): Flow<List<TripStop>>
}

@Dao
interface VisitDao {
    @Insert suspend fun insert(visit: Visit): Long
    @Update suspend fun update(visit: Visit)

    @Query("SELECT * FROM Visit WHERE id = :id")
    suspend fun find(id: Long): Visit?

    @Query("SELECT * FROM Visit WHERE tripId = :tripId")
    suspend fun findByTrip(tripId: Long): List<Visit>

    @Query("SELECT * FROM Visit WHERE tripId = :tripId ORDER BY id")
    fun observeByTrip(tripId: Long): Flow<List<Visit>>

    /** 홈 화면: 오늘 이후 후속 일정 */
    @Query("SELECT * FROM Visit WHERE followUpDate >= :from ORDER BY followUpDate, id LIMIT :limit")
    fun observeUpcoming(from: LocalDate, limit: Int): Flow<List<Visit>>

    /** "지난번 A사 미팅 알려줘": 해당 거래처 최근 방문 몇 건만 LLM에 넘긴다 */
    @Query("SELECT * FROM Visit WHERE client = :client ORDER BY id DESC LIMIT :limit")
    suspend fun recentByClient(client: String, limit: Int): List<Visit>
}

@Dao
interface ProfileDao {
    @Insert suspend fun insert(profile: DriverProfile): Long
    @Update suspend fun update(profile: DriverProfile)

    @Query("SELECT * FROM DriverProfile ORDER BY name")
    fun observeAll(): Flow<List<DriverProfile>>

    @Query("SELECT * FROM DriverProfile ORDER BY id LIMIT 1")
    suspend fun first(): DriverProfile?
}

@Dao
interface VehicleDao {
    @Insert suspend fun insert(vehicle: Vehicle): Long

    @Query("SELECT * FROM Vehicle")
    suspend fun all(): List<Vehicle>
}
