package de.ljunker.queuedos.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException

internal fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.message ?: "Request failed."))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("Invalid JSON request."))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError(cause.message ?: "Invalid request."))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled request failure", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Unexpected server error."))
        }
    }
}
