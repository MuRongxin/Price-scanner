package com.pricescanner.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object BarcodeLookup {

    private const val TAG = "BarcodeLookup"
    private const val TIMEOUT_MS = 5000

    private const val APP_ID = "hmg0llgahpkmtvus"
    private const val APP_SECRET = "Q1YJEVPqNhxIaYDRfHnSM669FTHwv2r0"
    private const val MXNZP_URL = "https://www.mxnzp.com/api/barcode/goods/details"

    suspend fun lookup(barcode: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始查询条码: $barcode")

        lookupMxnzp(barcode)?.let {
            Log.d(TAG, "✅ RollToolsApi命中: $it")
            return@withContext it
        }

        lookupOpenFoodFacts(barcode)?.let {
            Log.d(TAG, "✅ OpenFoodFacts命中: $it")
            return@withContext it
        }

        Log.d(TAG, "❌ 所有源均未找到")
        null
    }

    private fun lookupMxnzp(barcode: String): String? {
        return try {
            val url = URL("$MXNZP_URL?barcode=$barcode&app_id=$APP_ID&app_secret=$APP_SECRET")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"

            val code = conn.responseCode
            Log.d(TAG, "  RollToolsApi 响应码: $code")
            if (code != 200) {
                null
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                Log.d(TAG, "  RollToolsApi body: ${body.take(200)}")
                val json = JSONObject(body)
                if (json.optInt("code") != 1) {
                    Log.d(TAG, "  RollToolsApi code!=1: ${json.optString("msg")}")
                    null
                } else {
                    val data = json.optJSONObject("data")
                    data?.optString("goodsName", "")?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "  RollToolsApi 异常: ${e.message}")
            null
        }
    }

    private fun lookupOpenFoodFacts(barcode: String): String? {
        return try {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"

            val code = conn.responseCode
            Log.d(TAG, "  OpenFoodFacts 响应码: $code")
            if (code != 200) {
                null
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                if (json.optInt("status") != 1) {
                    null
                } else {
                    val product = json.optJSONObject("product")
                    product?.optString("product_name_zh", "")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: product?.optString("product_name", "")?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "  OpenFoodFacts 异常: ${e.message}")
            null
        }
    }
}
