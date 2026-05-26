package com.pricescanner.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.runBlocking

@Database(entities = [Product::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val ctx = context.applicationContext
                    val dbName = "price_scanner.db"

                    // 1. Backup old data before destructive migration
                    val oldData = backupOldData(ctx, dbName)

                    // 2. Build new DB (destructive migration wipes old schema)
                    val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
                        .fallbackToDestructiveMigration()
                        .build()

                    // 3. Restore old data into new schema
                    if (oldData.isNotEmpty()) {
                        runBlocking {
                            val dao = db.productDao()
                            oldData.forEach { dao.insert(it) }
                        }
                    }

                    INSTANCE = db
                    db
                }
            }
        }

        private fun backupOldData(ctx: Context, dbName: String): List<Product> {
            val dbFile = ctx.getDatabasePath(dbName)
            if (!dbFile.exists()) return emptyList()

            val result = mutableListOf<Product>()
            try {
                val oldDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                )
                // Check if old "products" table has "id" column (v2 schema)
                val cols = oldDb.rawQuery("PRAGMA table_info(products)", null)
                val hasId = cols.use { c ->
                    var found = false
                    while (c.moveToNext()) {
                        if (c.getString(c.getColumnIndexOrThrow("name")) == "id") found = true
                    }
                    found
                }

                if (hasId) {
                    // Old v2 schema: id, barcode, name, price, unit, createdAt
                    val cursor = oldDb.rawQuery(
                        "SELECT barcode, name, price, unit, createdAt FROM products ORDER BY createdAt DESC",
                        null
                    )
                    val seenBarcodes = mutableSetOf<String>()
                    cursor.use { c ->
                        while (c.moveToNext()) {
                            var barcode = c.getString(0) ?: ""
                            if (barcode.isBlank()) {
                                // Auto-generate for empty barcode
                                val name = c.getString(1) ?: ""
                                barcode = "a${(Math.abs(name.hashCode()) % 1000000000).toString().padStart(9, '0')}"
                            }
                            // Skip duplicates (keep first = latest by createdAt DESC)
                            if (barcode in seenBarcodes) continue
                            seenBarcodes.add(barcode)

                            result.add(
                                Product(
                                    barcode = barcode,
                                    name = c.getString(1) ?: "",
                                    price = c.getString(2) ?: "0.00",
                                    unit = c.getString(3) ?: "个",
                                    createdAt = c.getLong(4)
                                )
                            )
                        }
                    }
                }
                oldDb.close()
            } catch (_: Exception) {
                // If anything fails, just start fresh
            }
            return result
        }
    }
}
