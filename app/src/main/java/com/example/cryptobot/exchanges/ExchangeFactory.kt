package com.example.cryptobot.exchanges

object ExchangeFactory {
    fun create(
        exchangeName: String,
        apiKey: String,
        apiSecret: String,
        passphrase: String = ""
    ): ExchangeClient {
        return when (exchangeName.lowercase()) {
            "binance" -> BinanceExchange(apiKey, apiSecret)
            "bybit" -> BybitExchange(apiKey, apiSecret)
            "kucoin" -> KuCoinExchange(apiKey, apiSecret, passphrase)
            "nobitex" -> NobitexExchange(apiKey, apiSecret)
            "toobit" -> ToobitExchange(apiKey, apiSecret)
            "lbank" -> LBankExchange(apiKey, apiSecret)
            "demo" -> DemoExchange()
            else -> throw IllegalArgumentException(
                "صرافی '$exchangeName' هنوز پیاده‌سازی نشده. اسم‌های پشتیبانی‌شده: binance, bybit, kucoin, nobitex, toobit, lbank, demo"
            )
        }
    }
}
