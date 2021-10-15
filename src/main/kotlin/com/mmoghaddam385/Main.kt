package com.mmoghaddam385

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole

fun main() = runBlocking {
    AnsiConsole.systemInstall()

    val config = getConfig()
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
        delay(100)
    }.collect()
}