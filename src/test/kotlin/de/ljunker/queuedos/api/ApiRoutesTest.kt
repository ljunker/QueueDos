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
                    tickets = listOf(VersionedTicketRefRequest(ticket.id, ticket.version)),
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

        val committed = client.post("/api/tickets/${ticket.id}/commitment") {
            auth(memberToken)
            jsonBody(SaveTicketCommitmentRequest(true, bulkUpdated.single().version))
        }.body<TicketResponse>()
        assertEquals(listOf("user-member"), committed.committedUserIds)

        val detail = client.get("/api/tickets/${ticket.id}") {
            auth(adminToken)
        }.body<TicketDetailResponse>()
        assertEquals("Observed during import.", detail.comments.single().body)
        assertTrue(detail.revisions.revisions.any { it.action == "CREATED" })
        assertTrue(detail.revisions.revisions.none { revision -> revision.changes.any { it.field == "comment" } })
        val revisionPage = client.get("/api/tickets/${ticket.id}/revisions?limit=1") {
            auth(adminToken)
        }.body<TicketRevisionPageResponse>()
        assertEquals(1, revisionPage.revisions.size)
        val revisionDetail = client.get(
            "/api/tickets/${ticket.id}/revisions/${detail.revisions.revisions.last().version}"
        ) {
            auth(adminToken)
        }.body<TicketRevisionDetailResponse>()
        assertEquals(ticket.id, revisionDetail.snapshot.id)

        val staleUpdate = client.put("/api/tickets/${ticket.id}") {
            auth(adminToken)
            jsonBody(UpdateTicketRequest(expectedVersion = ticket.version, title = "Stale update"))
        }
        assertEquals(HttpStatusCode.Conflict, staleUpdate.status)
        assertEquals("TICKET_VERSION_CONFLICT", staleUpdate.body<ApiError>().code)

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
            jsonBody(TransitionTicketRequest(targetStatus, committed.version))
        }
        assertEquals(HttpStatusCode.Forbidden, memberTransition.status)

        val movedTicket = client.post("/api/tickets/${ticket.id}/transition") {
            auth(adminToken)
            jsonBody(TransitionTicketRequest(targetStatus, committed.version))
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

        val deletedTicket = client.delete("/api/tickets/${ticket.id}?expectedVersion=${movedTicket.version}") {
            auth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, deletedTicket.status)
        val deletedBootstrap = client.get("/api/bootstrap") { auth(adminToken) }.body<BootstrapResponse>()
        assertEquals(ticket.id, deletedBootstrap.deletedTickets.single { it.id == ticket.id }.id)
        val restored = client.post("/api/tickets/${ticket.id}/restore") {
            auth(adminToken)
            jsonBody(RestoreTicketRequest(deletedBootstrap.deletedTickets.single { it.id == ticket.id }.version))
        }.body<TicketResponse>()
        assertEquals(ticket.id, restored.id)

        val hook = client.post("/api/activity-hooks") {
            auth(adminToken)
            jsonBody(
                CreateActivityHookRequest(
                    eventType = de.ljunker.queuedos.domain.ActivityEventType.TICKET_CREATED,
                    webhookUrl = "https://hooks.slack.com/services/example",
                    messageTemplate = "{{actorName}} created {{ticketKey}}"
                )
            )
        }.body<ActivityHookResponse>()
        val pausedHook = client.put("/api/activity-hooks/${hook.id}") {
            auth(adminToken)
            jsonBody(UpdateActivityHookRequest(active = false))
        }.body<ActivityHookResponse>()
        assertEquals(false, pausedHook.active)
        assertTrue(client.get("/api/bootstrap") { auth(memberToken) }.body<BootstrapResponse>().activityHooks.isEmpty())
        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/api/activity-hooks/${hook.id}") { auth(adminToken) }.status
        )
    }

    @Test
    fun projectWizardApiCreatesAndUpdatesProjectSpecificConfiguration() = testApplication {
        application { module(newStore()) }
        val client = createClient { install(ClientContentNegotiation) { json(json) } }
        val adminToken = login(client, "admin@queuedos.local", "admin")

        val project = client.post("/api/projects") {
            auth(adminToken)
            jsonBody(
                CreateProjectRequest(
                    key = "WEB",
                    name = "Website",
                    color = "#7c3aed",
                    ticketTypes = listOf(
                        CreateProjectTicketTypeRequest("Feature", "User-facing change", "#16a34a"),
                        CreateProjectTicketTypeRequest("Defect", "Broken behavior", "#dc2626")
                    ),
                    statuses = listOf(
                        CreateProjectStatusRequest("Ideas", "TODO"),
                        CreateProjectStatusRequest("Building", "IN_PROGRESS"),
                        CreateProjectStatusRequest("Shipped", "DONE")
                    )
                )
            )
        }.body<ProjectResponse>()
        assertEquals("#7c3aed", project.color)

        var bootstrap = client.get("/api/bootstrap") { auth(adminToken) }.body<BootstrapResponse>()
        val types = bootstrap.ticketTypes.filter { it.projectId == project.id }
        val workflow = bootstrap.workflows.single { it.projectId == project.id }
        assertEquals(listOf("Defect", "Feature"), types.map { it.name }.sorted())
        assertEquals(listOf("Ideas", "Building", "Shipped"), workflow.statuses.map { it.name })
        assertEquals(6, workflow.transitions.size)

        val updatedProject = client.put("/api/projects/${project.id}") {
            auth(adminToken)
            jsonBody(UpdateProjectRequest(name = "Web platform", color = "#0891b2"))
        }.body<ProjectResponse>()
        assertEquals("#0891b2", updatedProject.color)

        val updatedType = client.put("/api/ticket-types/${types.first().id}") {
            auth(adminToken)
            jsonBody(UpdateTicketTypeRequest(name = "Bug", description = "A regression", color = "#b91c1c"))
        }.body<TicketTypeResponse>()
        assertEquals("Bug", updatedType.name)

        val invalid = client.post("/api/projects") {
            auth(adminToken)
            jsonBody(CreateProjectRequest("BAD", "Incomplete", ticketTypes = emptyList(), statuses = emptyList()))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        val legacyProject = client.post("/api/projects") {
            auth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"key":"LEG","name":"Legacy client"}""")
        }.body<ProjectResponse>()
        bootstrap = client.get("/api/bootstrap") { auth(adminToken) }.body()
        assertTrue(bootstrap.projects.none { it.key == "BAD" })
        assertEquals("#2563eb", legacyProject.color)
        assertEquals(4, bootstrap.ticketTypes.count { it.projectId == legacyProject.id })
        assertEquals(5, bootstrap.workflows.single { it.projectId == legacyProject.id }.statuses.size)
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
