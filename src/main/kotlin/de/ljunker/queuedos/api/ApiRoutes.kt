package de.ljunker.queuedos.api

import de.ljunker.queuedos.application.BadRequestFailure
import de.ljunker.queuedos.application.NotFoundFailure
import de.ljunker.queuedos.application.QueueDosServices
import de.ljunker.queuedos.domain.Priority
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

internal fun Application.configureRoutes(services: QueueDosServices) {
    routing {
        get("/") {
            call.respondFrontendAsset("index.html")
        }

        get("/api/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        post("/api/auth/login") {
            call.respond(services.auth.login(call.receive<LoginRequest>().toCommand()).toResponse())
        }

        authenticated {
            get("/api/bootstrap") {
                call.respond(services.queries.bootstrap(call.actor()).toResponse())
            }

            get("/api/tickets") {
                val actor = call.actor()
                val query = call.request.queryParameters
                call.respond(
                    services.queries.tickets(
                        actor = actor,
                        projectId = query["projectId"],
                        query = query["q"],
                        statusId = query["statusId"],
                        typeId = query["typeId"],
                        priority = query["priority"]?.takeIf { it.isNotBlank() }?.let { parsePriority(it) },
                        assigneeId = query["assigneeId"],
                        label = query["label"],
                        sort = query["sort"]
                    ).map { it.toResponse() }
                )
            }

            get("/api/tickets/{id}") {
                call.respond(services.queries.ticketDetail(call.actor(), call.pathId()).toResponse())
            }

            post("/api/tickets") {
                call.respond(
                    HttpStatusCode.Created,
                    services.tickets.create(call.actor(), call.receive<CreateTicketRequest>().toCommand()).toResponse()
                )
            }

            post("/api/tickets/bulk-update") {
                call.respond(
                    services.tickets.bulkUpdate(call.actor(), call.receive<BulkUpdateTicketsRequest>().toCommand())
                        .map { it.toResponse() }
                )
            }

            put("/api/tickets/{id}") {
                call.respond(
                    services.tickets.update(
                        call.actor(),
                        call.pathId(),
                        call.receive<UpdateTicketRequest>().toCommand()
                    ).toResponse()
                )
            }

            post("/api/tickets/{id}/transition") {
                call.respond(
                    services.tickets.transition(
                        call.actor(),
                        call.pathId(),
                        call.receive<TransitionTicketRequest>().toCommand()
                    ).toResponse()
                )
            }

            post("/api/tickets/{id}/comments") {
                call.respond(
                    HttpStatusCode.Created,
                    services.tickets.addComment(
                        call.actor(),
                        call.pathId(),
                        call.receive<CreateTicketCommentRequest>().toCommand()
                    ).toResponse()
                )
            }

            delete("/api/tickets/{id}") {
                services.tickets.delete(call.actor(), call.pathId())
                call.respond(HttpStatusCode.NoContent)
            }

            post("/api/projects") {
                call.respond(
                    HttpStatusCode.Created,
                    services.projects.create(call.actor(), call.receive<CreateProjectRequest>().toCommand())
                        .toResponse()
                )
            }

            put("/api/projects/{id}") {
                call.respond(
                    services.projects.update(
                        call.actor(),
                        call.pathId(),
                        call.receive<UpdateProjectRequest>().toCommand()
                    ).toResponse()
                )
            }

            post("/api/users") {
                call.respond(
                    HttpStatusCode.Created,
                    services.users.create(call.actor(), call.receive<CreateUserRequest>().toCommand()).toResponse()
                )
            }

            put("/api/users/{id}") {
                call.respond(
                    services.users.update(call.actor(), call.pathId(), call.receive<UpdateUserRequest>().toCommand())
                        .toResponse()
                )
            }

            post("/api/ticket-types") {
                call.respond(
                    HttpStatusCode.Created,
                    services.ticketTypes.create(call.actor(), call.receive<CreateTicketTypeRequest>().toCommand())
                        .toResponse()
                )
            }

            put("/api/ticket-types/{id}") {
                call.respond(
                    services.ticketTypes.update(
                        call.actor(),
                        call.pathId(),
                        call.receive<UpdateTicketTypeRequest>().toCommand()
                    )
                        .toResponse()
                )
            }

            delete("/api/ticket-types/{id}") {
                services.ticketTypes.delete(call.actor(), call.pathId())
                call.respond(HttpStatusCode.NoContent)
            }

            put("/api/projects/{id}/workflow") {
                call.respond(
                    services.workflows.save(
                        call.actor(),
                        call.pathId(),
                        call.receive<SaveWorkflowRequest>().toCommand()
                    ).toResponse()
                )
            }

            post("/api/saved-ticket-filters") {
                call.respond(
                    HttpStatusCode.Created,
                    services.savedTicketFilters.create(
                        call.actor(),
                        call.receive<CreateSavedTicketFilterRequest>().toCommand()
                    ).toResponse()
                )
            }

            put("/api/saved-ticket-filters/{id}") {
                call.respond(
                    services.savedTicketFilters.update(
                        call.actor(),
                        call.pathId(),
                        call.receive<UpdateSavedTicketFilterRequest>().toCommand()
                    ).toResponse()
                )
            }

            delete("/api/saved-ticket-filters/{id}") {
                services.savedTicketFilters.delete(call.actor(), call.pathId())
                call.respond(HttpStatusCode.NoContent)
            }
        }

        get("/{assetPath...}") {
            val assetPath = call.parameters.getAll("assetPath")?.joinToString("/") ?: "index.html"
            call.respondFrontendAsset(assetPath)
        }
    }
}

private fun ApplicationCall.pathId(): String =
    parameters["id"] ?: throw BadRequestFailure("Missing path id.")

private fun parsePriority(value: String): Priority =
    runCatching { Priority.valueOf(value.uppercase()) }.getOrElse {
        throw BadRequestFailure("Unknown priority.")
    }

private suspend fun ApplicationCall.respondFrontendAsset(path: String) {
    val normalized = path.trim('/').ifBlank { "index.html" }
    if (normalized == "api" || normalized.startsWith("api/") || normalized.contains("..")) {
        throw NotFoundFailure("Resource not found.")
    }

    val assetPath = "static/$normalized"
    val resourcePath = when {
        Thread.currentThread().contextClassLoader.getResource(assetPath) != null -> assetPath
        "." !in normalized.substringAfterLast('/') -> "static/index.html"
        else -> throw NotFoundFailure("Resource not found.")
    }
    val bytes = Thread.currentThread().contextClassLoader.getResource(resourcePath)?.readBytes()
        ?: throw NotFoundFailure("Resource not found.")

    respondBytes(bytes, contentTypeFor(resourcePath))
}

private fun contentTypeFor(path: String): ContentType =
    when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> ContentType.Text.Html
        "css" -> ContentType.Text.CSS
        "js", "mjs" -> ContentType.Application.JavaScript
        "json", "map" -> ContentType.Application.Json
        "svg" -> ContentType.parse("image/svg+xml")
        "png" -> ContentType.parse("image/png")
        "jpg", "jpeg" -> ContentType.parse("image/jpeg")
        "ico" -> ContentType.parse("image/x-icon")
        "txt" -> ContentType.Text.Plain
        else -> ContentType.Application.OctetStream
    }

@Serializable
private data class HealthResponse(
    val status: String
)
