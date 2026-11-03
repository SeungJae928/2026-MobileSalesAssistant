package com.salesdrive.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.repo.LEGACY_DEFAULT_NAME
import com.salesdrive.data.repo.ProfileRepository
import com.salesdrive.data.repo.VehicleRepository
import com.salesdrive.data.repo.registeredPlate
import com.salesdrive.profile.SeatRecall
import com.salesdrive.profile.SeatRestorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUi(
    val loaded: Boolean = false,
    val name: String = "",
    val department: String = "",
    val seatMemorySlot: Int? = null,
    val plateNumber: String = "",
    val model: String = "",
    val company: String = "",
    /** null이면 아직 조회 중이거나([checkingSeat]) 이 차량이 시트 메모리를 지원하지 않음 */
    val supportedSlots: IntRange? = null,
    val checkingSeat: Boolean = true,
    val lastRecall: SeatRecall? = null,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val restorer: SeatRestorer,
    private val vehicles: VehicleRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUi())
    val ui: StateFlow<ProfileUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val me = profiles.me()
            val car = vehicles.current()
            _ui.update {
                it.copy(
                    loaded = true,
                    name = me.name.takeUnless { it == LEGACY_DEFAULT_NAME }.orEmpty(),
                    department = me.department.orEmpty(),
                    seatMemorySlot = me.seatMemorySlot,
                    plateNumber = car.registeredPlate,
                    model = car.model.orEmpty(),
                    company = car.company.orEmpty(),
                )
            }
        }
        viewModelScope.launch {
            val slots = restorer.supportedSlots()
            _ui.update { it.copy(supportedSlots = slots, checkingSeat = false) }
        }
        viewModelScope.launch { restorer.last.collect { r -> _ui.update { it.copy(lastRecall = r) } } }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v, message = null) }
    fun setDepartment(v: String) = _ui.update { it.copy(department = v, message = null) }
    fun setSlot(v: Int?) = _ui.update { it.copy(seatMemorySlot = v, message = null) }
    fun setPlate(v: String) = _ui.update { it.copy(plateNumber = v, message = null) }
    fun setModel(v: String) = _ui.update { it.copy(model = v, message = null) }
    fun setCompany(v: String) = _ui.update { it.copy(company = v, message = null) }

    fun save() {
        val u = _ui.value
        if (u.name.isBlank()) {
            _ui.update { it.copy(message = "성명을 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            profiles.save(u.name, u.department, u.seatMemorySlot)
            vehicles.save(u.plateNumber, u.model, u.company)
            _ui.update { it.copy(message = "저장했어요") }
        }
    }

    fun recallNow() {
        val slot = _ui.value.seatMemorySlot ?: return
        viewModelScope.launch { restorer.recallNow(slot) }
    }
}
