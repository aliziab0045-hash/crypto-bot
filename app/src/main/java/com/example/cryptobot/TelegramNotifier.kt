package com.example.cryptobot

import com.example.cryptobot.exchanges.ExchangeHttp
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

object TelegramNotifier {
    fun send(botToken: String, chatId: String, message: String): Result<Unit> {
        if (botToken.isBlank() || chatId.isBlank()) return Result.success(Unit)
        return runCatching {
            val url = "https://api.telegram.org/bot${botToken.trim()}/sendMessage"
            val body = FormBody.Builder()
                .add("chat_id", chatId.trim())
                .add("text", message)
                .build()
            val request = Request.Builder().url(url).post(body).build()
            ExchangeHttp.client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Exception("Telegram HTTP ${response.code}")
                if (responseBody.isNotBlank() && !JSONObject(responseBody).optBoolean("ok", false)) {
                    throw Exception("Telegram: ${JSONObject(responseBody).optString("description", "خطای نامشخص")}")
                }
            }
        }
    }
}
