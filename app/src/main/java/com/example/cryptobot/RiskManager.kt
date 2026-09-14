package com.example.cryptobot

/**
 * مدیریت ریسک بر اساس سرمایه حساب.
 * با SL برابر 1%، مقدار پوزیشن طوری محاسبه می‌شود که حداکثر زیان تئوریک
 * در صورت فعال شدن حد ضرر برابر riskPercent از equity باشد.
 */
class RiskManager(
    private val riskPercent: Double = 1.0,
    private val stopLossPercent: Double = 1.0,
    private val takeProfitPercent: Double = 1.5,
    private val accountEquityUsd: Double = 100.0,
    private val minNotionalUsd: Double = 5.0
) {
    init {
        require(riskPercent > 0) { "درصد ریسک باید بیشتر از صفر باشد" }
        require(stopLossPercent > 0) { "درصد حد ضرر باید بیشتر از صفر باشد" }
        require(takeProfitPercent > 0) { "درصد حد سود باید بیشتر از صفر باشد" }
        require(accountEquityUsd > 0) { "سرمایه حساب باید بیشتر از صفر باشد" }
    }

    fun shouldStopLoss(entryPrice: Double, currentPrice: Double): Boolean {
        if (entryPrice <= 0.0) return false
        val changePercent = ((currentPrice - entryPrice) / entryPrice) * 100.0
        return changePercent <= -stopLossPercent
    }

    fun shouldTakeProfit(entryPrice: Double, currentPrice: Double): Boolean {
        if (entryPrice <= 0.0) return false
        val changePercent = ((currentPrice - entryPrice) / entryPrice) * 100.0
        return changePercent >= takeProfitPercent
    }

    /** حد ضرر بر پایه ATR (فاصله واقعی نوسان بازار به‌جای درصد ثابت) */
    fun shouldStopLossAtr(entryPrice: Double, currentPrice: Double, atr: Double, slMultiplier: Double): Boolean {
        if (entryPrice <= 0.0 || atr <= 0.0) return shouldStopLoss(entryPrice, currentPrice)
        return currentPrice <= entryPrice - (atr * slMultiplier)
    }

    /** حد سود بر پایه ATR */
    fun shouldTakeProfitAtr(entryPrice: Double, currentPrice: Double, atr: Double, tpMultiplier: Double): Boolean {
        if (entryPrice <= 0.0 || atr <= 0.0) return shouldTakeProfit(entryPrice, currentPrice)
        return currentPrice >= entryPrice + (atr * tpMultiplier)
    }

    fun calculateQuantity(entryPrice: Double): Double {
        require(entryPrice > 0) { "قیمت ورود نامعتبر است" }
        val riskUsd = accountEquityUsd * (riskPercent / 100.0)
        val stopDistance = entryPrice * (stopLossPercent / 100.0)
        return riskUsd / stopDistance
    }

    /** محاسبه حجم پوزیشن وقتی فاصله حد ضرر بر پایه ATR است، نه درصد ثابت */
    fun calculateQuantityForAtrStop(entryPrice: Double, atr: Double, slMultiplier: Double): Double {
        require(entryPrice > 0) { "قیمت ورود نامعتبر است" }
        if (atr <= 0.0) return calculateQuantity(entryPrice)
        val riskUsd = accountEquityUsd * (riskPercent / 100.0)
        val stopDistance = atr * slMultiplier
        if (stopDistance <= 0.0) return calculateQuantity(entryPrice)
        return riskUsd / stopDistance
    }

    /**
     * بررسی حداقل ارزش سفارش قبل از ارسال به صرافی.
     * بیشتر صرافی‌ها (مخصوصاً Binance/Bybit) سفارش‌های خیلی کوچک رو رد می‌کنن؛
     * این چک جلوی ارسال سفارش‌های محکوم‌به‌شکست و لاگ خطای بی‌فایده رو می‌گیره.
     */
    fun meetsMinNotional(entryPrice: Double, quantity: Double): Boolean {
        return (entryPrice * quantity) >= minNotionalUsd
    }

    fun riskUsd(): Double = accountEquityUsd * (riskPercent / 100.0)
    fun positionNotionalUsd(entryPrice: Double): Double = calculateQuantity(entryPrice) * entryPrice
}
