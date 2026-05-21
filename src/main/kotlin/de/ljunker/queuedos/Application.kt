package de.ljunker.queuedos

import de.ljunker.queuedos.api.configureApiAuthentication
import de.ljunker.queuedos.api.configureRoutes
import de.ljunker.queuedos.api.configureStatusPages
import de.ljunker.queuedos.application.BadRequestFailure
import de.ljunker.queuedos.application.QueueDosBackend
import de.ljunker.queuedos.config.appJson
import de.ljunker.queuedos.persistence.DriverManagerDataSource
import de.ljunker.queuedos.security.AuthTokenCodec
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import java.time.Duration

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port) {
        module()
    }.start(wait = true)
}

fun Application.module(
    backend: QueueDosBackend = backendFromEnvironment()
) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(appJson)
    }
    configureStatusPages()
    configureApiAuthentication(backend.services.auth)
    configureRoutes(backend.services)
}

private fun backendFromEnvironment(): QueueDosBackend {
    val tokenCodec = AuthTokenCodec(
        secret = System.getenv("QUEUEDOS_SESSION_SECRET") ?: "queuedos-development-session-secret-change-me",
        ttl = Duration.ofHours(System.getenv("QUEUEDOS_SESSION_TTL_HOURS")?.toLongOrNull() ?: 12)
    )
    val databaseUrl = System.getenv("QUEUEDOS_DATABASE_URL")?.takeIf { it.isNotBlank() }
        ?: throw BadRequestFailure("QUEUEDOS_DATABASE_URL is required.")
    val dataSource = DriverManagerDataSource(
        jdbcUrl = databaseUrl,
        username = System.getenv("QUEUEDOS_DATABASE_USER"),
        password = System.getenv("QUEUEDOS_DATABASE_PASSWORD")
    )
    return QueueDosBackend.create(dataSource, appJson, tokenCodec)
}
