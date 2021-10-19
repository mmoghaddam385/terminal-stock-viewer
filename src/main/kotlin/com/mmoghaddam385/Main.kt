package com.mmoghaddam385

import com.xenomachina.argparser.SystemExitException
import io.polygon.kotlin.sdk.websocket.PolygonWebSocketCluster
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import java.io.OutputStreamWriter
import kotlin.system.exitProcess

fun main(args: Array<out String>) = runBlocking {
    AnsiConsole.systemInstall()

    val config = try {
        getConfig(args)
    } catch (e: SystemExitException) {
        OutputStreamWriter(System.err).use { writer ->
            e.printUserMessage(
                    writer,
                    "terminal-stock-viewer",
                    System.getenv("COLUMNS")?.toInt() ?: 100
            )
        }

        exitProcess(e.returnCode)
    }

    val stateManager = StateManager()
    val priceRetriever = PriceRetriever(config, stateManager)

    launch {
        stateManager.run()
    }

    launch {
        priceRetriever.run()
    }

    launch {
        drawStateUpdates(config, stateManager.state)
    }

    AnsiConsole.systemUninstall()
}

suspend fun drawStateUpdates(config: Config, stateFlow: StateFlow<State>) {
    stateFlow.onEach { state ->
        println(Ansi.ansi().eraseScreen().render(state.render(config.debugMode)))
        delay(100) // Don't refresh the screen too often, it causes tearing
    }.collect()
}


fun polygonClusterByTicker(ticker: String): PolygonWebSocketCluster =
        when {
            ticker.startsWith("X:") -> PolygonWebSocketCluster.Crypto
            ticker.startsWith("C:") -> PolygonWebSocketCluster.Forex
            else -> PolygonWebSocketCluster.Stocks
        }