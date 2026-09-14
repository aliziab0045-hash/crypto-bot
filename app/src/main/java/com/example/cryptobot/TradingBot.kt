package com.example.cryptobot

import com.example.cryptobot.exchanges.ExchangeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


data class Trade(
    val type: String,
    val price: Double,
    val quantity: Double,
    val timestamp: Long,
    val pnl: Double? = null
)

class TradingBot(
    private val exchange: ExchangeClient,
    private val symbol: String = "BTCUSDT",
    accountEquityUsd: Double = 100.0,
    riskPercent: Double = 1.0,
    private val candleInterval: String = "4h",
    private val pollIntervalMs: Long = 60_000L,
    private val useTrendFilter: Boolean = true,
    private val trendInterval: String = "1d",
    private val trendMaPeriod: Int = 50,
    private val useAtrStops: Boolean = true,
    private val atrPeriod: Int = 14,
    private val atrSlMultiplier: Double = 1.5,
    private val atrTpMultiplier: Double = 2.5,
    minNotionalUsd: Double = 5.0
) {
    private val strategy = ScalpingStrategy()
    private val riskManager = RiskManager(
        riskPercent = riskPercent,
        stopLossPercent = 1.0,
        takeProfitPercent = 1.5,
        accountEquityUsd = accountEquityUsd,
        minNotionalUsd = minNotionalUsd
    )

    private var inPosition = false
    private var entryPrice = 0.0
    private var positionQuantity = 0.0
    private var entryAtr = 0.0
    private var running = false
    private var worker: Job? = null

    val tradeHistory = mutableListOf<Trade>()
    var cumulativePnl = 0.0
        private set

    fun start(
        scope: CoroutineScope,
        onLog: (String) -> Unit,
        onTrade: (Trade, Double) -> Unit = { _, _ -> }
    ) {
        if (running) return
        running = true
        worker = scope.launch(Dispatchers.IO) {
            onLog("[${exchange.name} - $symbol] ربات 4H شروع شد؛ ریسک ${riskManager.riskUsd()} USD")
            while (isActive && running) {
                try {
                    tick(onLog, onTrade)
                } catch (e: Exception) {
                    onLog("[${exchange.name} - $symbol] خطا: ${e.message ?: "خطای نامشخص"}")
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        running = false
        worker?.cancel()
        worker = null
    }

    private fun tick(onLog: (String) -> Unit, onTrade: (Trade, Double) -> Unit) {
        val candles = exchange.getRecentCandles(symbol, candleInterval, maxOf(20, atrPeriod + 5))
        if (candles.size < 20) {
            onLog("[${exchange.name} - $symbol] داده کافی برای 4H وجود ندارد (${candles.size}/20)")
            return
        }
        val closes = candles.map { it.close }

        val currentPrice = closes.last()
        val tag = "[${exchange.name} - $symbol]"
        val atr = if (useAtrStops) AtrCalculator.calculate(candles, atrPeriod) else 0.0

        if (inPosition) {
            val stopHit = if (useAtrStops && entryAtr > 0.0)
                riskManager.shouldStopLossAtr(entryPrice, currentPrice, entryAtr, atrSlMultiplier)
            else riskManager.shouldStopLoss(entryPrice, currentPrice)

            val profitHit = if (useAtrStops && entryAtr > 0.0)
                riskManager.shouldTakeProfitAtr(entryPrice, currentPrice, entryAtr, atrTpMultiplier)
            else riskManager.shouldTakeProfit(entryPrice, currentPrice)

            if (stopHit) {
                sell(currentPrice, onLog, onTrade, "حد ضرر فعال شد")
            } else if (profitHit) {
                sell(currentPrice, onLog, onTrade, "حد سود فعال شد")
            } else {
                onLog("$tag نظارت 4H... قیمت: $currentPrice | ورود: $entryPrice")
            }
            return
        }

        val trend = if (useTrendFilter) {
            try {
                val trendCandles = exchange.getRecentCandles(symbol, trendInterval, trendMaPeriod + 5)
                TrendDetector.detect(trendCandles.map { it.close }, trendMaPeriod)
            } catch (e: Exception) {
                Trend.UNKNOWN
            }
        } else Trend.UNKNOWN

        when (strategy.decide(closes, trend)) {
            Signal.BUY -> buy(currentPrice, atr, onLog, onTrade)
            Signal.SELL, Signal.HOLD -> onLog("$tag سیگنال ورود نیست. قیمت: $currentPrice")
        }
    }

    private fun buy(price: Double, atr: Double, onLog: (String) -> Unit, onTrade: (Trade, Double) -> Unit) {
        val quantity = if (useAtrStops && atr > 0.0)
            riskManager.calculateQuantityForAtrStop(price, atr, atrSlMultiplier)
        else riskManager.calculateQuantity(price)

        if (!riskManager.meetsMinNotional(price, quantity)) {
            onLog(
                "[${exchange.name} - $symbol] معامله رد شد: ارزش سفارش (%.2f USD) کمتر از حداقل مجازه"
                    .format(price * quantity)
            )
            return
        }

        val quantityText = "%.8f".format(java.util.Locale.US, quantity)
        val result = exchange.placeMarketOrder(symbol, "BUY", quantityText)

        entryPrice = price
        positionQuantity = quantity
        entryAtr = atr
        inPosition = true
        onLog("[${exchange.name} - $symbol] خرید انجام شد | qty=$quantityText | ارزش تقریبی=%.2f USD | پاسخ=$result".format(riskManager.positionNotionalUsd(price)))

        val trade = Trade("BUY", price, quantity, System.currentTimeMillis())
        tradeHistory.add(trade)
        onTrade(trade, cumulativePnl)
    }

    private fun sell(price: Double, onLog: (String) -> Unit, onTrade: (Trade, Double) -> Unit, reason: String) {
        if (positionQuantity <= 0.0) return
        val quantityText = "%.8f".format(java.util.Locale.US, positionQuantity)
        val result = exchange.placeMarketOrder(symbol, "SELL", quantityText)
        val pnl = (price - entryPrice) * positionQuantity
        cumulativePnl += pnl
        inPosition = false

        onLog("[${exchange.name} - $symbol] فروش انجام شد ($reason) | qty=$quantityText | PnL=%.4f USD | پاسخ=$result".format(pnl))
        val trade = Trade("SELL", price, positionQuantity, System.currentTimeMillis(), pnl)
        tradeHistory.add(trade)
        positionQuantity = 0.0
        entryPrice = 0.0
        entryAtr = 0.0
        onTrade(trade, cumulativePnl)
    }
}
