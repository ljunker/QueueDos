package de.ljunker.queuedos.api

import de.ljunker.queuedos.application.FailureKind
import de.ljunker.queuedos.application.QueueDosFailure
import de.ljunker.queuedos.config.appJson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
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
            if (cause.kind == FailureKind.UNAUTHORIZED && call.request.path() == "/mcp") {
                call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
            }
            call.respondApiError(status, ApiError(cause.message, cause.code, cause.currentVersion))
        }
        exception<SerializationException> { call, _ ->
            call.respondApiError(HttpStatusCode.BadRequest, ApiError("Invalid JSON request."))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondApiError(HttpStatusCode.BadRequest, ApiError(cause.message ?: "Invalid request."))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled request failure", cause)
            call.respondApiError(HttpStatusCode.InternalServerError, ApiError("Unexpected server error."))
        }
    }
}

private suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, error: ApiError) {
    respondText(appJson.encodeToString(ApiError.serializer(), error), ContentType.Application.Json, status)
}
