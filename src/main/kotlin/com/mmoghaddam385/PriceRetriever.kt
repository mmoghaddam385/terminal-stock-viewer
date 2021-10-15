package com.mmoghaddam385

import io.polygon.kotlin.sdk.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PriceRetriever(private val config: Config, private val stateManager: StateManager) : PolygonWebSocketListener {

    private val websocketClients = mapOf(
            PolygonWebSocketCluster.Crypto to PolygonWebSocketClient(
                    apiKey = config.apiKey,
                    cluster = PolygonWebSocketCluster.Crypto,
                    polygonWebSocketDomain = config.baseDomain,
                    listener = this
            ),
            PolygonWebSocketCluster.Forex to PolygonWebSocketClient(
                    apiKey = config.apiKey,
                    cluster = PolygonWebSocketCluster.Forex,
                    polygonWebSocketDomain = config.baseDomain,
                    listener = this
            ),
            PolygonWebSocketCluster.Stocks to PolygonWebSocketClient(
                    apiKey = config.apiKey,
                    cluster = PolygonWebSocketCluster.Stocks,
                    polygonWebSocketDomain = config.baseDomain,
                    listener = this
            )
    )

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
        for ((cluster, subscriptions) in subscriptionsByCluster) {
            websocketClients[cluster]?.let { client ->
                launch {
                    client.connectBlocking()
                    client.subscribeBlocking(subscriptions)
                }
            }
        }
    }

    private fun polygonClusterByTicker(ticker: String): PolygonWebSocketCluster =
            when {
                ticker.startsWith("X:") -> PolygonWebSocketCluster.Crypto
                ticker.startsWith("C:") -> PolygonWebSocketCluster.Forex
                else -> PolygonWebSocketCluster.Stocks
            }


    override fun onAuthenticated(client: PolygonWebSocketClient) {
        stateManager.addDebugMessage("Connected to ${client.cluster.name} cluster!")
    }

    override fun onDisconnect(client: PolygonWebSocketClient) {
        stateManager.addWarning("Disconnected from ${client.cluster.name} cluster!")
    }

    override fun onError(client: PolygonWebSocketClient, error: Throwable) {
        stateManager.addWarning("Error from ${client.cluster.name} cluster: ${error.message}")
    }

    override fun onReceive(client: PolygonWebSocketClient, message: PolygonWebSocketMessage) {
        when (message) {
            is PolygonWebSocketMessage.StatusMessage -> handleStatusMessage(message)
            is PolygonWebSocketMessage.CryptoMessage.Trade -> stateManager.onPriceUpdate(message.cryptoPair!!, message.price!!)
            is PolygonWebSocketMessage.ForexMessage.Quote -> stateManager.onPriceUpdate(message.currencyPair!!, message.bidPrice!!)
            is PolygonWebSocketMessage.StocksMessage.Trade -> stateManager.onPriceUpdate(message.ticker!!, message.price!!)
            else -> stateManager.addWarning("Unexpected websocket message: $message")
        }
    }

    private fun handleStatusMessage(message: PolygonWebSocketMessage.StatusMessage) {
        when (message.status) {
            "connected", "success" -> stateManager.addDebugMessage("Good status message: ${message.message}")
            else -> stateManager.addWarning("Bad status message: ${message.message}")
        }
    }
}