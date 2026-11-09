package com.salesdrive.voice

import java.time.LocalTime

/** "오후 3시", "세 시 반", "14:30" 같은 표현을 시각으로 바꾼다. 시각이 없으면 null */
internal object TimeFinder {
    private val HOUR_WORDS = mapOf(
        "한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6,
        "일곱" to 7, "여덟" to 8, "아홉" to 9, "열" to 10, "열한" to 11, "열두" to 12,
    )
    private val AM = setOf("오전", "아침", "새벽")
    private val PM = setOf("오후", "낮", "저녁", "밤")

    private val hourPattern = "\\d{1,2}|" + HOUR_WORDS.keys.sortedByDescending(String::length).joinToString("|")

    // "두 시간 뒤", "시작"은 시각이 아니다
    private val SPOKEN = Regex(
        "(?<![가-힣\\d])(?:(오전|아침|새벽|오후|낮|저녁|밤)\\s*)?($hourPattern)\\s*시(?!간|작)(?:\\s*(?:(\\d{1,2})\\s*분|(반)))?",
    )
    private val CLOCK = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])")

    /** 오전·오후를 말하지 않은 1~6시는 업무 시간인 오후로 본다 */
    private const val LAST_ASSUMED_PM_HOUR = 6

    fun find(text: String): LocalTime? {
        CLOCK.find(text)?.let { m -> return timeOf(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        val m = SPOKEN.find(text) ?: return null
        val spoken = m.groupValues[2]
        val hour = spoken.toIntOrNull() ?: HOUR_WORDS.getValue(spoken)
        val minute = if (m.groupValues[4].isNotEmpty()) 30 else m.groupValues[3].toIntOrNull() ?: 0
        return timeOf(to24Hour(m.groupValues[1], hour), minute)
    }

    private fun to24Hour(marker: String, hour: Int): Int = when {
        marker in AM -> if (hour == 12) 0 else hour
        marker == "밤" && hour == 12 -> 0
        marker in PM -> if (hour < 12) hour + 12 else hour
        hour in 1..LAST_ASSUMED_PM_HOUR -> hour + 12
        else -> hour
    }

    private fun timeOf(hour: Int, minute: Int): LocalTime? =
        if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}
