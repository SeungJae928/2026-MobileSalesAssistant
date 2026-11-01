package com.salesdrive

import android.content.Context
import androidx.room.Room
import com.salesdrive.data.db.SalesDriveDatabase
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.fake.FakeVehicleSource
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

    // TODO: Pleos 신호 확인 후 실제 구현으로 교체. 그전까지는 Fake 시나리오로 동작
    @Provides @Singleton
    fun vehicleSignalSource(): VehicleSignalSource = FakeVehicleSource(
        scenario = FakeVehicleSource.DEMO_SCENARIO,
        from = FakeVehicleSource.DEMO_FROM,
        to = FakeVehicleSource.DEMO_TO,
    )
}
