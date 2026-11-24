package com.pablojimenez.financetracker.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pablojimenez.financetracker.domain.PreloadedMerchants
import com.pablojimenez.financetracker.domain.merchantNameKey
import com.pablojimenez.financetracker.domain.sameMerchantName

/**
 * Adds the Merchants, seeded with every preloaded one, and turns each Transaction's free-text
 * merchant into a link to a Merchant: the one with the same name (see [sameMerchantName]) when
 * there is one, otherwise a new owner's Merchant (see [ownersMerchant]). The free-text column goes.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `merchants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `keywords` TEXT NOT NULL, `defaultCategoryId` INTEGER, " +
                "FOREIGN KEY(`defaultCategoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_merchants_defaultCategoryId` ON `merchants` (`defaultCategoryId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `preloaded_merchant_seeds` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
        seedPreloadedMerchants(db)
        val merchantIds = linkFreeTextMerchants(db)
        rebuildTransactionsWithMerchantIds(db, merchantIds)
    }
}

/** A version 6 Transaction with a free-text merchant, as the migration needs it. */
private class FreeTextMerchant(
    val transactionId: Long,
    val text: String,
    val categoryId: Long?,
    val date: Long,
    val time: Long?,
)

/** The Merchant id of every Transaction that had a free-text merchant, by Transaction id. */
private fun linkFreeTextMerchants(db: SupportSQLiteDatabase): Map<Long, Long> {
    val rows = db.query(
        "SELECT id, merchant, categoryId, date, time FROM transactions WHERE merchant IS NOT NULL",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val text = cursor.getString(1).trim()
                if (text.isEmpty()) continue
                add(
                    FreeTextMerchant(
                        transactionId = cursor.getLong(0),
                        text = text,
                        categoryId = if (cursor.isNull(2)) null else cursor.getLong(2),
                        date = cursor.getLong(3),
                        time = if (cursor.isNull(4)) null else cursor.getLong(4),
                    ),
                )
            }
        }
    }
    val existing = db.query("SELECT id, name FROM merchants").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1)) }
    }
    val links = mutableMapOf<Long, Long>()
    rows.groupBy { merchantNameKey(it.text) }.values.forEach { group ->
        val merchantId = existing.firstOrNull { (_, name) -> sameMerchantName(name, group.first().text) }?.first
            ?: ownersMerchant(group).let { (name, categoryId) ->
                db.insert(
                    "merchants",
                    SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("name", name)
                        put("keywords", "")
                        put("defaultCategoryId", categoryId)
                    },
                )
            }
        group.forEach { links[it.transactionId] = merchantId }
    }
    return links
}

/**
 * The name and default Category of an owner's Merchant made from [group], Transactions whose
 * merchants are the same name: the spelling of the most recent one, and the Category most of them
 * have, a tie going to the Category of the most recent one. Most recent is the latest date, then
 * time (one without a time comes after those with one, as in a day's list), then id.
 */
private fun ownersMerchant(group: List<FreeTextMerchant>): Pair<String, Long?> {
    val newestFirst = group.sortedWith(
        compareByDescending<FreeTextMerchant> { it.date }
            .thenBy { it.time == null }
            .thenByDescending { it.time }
            .thenByDescending { it.transactionId },
    )
    val counts = group.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
    val most = counts.values.maxOrNull()
    val categoryId = newestFirst.firstOrNull { it.categoryId != null && counts[it.categoryId] == most }?.categoryId
    return newestFirst.first().text to categoryId
}

/**
 * Rebuilds `transactions` with `merchantId` (from [merchantIds]) instead of the free-text
 * `merchant`, keeping every other column and the AUTOINCREMENT counter, so ids keep growing.
 */
private fun rebuildTransactionsWithMerchantIds(db: SupportSQLiteDatabase, merchantIds: Map<Long, Long>) {
    val sequence = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'transactions'").use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
    db.execSQL(
        "CREATE TABLE `transactions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`accountId` INTEGER NOT NULL, `categoryId` INTEGER, `amountCents` INTEGER NOT NULL, " +
            "`date` INTEGER NOT NULL, `time` INTEGER, `merchantId` INTEGER, `note` TEXT, " +
            "`source` TEXT NOT NULL, `transferId` INTEGER, " +
            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
            "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
            "FOREIGN KEY(`merchantId`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    )
    db.execSQL(
        "INSERT INTO transactions_new " +
            "(id, accountId, categoryId, amountCents, date, time, merchantId, note, source, transferId) " +
            "SELECT id, accountId, categoryId, amountCents, date, time, NULL, note, source, transferId " +
            "FROM transactions",
    )
    merchantIds.forEach { (transactionId, merchantId) ->
        db.execSQL("UPDATE transactions_new SET merchantId = ? WHERE id = ?", arrayOf<Any?>(merchantId, transactionId))
    }
    db.execSQL("DROP TABLE transactions")
    db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
    val highestId = db.query("SELECT IFNULL(MAX(id), 0) FROM transactions").use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }
    db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'transactions'")
    db.execSQL(
        "INSERT INTO sqlite_sequence (name, seq) VALUES ('transactions', ?)",
        arrayOf<Any?>(maxOf(sequence, highestId)),
    )
    listOf("accountId", "categoryId", "date", "transferId", "merchantId").forEach { column ->
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_$column` ON `transactions` (`$column`)")
    }
}

/** Seeds the preloaded Merchants a database is missing every time it opens (after any migration). */
internal object SeedPreloadedMerchants : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        seedPreloadedMerchants(db)
    }
}

/**
 * Adds every [PreloadedMerchants] entry whose key [db] was never seeded with, and records the key.
 * An entry whose name matches a Merchant already there (see [sameMerchantName]) adds nothing: that
 * Merchant is kept as it is. A default Category that no longer exists becomes none.
 */
internal fun seedPreloadedMerchants(db: SupportSQLiteDatabase) {
    val seeded = db.strings("SELECT `key` FROM preloaded_merchant_seeds").toSet()
    val pending = PreloadedMerchants.filterNot { it.key in seeded }
    if (pending.isEmpty()) return
    db.beginTransaction()
    try {
        val names = db.strings("SELECT name FROM merchants").toMutableList()
        val categoryIds = db.longs("SELECT id FROM categories").toSet()
        pending.forEach { merchant ->
            if (names.none { sameMerchantName(it, merchant.name) }) {
                db.execSQL(
                    "INSERT INTO merchants (name, keywords, defaultCategoryId) VALUES (?, ?, ?)",
                    arrayOf<Any?>(
                        merchant.name,
                        merchant.keywords.joinToString(MerchantEntity.KEYWORD_SEPARATOR),
                        merchant.defaultCategorySeedId.takeIf { it in categoryIds },
                    ),
                )
                names += merchant.name
            }
            db.execSQL("INSERT INTO preloaded_merchant_seeds (`key`) VALUES (?)", arrayOf<Any?>(merchant.key))
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

private fun SupportSQLiteDatabase.strings(sql: String): List<String> =
    query(sql).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

private fun SupportSQLiteDatabase.longs(sql: String): List<Long> =
    query(sql).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
