package com.denispodpovetniy.traininglog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * Миграции схемы Room.
 *
 * Миграция 1 → 2 добавляет к пресету список единиц измерения. Старые строки
 * прогрессии не разбираются и не перезаписываются: они показываются только для
 * чтения до явной перезаписи (см. `PLAN.md` §3.9).
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_UNITS_COLUMN)
        }

        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(ADD_UNITS_COLUMN)
        }
    }

    private const val ADD_UNITS_COLUMN =
        "ALTER TABLE exercises ADD COLUMN units TEXT NOT NULL DEFAULT ''"
}
