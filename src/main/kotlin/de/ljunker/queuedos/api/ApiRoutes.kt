package de.ljunker.queuedos.api

import de.ljunker.queuedos.domain.Priority
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.persistence.DataStore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

internal fun Application.configureRoutes(store: DataStore) {
    routing {
        get("/") {
            call.respondStaticResource("static/index.html", ContentType.Text.Html)
        }
        get("/style.css") {
            call.respondStaticResource("static/style.css", ContentType.Text.CSS)
        }
        get("/app.js") {
            call.respondStaticResource("static/app.js", ContentType.Application.JavaScript)
        }

        get("/api/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        post("/api/auth/login") {
            call.respond(store.login(call.receive()))
        }

        get("/api/bootstrap") {
            call.respond(store.bootstrap(call.requireUser(store)))
        }

        get("/api/tickets") {
            val actor = call.requireUser(store)
            val query = call.request.queryParameters
            call.respond(
                store.listTickets(
                    actor = actor,
                    projectId = query["projectId"],
                    query = query["q"],
                    statusId = query["statusId"],
                    typeId = query["typeId"],
                    priority = query["priority"]?.takeIf { it.isNotBlank() }?.let { parsePriority(it) },
                    assigneeId = query["assigneeId"],
                    sort = query["sort"]
                )
            )
        }

        post("/api/tickets") {
            call.respond(HttpStatusCode.Created, store.createTicket(call.requireUser(store), call.receive()))
        }

        put("/api/tickets/{id}") {
            call.respond(store.updateTicket(call.requireUser(store), call.pathId(), call.receive()))
        }

        post("/api/tickets/{id}/transition") {
            call.respond(store.transitionTicket(call.requireUser(store), call.pathId(), call.receive()))
        }

        delete("/api/tickets/{id}") {
            store.deleteTicket(call.requireUser(store), call.pathId())
            call.respond(HttpStatusCode.NoContent)
        }

        post("/api/projects") {
            call.respond(HttpStatusCode.Created, store.createProject(call.requireUser(store), call.receive()))
        }

        put("/api/projects/{id}") {
            call.respond(store.updateProject(call.requireUser(store), call.pathId(), call.receive()))
        }

        post("/api/users") {
            call.respond(HttpStatusCode.Created, store.createUser(call.requireUser(store), call.receive()))
        }

        put("/api/users/{id}") {
            call.respond(store.updateUser(call.requireUser(store), call.pathId(), call.receive()))
        }

        post("/api/ticket-types") {
            call.respond(HttpStatusCode.Created, store.createTicketType(call.requireUser(store), call.receive()))
        }

        put("/api/ticket-types/{id}") {
            call.respond(store.updateTicketType(call.requireUser(store), call.pathId(), call.receive()))
        }

        delete("/api/ticket-types/{id}") {
            store.deleteTicketType(call.requireUser(store), call.pathId())
            call.respond(HttpStatusCode.NoContent)
        }

        put("/api/projects/{id}/workflow") {
            call.respond(store.saveWorkflow(call.requireUser(store), call.pathId(), call.receive()))
        }
    }
}

private suspend fun ApplicationCall.requireUser(store: DataStore): User {
    val token = request.headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: request.headers["X-Auth-Token"]?.trim()?.takeIf { it.isNotBlank() }
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Missing authentication token.")

    return store.userByToken(token)
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid or expired authentication token.")
}

private fun ApplicationCall.pathId(): String =
    parameters["id"] ?: throw ApiException(HttpStatusCode.BadRequest, "Missing path id.")

private fun parsePriority(value: String): Priority =
    runCatching { Priority.valueOf(value.uppercase()) }.getOrElse {
        throw ApiException(HttpStatusCode.BadRequest, "Unknown priority.")
    }

private suspend fun ApplicationCall.respondStaticResource(path: String, contentType: ContentType) {
    val text = Thread.currentThread().contextClassLoader.getResource(path)?.readText()
        ?: throw ApiException(HttpStatusCode.NotFound, "Resource not found.")
    respondText(text, contentType)
}

@Serializable
private data class HealthResponse(
    val status: String
)
