package com.pricescanner.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val price: String,
    val unit: String = "个",
    val createdAt: Long = System.currentTimeMillis()
)
