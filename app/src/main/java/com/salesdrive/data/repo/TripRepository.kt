package com.salesdrive.data.repo

import com.salesdrive.data.db.DriverProfile
import com.salesdrive.data.db.ProfileDao
import com.salesdrive.data.db.StopDao
import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.PurposeSource
import com.salesdrive.data.db.Trip
import com.salesdrive.data.db.TripDao
import com.salesdrive.data.db.TripStop
import com.salesdrive.data.db.TripSummary
import com.salesdrive.data.db.Vehicle
import com.salesdrive.data.db.VehicleDao
import com.salesdrive.trip.TripStore
import com.salesdrive.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val vehicleDao: VehicleDao,
    private val profileDao: ProfileDao,
    private val stopDao: StopDao,
) : TripStore {

    fun observeRecent(limit: Int): Flow<List<TripSummary>> = tripDao.observeRecentSummaries(limit)

    override suspend fun openTrip(): Trip? = tripDao.findOpen()

    /** 목적은 출발 화면·음성으로 바로잡기 전까지 업무(BUSINESS) 기본값 */
    override suspend fun startTrip(at: VehicleSnapshot): Long = tripDao.insert(
        Trip(
            vehicleId = currentVehicleId(),
            driverId = currentDriverId(),
            startedAt = Instant.ofEpochMilli(at.timestampMs),
            odoStartKm = at.odometerKm,
            startLat = at.location?.lat,
            startLng = at.location?.lng,
            purpose = Purpose.BUSINESS,
            purposeSource = PurposeSource.DEFAULT,
        ),
    )

    override suspend fun endTrip(at: VehicleSnapshot): Long? {
        val open = tripDao.findOpen() ?: return null
        tripDao.update(
            open.copy(
                endedAt = Instant.ofEpochMilli(at.timestampMs),
                odoEndKm = at.odometerKm,
                endLat = at.location?.lat,
                endLng = at.location?.lng,
            ),
        )
        return open.id
    }

    override suspend fun addStop(arrived: VehicleSnapshot, departed: VehicleSnapshot) {
        val open = tripDao.findOpen() ?: return
        stopDao.insert(
            TripStop(
                tripId = open.id,
                arrivedAt = Instant.ofEpochMilli(arrived.timestampMs),
                departedAt = Instant.ofEpochMilli(departed.timestampMs),
                odoKm = arrived.odometerKm,
                lat = arrived.location?.lat,
                lng = arrived.location?.lng,
            ),
        )
    }

    override suspend fun lastEndedTripId(): Long? = tripDao.lastEndedId()

    // TODO: 설정 화면의 차량 등록, READY의 운전자 선택이 생기면 그 값을 쓴다. 그전까지는 기본값 하나를 만들어 둔다
    private suspend fun currentVehicleId(): Long =
        vehicleDao.all().firstOrNull()?.id ?: vehicleDao.insert(Vehicle(plateNumber = "미등록", model = null))

    private suspend fun currentDriverId(): Long =
        profileDao.first()?.id ?: profileDao.insert(DriverProfile(name = "기본 운전자", department = null))
}
