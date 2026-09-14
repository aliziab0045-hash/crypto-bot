package com.example.cryptobot.exchanges

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64

/**
 * پیاده‌سازی KuCoin
 * گرفتن API Key: https://sandbox.kucoin.com (برای تست) یا حساب واقعی KuCoin
 * نکته: KuCoin علاوه بر Key و Secret، یه "Passphrase" هم می‌خواد.
 */
class KuCoinExchange(
    private val apiKey: String,
    private val apiSecret: String,
    private val apiPassphrase: String,
    private val baseUrl: String = "https://openapi-sandbox.kucoin.com"
) : ExchangeClient {

    override val name = "KuCoin"

    // KuCoin نماد رو با خط تیره می‌خواد، مثلاً BTC-USDT به‌جای BTCUSDT
    private fun toKucoinSymbol(symbol: String): String {
        if (symbol.contains("-")) return symbol
        val quote = listOf("USDT", "USDC", "BTC").firstOrNull { symbol.endsWith(it) } ?: "USDT"
        val base = symbol.removeSuffix(quote)
        return "$base-$quote"
    }

    override fun getCurrentPrice(symbol: String): Double {
        val url = "$baseUrl/api/v1/market/orderbook/level1?symbol=${toKucoinSymbol(symbol)}"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از KuCoin دریافت نشد")
            return JSONObject(body).getJSONObject("data").getString("price").toDouble()
        }
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val kucoinType = when (interval.lowercase()) { "1m" -> "1min", "5m" -> "5min", "15m" -> "15min", "30m" -> "30min", "1h" -> "1hour", "2h" -> "2hour", "4h" -> "4hour", "6h" -> "6hour", "8h" -> "8hour", "12h" -> "12hour", "1d" -> "1day", "1w" -> "1week", else -> interval }
        val url = "$baseUrl/api/v1/market/candles?type=$kucoinType&symbol=${toKucoinSymbol(symbol)}"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از KuCoin دریافت نشد")
            val data = JSONObject(body).getJSONArray("data")
            // هر ردیف: [time, open, close, high, low, volume, turnover] - جدیدترین اول
            val candles = (0 until minOf(limit, data.length())).map {
                val row = data.getJSONArray(it)
                Candle(high = row.getString(3).toDouble(), low = row.getString(4).toDouble(), close = row.getString(2).toDouble())
            }
            return candles.reversed()
        }
    }

    override fun placeMarketOrder(symbol: String, side: String, quantity: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val body = JSONObject().apply {
            put("clientOid", System.currentTimeMillis().toString())
            put("side", side.lowercase())
            put("symbol", toKucoinSymbol(symbol))
            put("type", "market")
            put("size", quantity)
        }.toString()

        val strToSign = timestamp + "POST" + "/api/v1/orders" + body
        val signature = Base64.getEncoder().encodeToString(
            ExchangeHttp.hmacSha256(apiSecret, strToSign).let {
                // hmacSha256 در ExchangeHttp هگز برمی‌گردونه؛ اینجا بایت خام لازم داریم
                it.chunked(2).map { hex -> hex.toInt(16).toByte() }.toByteArray()
            }
        )
        val signedPassphrase = Base64.getEncoder().encodeToString(
            ExchangeHttp.hmacSha256(apiSecret, apiPassphrase).chunked(2)
                .map { hex -> hex.toInt(16).toByte() }.toByteArray()
        )

        val request = Request.Builder()
            .url("$baseUrl/api/v1/orders")
            .addHeader("KC-API-KEY", apiKey)
            .addHeader("KC-API-SIGN", signature)
            .addHeader("KC-API-TIMESTAMP", timestamp)
            .addHeader("KC-API-PASSPHRASE", signedPassphrase)
            .addHeader("KC-API-KEY-VERSION", "2")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        ExchangeHttp.client.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw Exception("پاسخی از KuCoin دریافت نشد")
        }
    }
}
