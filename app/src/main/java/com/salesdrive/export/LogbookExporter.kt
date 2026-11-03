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
import com.salesdrive.data.db.VisitDao
import com.salesdrive.data.repo.VehicleRepository
import com.salesdrive.data.repo.registeredPlate
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
    private val vehicles: VehicleRepository,
) {
    /** 저장한 위치(사람이 읽을 경로). 실패하면 예외 */
    suspend fun export(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): String = withContext(Dispatchers.IO) {
        val trips = tripDao.endedBetween(
            month.atDay(1).atStartOfDay(zone).toInstant(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        )
        val vehicle = vehicles.current().let { it.copy(plateNumber = it.registeredPlate) }
        val csv = LogbookCsv.build(
            LogbookCsv.Input(
                from = month.atDay(1),
                to = month.atEndOfMonth(),
                vehicle = vehicle,
                company = vehicle.company,
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
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"
            // 같은 달을 다시 내보내면 "(1)" 사본을 만들지 않고 이 앱이 만든 파일을 덮어쓴다
            val uri = findOwnDownload(name, relativePath) ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                },
            ) ?: error("다운로드 폴더에 파일을 만들 수 없습니다")
            resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            return "다운로드/$FOLDER/$name"
        }
        val dir = File(context.getExternalFilesDir(null), FOLDER).apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** 다운로드 폴더에서 이 앱이 만든 같은 이름의 파일. 다른 앱 파일은 권한 없이 보이지 않는다 */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findOwnDownload(name: String, relativePath: String): android.net.Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(name, relativePath),
            "${MediaStore.Downloads._ID} DESC",
        )?.use { c ->
            if (c.moveToFirst()) return android.content.ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return null
    }

    private companion object {
        const val TAG = "LogbookExporter"
        const val FOLDER = "영업이동비서"
    }
}
