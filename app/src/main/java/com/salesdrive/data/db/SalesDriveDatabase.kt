package com.salesdrive.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Trip::class, TripStop::class, Visit::class, DriverProfile::class, Vehicle::class, Schedule::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class SalesDriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun visitDao(): VisitDao
    abstract fun profileDao(): ProfileDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun scheduleDao(): ScheduleDao
}
