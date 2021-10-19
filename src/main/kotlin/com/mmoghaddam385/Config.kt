package com.mmoghaddam385

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default

data class Config(
        val apiKey: String,
        val tickers: List<String>,
        val websocketDomain: String = "socket.polygon.io",
        val restDomain: String = "api.polygon.io",
        val debugMode: Boolean = false
)

class Args(parser: ArgParser) {
    val websocketDomain by parser.storing("--websocket-domain",
            help = "The Polygon.io domain to use when connecting websockets. Defaults to 'socket.polygon.io'"
    ).default("socket.polygon.io")

    val restDomain by parser.storing("--rest-domain",
            help = "The Polygon.io domain to use when making RESTful API requests. Defaults to 'api.polygon.io'"
    ).default("api.polygon.io")

    val debugMode by parser.flagging("-d", "--debug",
            help = "Enables debug mode. Extra log messages are displayed in debug mode."
    )

    val tickers by parser.positionalList(
            name = "TICKERS",
            sizeRange = 1..Int.MAX_VALUE,
            help = "The list of tickers to subscribe to. Can be any asset class, assuming the provided key has the corresponding entitlements"
    )
}

@Throws(SystemExitException::class)
fun getConfig(args: Array<out String>): Config {
    val helpFormatter = DefaultHelpFormatter(epilogue = """
        Also note that this program expects the environment variable POLYGON_API_KEY to be set and contain a valid polygon.io apikey.
        The key needs to be entitled to access real-time data for the asset classes of whatever tickers you provide as arguments.
        For more info visit https://polygon.io/pricing
    """.trimIndent())

    val parsedArgs = ArgParser(args, helpFormatter = helpFormatter).parseInto(::Args)

    val apiKey = System.getenv("POLYGON_API_KEY")
    if (apiKey.isNullOrEmpty()) {
        throw SystemExitException("Missing POLYGON_API_KEY env var", 1)
    }

    return Config(
            apiKey = apiKey,
            tickers = parsedArgs.tickers,
            websocketDomain = parsedArgs.websocketDomain,
            restDomain = parsedArgs.restDomain,
            debugMode = parsedArgs.debugMode
    )
}
