package com.example.cryptobot

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cryptobot.exchanges.ExchangeFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BotSlot(
    private val activity: MainActivity,
    private val slotKey: String,
    exchangeInputId: Int,
    symbolInputId: Int,
    apiKeyInputId: Int,
    apiSecretInputId: Int,
    passphraseInputId: Int,
    startButtonId: Int,
    stopButtonId: Int,
    statusTextId: Int,
    pnlTextId: Int,
    logTextId: Int,
    logScrollId: Int,
    private val historyText: TextView,
    private val telegramTokenInput: EditText,
    private val telegramChatIdInput: EditText
) {
    private var bots: List<TradingBot> = emptyList()
    private val pnlBySymbol = mutableMapOf<String, Double>()
    private val recentTrades = mutableListOf<Pair<String, Trade>>()

    private val exchangeInput: EditText = activity.findViewById(exchangeInputId)
    private val symbolInput: EditText = activity.findViewById(symbolInputId)
    private val apiKeyInput: EditText = activity.findViewById(apiKeyInputId)
    private val apiSecretInput: EditText = activity.findViewById(apiSecretInputId)
    private val passphraseInput: EditText = activity.findViewById(passphraseInputId)
    private val startButton: Button = activity.findViewById(startButtonId)
    private val stopButton: Button = activity.findViewById(stopButtonId)
    private val statusText: TextView = activity.findViewById(statusTextId)
    private val pnlText: TextView = activity.findViewById(pnlTextId)
    private val logText: TextView = activity.findViewById(logTextId)
    private val logScroll: ScrollView = activity.findViewById(logScrollId)

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        stopButton.isEnabled = false
        startButton.setOnClickListener { start() }
        stopButton.setOnClickListener { stop() }

        // بازخوانی تاریخچه ذخیره‌شده این اسلات از دفعه قبل (اگه اپ بسته/کرش شده باشه از دست نمی‌ره)
        val (savedTrades, savedPnl) = TradeHistoryStore.load(activity, slotKey)
        recentTrades.addAll(savedTrades)
        pnlBySymbol.putAll(savedPnl)
        if (recentTrades.isNotEmpty()) {
            updateHistory()
            updateTotalPnl()
        }
    }

    private fun start() {
        val exchangeName = exchangeInput.text.toString().trim().ifEmpty { "binance" }
        val apiKey = apiKeyInput.text.toString().trim()
        val apiSecret = apiSecretInput.text.toString().trim()
        val passphrase = passphraseInput.text.toString().trim()
        val symbolsRaw = symbolInput.text.toString().trim().ifEmpty { "BTCUSDT" }
        val symbols = symbolsRaw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val telegramToken = telegramTokenInput.text.toString().trim()
        val telegramChatId = telegramChatIdInput.text.toString().trim()

        if (exchangeName.lowercase() != "demo" && (apiKey.isEmpty() || apiSecret.isEmpty())) {
            appendLog("لطفاً API Key و Secret رو وارد کن")
            return
        }

        pnlBySymbol.clear()
        recentTrades.clear()
        TradeHistoryStore.clear(activity, slotKey)

        val newBots = mutableListOf<TradingBot>()
        for (symbol in symbols) {
            try {
                val exchange = ExchangeFactory.create(exchangeName, apiKey, apiSecret, passphrase)
                val bot = TradingBot(exchange, symbol, accountEquityUsd = 100.0, riskPercent = 1.0, candleInterval = "4h")
                newBots.add(bot)
                pnlBySymbol[symbol] = 0.0

                bot.start(
                    activity.lifecycleScope,
                    onLog = { log -> activity.runOnUiThread { appendLog(log) } },
                    onTrade = { trade, cumulativePnl ->
                        activity.runOnUiThread {
                            pnlBySymbol[symbol] = cumulativePnl
                            recentTrades.add(symbol to trade)
                            updateHistory()
                            updateTotalPnl()
                            TradeHistoryStore.save(activity, slotKey, recentTrades, pnlBySymbol)

                            val title = if (trade.type == "BUY") "خرید انجام شد" else "فروش انجام شد"
                            val message = "${exchange.name} - $symbol - قیمت: %.2f".format(trade.price)
                            NotificationHelper.notify(activity, title, message)
                        }
                        val telegramMessage = buildString {
                            append(if (trade.type == "BUY") "🟢 خرید" else "🔴 فروش")
                            append(" | ${exchange.name} | $symbol\n")
                            append("تایم‌فریم: 4H\n")
                            append("قیمت: %.2f\n".format(trade.price))
                            trade.pnl?.let { append("سود/زیان: %.2f USD\n".format(it)) }
                            append("سود/زیان تجمعی این ارز: %.2f USD".format(cumulativePnl))
                        }
                        TelegramNotifier.send(telegramToken, telegramChatId, telegramMessage)
                    }
                )
            } catch (e: Exception) {
                appendLog("خطا در شروع $symbol: ${e.message}")
            }
        }

        bots = newBots
        if (newBots.isNotEmpty()) {
            statusText.text = "وضعیت: در حال اجرا روی ${exchangeName} (${symbols.joinToString(", ")})"
            startButton.isEnabled = false
            stopButton.isEnabled = true
            appendLog("ربات روی ${symbols.size} ارز شروع شد: ${symbols.joinToString(", ")}")
        }
    }

    private fun stop() {
        bots.forEach { it.stop() }
        bots = emptyList()
        statusText.text = "وضعیت: متوقف شد"
        startButton.isEnabled = true
        stopButton.isEnabled = false
        appendLog("ربات متوقف شد")
    }

    private fun appendLog(line: String) {
        logText.append("\n$line")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateTotalPnl() {
        val total = pnlBySymbol.values.sum()
        val breakdown = pnlBySymbol.entries.joinToString(" | ") { (symbol, pnl) -> "$symbol: %.2f".format(pnl) }
        pnlText.text = "سود/زیان تجمعی کل: %.2f  ($breakdown)".format(total)
    }

    private fun updateHistory() {
        if (recentTrades.isEmpty()) {
            historyText.text = "هنوز معامله‌ای ثبت نشده"
            return
        }
        val lines = recentTrades.takeLast(15).reversed().map { (symbol, trade) ->
            val time = dateFormat.format(Date(trade.timestamp))
            val typeLabel = if (trade.type == "BUY") "خرید" else "فروش"
            val pnlLabel = trade.pnl?.let { " | سود/زیان: %.2f".format(it) } ?: ""
            "$time - [$symbol] $typeLabel در %.2f$pnlLabel".format(trade.price)
        }
        historyText.text = lines.joinToString("\n")
    }
}

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val telegramTokenInput: EditText = findViewById(R.id.telegramTokenInput)
        val telegramChatIdInput: EditText = findViewById(R.id.telegramChatIdInput)

        BotSlot(
            this,
            "slot1",
            R.id.exchangeInput1, R.id.symbolInput1, R.id.apiKeyInput1, R.id.apiSecretInput1,
            R.id.passphraseInput1, R.id.startButton1, R.id.stopButton1, R.id.statusText1,
            R.id.pnlText1, R.id.logText1, R.id.logScroll1, findViewById(R.id.historyText1),
            telegramTokenInput, telegramChatIdInput
        )

        BotSlot(
            this,
            "slot2",
            R.id.exchangeInput2, R.id.symbolInput2, R.id.apiKeyInput2, R.id.apiSecretInput2,
            R.id.passphraseInput2, R.id.startButton2, R.id.stopButton2, R.id.statusText2,
            R.id.pnlText2, R.id.logText2, R.id.logScroll2, findViewById(R.id.historyText2),
            telegramTokenInput, telegramChatIdInput
        )
    }
}
