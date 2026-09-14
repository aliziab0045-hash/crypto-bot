package com.example.cryptobot.exchanges

import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * پیاده‌سازی Binance
 * گرفتن API Key: https://testnet.binance.vision (برای تست) یا حساب واقعی Binance
 */
class BinanceExchange(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://testnet.binance.vision"
) : ExchangeClient {

    override val name = "Binance"

    override fun getCurrentPrice(symbol: String): Double {
        val url = "$baseUrl/api/v3/ticker/price?symbol=$symbol"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از Binance دریافت نشد")
            return JSONObject(body).getString("price").toDouble()
        }
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val url = "$baseUrl/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از Binance دریافت نشد")
            val arr = JSONArray(body)
            // فرمت کندل بایننس: [openTime, open, high, low, close, volume, ...]
            return (0 until arr.length()).map {
                val row = arr.getJSONArray(it)
                Candle(high = row.getString(2).toDouble(), low = row.getString(3).toDouble(), close = row.getString(4).toDouble())
            }
        }
    }

    override fun placeMarketOrder(symbol: String, side: String, quantity: String): String {
        val timestamp = System.currentTimeMillis()
        val params = "symbol=$symbol&side=$side&type=MARKET&quantity=$quantity&timestamp=$timestamp"
        val signature = ExchangeHttp.hmacSha256(apiSecret, params)
        val url = "$baseUrl/api/v3/order?$params&signature=$signature"

        val request = Request.Builder()
            .url(url)
            .addHeader("X-MBX-APIKEY", apiKey)
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        ExchangeHttp.client.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw Exception("پاسخی از Binance دریافت نشد")
        }
    }
}
