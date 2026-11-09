package com.salesdrive.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.salesdrive.data.db.Trip
import com.salesdrive.data.db.TripDao
import com.salesdrive.data.repo.ScheduleRepository
import com.salesdrive.data.repo.VisitRepository
import com.salesdrive.gleo.GleoAction.Status
import com.salesdrive.trip.OpenTripPolicy
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gleo AI가 schema.json의 function을 고르면 sendBroadcast로 여기로 온다(component="service").
 * UI 없이 저장만 하므로 주행 중에도 화면을 띄우지 않는다. Gleo는 7초 뒤 취소하므로 그 안에 Callback을 보낸다.
 */
class GleoActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun handler(): GleoActionHandler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = GleoAction.functionName(intent.action ?: return, context.packageName)
        val param = intent.getStringExtra(GleoAction.EXTRA_ACTION_PARAM)
        Log.i(TAG, "action $action param=${param?.length ?: 0}자")
        val handler = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).handler()
        val pending = goAsync()
        scope.launch {
            try {
                val response = withTimeoutOrNull(REPLY_WITHIN_MS) { handler.handle(action, param) }
                    ?: GleoAction.callback(action, Status.FAIL, "Saving the visit took too long.")
                reply(context, response)
            } finally {
                pending.finish()
            }
        }
    }

    private fun reply(context: Context, response: String) {
        context.sendBroadcast(
            Intent(GleoAction.RESPONSE_ACTION)
                .putExtra("packageName", context.packageName)
                .putExtra("response", response),
        )
        Log.i(TAG, "callback $response")
    }

    private companion object {
        const val TAG = "GleoAction"
        const val REPLY_WITHIN_MS = 5_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** 받은 function을 처리하고 Callback JSON을 돌려준다 */
@Singleton
class GleoActionHandler @Inject constructor(
    private val trips: TripDao,
    private val visits: VisitRepository,
    private val schedules: ScheduleRepository,
) {
    suspend fun handle(action: String, actionParam: String?): String = when (action) {
        GleoAction.RECORD_CLIENT_VISIT -> recordVisit(GleoAction.params(actionParam))
        GleoAction.RECORD_SCHEDULE -> recordSchedule(GleoAction.params(actionParam))
        else -> GleoAction.callback(action, Status.FAIL, "This app does not support that request.")
    }

    private suspend fun recordSchedule(p: Map<String, String>): String {
        val fn = GleoAction.RECORD_SCHEDULE
        val title = p["title"]
            ?: return GleoAction.callback(fn, Status.ASKING, "What is the plan?", mapOf("missingField" to "title"))
        val date = GleoAction.scheduleDate(p, LocalDate.now())
            ?: return GleoAction.callback(fn, Status.ASKING, "What day is the plan on?", mapOf("missingField" to "when"))
        val schedule = GleoAction.schedule(p, title, date)
        val id = schedules.save(schedule)
        Log.i("GleoAction", "schedule #$id saved for $date")
        return GleoAction.callback(
            fn, Status.SUCCESS, "The plan was saved to the calendar.",
            mapOf(
                "title" to schedule.title,
                "date" to "${date.monthValue}월 ${date.dayOfMonth}일",
                "time" to schedule.time?.toString(),
            ),
        )
    }

    private suspend fun recordVisit(p: Map<String, String>): String {
        val fn = GleoAction.RECORD_CLIENT_VISIT
        val v = GleoAction.visit(p, LocalDate.now())
        if (GleoAction.isEmpty(v)) {
            return GleoAction.callback(fn, Status.ASKING, "Please tell me what happened at the visit.", mapOf("missingField" to "summary"))
        }
        val trip = targetTrip()
            ?: return GleoAction.callback(fn, Status.FAIL, "There is no trip recorded today to attach the visit to.")
        val id = visits.saveParsed(trip.id, GleoAction.rawText(p), v)
        Log.i("GleoAction", "visit #$id saved to trip #${trip.id}")
        return GleoAction.callback(
            fn, Status.SUCCESS, "The visit note was saved to today's sales log.",
            mapOf(
                "client" to v.client,
                "contact" to v.contact,
                "summary" to v.summary,
                "followUpDate" to v.followUpDate?.toString(),
            ),
        )
    }

    /** 주행 중이면 그 주행, 아니면 오늘 끝난 마지막 주행. 날을 넘겨 버려진 주행에는 붙이지 않는다 */
    private suspend fun targetTrip(zone: ZoneId = ZoneId.systemDefault()): Trip? {
        trips.findOpen()?.takeUnless { OpenTripPolicy.isAbandoned(it.startedAt, Instant.now(), zone) }?.let { return it }
        val last = trips.lastEnded() ?: return null
        val today = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return last.takeIf { (it.endedAt ?: return null) >= today }
    }
}
