package com.salesdrive.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.ui.common.SectionCard
import com.salesdrive.ui.common.TIME
import com.salesdrive.ui.common.ZONE

/** 내 정보: 운행기록부 성명·부서와 시트 메모리 번호. 운전자 구분은 차량 프로필이 한다 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionCard("운행기록부에 들어갈 정보") {
                OutlinedTextField(
                    ui.name, viewModel::setName, Modifier.fillMaxWidth(),
                    label = { Text("성명") }, singleLine = true, enabled = ui.loaded,
                )
                OutlinedTextField(
                    ui.department, viewModel::setDepartment, Modifier.fillMaxWidth(),
                    label = { Text("부서") }, singleLine = true, enabled = ui.loaded,
                )
            }
            SectionCard("시트 자세") {
                SeatSlotPicker(ui, viewModel::setSlot)
                if (ui.seatMemorySlot != null && ui.supportedSlots != null) {
                    OutlinedButton(onClick = viewModel::recallNow) { Text("지금 ${ui.seatMemorySlot}번 불러오기") }
                }
                ui.lastRecall?.let { r ->
                    val time = TIME.format(r.at.atZone(ZONE))
                    Text(
                        if (r.ok) "$time  운전자 프리셋 ${r.slot}번을 불러왔어요" else "$time  ${r.reason}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = viewModel::save, enabled = ui.loaded) { Text("저장", style = MaterialTheme.typography.titleMedium) }
                ui.message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionCard("차량 프로필") {
                Note("운전자는 차량 화면 오른쪽 위 프로필로 구분해요. 별도 로그인은 없어요.")
                Note("주행·방문 기록과 내보낸 운행기록부는 차량 프로필마다 따로 저장되고, 다른 프로필에서는 보이지 않아요.")
                Note("차량 프로필을 지우면 그 기록도 함께 지워져요. 지우기 전에 운행기록부를 내보내 두세요.")
            }
            SectionCard("시트 메모리란?") {
                Note("차량 설정 > 시트 > 운전자 프리셋 1·2번에 자세를 저장해 두고, 여기서 그 번호를 고르세요.")
                Note("시동을 켜면 기어 P·정차 상태에서 한 번 불러와요. 주행 중에는 시트를 움직이지 않아요.")
            }
        }
    }
}

@Composable
private fun SeatSlotPicker(ui: ProfileUi, onSelect: (Int?) -> Unit) {
    val slots = ui.supportedSlots
    when {
        ui.checkingSeat -> Note("차량 시트 메모리를 확인하고 있어요…")
        slots == null -> Note("이 차량에서는 시트 메모리를 쓸 수 없어요. 운행 기록은 그대로 동작해요.")
        else -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SlotButton("사용 안 함", ui.seatMemorySlot == null) { onSelect(null) }
            slots.forEach { n -> SlotButton("${n}번", ui.seatMemorySlot == n) { onSelect(n) } }
        }
    }
}

@Composable
private fun SlotButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label, style = MaterialTheme.typography.titleMedium) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
