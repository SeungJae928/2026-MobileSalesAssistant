package com.salesdrive.export

import com.salesdrive.data.db.DriverProfile
import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.PurposeSource
import com.salesdrive.data.db.Trip
import com.salesdrive.data.db.TripStop
import com.salesdrive.data.db.Vehicle
import com.salesdrive.data.db.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LogbookCsvTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val at = Instant.parse("2026-11-02T00:30:00Z") // 서울 09:30 월요일

    private fun trip(id: Long, start: Double, end: Double?, purpose: Purpose) = Trip(
        id = id, vehicleId = 1, driverId = 1, startedAt = at, endedAt = end?.let { at.plusSeconds(3600) },
        odoStartKm = start, odoEndKm = end, startLat = null, startLng = null,
        purpose = purpose, purposeSource = PurposeSource.DEFAULT, naviDestination = "코엑스",
    )

    private fun build() = LogbookCsv.build(
        LogbookCsv.Input(
            from = LocalDate.of(2026, 11, 1),
            to = LocalDate.of(2026, 11, 30),
            vehicle = Vehicle(1, "12가3456", "아이오닉 5"),
            trips = listOf(
                trip(1, 100.0, 130.5, Purpose.BUSINESS),
                trip(2, 130.5, 140.5, Purpose.COMMUTE),
                trip(3, 140.5, 150.5, Purpose.PERSONAL),
                trip(4, 150.5, null, Purpose.BUSINESS), // 진행 중은 빠진다
            ),
            drivers = mapOf(1L to DriverProfile(1, "홍길동", "영업1팀")),
            visits = mapOf(1L to listOf(Visit(tripId = 1, rawUtterance = "원문", client = "A상사", contact = "김부장", summary = "납품, \"미팅\""))),
            stops = mapOf(1L to listOf(TripStop(tripId = 1, arrivedAt = at, departedAt = at, odoKm = 110.0, lat = null, lng = null, name = "망향휴게소"))),
            zone = zone,
        ),
    )

    @Test fun `rows follow the tax form with commute and business columns`() {
        val lines = build().removePrefix("﻿").split("\r\n")
        val header = lines.indexOf(LogbookCsv.HEADER.joinToString(","))
        assertTrue(header > 0)
        assertEquals(
            "2026-11-02(월),영업1팀,홍길동,100.0,130.5,30.5,,30.5,\"A상사 김부장 납품, \"\"미팅\"\" / 경유: 망향휴게소\"",
            lines[header + 1],
        )
        assertEquals("2026-11-02(월),영업1팀,홍길동,130.5,140.5,10.0,10.0,,코엑스 방문", lines[header + 2])
        assertEquals("2026-11-02(월),영업1팀,홍길동,140.5,150.5,10.0,,,개인 / 코엑스 방문", lines[header + 3])
        assertEquals("", lines[header + 4])
        assertEquals("과세기간 총주행거리(km),50.5", lines[header + 5])
        assertEquals("업무용 사용거리(km),40.5", lines[header + 6])
        assertEquals("업무사용비율(%),80.2", lines[header + 7])
    }

    @Test fun `starts with a BOM so Excel reads Korean`() {
        assertTrue(build().startsWith("﻿업무용승용차 운행기록부"))
    }
}
