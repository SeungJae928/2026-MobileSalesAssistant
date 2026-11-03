package com.salesdrive.data.repo

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val vehicleDao: VehicleDao,
    private val profiles: ProfileRepository,
    private val stopDao: StopDao,
) : TripStore {

    fun observeRecent(limit: Int): Flow<List<TripSummary>> = tripDao.observeRecentSummaries(limit)

    fun observeBetween(from: Instant, until: Instant): Flow<List<TripSummary>> = tripDao.observeSummariesBetween(from, until)

    fun observeTrip(id: Long): Flow<Trip?> = tripDao.observe(id)

    fun observeStops(tripId: Long): Flow<List<TripStop>> = stopDao.observeByTrip(tripId)

    suspend fun endedBetween(from: Instant, until: Instant): List<Trip> = tripDao.endedBetween(from, until)

    private val _nextPurpose = MutableStateFlow<Purpose?>(null)

    /** 출발 전 화면에서 고른 이번 주행 목적. 주행이 시작되면 그 주행에 들어가고 비워진다 */
    val nextPurpose: StateFlow<Purpose?> = _nextPurpose.asStateFlow()

    fun chooseNextPurpose(purpose: Purpose?) {
        _nextPurpose.value = purpose
    }

    /** 기록 확인 화면에서 운전자가 직접 고친 목적. 이후 음성 파싱이 덮어쓰지 않는다 */
    suspend fun setPurpose(tripId: Long, purpose: Purpose) {
        val trip = tripDao.find(tripId) ?: return
        tripDao.update(trip.copy(purpose = purpose, purposeSource = PurposeSource.USER_TAP))
    }

    override suspend fun openTrip(): Trip? = tripDao.findOpen()

    /** 출발 전에 고른 목적이 없으면 업무(BUSINESS) 기본값. 도착 후 음성으로 바로잡힐 수 있다 */
    override suspend fun startTrip(at: VehicleSnapshot): Long {
        val chosen = _nextPurpose.getAndUpdate { null }
        return tripDao.insert(
            Trip(
                vehicleId = currentVehicleId(),
                driverId = profiles.me().id,
                startedAt = Instant.ofEpochMilli(at.timestampMs),
                odoStartKm = at.odometerKm,
                startLat = at.location?.lat,
                startLng = at.location?.lng,
                purpose = chosen ?: Purpose.BUSINESS,
                purposeSource = if (chosen != null) PurposeSource.USER_TAP else PurposeSource.DEFAULT,
            ),
        )
    }

    override suspend fun endTrip(at: VehicleSnapshot, destination: String?): Long? {
        val open = tripDao.findOpen() ?: return null
        tripDao.update(
            open.copy(
                endedAt = Instant.ofEpochMilli(at.timestampMs),
                odoEndKm = at.odometerKm,
                endLat = at.location?.lat,
                endLng = at.location?.lng,
                naviDestination = destination ?: open.naviDestination,
            ),
        )
        return open.id
    }

    override suspend fun addStop(arrived: VehicleSnapshot, departed: VehicleSnapshot, name: String?) {
        val open = tripDao.findOpen() ?: return
        stopDao.insert(
            TripStop(
                tripId = open.id,
                arrivedAt = Instant.ofEpochMilli(arrived.timestampMs),
                departedAt = Instant.ofEpochMilli(departed.timestampMs),
                odoKm = arrived.odometerKm,
                lat = arrived.location?.lat,
                lng = arrived.location?.lng,
                name = name,
            ),
        )
    }

    override suspend fun lastEndedTripId(): Long? = tripDao.lastEndedId()

    // TODO: 설정 화면에 차량 등록이 생기면 그 값을 쓴다. 그전까지는 기본값 하나를 만들어 둔다
    private suspend fun currentVehicleId(): Long =
        vehicleDao.all().firstOrNull()?.id ?: vehicleDao.insert(Vehicle(plateNumber = "미등록", model = null))
}
