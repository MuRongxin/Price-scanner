package com.pricescanner.app.data

import com.pricescanner.app.util.PinyinUtil
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {

    val allProducts: Flow<List<Product>> = dao.getAllProducts()

    suspend fun getByBarcode(barcode: String): Product? =
        if (barcode.isBlank()) null else dao.getByBarcode(barcode)

    suspend fun insert(product: Product) = dao.insert(product)

    suspend fun delete(product: Product) = dao.delete(product)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAllProductsOnce(): List<Product> = dao.getAllProductsOnce()

    fun search(products: List<Product>, query: String): List<Product> {
        if (query.isBlank()) return products

        val q = query.lowercase()
        data class Scored(val product: Product, val score: Int, val pos: Int)

        return products.mapNotNull { p ->
            val name = p.name.lowercase()

            if (name.contains(q))
                return@mapNotNull Scored(p, 0, name.indexOf(q))
            if (p.barcode.contains(q))
                return@mapNotNull Scored(p, 0, 0)

            val charsInOrder = charsInOrder(name, q)
            if (charsInOrder >= 0)
                return@mapNotNull Scored(p, 1, charsInOrder)

            val py = PinyinUtil.getInitials(p.name)
            if (py.contains(q))
                return@mapNotNull Scored(p, 2, py.indexOf(q))

            if (allCharsPresent(name, q))
                return@mapNotNull Scored(p, 3, 0)

            if (allCharsPresent(py, q))
                return@mapNotNull Scored(p, 4, 0)

            null
        }
            .sortedWith(compareBy({ it.score }, { it.pos }))
            .map { it.product }
    }

    private fun charsInOrder(text: String, query: String): Int {
        var ti = 0
        for (qi in query.indices) {
            ti = text.indexOf(query[qi], ti)
            if (ti == -1) return -1
            ti++
        }
        return text.indexOf(query[0])
    }

    private fun allCharsPresent(text: String, query: String): Boolean {
        for (c in query) {
            if (c !in text) return false
        }
        return true
    }
}
