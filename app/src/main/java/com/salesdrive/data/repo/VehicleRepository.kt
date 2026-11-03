package com.salesdrive.data.repo

import com.salesdrive.data.db.Vehicle
import com.salesdrive.data.db.VehicleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 이 차의 정보(운행기록부 머리말: 차종·자동차등록번호·상호). 앱은 차 한 대에서만 돌므로 한 줄만 둔다.
 * 처음 주행할 때 빈 값으로 만들고, 운전자가 내 정보 화면에서 채운다.
 */
@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleDao,
) {
    private val createLock = Mutex()

    fun observe(): Flow<Vehicle?> = dao.observeFirst()

    suspend fun current(): Vehicle = createLock.withLock {
        dao.first() ?: Vehicle(plateNumber = "", model = null)
            .let { it.copy(id = dao.insert(it)) }
    }

    suspend fun save(plateNumber: String, model: String?, company: String?) {
        dao.update(
            current().copy(
                plateNumber = plateNumber.trim(),
                model = model?.trim()?.takeIf { it.isNotEmpty() },
                company = company?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}

/** 예전 버전이 자동으로 넣은 번호판 자리표시 */
internal const val LEGACY_UNREGISTERED_PLATE = "미등록"

/** 화면·CSV에 쓸 번호판. 아직 입력하지 않았으면 빈 문자열 */
val Vehicle.registeredPlate: String
    get() = plateNumber.takeUnless { it == LEGACY_UNREGISTERED_PLATE }.orEmpty()

val Vehicle?.needsSetup: Boolean
    get() = this == null || registeredPlate.isBlank()
