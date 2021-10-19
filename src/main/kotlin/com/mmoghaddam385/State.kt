package com.mmoghaddam385

import io.polygon.kotlin.sdk.websocket.PolygonWebSocketCluster
import org.fusesource.jansi.Ansi
import java.text.NumberFormat
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

enum class ClusterStatus {
    CONNECTED,
    DISCONNECTED,
}

data class TickData(val price: Double, val previousClosePrice: Double?, val lastUpdated: ZonedDateTime)

private data class TickerDisplayData(
        val ticker: String,
        val price: String,
        val percentChange: String,
        val priceLastUpdated: String,
        val color: Ansi.Color,
        val strikeThrough: Boolean,
)

/**
 * State is an immutable representation of the state of all the tickers at a single point in time.
 * This class also knows how to render itself as a string for display.
 */
data class State(
        val prices: Map<String, TickData>,
        val clusterStatuses: Map<PolygonWebSocketCluster, ClusterStatus>,
        val warnings: List<String>,
        val debugMessages: List<String>,
        val lastUpdated: ZonedDateTime = ZonedDateTime.now()
) {

    private val priceFormatter = NumberFormat.getCurrencyInstance(Locale.US) // Hard coded to USD for now
    private val percentageFormatter = NumberFormat.getPercentInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /**
     * render returns a formatted string representing this state.
     */
    fun render(debugMode: Boolean) = buildString {
        append(Ansi.ansi().bold().a("Terminal Stock Viewer\n\n").boldOff())

        // append debug messages
        if (debugMode && debugMessages.isNotEmpty()) {
            append("Debug Logs:\n")
            debugMessages.forEach { append("\t$it\n") }
            append("\n")
        }

        // append warning messages
        if (warnings.isNotEmpty()) {
            append("Warnings:\n")
            warnings.forEach { append("\t$it\n") }
        }

        // append the price data
        append(renderPrices())
    }

    private fun renderPrices(): String {
        if (prices.isEmpty()) {
            return "no data!"
        }

        val rows = mutableListOf<TickerDisplayData>()

        // Prepare all the display data
        for ((ticker, data) in prices) {
            val percentChange = data.previousClosePrice?.let {
                val value = (data.price - it) / it
                if (value >= 0) {
                    return@let "+" + percentageFormatter.format(value) // Explicitly show a plus sign for positive numbers
                } else {
                    return@let percentageFormatter.format(value) // The minus sign is automatically added here
                }
            } ?: ""

            rows.add(TickerDisplayData(
                    ticker = ticker,
                    price = priceFormatter.format(data.price),
                    percentChange = percentChange,
                    priceLastUpdated = "[${Duration.between(data.lastUpdated, ZonedDateTime.now()).formatForDisplay()} old]",
                    color = data.previousClosePrice?.let { if (data.price > it) Ansi.Color.GREEN else Ansi.Color.RED }
                            ?: Ansi.Color.DEFAULT,
                    strikeThrough = clusterStatuses[polygonClusterByTicker(ticker)] == ClusterStatus.DISCONNECTED,
            ))
        }

        // Render the actual string into a table per cluster.
        return buildString {
            rows.groupBy { polygonClusterByTicker(it.ticker) }.forEach { (cluster, displayData) ->
                val longestTicker = displayData.maxOf { it.ticker.length }
                val longestPrice = displayData.maxOf { it.price.length }
                val longestPercentChange = displayData.maxOf { it.percentChange.length }
                val longestPriceLastUpdated = displayData.maxOf { it.priceLastUpdated.length }
                val longestRow = longestTicker + longestPrice + longestPercentChange + longestPriceLastUpdated + 7

                append("\n${cluster.name}:\n")
                append("┌" + "─".repeat(longestRow) + "┐\n") // Table top row

                // Table rows
                displayData.sortedBy { it.ticker }.map {
                    Ansi.ansi().run {
                        if (it.strikeThrough) {
                            a(Ansi.Attribute.STRIKETHROUGH_ON)
                        }

                        fg(it.color).bold().a(it.ticker.padEnd(longestTicker)).boldOff().fgDefault()
                        a(" │ ")

                        fg(it.color)

                        bold().a(it.price.padEnd(longestPrice)).boldOff().a(" ")
                        a(it.percentChange.padEnd(longestPercentChange)).a(" ")
                        a(it.priceLastUpdated.padEnd(longestPriceLastUpdated))

                        fgDefault()

                        if (it.strikeThrough) {
                            a(Ansi.Attribute.STRIKETHROUGH_OFF)
                        }

                        return@run this
                    }
                }.forEach {
                    append("│ ")
                    append(it)
                    append(" │\n")
                }

                append("└" + "─".repeat(longestRow) + "┘") // Table bottom row
            }

        }

    }

    private fun Duration.formatForDisplay(): String {
        if (toSeconds() < 1) { // we don't care about millis here
            return "<1s"
        }

        var duration = this
        var forceDisplay = false

        return buildString {
            if (duration.toDays() > 0) {
                append("${duration.toDays()}d")
                duration = duration.minusDays(duration.toDays())
                forceDisplay = true // Now that days are shown, show everything else
            }

            if (forceDisplay || duration.toHours() > 0) {
                append("${duration.toHours().toString().padStart(2)}h")
                duration = duration.minusHours(duration.toHours())
                forceDisplay = true // Now that hours are shown, show everything else
            }

            if (forceDisplay || duration.toMinutes() > 0) {
                append("${duration.toMinutes().toString().padStart(2)}m")
                duration = duration.minusMinutes(duration.toMinutes())
                forceDisplay = true // Now that minutes are shown, show everything else
            }

            if (forceDisplay || duration.toSeconds() > 0) {
                append("${duration.toSeconds().toString().padStart(2)}s")
            }
        }
    }
}
