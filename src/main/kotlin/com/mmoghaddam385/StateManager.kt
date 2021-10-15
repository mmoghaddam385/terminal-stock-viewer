package com.mmoghaddam385

import io.polygon.kotlin.sdk.websocket.PolygonWebSocketCluster
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

class StateManager {

    private val prices = mutableMapOf<String, TickData>()
    private val warnings = mutableListOf<String>()
    private val debugMessages = mutableListOf<String>()
    private val clusterStatuses = mutableMapOf<PolygonWebSocketCluster, ClusterStatus>()

    private val _state = MutableStateFlow(State(mapOf(), mapOf(), listOf(), listOf()))
    val state = _state.asStateFlow()

    /**
     * run will emit the state once per second second to ensure our timers update even if no trades are coming through.
     */
    suspend fun run() {
        while (true) {
            delay(1000)
            mutateState { } // Don't actually mutate state, but trigger a state push so times get updated.
        }
    }

    fun onPriceUpdate(ticker: String, price: Double, time: ZonedDateTime) = mutateState {
        prices[ticker] = TickData(price, prices[ticker]?.previousClosePrice, time)
    }

    fun setPreviousClosePrice(ticker: String, price: Double) = mutateState {
        prices[ticker] = TickData(prices[ticker]?.price ?: 0.0, price, prices[ticker]?.lastUpdated
                ?: ZonedDateTime.now())
    }

    fun setClusterStatus(cluster: PolygonWebSocketCluster, status: ClusterStatus) = mutateState {
        clusterStatuses[cluster] = status

        when (status) {
            ClusterStatus.CONNECTED -> debugMessages.add("Connected to ${cluster.name} cluster!")
            ClusterStatus.DISCONNECTED -> warnings.add("Disconnected from ${cluster.name} cluster!")
        }
    }

    fun addWarning(warning: String) = mutateState {
        warnings.add(warning)
    }

    fun addDebugMessage(message: String) = mutateState {
        debugMessages.add(message)
    }

    /**
     * mutateState runs a blocking co-routine that's synchronized such that only one call to mutateState can be running at a time.
     * After running the provided block, a new state is emitted onto the StateFlow.
     */
    private fun mutateState(block: suspend () -> Unit) = synchronized(this) {
        runBlocking {
            block()
            _state.emit(State(prices.toMap(), clusterStatuses.toMap(), warnings.toList(), debugMessages.toList()))
        }
    }
}