package com.najmulcodes.zapflick.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Never use a destructive fallback: it would wipe the download history. */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.V1_TO_V2.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
