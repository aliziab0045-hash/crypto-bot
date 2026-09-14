package com.example.cryptobot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیره دائمی تاریخچه معاملات و سود/زیان تجمعی هر اسلات،
 * تا با بستن یا کرش کردن اپ، این اطلاعات از بین نره.
 * از SharedPreferences با فرمت JSON استفاده می‌کنه (ساده و بدون نیاز به دیتابیس).
 */
object TradeHistoryStore {
    private const val PREFS_NAME = "trade_history_store"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** ذخیره لیست معاملات (هر کدام همراه با اسم ارز) برای یک اسلات مشخص */
    fun save(context: Context, slotKey: String, trades: List<Pair<String, Trade>>, pnlBySymbol: Map<String, Double>) {
        val tradesArray = JSONArray()
        for ((symbol, trade) in trades) {
            val obj = JSONObject()
            obj.put("symbol", symbol)
            obj.put("type", trade.type)
            obj.put("price", trade.price)
            obj.put("quantity", trade.quantity)
            obj.put("timestamp", trade.timestamp)
            if (trade.pnl != null) obj.put("pnl", trade.pnl)
            tradesArray.put(obj)
        }

        val pnlObj = JSONObject()
        for ((symbol, pnl) in pnlBySymbol) pnlObj.put(symbol, pnl)

        val root = JSONObject()
        root.put("trades", tradesArray)
        root.put("pnl", pnlObj)

        prefs(context).edit().putString(slotKey, root.toString()).apply()
    }

    /** بازخوانی معاملات و سود/زیان ذخیره‌شده یک اسلات؛ اگه چیزی ذخیره نشده بود، خالی برمی‌گرده */
    fun load(context: Context, slotKey: String): Pair<MutableList<Pair<String, Trade>>, MutableMap<String, Double>> {
        val raw = prefs(context).getString(slotKey, null)
            ?: return mutableListOf<Pair<String, Trade>>() to mutableMapOf()

        return try {
            val root = JSONObject(raw)
            val tradesArray = root.getJSONArray("trades")
            val trades = mutableListOf<Pair<String, Trade>>()
            for (i in 0 until tradesArray.length()) {
                val obj = tradesArray.getJSONObject(i)
                val trade = Trade(
                    type = obj.getString("type"),
                    price = obj.getDouble("price"),
                    quantity = obj.getDouble("quantity"),
                    timestamp = obj.getLong("timestamp"),
                    pnl = if (obj.has("pnl")) obj.getDouble("pnl") else null
                )
                trades.add(obj.getString("symbol") to trade)
            }

            val pnlObj = root.getJSONObject("pnl")
            val pnlMap = mutableMapOf<String, Double>()
            val keys = pnlObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                pnlMap[key] = pnlObj.getDouble(key)
            }

            trades to pnlMap
        } catch (e: Exception) {
            mutableListOf<Pair<String, Trade>>() to mutableMapOf()
        }
    }

    /** پاک کردن تاریخچه ذخیره‌شده یک اسلات (مثلاً موقع شروع دوره معاملاتی جدید) */
    fun clear(context: Context, slotKey: String) {
        prefs(context).edit().remove(slotKey).apply()
    }
}
