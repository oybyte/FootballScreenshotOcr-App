package com.fifa.ocr.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        FixtureEntity::class,
        SlotEntity::class,
        AssetEntity::class,
        CaptureSessionEntity::class,
        CaptureSegmentEntity::class,
        DiagnosticEntity::class,
        RevisionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun fixtureDao(): FixtureDao
    abstract fun slotDao(): SlotDao
    abstract fun assetDao(): AssetDao
    abstract fun sessionDao(): SessionDao
    abstract fun segmentDao(): SegmentDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun revisionDao(): RevisionDao
}
