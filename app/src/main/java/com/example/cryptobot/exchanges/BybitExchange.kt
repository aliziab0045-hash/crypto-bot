package com.example.cryptobot.exchanges

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BybitExchange(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://api-testnet.bybit.com"
) : ExchangeClient {

    override val name = "Bybit"

    override fun getCurrentPrice(symbol: String): Double {
        val url = "$baseUrl/v5/market/tickers?category=spot&symbol=$symbol"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از Bybit دریافت نشد")
            val list = JSONObject(body).getJSONObject("result").getJSONArray("list")
            return list.getJSONObject(0).getString("lastPrice").toDouble()
        }
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val bybitInterval = when (interval.lowercase()) {
            "1m" -> "1"
            "5m" -> "5"
            "15m" -> "15"
            "30m" -> "30"
            "1h" -> "60"
            "2h" -> "120"
            "4h" -> "240"
            "6h" -> "360"
            "12h" -> "720"
            "1d" -> "D"
            "1w" -> "W"
            else -> interval
        }
        val url = "$baseUrl/v5/market/kline?category=spot&symbol=$symbol&interval=$bybitInterval&limit=$limit"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از Bybit دریافت نشد")
            val list = JSONObject(body).getJSONObject("result").getJSONArray("list")
            // فرمت هر ردیف: [start, open, high, low, close, volume, turnover]؛ جدیدترین اول است، برعکس می‌کنیم
            return (list.length() - 1 downTo 0).map {
                val row = list.getJSONArray(it)
                Candle(high = row.getString(2).toDouble(), low = row.getString(3).toDouble(), close = row.getString(4).toDouble())
            }
        }
    }

    override fun placeMarketOrder(symbol: String, side: String, quantity: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val body = JSONObject().apply {
            put("category", "spot")
            put("symbol", symbol)
            put("side", side.lowercase().replaceFirstChar { it.uppercase() })
            put("orderType", "Market")
            put("qty", quantity)
        }.toString()

        val recvWindow = "5000"
        val payload = timestamp + apiKey + recvWindow + body
        val signature = ExchangeHttp.hmacSha256(apiSecret, payload)

        val request = Request.Builder()
            .url("$baseUrl/v5/order/create")
            .addHeader("X-BAPI-API-KEY", apiKey)
            .addHeader("X-BAPI-SIGN", signature)
            .addHeader("X-BAPI-TIMESTAMP", timestamp)
            .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        ExchangeHttp.client.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw Exception("پاسخی از Bybit دریافت نشد")
        }
    }
}
