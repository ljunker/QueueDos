package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.api.ApiException
import de.ljunker.queuedos.api.CreateTicketCommentRequest
import de.ljunker.queuedos.api.CreateProjectRequest
import de.ljunker.queuedos.api.CreateTicketRequest
import de.ljunker.queuedos.api.LoginRequest
import de.ljunker.queuedos.api.SaveWorkflowRequest
import de.ljunker.queuedos.api.TransitionTicketRequest
import de.ljunker.queuedos.api.UpdateTicketRequest
import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.WorkflowTransition
import de.ljunker.queuedos.security.AuthTokenCodec
import de.ljunker.queuedos.security.BCRYPT_PASSWORD_MARKER
import de.ljunker.queuedos.security.legacySha256Hash
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataStoreTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun seedAdminCanLogin() {
        val store = newStore()

        val response = store.login(LoginRequest("admin@queuedos.local", "admin"))

        assertEquals(Role.ADMIN, response.user.role)
    }

    @Test
    fun tokensAreStatelessAcrossStoreInstances() {
        val dir = Files.createTempDirectory("queuedos-test")
        val dataFile = dir.resolve("state.json")
        val tokenCodec = AuthTokenCodec("test-session-secret-that-is-long-enough")
        val firstStore = DataStore(dataFile, json, tokenCodec)

        val token = firstStore.login(LoginRequest("admin@queuedos.local", "admin")).token
        val secondStore = DataStore(dataFile, json, tokenCodec)

        assertEquals("user-admin", secondStore.userByToken(token)?.id)
    }

    @Test
    fun legacyPasswordHashesAreMigratedToBcryptOnLogin() {
        val dir = Files.createTempDirectory("queuedos-test")
        val dataFile = dir.resolve("state.json")
        val store = DataStore(dataFile, json)
        val original = json.decodeFromString<AppData>(Files.readString(dataFile))
        val legacyAdmin = original.users.first { it.id == "user-admin" }.copy(
            passwordSalt = "legacy-admin",
            passwordHash = legacySha256Hash("admin", "legacy-admin")
        )
        Files.writeString(
            dataFile,
            json.encodeToString(original.copy(users = original.users.map { if (it.id == legacyAdmin.id) legacyAdmin else it }))
        )
        val migratedStore = DataStore(dataFile, json)

        migratedStore.login(LoginRequest("admin@queuedos.local", "admin"))

        val migrated = json.decodeFromString<AppData>(Files.readString(dataFile)).users.first { it.id == "user-admin" }
        assertEquals(BCRYPT_PASSWORD_MARKER, migrated.passwordSalt)
        assertTrue(migrated.passwordHash.startsWith("\$2"))
    }

    @Test
    fun memberCannotCreateProject() {
        val store = newStore()
        val member = store.userByToken(store.login(LoginRequest("member@queuedos.local", "member")).token)!!

        val failure = assertFailsWith<ApiException> {
            store.createProject(member, CreateProjectRequest("OPS", "Operations"))
        }

        assertEquals(HttpStatusCode.Forbidden, failure.status)
    }

    @Test
    fun workflowTransitionsAreEnforced() {
        val store = newStore()
        val admin = store.userByToken(store.login(LoginRequest("admin@queuedos.local", "admin")).token)!!
        val bootstrap = store.bootstrap(admin)
        val project = bootstrap.projects.first()
        val workflow = bootstrap.workflows.first { it.projectId == project.id }
        val todoTicket = bootstrap.tickets.first { it.statusId == "status-todo" }

        store.saveWorkflow(
            admin,
            project.id,
            SaveWorkflowRequest(statuses = workflow.statuses, transitions = emptyList())
        )

        val failure = assertFailsWith<ApiException> {
            store.transitionTicket(admin, todoTicket.id, TransitionTicketRequest("status-done"))
        }

        assertEquals(HttpStatusCode.Conflict, failure.status)
    }

    @Test
    fun ticketKeysIncrementPerProject() {
        val store = newStore()
        val admin = store.userByToken(store.login(LoginRequest("admin@queuedos.local", "admin")).token)!!
        val bootstrap = store.bootstrap(admin)
        val project = bootstrap.projects.first()
        val type = bootstrap.ticketTypes.first { it.projectId == project.id }

        val ticket = store.createTicket(
            admin,
            CreateTicketRequest(
                projectId = project.id,
                title = "Next ticket",
                typeId = type.id
            )
        )

        assertEquals("QDOS-4", ticket.key)
    }

    @Test
    fun ticketMetadataCommentsAndChangesAreTracked() {
        val store = newStore()
        val admin = store.userByToken(store.login(LoginRequest("admin@queuedos.local", "admin")).token)!!
        val bootstrap = store.bootstrap(admin)
        val project = bootstrap.projects.first()
        val type = bootstrap.ticketTypes.first { it.projectId == project.id }

        val ticket = store.createTicket(
            admin,
            CreateTicketRequest(
                projectId = project.id,
                title = "Customer outage",
                typeId = type.id,
                labels = listOf("Customer", "Blocked"),
                dueDate = "2026-06-01",
                estimate = 8
            )
        )
        store.updateTicket(
            admin,
            ticket.id,
            UpdateTicketRequest(
                title = "Customer outage follow-up",
                labels = listOf("customer"),
                clearDueDate = true,
                estimate = 5
            )
        )
        store.addComment(admin, ticket.id, CreateTicketCommentRequest("Waiting on logs."))

        val detail = store.ticketDetail(admin, ticket.id)

        assertEquals(listOf("customer"), detail.ticket.labels)
        assertEquals(null, detail.ticket.dueDate)
        assertEquals(5, detail.ticket.estimate)
        assertEquals("Waiting on logs.", detail.comments.single().body)
        assertTrue(detail.changes.any { it.field == "title" })
        assertTrue(detail.changes.any { it.field == "comment" })
    }

    @Test
    fun workflowSupportsGlobalTransitionsAndBackwardRules() {
        val store = newStore()
        val admin = store.userByToken(store.login(LoginRequest("admin@queuedos.local", "admin")).token)!!
        val bootstrap = store.bootstrap(admin)
        val project = bootstrap.projects.first()
        val workflow = bootstrap.workflows.first { it.projectId == project.id }
        val todoTicket = bootstrap.tickets.first { it.statusId == "status-todo" }

        store.saveWorkflow(
            admin,
            project.id,
            SaveWorkflowRequest(
                statuses = workflow.statuses,
                transitions = listOf(
                    WorkflowTransition(
                        id = "transition-global-done",
                        toStatusId = "status-done",
                        allowedRoles = listOf(Role.ADMIN),
                        globalTransition = true
                    ),
                    WorkflowTransition(
                        id = "transition-done-todo",
                        fromStatusId = "status-done",
                        toStatusId = "status-todo",
                        allowedRoles = listOf(Role.ADMIN),
                        allowBackward = false
                    )
                )
            )
        )

        val moved = store.transitionTicket(admin, todoTicket.id, TransitionTicketRequest("status-done"))
        val failure = assertFailsWith<ApiException> {
            store.transitionTicket(admin, moved.id, TransitionTicketRequest("status-todo"))
        }

        assertEquals("status-done", moved.statusId)
        assertEquals(HttpStatusCode.Conflict, failure.status)
    }

    private fun newStore(): DataStore {
        val dir = Files.createTempDirectory("queuedos-test")
        return DataStore(dir.resolve("state.json"), json)
    }
}
