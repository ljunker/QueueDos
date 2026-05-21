package de.ljunker.queuedos.api

import de.ljunker.queuedos.application.FailureKind
import de.ljunker.queuedos.application.QueueDosFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException

internal fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<QueueDosFailure> { call, cause ->
            val status = when (cause.kind) {
                FailureKind.BAD_REQUEST -> HttpStatusCode.BadRequest
                FailureKind.UNAUTHORIZED -> HttpStatusCode.Unauthorized
                FailureKind.FORBIDDEN -> HttpStatusCode.Forbidden
                FailureKind.NOT_FOUND -> HttpStatusCode.NotFound
                FailureKind.CONFLICT -> HttpStatusCode.Conflict
            }
            call.respond(status, ApiError(cause.message))
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
