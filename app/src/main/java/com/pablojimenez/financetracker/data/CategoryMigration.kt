package com.pablojimenez.financetracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 7 to 8: `categories.id` becomes AUTOINCREMENT, so a new Category never gets the id of a
 * deleted one, and a Metric filter that still names a deleted id never starts matching a new
 * Category (ADR 0002). It rebuilds the table the way SQLite documents (create the new table, copy,
 * drop the old one, rename): every row keeps its id, parent, name, kind and position. Room runs
 * migrations before it turns foreign keys on, so the rows in `transactions` and `merchants` that
 * point at Categories are untouched and point at the rebuilt table under the same name. Last, it
 * seeds `sqlite_sequence` with the highest id there is now.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`parentId` INTEGER, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`parentId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        db.execSQL(
            "INSERT INTO categories_new (id, parentId, name, kind, position) " +
                "SELECT id, parentId, name, kind, position FROM categories",
        )
        db.execSQL("DROP TABLE categories")
        db.execSQL("ALTER TABLE categories_new RENAME TO categories")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories` (`parentId`)")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'categories'")
        db.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT 'categories', IFNULL(MAX(id), 0) FROM categories")
    }
}
