package com.salesdrive.data

import com.salesdrive.data.db.Vehicle
import com.salesdrive.data.repo.needsSetup
import com.salesdrive.data.repo.registeredPlate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleInfoTest {

    @Test fun `예전 버전의 미등록 자리표시는 빈 번호판으로 본다`() {
        val legacy = Vehicle(plateNumber = "미등록", model = null)
        assertEquals("", legacy.registeredPlate)
        assertTrue(legacy.needsSetup)
    }

    @Test fun `번호판을 입력하면 안내가 사라진다`() {
        val car = Vehicle(plateNumber = "12가3456", model = "아이오닉 5")
        assertEquals("12가3456", car.registeredPlate)
        assertFalse(car.needsSetup)
    }

    @Test fun `차량 정보가 아직 없으면 입력을 안내한다`() {
        val none: Vehicle? = null
        assertTrue(none.needsSetup)
    }
}
