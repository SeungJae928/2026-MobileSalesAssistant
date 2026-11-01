package com.salesdrive.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Trip::class, Visit::class, DriverProfile::class, Vehicle::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class SalesDriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun visitDao(): VisitDao
    abstract fun profileDao(): ProfileDao
    abstract fun vehicleDao(): VehicleDao
}
