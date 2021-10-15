package com.mmoghaddam385

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi
import java.text.NumberFormat
import java.util.*

data class TickerPrice(val ticker: String, val price: Double)

data class State(val prices: Map<String, Double>, val warnings: List<String>, val debugMessages: List<String>) {

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US) // Hard coded to USD for now

    fun render(debugMode: Boolean) = buildString {
        append(Ansi.ansi().bold().a("Terminal Stock Viewer\n\n").boldOff())

        if (debugMode && debugMessages.isNotEmpty()) {
            append("Debug Logs:\n")
            debugMessages.forEach { append("\t$it\n") }
            append("\n")
        }

        if (warnings.isNotEmpty()) {
            append("Warnings:\n")
            warnings.forEach { append("\t$it\n") }
            append("\n")
        }

        append(renderPrices())
    }

    private fun renderPrices(): String {
        if (prices.isEmpty()) {
            return "no data!"
        }

        val longestTicker = prices.keys.maxOf { it.length }

        return buildString {
            for ((ticker, price) in prices) {
                append("${ticker.padEnd(longestTicker)} -> ${numberFormat.format(price)}\n")
            }
        }
    }
}

class StateManager {
    private val priceUpdateChannel = Channel<TickerPrice>(10_000)

    private val prices = mutableMapOf<String, Double>()
    private val warnings = mutableListOf<String>()
    private val debugMessages = mutableListOf<String>()

    private val _state = MutableStateFlow(State(mapOf(), listOf(), listOf()))
    val state = _state.asStateFlow()

    suspend fun run() {
        for (update in priceUpdateChannel) {
            prices[update.ticker] = update.price

            val syncWarnings = synchronized(warnings) { warnings.toList() }
            val syncDebugMessages = synchronized(debugMessages) { debugMessages.toList() }

            _state.emit(State(prices.toMap(), syncWarnings, syncDebugMessages))
        }
    }

    fun onPriceUpdate(ticker: String, price: Double) = runBlocking {
        priceUpdateChannel.send(TickerPrice(ticker, price))
    }

    // TODO: adding a warning or debug message doesn't trigger a new state push

    fun addWarning(warning: String) = synchronized(warnings) {
        warnings.add(warning)
    }

    fun addDebugMessage(message: String) = synchronized(debugMessages) {
        debugMessages.add(message)
    }

}