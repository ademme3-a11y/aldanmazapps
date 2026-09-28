package com.aldanmaz.drivedashboard.data.trip

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AldanmazDriveDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN estimatedFuelCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE trips ADD COLUMN speed0To30Seconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN speed31To50Seconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN speed51To70Seconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN speed71To90Seconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN speed91To120Seconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN speedOver120Seconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 36 test sürümünde eski, sürücüsüz deneme kayıtları bilinçli olarak temizlenir.
                db.execSQL("DELETE FROM trips")
                db.execSQL("ALTER TABLE trips ADD COLUMN driverId TEXT NOT NULL DEFAULT '1'")
                db.execSQL("ALTER TABLE trips ADD COLUMN driverName TEXT NOT NULL DEFAULT 'Mehmet'")
                db.execSQL("ALTER TABLE trips ADD COLUMN startLatitude REAL")
                db.execSQL("ALTER TABLE trips ADD COLUMN startLongitude REAL")
                db.execSQL("ALTER TABLE trips ADD COLUMN endLatitude REAL")
                db.execSQL("ALTER TABLE trips ADD COLUMN endLongitude REAL")
                db.execSQL("ALTER TABLE trips ADD COLUMN startAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trips ADD COLUMN endAddress TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN stopEventsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        @Volatile
        private var INSTANCE: AldanmazDriveDatabase? = null

        fun getInstance(
            context: Context
        ): AldanmazDriveDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AldanmazDriveDatabase::class.java,
                    "aldanmaz_drive.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
