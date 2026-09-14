package com.example.cryptobot.exchanges

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest

class LBankExchange(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://api.lbank.info"
) : ExchangeClient {

    override val name = "LBank"

    private fun toLbankSymbol(symbol: String): String {
        val upper = symbol.uppercase()
        val quote = listOf("USDT", "USDC", "BTC").firstOrNull { upper.endsWith(it) } ?: "USDT"
        val base = upper.removeSuffix(quote)
        return "${base.lowercase()}_${quote.lowercase()}"
    }

    private fun md5Sign(params: Map<String, String>): String {
        val sorted = params.toSortedMap()
        val query = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        val toSign = "$query&secret_key=$apiSecret"
        val digest = MessageDigest.getInstance("MD5").digest(toSign.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }

    override fun getCurrentPrice(symbol: String): Double {
        val url = "$baseUrl/v2/ticker.do?symbol=${toLbankSymbol(symbol)}"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از آل‌بنک دریافت نشد")
            val arr = JSONArray(body)
            return arr.getJSONObject(0).getJSONObject("ticker").getDouble("latest")
        }
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val nowSeconds = System.currentTimeMillis() / 1000
        val lbankType = when (interval.lowercase()) { "1m" -> "minute1", "5m" -> "minute5", "15m" -> "minute15", "30m" -> "minute30", "1h" -> "hour1", "4h" -> "hour4", "8h" -> "hour8", "12h" -> "hour12", "1d" -> "day1", "1w" -> "week1", else -> "hour4" }
        val url = "$baseUrl/v2/kline.do?symbol=${toLbankSymbol(symbol)}&size=$limit&type=$lbankType&time=$nowSeconds"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از آل‌بنک دریافت نشد")
            val arr = JSONArray(body)
            // فرمت: [timestamp, open, high, low, close, volume]
            return (0 until arr.length()).map {
                val row = arr.getJSONArray(it)
                Candle(high = row.getDouble(2), low = row.getDouble(3), close = row.getDouble(4))
            }
        }
    }

    override fun placeMarketOrder(symbol: String, side: String, quantity: String): String {
        val params = mutableMapOf(
            "api_key" to apiKey,
            "symbol" to toLbankSymbol(symbol),
            "type" to if (side.uppercase() == "BUY") "buy_market" else "sell_market",
            "amount" to quantity
        )
        val sign = md5Sign(params)
        params["sign"] = sign

        val formBuilder = FormBody.Builder()
        params.forEach { (key, value) -> formBuilder.add(key, value) }

        val request = Request.Builder()
            .url("$baseUrl/v1/create_order.do")
            .post(formBuilder.build())
            .build()

        ExchangeHttp.client.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw Exception("پاسخی از آل‌بنک دریافت نشد")
        }
    }
}
