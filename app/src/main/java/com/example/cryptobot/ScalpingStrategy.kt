package com.example.cryptobot

enum class Signal { BUY, SELL, HOLD }

/** روند تایم‌فریم بالاتر (مثلاً روزانه) برای فیلتر کردن معاملات خلاف‌جهت */
enum class Trend { UP, DOWN, UNKNOWN }

class ScalpingStrategy(
    private val shortPeriod: Int = 5,
    private val longPeriod: Int = 20,
    private val rsiPeriod: Int = 14
) {
    private fun average(prices: List<Double>, period: Int): Double {
        val slice = prices.takeLast(period)
        return slice.sum() / slice.size
    }

    private fun rsi(prices: List<Double>): Double {
        if (prices.size < rsiPeriod + 1) return 50.0
        val recent = prices.takeLast(rsiPeriod + 1)
        var gains = 0.0
        var losses = 0.0
        for (i in 1 until recent.size) {
            val diff = recent[i] - recent[i - 1]
            if (diff > 0) gains += diff else losses += -diff
        }
        val avgGain = gains / rsiPeriod
        val avgLoss = losses / rsiPeriod
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }

    /**
     * تصمیم اصلی استراتژی.
     * trend: روند تایم‌فریم بالاتر (مثلاً روزانه). اگر UP نباشد، سیگنال خرید رد می‌شود
     * (فیلتر روند) — دقیقاً همون فیلتری که رو نسخه طلای MT5 اضافه شد.
     */
    fun decide(recentCloses: List<Double>, trend: Trend = Trend.UNKNOWN): Signal {
        if (recentCloses.size < longPeriod) return Signal.HOLD

        val shortMA = average(recentCloses, shortPeriod)
        val longMA = average(recentCloses, longPeriod)
        val diffPercent = ((shortMA - longMA) / longMA) * 100
        val rsiValue = rsi(recentCloses)

        val rawSignal = when {
            diffPercent > 0.1 && rsiValue < 70 -> Signal.BUY
            diffPercent < -0.1 && rsiValue > 30 -> Signal.SELL
            else -> Signal.HOLD
        }

        if (rawSignal == Signal.BUY && trend == Trend.DOWN) {
            return Signal.HOLD // خلاف روند کلی؛ فیلتر رد کرد
        }
        return rawSignal
    }
}

/** تشخیص روند از قیمت‌های بسته‌شدن یک تایم‌فریم بالاتر (مثلاً روزانه) */
object TrendDetector {
    fun detect(higherTimeframeCloses: List<Double>, maPeriod: Int = 50): Trend {
        if (higherTimeframeCloses.size < maPeriod) return Trend.UNKNOWN
        val ma = higherTimeframeCloses.takeLast(maPeriod).sum() / maPeriod
        val lastClose = higherTimeframeCloses.last()
        return when {
            lastClose > ma -> Trend.UP
            lastClose < ma -> Trend.DOWN
            else -> Trend.UNKNOWN
        }
    }
}

/** محاسبه ATR (میانگین ساده True Range) از کندل‌های high/low/close */
object AtrCalculator {
    fun calculate(candles: List<com.example.cryptobot.exchanges.Candle>, period: Int = 14): Double {
        if (candles.size < period + 1) return 0.0
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prevClose = candles[i - 1].close
            val tr = maxOf(
                cur.high - cur.low,
                kotlin.math.abs(cur.high - prevClose),
                kotlin.math.abs(cur.low - prevClose)
            )
            trueRanges.add(tr)
        }
        val recent = trueRanges.takeLast(period)
        if (recent.isEmpty()) return 0.0
        return recent.sum() / recent.size
    }
}
