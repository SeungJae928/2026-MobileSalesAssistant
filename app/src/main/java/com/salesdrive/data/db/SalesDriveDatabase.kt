package com.salesdrive.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Trip::class, TripStop::class, Visit::class, DriverProfile::class, Vehicle::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class SalesDriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun visitDao(): VisitDao
    abstract fun profileDao(): ProfileDao
    abstract fun vehicleDao(): VehicleDao
}
