package com.salesdrive.export

import com.salesdrive.data.Mileage
import com.salesdrive.data.db.DriverProfile
import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.Trip
import com.salesdrive.data.db.TripStop
import com.salesdrive.data.db.Vehicle
import com.salesdrive.data.db.Visit
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 국세청 「업무용승용차 운행기록부」 양식(법인세법 시행규칙 별지 제29호) 순서의 CSV.
 * 주행 1회가 한 줄이고, 비고에는 방문 요약(없으면 원문)·경유지·목적을 적는다.
 * 양식 항목은 제출 전에 국세청 고시 원문과 대조할 것.
 */
object LogbookCsv {

    data class Input(
        val from: LocalDate,
        /** 포함 마지막 날 */
        val to: LocalDate,
        val vehicle: Vehicle?,
        val company: String? = null,
        val trips: List<Trip>,
        val drivers: Map<Long, DriverProfile>,
        val visits: Map<Long, List<Visit>>,
        val stops: Map<Long, List<TripStop>>,
        val zone: ZoneId,
    )

    val HEADER = listOf(
        "사용일자(요일)", "부서", "성명",
        "주행 전 계기판의 거리(km)", "주행 후 계기판의 거리(km)", "주행거리(km)",
        "출·퇴근용(km)", "일반 업무용(km)", "비고",
    )

    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun build(input: Input): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("업무용승용차 운행기록부")
        rows += listOf("과세기간", "${DAY.format(input.from)} ~ ${DAY.format(input.to)}")
        input.company?.let { rows += listOf("상호", it) }
        rows += listOf("차종", input.vehicle?.model.orEmpty(), "자동차등록번호", input.vehicle?.plateNumber.orEmpty())
        rows.add(emptyList())
        rows += HEADER

        val ended = input.trips.filter { it.endedAt != null && it.odoEndKm != null }.sortedBy { it.startedAt }
        ended.forEach { trip ->
            val day = trip.startedAt.atZone(input.zone).toLocalDate()
            val driver = input.drivers[trip.driverId]
            val km = Mileage.distanceKm(trip)
            rows += listOf(
                "${DAY.format(day)}(${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})",
                driver?.department.orEmpty(),
                driver?.name.orEmpty(),
                num(trip.odoStartKm),
                num(trip.odoEndKm!!),
                num(km),
                if (trip.purpose == Purpose.COMMUTE) num(km) else "",
                if (trip.purpose == Purpose.BUSINESS) num(km) else "",
                note(trip, input.visits[trip.id].orEmpty(), input.stops[trip.id].orEmpty()),
            )
        }

        val t = Mileage.totals(ended)
        rows.add(emptyList())
        rows += listOf("과세기간 총주행거리(km)", num(t.totalKm))
        rows += listOf("업무용 사용거리(km)", num(t.businessKm + t.commuteKm))
        rows += listOf("업무사용비율(%)", t.businessRatio?.let { num(it * 100) } ?: "")
        // 엑셀이 한글을 깨뜨리지 않도록 BOM을 붙인다
        return "﻿" + rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::escape) } + "\r\n"
    }

    /** 비고: 방문 요약(거래처 포함) · 경유지 · 개인 용도 표시 */
    fun note(trip: Trip, visits: List<Visit>, stops: List<TripStop>): String {
        val parts = mutableListOf<String>()
        if (trip.purpose == Purpose.PERSONAL) parts += "개인"
        trip.note?.takeIf(String::isNotBlank)?.let(parts::add)
        visits.forEach { v ->
            val what = v.summary ?: v.rawUtterance
            parts += listOfNotNull(v.client, v.contact, what).joinToString(" ")
        }
        if (visits.isEmpty()) trip.naviDestination?.let { parts += "$it 방문" }
        if (stops.isNotEmpty()) parts += "경유: " + stops.joinToString("·") { it.name ?: "정차" }
        return parts.joinToString(" / ")
    }

    private fun num(km: Double) = "%.1f".format(Locale.ROOT, km)

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
