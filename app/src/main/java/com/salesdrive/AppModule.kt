package com.salesdrive

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import com.salesdrive.data.db.SalesDriveDatabase
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.data.repo.VisitRepository
import com.salesdrive.navi.NaviSource
import com.salesdrive.navi.NoNaviSource
import com.salesdrive.navi.PleosNaviSource
import com.salesdrive.trip.TripStore
import com.salesdrive.vehicle.CarDrivingRestriction
import com.salesdrive.vehicle.DrivingRestriction
import com.salesdrive.vehicle.PleosVehicleSource
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.fake.DemoSwitchSource
import com.salesdrive.vehicle.fake.FakeVehicleSource
import com.salesdrive.voice.FallbackVisitParser
import com.salesdrive.voice.LlmVisitParser
import com.salesdrive.voice.PleosLlmClient
import com.salesdrive.voice.PleosSpeechCapture
import com.salesdrive.voice.RuleVisitParser
import com.salesdrive.voice.PleosVoicePrompter
import com.salesdrive.voice.SpeechCapture
import com.salesdrive.voice.VisitParser
import com.salesdrive.voice.VisitStore
import com.salesdrive.voice.VoicePrompter
import com.salesdrive.vehicle.AndroidLocationSource
import com.salesdrive.vehicle.NoSeatMemory
import com.salesdrive.vehicle.PleosSeatMemory
import com.salesdrive.vehicle.SeatMemory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SalesDriveDatabase =
        Room.databaseBuilder(context, SalesDriveDatabase::class.java, "salesdrive.db").build()

    @Provides fun tripDao(db: SalesDriveDatabase) = db.tripDao()
    @Provides fun stopDao(db: SalesDriveDatabase) = db.stopDao()
    @Provides fun visitDao(db: SalesDriveDatabase) = db.visitDao()
    @Provides fun profileDao(db: SalesDriveDatabase) = db.profileDao()
    @Provides fun vehicleDao(db: SalesDriveDatabase) = db.vehicleDao()

    @Provides @Singleton
    fun naviSource(@ApplicationContext context: Context): NaviSource =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) PleosNaviSource(context) else NoNaviSource()

    /** Pleos LLM으로 먼저 파싱하고, 실패하면 규칙 기반 파서 */
    @Provides @Singleton
    fun visitParser(llm: PleosLlmClient): VisitParser = FallbackVisitParser(LlmVisitParser(llm), RuleVisitParser)

    @Provides @Singleton
    fun seatMemory(@ApplicationContext context: Context): SeatMemory =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) PleosSeatMemory(context) else NoSeatMemory

    @Provides @Singleton
    fun drivingRestriction(@ApplicationContext context: Context): DrivingRestriction = CarDrivingRestriction(context)

    /**
     * 차량(Pleos 에뮬레이터 포함)에서는 실제 신호, 폰 AVD에서는 Fake 시나리오.
     * 어느 쪽이든 신호 확인 화면의 버튼으로 시연용 시나리오를 바꿔 끼울 수 있다.
     */
    @Provides @Singleton
    fun demoSwitchSource(@ApplicationContext context: Context): DemoSwitchSource {
        val real = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            PleosVehicleSource(context, AndroidLocationSource(context))
        } else {
            FakeVehicleSource(
                scenario = FakeVehicleSource.DEMO_SCENARIO,
                from = FakeVehicleSource.DEMO_FROM,
                to = FakeVehicleSource.DEMO_TO,
            )
        }
        return DemoSwitchSource(real) { startKm ->
            FakeVehicleSource(
                scenario = FakeVehicleSource.DEMO_VIDEO_SCENARIO,
                from = FakeVehicleSource.DEMO_FROM,
                to = FakeVehicleSource.DEMO_TO,
                startOdometerKm = startKm,
                speedUp = 1.0,
                odometerScale = FakeVehicleSource.DEMO_ODOMETER_SCALE,
            )
        }
    }

    @Provides
    fun vehicleSignalSource(source: DemoSwitchSource): VehicleSignalSource = source
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {
    @Binds abstract fun tripStore(repository: TripRepository): TripStore
    @Binds abstract fun visitStore(repository: VisitRepository): VisitStore
    @Binds abstract fun voicePrompter(impl: PleosVoicePrompter): VoicePrompter
    @Binds abstract fun speechCapture(impl: PleosSpeechCapture): SpeechCapture
}
