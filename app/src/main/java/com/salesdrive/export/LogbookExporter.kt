package com.salesdrive.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.salesdrive.data.db.ProfileDao
import com.salesdrive.data.db.StopDao
import com.salesdrive.data.db.TripDao
import com.salesdrive.data.db.VehicleDao
import com.salesdrive.data.db.VisitDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 월별 운행기록부를 CSV로 저장한다. Android 10 이상은 다운로드 폴더, 그 아래는 앱 전용 폴더 */
@Singleton
class LogbookExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripDao: TripDao,
    private val visitDao: VisitDao,
    private val stopDao: StopDao,
    private val profileDao: ProfileDao,
    private val vehicleDao: VehicleDao,
) {
    /** 저장한 위치(사람이 읽을 경로). 실패하면 예외 */
    suspend fun export(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): String = withContext(Dispatchers.IO) {
        val trips = tripDao.endedBetween(
            month.atDay(1).atStartOfDay(zone).toInstant(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        )
        val csv = LogbookCsv.build(
            LogbookCsv.Input(
                from = month.atDay(1),
                to = month.atEndOfMonth(),
                vehicle = vehicleDao.all().firstOrNull(),
                trips = trips,
                drivers = profileDao.all().associateBy { it.id },
                visits = trips.associate { it.id to visitDao.findByTrip(it.id) },
                stops = trips.associate { it.id to stopDao.findByTrip(it.id) },
                zone = zone,
            ),
        )
        val name = "운행기록부_%d-%02d.csv".format(month.year, month.monthValue)
        write(name, csv.toByteArray(Charsets.UTF_8)).also { Log.i(TAG, "exported ${trips.size} trips to $it") }
    }

    private fun write(name: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("다운로드 폴더에 파일을 만들 수 없습니다")
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            return "다운로드/$FOLDER/$name"
        }
        val dir = File(context.getExternalFilesDir(null), FOLDER).apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private companion object {
        const val TAG = "LogbookExporter"
        const val FOLDER = "영업이동비서"
    }
}
