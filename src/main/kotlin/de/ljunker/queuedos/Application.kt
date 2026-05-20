package de.ljunker.queuedos

import de.ljunker.queuedos.api.configureRoutes
import de.ljunker.queuedos.api.configureStatusPages
import de.ljunker.queuedos.config.appJson
import de.ljunker.queuedos.persistence.DataStore
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import java.nio.file.Path

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port) {
        module()
    }.start(wait = true)
}

fun Application.module(
    store: DataStore = dataStoreFromEnvironment()
) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(appJson)
    }
    configureStatusPages()
    configureRoutes(store)
}

private fun dataStoreFromEnvironment(): DataStore =
    DataStore(
        dataFile = Path.of(System.getenv("QUEUEDOS_DATA_FILE") ?: "data/queuedos.json"),
        json = appJson
    )
