package com.salesdrive.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TripDao {
    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip)

    /** 앱이 죽었다 살아났을 때 이어서 기록할 진행 중 주행 */
    @Query("SELECT * FROM Trip WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun findOpen(): Trip?

    @Query("SELECT id FROM Trip WHERE endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastEndedId(): Long?

    @Query("SELECT * FROM Trip WHERE startedAt >= :from AND startedAt < :until ORDER BY startedAt")
    fun observeBetween(from: Instant, until: Instant): Flow<List<Trip>>

    @Query("SELECT * FROM Trip ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Trip>>

    @Query(
        "SELECT Trip.*, (SELECT COUNT(*) FROM TripStop WHERE TripStop.tripId = Trip.id) AS stopCount " +
            "FROM Trip ORDER BY startedAt DESC LIMIT :limit",
    )
    fun observeRecentSummaries(limit: Int): Flow<List<TripSummary>>
}

/** 목록 한 줄: 주행과 경유 정차 횟수 */
data class TripSummary(
    @Embedded val trip: Trip,
    val stopCount: Int,
)

@Dao
interface StopDao {
    @Insert suspend fun insert(stop: TripStop): Long

    @Query("SELECT * FROM TripStop WHERE tripId = :tripId ORDER BY arrivedAt")
    suspend fun findByTrip(tripId: Long): List<TripStop>
}

@Dao
interface VisitDao {
    @Insert suspend fun insert(visit: Visit): Long
    @Update suspend fun update(visit: Visit)

    @Query("SELECT * FROM Visit WHERE tripId = :tripId")
    suspend fun findByTrip(tripId: Long): List<Visit>

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
