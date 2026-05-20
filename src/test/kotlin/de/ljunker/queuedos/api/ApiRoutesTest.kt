package de.ljunker.queuedos.api

import de.ljunker.queuedos.module
import de.ljunker.queuedos.domain.Project
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.TicketComment
import de.ljunker.queuedos.domain.TicketType
import de.ljunker.queuedos.domain.Workflow
import de.ljunker.queuedos.domain.WorkflowTransition
import de.ljunker.queuedos.persistence.DataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRoutesTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun apiCoversProjectsTicketsTicketTypesWorkflowsAndRights() = testApplication {
        application {
            module(newStore())
        }
        val client = createClient {
            install(ClientContentNegotiation) {
                json(json)
            }
        }
        val adminToken = login(client, "admin@queuedos.local", "admin")
        val memberToken = login(client, "member@queuedos.local", "member")

        val forbiddenProject = client.post("/api/projects") {
            auth(memberToken)
            jsonBody(CreateProjectRequest("OPS", "Operations"))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenProject.status)

        val project = client.post("/api/projects") {
            auth(adminToken)
            jsonBody(CreateProjectRequest("OPS", "Operations"))
        }.body<Project>()
        assertEquals("OPS", project.key)

        val bootstrap = client.get("/api/bootstrap") {
            auth(adminToken)
        }.body<BootstrapResponse>()
        val workflow = bootstrap.workflows.first { it.projectId == project.id }
        val defaultType = bootstrap.ticketTypes.first { it.projectId == project.id }

        val createdType = client.post("/api/ticket-types") {
            auth(adminToken)
            jsonBody(CreateTicketTypeRequest(projectId = project.id, name = "Incident", color = "#dc2626"))
        }.body<TicketType>()
        val updatedType = client.put("/api/ticket-types/${createdType.id}") {
            auth(adminToken)
            jsonBody(UpdateTicketTypeRequest(name = "Production Incident", color = "#b91c1c"))
        }.body<TicketType>()
        assertEquals("Production Incident", updatedType.name)

        val ticket = client.post("/api/tickets") {
            auth(adminToken)
            jsonBody(
                CreateTicketRequest(
                    projectId = project.id,
                    title = "Database latency",
                    typeId = updatedType.id,
                    assigneeId = "user-member",
                    labels = listOf("Database", "Urgent"),
                    dueDate = "2026-06-01",
                    estimate = 13
                )
            )
        }.body<Ticket>()
        assertEquals("OPS-1", ticket.key)
        assertEquals(listOf("database", "urgent"), ticket.labels)

        val listedTickets = client.get("/api/tickets?projectId=${project.id}&q=database&typeId=${updatedType.id}&label=database") {
            auth(adminToken)
        }.body<List<Ticket>>()
        assertTrue(listedTickets.any { it.id == ticket.id })

        val comment = client.post("/api/tickets/${ticket.id}/comments") {
            auth(adminToken)
            jsonBody(CreateTicketCommentRequest("Observed during import."))
        }.body<TicketComment>()
        assertEquals(ticket.id, comment.ticketId)

        val detail = client.get("/api/tickets/${ticket.id}") {
            auth(adminToken)
        }.body<TicketDetailResponse>()
        assertEquals("Observed during import.", detail.comments.single().body)
        assertTrue(detail.changes.any { it.field == "ticket" })
        assertTrue(detail.changes.any { it.field == "comment" })

        val deleteUsedType = client.delete("/api/ticket-types/${updatedType.id}") {
            auth(adminToken)
        }
        assertEquals(HttpStatusCode.Conflict, deleteUsedType.status)

        val sourceStatus = workflow.statuses.first().id
        val targetStatus = workflow.statuses[1].id
        val savedWorkflow = client.put("/api/projects/${project.id}/workflow") {
            auth(adminToken)
            jsonBody(
                SaveWorkflowRequest(
                    statuses = workflow.statuses,
                    transitions = listOf(
                        WorkflowTransition(
                            id = "transition-admin-only",
                            fromStatusId = sourceStatus,
                            toStatusId = targetStatus,
                            allowedRoles = listOf(Role.ADMIN)
                        )
                    )
                )
            )
        }.body<Workflow>()
        assertEquals(1, savedWorkflow.transitions.size)

        val memberTransition = client.post("/api/tickets/${ticket.id}/transition") {
            auth(memberToken)
            jsonBody(TransitionTicketRequest(targetStatus))
        }
        assertEquals(HttpStatusCode.Forbidden, memberTransition.status)

        val movedTicket = client.post("/api/tickets/${ticket.id}/transition") {
            auth(adminToken)
            jsonBody(TransitionTicketRequest(targetStatus))
        }.body<Ticket>()
        assertEquals(targetStatus, movedTicket.statusId)

        val deleteUnusedType = client.delete("/api/ticket-types/${defaultType.id}") {
            auth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, deleteUnusedType.status)
    }

    private suspend fun login(client: HttpClient, email: String, password: String): String =
        client.post("/api/auth/login") {
            jsonBody(LoginRequest(email, password))
        }.body<LoginResponse>().token

    private fun HttpRequestBuilder.auth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun newStore(): DataStore {
        val dir = Files.createTempDirectory("queuedos-api-test")
        return DataStore(dir.resolve("state.json"), json)
    }
}
