package com.shivarya.locationspeedtracker.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [SessionEntity::class, TrackPointEntity::class, LapEntity::class, MetricEntity::class, PerfRunEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun points(): TrackPointDao
    abstract fun laps(): LapDao
    abstract fun metrics(): MetricDao
    abstract fun perfRuns(): PerfRunDao

    companion object {
        /** Also named in data_extraction_rules.xml / backup_rules.xml. */
        const val NAME = "speedtrack.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun sessionDao(db: AppDatabase) = db.sessions()
    @Provides fun pointDao(db: AppDatabase) = db.points()
    @Provides fun lapDao(db: AppDatabase) = db.laps()
    @Provides fun metricDao(db: AppDatabase) = db.metrics()
    @Provides fun perfRunDao(db: AppDatabase) = db.perfRuns()
}
