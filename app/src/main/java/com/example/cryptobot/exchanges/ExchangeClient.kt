package com.example.cryptobot.exchanges

/** یک کندل با قیمت‌های بالا/پایین/بسته‌شدن (برای محاسبه ATR لازم است) */
data class Candle(val high: Double, val low: Double, val close: Double)

/**
 * رابط مشترک برای همه صرافی‌ها
 * هر صرافی جدید که بخوایم اضافه کنیم، فقط باید این رابط رو پیاده‌سازی کنه.
 * این‌طوری بقیه بخش‌های ربات (استراتژی، مدیریت ریسک) اصلاً لازم نیست تغییر کنن.
 */
interface ExchangeClient {
    /** نام صرافی، مثلاً "Binance" یا "Bybit" */
    val name: String

    /** گرفتن قیمت لحظه‌ای یک جفت ارز، مثلاً BTCUSDT */
    fun getCurrentPrice(symbol: String): Double

    /** گرفتن کندل‌های اخیر (high/low/close) — برای میانگین متحرک، RSI و ATR لازم است */
    fun getRecentCandles(symbol: String, interval: String = "4h", limit: Int = 20): List<Candle>

    /** فقط قیمت‌های بسته‌شدن اخیر؛ روی getRecentCandles ساخته شده، نیازی به پیاده‌سازی جدا نیست */
    fun getRecentCloses(symbol: String, interval: String = "4h", limit: Int = 20): List<Double> =
        getRecentCandles(symbol, interval, limit).map { it.close }

    /** ثبت سفارش خرید/فروش. side: "BUY" یا "SELL" */
    fun placeMarketOrder(symbol: String, side: String, quantity: String): String
}
