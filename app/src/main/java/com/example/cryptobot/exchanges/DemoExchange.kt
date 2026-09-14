package com.example.cryptobot.exchanges

class DemoExchange : ExchangeClient {

    private val priceSource = ToobitExchange(apiKey = "", apiSecret = "")

    override val name = "Demo (آزمایشی)"

    override fun getCurrentPrice(symbol: String): Double {
        return priceSource.getCurrentPrice(symbol)
    }

    override fun getRecentCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        return priceSource.getRecentCandles(symbol, interval, limit)
    }

    override fun placeMarketOrder(symbol: String, side: String, quantity: String): String {
        return "{\"status\":\"شبیه‌سازی شد (آزمایشی)\",\"symbol\":\"$symbol\",\"side\":\"$side\",\"quantity\":\"$quantity\"}"
    }
}
