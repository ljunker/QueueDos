package de.ljunker.queuedos

import de.ljunker.queuedos.api.configureRoutes
import de.ljunker.queuedos.api.configureStatusPages
import de.ljunker.queuedos.config.appJson
import de.ljunker.queuedos.persistence.FileAppDataStorage
import de.ljunker.queuedos.persistence.DataStore
import de.ljunker.queuedos.persistence.PostgreSqlAppDataStorage
import de.ljunker.queuedos.security.AuthTokenCodec
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import java.nio.file.Path
import java.time.Duration

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

private fun dataStoreFromEnvironment(): DataStore {
    val tokenCodec = AuthTokenCodec(
        secret = System.getenv("QUEUEDOS_SESSION_SECRET") ?: "queuedos-development-session-secret-change-me",
        ttl = Duration.ofHours(System.getenv("QUEUEDOS_SESSION_TTL_HOURS")?.toLongOrNull() ?: 12)
    )
    val databaseUrl = System.getenv("QUEUEDOS_DATABASE_URL")?.takeIf { it.isNotBlank() }
    val storage = if (databaseUrl == null) {
        FileAppDataStorage(
            dataFile = Path.of(System.getenv("QUEUEDOS_DATA_FILE") ?: "data/queuedos.json"),
            json = appJson
        )
    } else {
        PostgreSqlAppDataStorage(
            jdbcUrl = databaseUrl,
            username = System.getenv("QUEUEDOS_DATABASE_USER"),
            password = System.getenv("QUEUEDOS_DATABASE_PASSWORD"),
            json = appJson,
            legacySnapshotId = System.getenv("QUEUEDOS_LEGACY_DATABASE_STATE_ID") ?: "default"
        )
    }
    return DataStore(storage, tokenCodec)
}
