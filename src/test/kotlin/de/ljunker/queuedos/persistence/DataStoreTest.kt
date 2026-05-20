package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.api.ApiException
import de.ljunker.queuedos.api.CreateProjectRequest
import de.ljunker.queuedos.api.CreateTicketRequest
import de.ljunker.queuedos.api.LoginRequest
import de.ljunker.queuedos.api.SaveWorkflowRequest
import de.ljunker.queuedos.api.TransitionTicketRequest
import de.ljunker.queuedos.domain.Role
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files

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

    private fun newStore(): DataStore {
        val dir = Files.createTempDirectory("queuedos-test")
        return DataStore(dir.resolve("state.json"), json)
    }
}
