package com.mmoghaddam385

import io.polygon.kotlin.sdk.rest.PolygonRestClient
import io.polygon.kotlin.sdk.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PriceRetriever(private val config: Config, private val stateManager: StateManager) : PolygonWebSocketListener {

    private val restClient = PolygonRestClient(
            apiKey = config.apiKey,
            polygonApiDomain = config.restDomain
    )

    /**
     * We need an instance of PolygonWebSocketClient for each cluster we might connect to,
     * since cluster is a property of the client object.
     *
     * I wonder who designed it like that... :eyes:
     */
    private val websocketClients = mapOf(
            PolygonWebSocketCluster.Crypto to PolygonWebSocketClient(
                    apiKey = config.apiKey,
                    cluster = PolygonWebSocketCluster.Crypto,
                    polygonWebSocketDomain = config.websocketDomain,
                    listener = this
            ),
            PolygonWebSocketCluster.Forex to PolygonWebSocketClient(
                    apiKey = config.apiKey,
                    cluster = PolygonWebSocketCluster.Forex,
                    polygonWebSocketDomain = config.websocketDomain,
                    listener = this
            ),
            PolygonWebSocketCluster.Stocks to PolygonWebSocketClient(
                    apiKey = config.apiKey,
                    cluster = PolygonWebSocketCluster.Stocks,
                    polygonWebSocketDomain = config.websocketDomain,
                    listener = this
            )
    )

    /**
     * subscriptionsByCluster is a map of cluster to list of subscriptions.
     * Grouping them like this helps since we have to connect to various different websockets depending on the type of ticker.
     */
    private val subscriptionsByCluster by lazy {
        config.tickers
                .associateWith { polygonClusterByTicker(it) }
                .entries.associate { (ticker, cluster) ->
                    ticker to when (cluster) {
                        PolygonWebSocketCluster.Crypto -> PolygonWebSocketChannel.Crypto.Trades
                        PolygonWebSocketCluster.Forex -> PolygonWebSocketChannel.Forex.Quotes
                        else -> PolygonWebSocketChannel.Stocks.Trades
                    }
                }
                .map { (ticker, channel) ->
                    PolygonWebSocketSubscription(channel, ticker)
                }
                .groupBy { polygonClusterByTicker(it.symbol) }
    }

    fun run() = runBlocking {
        launch { retrievePreviousClosePrices() } // Get previous close prices in the background

        retrieveInitialPrices()

        // Connect and subscribe to whichever websockets we need to
        for ((cluster, subscriptions) in subscriptionsByCluster) {
            websocketClients[cluster]?.let { client ->
                launch {
                    client.connectBlocking()
                    client.subscribeBlocking(subscriptions)
                }
            }
        }
    }

    private fun retrievePreviousClosePrices() {
        subscriptionsByCluster.flatMap { it.value }.forEach { sub ->
            // Everyone uses the same prev-close endpoint
            // But it doesn't like '-' in crypto/fx pairs...
            val results = restClient.stocksClient.getPreviousCloseBlocking(sub.symbol.replace("-", ""), false).results

            if (results.isNotEmpty()) {
                results[0].close?.let { stateManager.setPreviousClosePrice(sub.symbol, it) }
            }
        }
    }

    private fun retrieveInitialPrices() {
        // Get initial crypto ticker prices
        subscriptionsByCluster[PolygonWebSocketCluster.Crypto]?.let { subs ->
            for (sub in subs) {
                val pair = sub.symbol.substring(2).split('-')
                if (pair.size != 2) {
                    stateManager.addWarning("Could not parse crypto ticker: ${sub.symbol}")
                    continue
                }

                val lastTrade = restClient.cryptoClient.getLastTradeBlocking(pair[0], pair[1]).last
                if (lastTrade.price != null && lastTrade.timestamp != null) {
                    stateManager.onPriceUpdate(sub.symbol, lastTrade.price!!, zonedDateTimeFromTimestamp(lastTrade.timestamp!!))
                } else {
                    stateManager.addWarning("couldn't get initial price for: ${sub.symbol}")
                }
            }
        }

        // Get initial forex ticker prices
        subscriptionsByCluster[PolygonWebSocketCluster.Forex]?.let { subs ->
            for (sub in subs) {
                val pair = sub.symbol.substring(2).split('-')
                if (pair.size != 2) {
                    stateManager.addWarning("Could not parse forex ticker: ${sub.symbol}")
                    continue
                }

                val lastQuote = restClient.forexClient.getLastQuoteBlocking(pair[0], pair[1]).last
                if (lastQuote.bid != null && lastQuote.timestamp != null) {
                    stateManager.onPriceUpdate(sub.symbol, lastQuote.bid!!, zonedDateTimeFromTimestamp(lastQuote.timestamp!!))
                } else {
                    stateManager.addWarning("couldn't get initial price for: ${sub.symbol}")
                }
            }
        }

        // Get initial crypto ticker prices
        subscriptionsByCluster[PolygonWebSocketCluster.Stocks]?.let { subs ->
            for (sub in subs) {
                restClient.stocksClient.getLastTradeBlocking(sub.symbol).lastTrade?.let { lastTrade ->
                    if (lastTrade.price != null && lastTrade.timestamp != null) {
                        stateManager.onPriceUpdate(sub.symbol, lastTrade.price!!, zonedDateTimeFromTimestamp(lastTrade.timestamp!!))
                    } else {
                        stateManager.addWarning("couldn't get initial price for: ${sub.symbol}")
                    }
                }
            }
        }
    }

    override fun onAuthenticated(client: PolygonWebSocketClient) {
        stateManager.addDebugMessage("Connected to ${client.cluster.name} cluster!")
    }

    override fun onDisconnect(client: PolygonWebSocketClient) {
        stateManager.setClusterStatus(client.cluster, ClusterStatus.DISCONNECTED)
    }

    override fun onError(client: PolygonWebSocketClient, error: Throwable) {
        stateManager.addWarning("Error from ${client.cluster.name} cluster: ${error.message}")
    }

    override fun onReceive(client: PolygonWebSocketClient, message: PolygonWebSocketMessage) {
        when (message) {
            is PolygonWebSocketMessage.StatusMessage -> handleStatusMessage(client, message)
            is PolygonWebSocketMessage.CryptoMessage.Trade ->
                stateManager.onPriceUpdate("X:" + message.cryptoPair!!, message.price!!, zonedDateTimeFromTimestamp(message.exchangeTimestampMillis!!))
            is PolygonWebSocketMessage.ForexMessage.Quote ->
                stateManager.onPriceUpdate("C:" + message.currencyPair!!.replace('/', '-'), message.bidPrice!!, zonedDateTimeFromTimestamp(message.timestampMillis!!))
            is PolygonWebSocketMessage.StocksMessage.Trade ->
                stateManager.onPriceUpdate(message.ticker!!, message.price!!, zonedDateTimeFromTimestamp(message.timestampMillis!!))
            else -> stateManager.addWarning("Unexpected websocket message: $message")
        }
    }

    private fun handleStatusMessage(client: PolygonWebSocketClient, message: PolygonWebSocketMessage.StatusMessage) {
        when (message.status) {
            "connected" -> stateManager.setClusterStatus(client.cluster, ClusterStatus.CONNECTED)
            "success" -> stateManager.addDebugMessage("Good status message: ${message.message}")
            else -> stateManager.addWarning("Unexpected status message: ${message.message}")
        }
    }

    private fun zonedDateTimeFromTimestamp(millis: Long) =
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
}