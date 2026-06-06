package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATIONS: Array<Migration> = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE imported ADD COLUMN age_secret_key TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE pending ADD COLUMN age_secret_key TEXT NOT NULL DEFAULT ''")
        }
    }
)

val LEGACY_MIGRATION = ::migrationFromLegacy
