package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.*
import de.ljunker.queuedos.security.AuthTokenCodec
import de.ljunker.queuedos.security.BCRYPT_PASSWORD_MARKER
import de.ljunker.queuedos.security.legacySha256Hash
import de.ljunker.queuedos.support.PostgresTestBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QueueDosServicesTest {
    @Test
    fun seedAdminCanLogin() {
        val services = newServices()

        val response = services.auth.login(LoginCommand("admin@queuedos.local", "admin"))

        assertEquals(Role.ADMIN, response.user.role)
    }

    @Test
    fun tokensAreStatelessAcrossBackendInstances() {
        val tokenCodec = AuthTokenCodec("test-session-secret-that-is-long-enough")
        val fixture = PostgresTestBackend.create(tokenCodec)
        val token = fixture.backend.services.auth.login(LoginCommand("admin@queuedos.local", "admin")).token
        val secondBackend = QueueDosBackend.create(fixture.dataSource, de.ljunker.queuedos.config.appJson, tokenCodec)

        assertEquals("user-admin", secondBackend.services.auth.userByToken(token)?.id)
    }

    @Test
    fun legacyPasswordHashesAreMigratedToBcryptOnLogin() {
        val fixture = PostgresTestBackend.create()
        fixture.sql {
            prepareStatement("UPDATE queuedos_users SET password_salt = ?, password_hash = ? WHERE id = ?").use {
                it.setString(1, "legacy-admin")
                it.setString(2, legacySha256Hash("admin", "legacy-admin"))
                it.setString(3, "user-admin")
                it.executeUpdate()
            }
        }

        fixture.backend.services.auth.login(LoginCommand("admin@queuedos.local", "admin"))

        fixture.sql {
            prepareStatement("SELECT password_salt, password_hash FROM queuedos_users WHERE id = ?").use {
                it.setString(1, "user-admin")
                it.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(BCRYPT_PASSWORD_MARKER, result.getString("password_salt"))
                    assertTrue(result.getString("password_hash").startsWith("\$2"))
                }
            }
        }
    }

    @Test
    fun memberCannotCreateProject() {
        val services = newServices()
        val member = services.auth.login(LoginCommand("member@queuedos.local", "member")).user

        val failure = assertFailsWith<QueueDosFailure> {
            services.projects.create(member, CreateProjectCommand("OPS", "Operations", ""))
        }

        assertEquals(FailureKind.FORBIDDEN, failure.kind)
    }

    @Test
    fun workflowTransitionsAreEnforced() {
        val services = newServices()
        val admin = admin(services)
        val bootstrap = services.queries.bootstrap(admin)
        val project = bootstrap.projects.first()
        val workflow = bootstrap.workflows.first { it.projectId == project.id }
        val todoTicket = bootstrap.tickets.first { it.statusId == "status-todo" }

        services.workflows.save(admin, project.id, SaveWorkflowCommand(workflow.statuses, emptyList()))

        val failure = assertFailsWith<QueueDosFailure> {
            services.tickets.transition(admin, todoTicket.id, TransitionTicketCommand("status-done"))
        }

        assertEquals(FailureKind.CONFLICT, failure.kind)
    }

    @Test
    fun ticketKeysIncrementPerProject() {
        val services = newServices()
        val admin = admin(services)
        val bootstrap = services.queries.bootstrap(admin)
        val project = bootstrap.projects.first()
        val type = bootstrap.ticketTypes.first { it.projectId == project.id }

        val ticket = services.tickets.create(
            admin,
            CreateTicketCommand(
                projectId = project.id,
                title = "Next ticket",
                description = "",
                typeId = type.id,
                priority = Priority.MEDIUM,
                assigneeId = null,
                statusId = null,
                labels = emptyList(),
                dueDate = null,
                estimate = null
            )
        )

        assertEquals("QDOS-4", ticket.key)
    }

    @Test
    fun ticketMetadataCommentsAndChangesAreTracked() {
        val services = newServices()
        val admin = admin(services)
        val bootstrap = services.queries.bootstrap(admin)
        val project = bootstrap.projects.first()
        val type = bootstrap.ticketTypes.first { it.projectId == project.id }

        val ticket = services.tickets.create(
            admin,
            CreateTicketCommand(
                projectId = project.id,
                title = "Customer outage",
                description = "",
                typeId = type.id,
                priority = Priority.MEDIUM,
                assigneeId = null,
                statusId = null,
                labels = listOf("Customer", "Blocked"),
                dueDate = "2026-06-01",
                estimate = 8
            )
        )
        services.tickets.update(
            admin,
            ticket.id,
            UpdateTicketCommand(
                title = "Customer outage follow-up",
                description = null,
                typeId = null,
                priority = null,
                assigneeId = null,
                labels = listOf("customer"),
                dueDate = null,
                estimate = 5,
                clearDueDate = true,
                clearEstimate = false
            )
        )
        services.tickets.addComment(admin, ticket.id, AddTicketCommentCommand("Waiting on logs."))

        val detail = services.queries.ticketDetail(admin, ticket.id)

        assertEquals(listOf("customer"), detail.ticket.labels)
        assertEquals(null, detail.ticket.dueDate)
        assertEquals(5, detail.ticket.estimate)
        assertEquals("Waiting on logs.", detail.comments.single().body)
        assertTrue(detail.changes.any { it.field == "title" })
        assertTrue(detail.changes.any { it.field == "comment" })
    }

    @Test
    fun workflowSupportsGlobalTransitionsAndBackwardRules() {
        val services = newServices()
        val admin = admin(services)
        val bootstrap = services.queries.bootstrap(admin)
        val project = bootstrap.projects.first()
        val workflow = bootstrap.workflows.first { it.projectId == project.id }
        val todoTicket = bootstrap.tickets.first { it.statusId == "status-todo" }

        services.workflows.save(
            admin,
            project.id,
            SaveWorkflowCommand(
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

        val moved = services.tickets.transition(admin, todoTicket.id, TransitionTicketCommand("status-done"))
        val failure = assertFailsWith<QueueDosFailure> {
            services.tickets.transition(admin, moved.id, TransitionTicketCommand("status-todo"))
        }

        assertEquals("status-done", moved.statusId)
        assertEquals(FailureKind.CONFLICT, failure.kind)
    }

    @Test
    fun savedTicketFiltersArePrivateValidatedAndRenamable() {
        val services = newServices()
        val admin = admin(services)
        val member = services.auth.login(LoginCommand("member@queuedos.local", "member")).user
        val project = services.queries.bootstrap(admin).projects.first()

        val projectFilter = services.savedTicketFilters.create(
            admin,
            CreateSavedTicketFilterCommand(
                name = "Critical todo",
                view = SavedTicketFilterView.PROJECT_LIST,
                projectId = project.id,
                filters = SavedTicketFilterCriteria(statusId = "status-todo", priority = Priority.CRITICAL)
            )
        )
        val renamed = services.savedTicketFilters.update(
            admin,
            projectFilter.id,
            UpdateSavedTicketFilterCommand(name = "Critical queue", filters = null)
        )

        assertEquals("Critical queue", renamed.name)
        assertEquals(listOf(renamed), services.queries.bootstrap(admin).savedTicketFilters)
        assertTrue(services.queries.bootstrap(member).savedTicketFilters.isEmpty())

        val duplicate = assertFailsWith<QueueDosFailure> {
            services.savedTicketFilters.create(
                admin,
                CreateSavedTicketFilterCommand(
                    name = "critical queue",
                    view = SavedTicketFilterView.PROJECT_LIST,
                    projectId = project.id,
                    filters = SavedTicketFilterCriteria()
                )
            )
        }
        val privateFailure = assertFailsWith<QueueDosFailure> {
            services.savedTicketFilters.update(member, projectFilter.id, UpdateSavedTicketFilterCommand("Stolen", null))
        }
        val invalidMyTickets = assertFailsWith<QueueDosFailure> {
            services.savedTicketFilters.create(
                admin,
                CreateSavedTicketFilterCommand(
                    name = "Status in mine",
                    view = SavedTicketFilterView.MY_TICKETS,
                    projectId = null,
                    filters = SavedTicketFilterCriteria(statusId = "status-todo")
                )
            )
        }

        assertEquals(FailureKind.CONFLICT, duplicate.kind)
        assertEquals(FailureKind.NOT_FOUND, privateFailure.kind)
        assertEquals(FailureKind.BAD_REQUEST, invalidMyTickets.kind)

        services.savedTicketFilters.delete(admin, projectFilter.id)
        assertTrue(services.queries.bootstrap(admin).savedTicketFilters.isEmpty())
    }

    @Test
    fun bulkTicketUpdatesValidateBeforeChangingTickets() {
        val services = newServices()
        val admin = admin(services)
        val bootstrap = services.queries.bootstrap(admin)
        val project = bootstrap.projects.first()
        val ticketIds = bootstrap.tickets.take(2).map { it.id }

        val reassigned = services.tickets.bulkUpdate(
            admin,
            BulkUpdateTicketsCommand(ticketIds, "user-member", clearAssignee = false, priority = Priority.LOW)
        )
        assertTrue(reassigned.all { it.assigneeId == "user-member" && it.priority == Priority.LOW })

        val cleared = services.tickets.bulkUpdate(
            admin,
            BulkUpdateTicketsCommand(ticketIds, assigneeId = null, clearAssignee = true, priority = null)
        )
        assertTrue(cleared.all { it.assigneeId == null })

        val emptySelection = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(admin, BulkUpdateTicketsCommand(emptyList(), null, false, Priority.HIGH))
        }
        val emptyMutation = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(admin, BulkUpdateTicketsCommand(ticketIds, null, false, null))
        }
        val beforeInvalid = services.queries.bootstrap(admin).tickets.filter { it.id in ticketIds }
        val invalidAssignee = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(
                admin,
                BulkUpdateTicketsCommand(ticketIds, "user-outside", false, Priority.CRITICAL)
            )
        }
        assertEquals(beforeInvalid, services.queries.bootstrap(admin).tickets.filter { it.id in ticketIds })

        services.projects.update(admin, project.id, UpdateProjectCommand(null, null, null, archived = true))
        val archivedFailure = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(admin, BulkUpdateTicketsCommand(ticketIds, null, false, Priority.HIGH))
        }

        assertEquals(FailureKind.BAD_REQUEST, emptySelection.kind)
        assertEquals(FailureKind.BAD_REQUEST, emptyMutation.kind)
        assertEquals(FailureKind.NOT_FOUND, invalidAssignee.kind)
        assertEquals(FailureKind.CONFLICT, archivedFailure.kind)
        assertEquals(beforeInvalid, services.queries.bootstrap(admin).tickets.filter { it.id in ticketIds })
    }

    private fun newServices(): QueueDosServices = PostgresTestBackend.create().backend.services

    private fun admin(services: QueueDosServices) =
        services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
}
