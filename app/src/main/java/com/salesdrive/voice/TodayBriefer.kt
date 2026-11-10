package com.salesdrive.voice

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.salesdrive.data.repo.ScheduleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** 시동을 걸면 오늘 남은 일정을 읽어 준다. 하루에 시동을 여러 번 걸어도 안내는 한 번뿐이다 */
@Singleton
class TodayBriefer @Inject constructor(
    @ApplicationContext context: Context,
    private val schedules: ScheduleRepository,
    private val voice: VoicePrompter,
) {
    private val prefs = context.getSharedPreferences("today_briefing", Context.MODE_PRIVATE)

    suspend fun announceOnce() {
        val today = LocalDate.now()
        if (prefs.getString(KEY_LAST_DATE, null) == today.toString()) return
        val text = Briefing.today(schedules.eventsOn(today), LocalTime.now()) ?: return
        prefs.edit { putString(KEY_LAST_DATE, today.toString()) }
        Log.i(TAG, "announcing today's schedule")
        voice.speak(text)
    }

    private companion object {
        const val TAG = "TodayBriefer"
        const val KEY_LAST_DATE = "lastDate"
    }
}
