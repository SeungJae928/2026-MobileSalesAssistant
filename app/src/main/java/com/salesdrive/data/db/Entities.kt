package com.salesdrive.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

enum class Purpose { BUSINESS, COMMUTE, PERSONAL }

enum class PurposeSource { USER_TAP, VOICE, DEFAULT }

@Entity
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateNumber: String,
    val model: String?,
)

@Entity
data class DriverProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val department: String?,
    val seatPreset: String? = null,      // JSON {축: 값}, 지원 축만
    val comfortPreset: String? = null,   // JSON: 공조 온도, 핸들·미러 열선
    val presetCapturedAt: Instant? = null,
    val locationConsentAt: Instant? = null, // 위치정보 수집 동의 시각
)

/** 주행 1회 = 운행기록부 한 줄 */
@Entity(indices = [Index("startedAt"), Index("endedAt")])
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val driverId: Long,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val odoStartKm: Double,              // 계기판 값 그대로
    val odoEndKm: Double? = null,
    val startLat: Double?,
    val startLng: Double?,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val purpose: Purpose,
    val purposeSource: PurposeSource,
    val naviDestination: String? = null, // NaviHelper에서 받은 목적지명
    val note: String? = null,            // 기록부 비고란
)

/** 주행 도중 들른 경유 정차(휴게소·졸음쉼터 등). 주행을 끊지 않고 운행기록부 비고로 남긴다 */
@Entity(
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TripStop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val arrivedAt: Instant,
    val departedAt: Instant,
    val odoKm: Double,
    val lat: Double?,
    val lng: Double?,
    val name: String? = null,            // NaviHelper 경유지명
)

@Entity(
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index("client"), Index("followUpDate")],
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val rawUtterance: String,            // STT 원문은 항상 보존
    val client: String? = null,
    val contact: String? = null,
    val summary: String? = null,
    val followUp: String? = null,
    val followUpDate: LocalDate? = null,
)
