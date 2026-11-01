package com.salesdrive

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import com.salesdrive.data.db.SalesDriveDatabase
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.trip.TripStore
import com.salesdrive.vehicle.PleosVehicleSource
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.fake.FakeVehicleSource
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
    @Provides fun visitDao(db: SalesDriveDatabase) = db.visitDao()
    @Provides fun profileDao(db: SalesDriveDatabase) = db.profileDao()
    @Provides fun vehicleDao(db: SalesDriveDatabase) = db.vehicleDao()

    /** 차량(Pleos 에뮬레이터 포함)에서는 실제 신호, 폰 AVD에서는 Fake 시나리오 */
    @Provides @Singleton
    fun vehicleSignalSource(@ApplicationContext context: Context): VehicleSignalSource =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            PleosVehicleSource(context)
        } else {
            FakeVehicleSource(
                scenario = FakeVehicleSource.DEMO_SCENARIO,
                from = FakeVehicleSource.DEMO_FROM,
                to = FakeVehicleSource.DEMO_TO,
            )
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {
    @Binds abstract fun tripStore(repository: TripRepository): TripStore
}
