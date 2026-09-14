package com.example.cryptobot.exchanges

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ToobitExchange(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://api.toobit.com"
) : ExchangeClient {

    override val name = "Toobit"

    override fun getCurrentPrice(symbol: String): Double {
        val url = "$baseUrl/quote/v1/ticker/price?symbol=$symbol"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از توبیت دریافت نشد")
            val arr = JSONArray(body)
            return arr.getJSONObject(0).getString("p").toDouble()
        }
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val url = "$baseUrl/quote/v1/klines?symbol=$symbol&interval=$interval&limit=$limit"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از توبیت دریافت نشد")
            val arr = JSONArray(body)
            // فرمت مشابه بایننس: [openTime, open, high, low, close, volume, ...]
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
        val url = "$baseUrl/api/v1/spot/order?$params&signature=$signature"

        val request = Request.Builder()
            .url(url)
            .addHeader("X-BB-APIKEY", apiKey)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()

        ExchangeHttp.client.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw Exception("پاسخی از توبیت دریافت نشد")
        }
    }
}
