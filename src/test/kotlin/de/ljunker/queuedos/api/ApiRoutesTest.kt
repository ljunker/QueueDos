package de.ljunker.queuedos.api

import de.ljunker.queuedos.application.QueueDosBackend
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.SavedTicketFilterView
import de.ljunker.queuedos.module
import de.ljunker.queuedos.support.PostgresTestBackend
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

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
        }.body<ProjectResponse>()
        assertEquals("OPS", project.key)

        val bootstrap = client.get("/api/bootstrap") {
            auth(adminToken)
        }.body<BootstrapResponse>()
        val workflow = bootstrap.workflows.first { it.projectId == project.id }
        val defaultType = bootstrap.ticketTypes.first { it.projectId == project.id }

        val createdType = client.post("/api/ticket-types") {
            auth(adminToken)
            jsonBody(CreateTicketTypeRequest(projectId = project.id, name = "Incident", color = "#dc2626"))
        }.body<TicketTypeResponse>()
        val updatedType = client.put("/api/ticket-types/${createdType.id}") {
            auth(adminToken)
            jsonBody(UpdateTicketTypeRequest(name = "Production Incident", color = "#b91c1c"))
        }.body<TicketTypeResponse>()
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
        }.body<TicketResponse>()
        assertEquals("OPS-1", ticket.key)
        assertEquals(listOf("database", "urgent"), ticket.labels)

        val listedTickets = client.get("/api/tickets?projectId=${project.id}&q=database&typeId=${updatedType.id}&label=database") {
            auth(adminToken)
        }.body<List<TicketResponse>>()
        assertTrue(listedTickets.any { it.id == ticket.id })

        val savedFilter = client.post("/api/saved-ticket-filters") {
            auth(adminToken)
            jsonBody(
                CreateSavedTicketFilterRequest(
                    name = "Database work",
                    view = SavedTicketFilterView.PROJECT_LIST,
                    projectId = project.id,
                    filters = SavedTicketFilterCriteriaDto(q = "database", typeId = updatedType.id)
                )
            )
        }.body<SavedTicketFilterResponse>()
        val renamedFilter = client.put("/api/saved-ticket-filters/${savedFilter.id}") {
            auth(adminToken)
            jsonBody(UpdateSavedTicketFilterRequest(name = "Production database work"))
        }.body<SavedTicketFilterResponse>()
        assertEquals("Production database work", renamedFilter.name)
        assertEquals(
            listOf(renamedFilter),
            client.get("/api/bootstrap") { auth(adminToken) }.body<BootstrapResponse>().savedTicketFilters
        )
        assertTrue(client.get("/api/bootstrap") { auth(memberToken) }.body<BootstrapResponse>().savedTicketFilters.isEmpty())

        val bulkUpdated = client.post("/api/tickets/bulk-update") {
            auth(adminToken)
            jsonBody(
                BulkUpdateTicketsRequest(
                    ticketIds = listOf(ticket.id),
                    assigneeId = "user-admin",
                    priority = de.ljunker.queuedos.domain.Priority.CRITICAL
                )
            )
        }.body<List<TicketResponse>>()
        assertEquals("user-admin", bulkUpdated.single().assigneeId)

        val comment = client.post("/api/tickets/${ticket.id}/comments") {
            auth(adminToken)
            jsonBody(CreateTicketCommentRequest("Observed during import."))
        }.body<TicketCommentResponse>()
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
                        WorkflowTransitionDto(
                            id = "transition-admin-only",
                            fromStatusId = sourceStatus,
                            toStatusId = targetStatus,
                            allowedRoles = listOf(Role.ADMIN)
                        )
                    )
                )
            )
        }.body<WorkflowResponse>()
        assertEquals(1, savedWorkflow.transitions.size)

        val memberTransition = client.post("/api/tickets/${ticket.id}/transition") {
            auth(memberToken)
            jsonBody(TransitionTicketRequest(targetStatus))
        }
        assertEquals(HttpStatusCode.Forbidden, memberTransition.status)

        val movedTicket = client.post("/api/tickets/${ticket.id}/transition") {
            auth(adminToken)
            jsonBody(TransitionTicketRequest(targetStatus))
        }.body<TicketResponse>()
        assertEquals(targetStatus, movedTicket.statusId)

        val deleteUnusedType = client.delete("/api/ticket-types/${defaultType.id}") {
            auth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, deleteUnusedType.status)

        val deleteSavedFilter = client.delete("/api/saved-ticket-filters/${savedFilter.id}") {
            auth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, deleteSavedFilter.status)
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

    private fun newStore(): QueueDosBackend = PostgresTestBackend.create().backend
}
