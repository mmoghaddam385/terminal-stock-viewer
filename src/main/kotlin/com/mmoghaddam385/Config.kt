package com.mmoghaddam385

data class Config(
        val apiKey: String,
        val tickers: List<String>,
        val baseDomain: String = "socket.polygon.io",
        val debugMode: Boolean = false
)

fun getConfig(): Config {
    val apiKey = System.getenv("POLYGON_API_KEY")
    if (apiKey.isNullOrEmpty()) {
        throw Exception("Missing POLYGON_API_KEY env var")
    }

    // TODO: Make this actually configurable via file or something
    return Config(apiKey = apiKey, tickers = listOf("X:BTC-USD", "C:EUR-USD", "X:ETH-USD"))
}
