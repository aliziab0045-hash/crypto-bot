package com.example.cryptobot.exchanges

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.util.Base64

class NobitexExchange(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://api.nobitex.ir"
) : ExchangeClient {

    override val name = "Nobitex"

    private fun splitSymbol(symbol: String): Pair<String, String> {
        val upper = symbol.uppercase()
        val quote = listOf("USDT", "IRT", "BTC").firstOrNull { upper.endsWith(it) } ?: "USDT"
        val base = upper.removeSuffix(quote)
        return base.lowercase() to quote.lowercase()
    }

    private fun signRequest(method: String, fullPath: String, rawBody: String): Pair<String, String> {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val payload = timestamp + method + fullPath + rawBody
        val keyBytes = Base64.getUrlDecoder().decode(apiSecret)
        val privateKey = Ed25519PrivateKeyParameters(keyBytes, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val payloadBytes = payload.toByteArray()
        signer.update(payloadBytes, 0, payloadBytes.size)
        val signatureBytes = signer.generateSignature()
        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
        return timestamp to signatureB64
    }

    override fun getCurrentPrice(symbol: String): Double {
        val (base, quote) = splitSymbol(symbol)
        val url = "$baseUrl/market/stats?srcCurrency=$base&dstCurrency=$quote"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از نوبیتکس دریافت نشد")
            val stats = JSONObject(body).getJSONObject("stats").getJSONObject("$base-$quote")
            return stats.getString("latest").toDouble()
        }
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val (base, quote) = splitSymbol(symbol)
        val nobitexSymbol = (base + quote).uppercase()
        val now = System.currentTimeMillis() / 1000
        val from = now - (limit * 60)
        val url = "$baseUrl/market/udf/history?symbol=$nobitexSymbol&resolution=1&from=$from&to=$now"
        val request = Request.Builder().url(url).build()
        ExchangeHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخی از نوبیتکس دریافت نشد")
            val json = JSONObject(body)
            val closesArray = json.getJSONArray("c")
            val highsArray = json.getJSONArray("h")
            val lowsArray = json.getJSONArray("l")
            return (0 until closesArray.length()).map {
                Candle(high = highsArray.getDouble(it), low = lowsArray.getDouble(it), close = closesArray.getDouble(it))
            }
        }
    }

    override fun placeMarketOrder(symbol: String, side: String, quantity: String): String {
        val (base, quote) = splitSymbol(symbol)
        val bodyObj = JSONObject().apply {
            put("type", side.lowercase())
            put("execution", "market")
            put("srcCurrency", base)
            put("dstCurrency", quote)
            put("amount", quantity)
        }
        val rawBody = bodyObj.toString()
        val path = "/market/orders/add"
        val (timestamp, signature) = signRequest("POST", path, rawBody)

        val request = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("Nobitex-Key", apiKey)
            .addHeader("Nobitex-Signature", signature)
            .addHeader("Nobitex-Timestamp", timestamp)
            .post(rawBody.toRequestBody("application/json".toMediaType()))
            .build()

        ExchangeHttp.client.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw Exception("پاسخی از نوبیتکس دریافت نشد")
        }
    }
}
