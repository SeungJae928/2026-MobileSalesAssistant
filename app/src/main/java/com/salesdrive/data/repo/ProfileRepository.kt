package com.salesdrive.data.repo

import com.salesdrive.data.db.DriverProfile
import com.salesdrive.data.db.ProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 내 정보. 운전자 구분은 차량(AAOS) 사용자 프로필이 하므로, 프로필마다 따로 있는 이 앱 DB에는 한 줄만 둔다.
 * 차량 프로필 이름은 시스템 권한(QUERY_USERS 등)이 있어야 읽을 수 있어, 성명·부서는 운전자가 내 정보 화면에서 입력한다.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
) {
    private val createLock = Mutex()

    fun observe(): Flow<DriverProfile?> = dao.observeFirst()

    suspend fun me(): DriverProfile = createLock.withLock {
        dao.first() ?: DriverProfile(name = "", department = null)
            .let { it.copy(id = dao.insert(it)) }
    }

    suspend fun save(name: String, department: String?, seatMemorySlot: Int?) {
        val current = me()
        dao.update(
            current.copy(
                name = name.trim(),
                department = department?.trim()?.takeIf { it.isNotEmpty() },
                seatMemorySlot = seatMemorySlot,
            ),
        )
    }
}

/** 운행기록부 성명·부서가 비어 있으면 홈에서 입력을 안내한다. 예전 버전이 만든 기본 이름도 빈 것으로 본다 */
val DriverProfile?.needsSetup: Boolean
    get() = this == null || name.isBlank() || name == LEGACY_DEFAULT_NAME || department.isNullOrBlank()

internal const val LEGACY_DEFAULT_NAME = "기본 운전자"
