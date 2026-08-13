package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.*
import de.ljunker.queuedos.security.AuthTokenCodec
import de.ljunker.queuedos.security.BCRYPT_PASSWORD_MARKER
import de.ljunker.queuedos.security.legacySha256Hash
import de.ljunker.queuedos.support.PostgresTestBackend
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
    fun projectWizardConfigurationIsCreatedAtomically() {
        val services = newServices()
        val admin = admin(services)

        val project = services.projects.create(
            admin,
            CreateProjectCommand(
                key = "OPS",
                name = "Operations",
                description = "Production work",
                color = "#0f766e",
                ticketTypes = listOf(
                    CreateProjectTicketTypeCommand("Incident", "Production incident", "#dc2626"),
                    CreateProjectTicketTypeCommand("Change", "Planned change", "#2563eb")
                ),
                statuses = listOf(
                    CreateProjectStatusCommand("Incoming", "TODO"),
                    CreateProjectStatusCommand("Investigating", "IN_PROGRESS"),
                    CreateProjectStatusCommand("Resolved", "DONE")
                )
            )
        )

        val bootstrap = services.queries.bootstrap(admin)
        val types = bootstrap.ticketTypes.filter { it.projectId == project.id }
        val workflow = bootstrap.workflows.single { it.projectId == project.id }
        assertEquals("#0f766e", project.color)
        assertEquals(listOf("Change", "Incident"), types.map { it.name }.sorted())
        assertEquals(listOf("Incoming", "Investigating", "Resolved"), workflow.statuses.map { it.name })
        assertEquals(listOf("TODO", "IN_PROGRESS", "DONE"), workflow.statuses.map { it.category })
        assertEquals(6, workflow.transitions.size)
        assertTrue(workflow.transitions.all { it.allowedRoles == listOf(Role.ADMIN, Role.MEMBER) })
    }

    @Test
    fun onlyAdminsCanDeleteProjectsAndTheirConfiguration() {
        val services = newServices()
        val admin = admin(services)
        val member = services.auth.login(LoginCommand("member@queuedos.local", "member")).user
        val project = services.projects.create(
            admin,
            CreateProjectCommand(
                key = "DEL",
                name = "Disposable",
                description = "",
                ticketTypes = validTypes(),
                statuses = validStatuses()
            )
        )
        val beforeDelete = services.queries.bootstrap(admin)
        val type = beforeDelete.ticketTypes.single { it.projectId == project.id }
        val workflow = beforeDelete.workflows.single { it.projectId == project.id }
        val ticket = services.tickets.create(
            admin,
            CreateTicketCommand(
                projectId = project.id,
                title = "Delete with project",
                description = "",
                typeId = type.id,
                priority = Priority.MEDIUM,
                assigneeId = null,
                statusId = workflow.statuses.first().id,
                labels = emptyList(),
                dueDate = null,
                estimate = null
            )
        )

        val forbidden = assertFailsWith<QueueDosFailure> {
            services.projects.delete(member, project.id)
        }
        assertEquals(FailureKind.FORBIDDEN, forbidden.kind)

        services.projects.delete(admin, project.id)

        val afterDelete = services.queries.bootstrap(admin)
        assertTrue(afterDelete.projects.none { it.id == project.id })
        assertTrue(afterDelete.ticketTypes.none { it.projectId == project.id })
        assertTrue(afterDelete.workflows.none { it.projectId == project.id })
        assertTrue(afterDelete.tickets.none { it.id == ticket.id })
    }

    @Test
    fun invalidWizardConfigurationRollsBackTheWholeProject() {
        val services = newServices()
        val admin = admin(services)

        val failures = listOf(
            CreateProjectCommand("E01", "Empty types", "", ticketTypes = emptyList(), statuses = validStatuses()),
            CreateProjectCommand("E02", "One status", "", ticketTypes = validTypes(), statuses = listOf(CreateProjectStatusCommand("Open", "TODO"))),
            CreateProjectCommand("E03", "Duplicate types", "", ticketTypes = listOf(
                CreateProjectTicketTypeCommand("Task", "", "#2563eb"),
                CreateProjectTicketTypeCommand("task", "", "#dc2626")
            ), statuses = validStatuses()),
            CreateProjectCommand("E04", "Duplicate statuses", "", ticketTypes = validTypes(), statuses = listOf(
                CreateProjectStatusCommand("Open", "TODO"),
                CreateProjectStatusCommand("open", "DONE")
            )),
            CreateProjectCommand("E05", "Bad category", "", ticketTypes = validTypes(), statuses = listOf(
                CreateProjectStatusCommand("Open", "UNKNOWN"),
                CreateProjectStatusCommand("Done", "DONE")
            )),
            CreateProjectCommand("E06", "Bad color", "", color = "blue", ticketTypes = validTypes(), statuses = validStatuses())
        )

        failures.forEach { command ->
            assertEquals(FailureKind.BAD_REQUEST, assertFailsWith<QueueDosFailure> {
                services.projects.create(admin, command)
            }.kind)
        }

        val keys = services.queries.bootstrap(admin).projects.map { it.key }
        assertTrue(failures.none { it.key in keys })
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
            services.tickets.transition(admin, todoTicket.id, TransitionTicketCommand("status-done", todoTicket.version))
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
                expectedVersion = ticket.version,
                title = "Customer outage follow-up",
                description = null,
                typeId = null,
                priority = null,
                assigneeId = null,
                clearAssignee = false,
                labels = listOf("customer"),
                dueDate = null,
                estimate = 5,
                clearDueDate = true,
                clearEstimate = false,
                statusId = null
            )
        )
        services.tickets.addComment(admin, ticket.id, AddTicketCommentCommand("Waiting on logs."))

        val detail = services.queries.ticketDetail(admin, ticket.id)

        assertEquals(listOf("customer"), detail.ticket.labels)
        assertEquals(null, detail.ticket.dueDate)
        assertEquals(5, detail.ticket.estimate)
        assertEquals("Waiting on logs.", detail.comments.single().body)
        assertTrue(detail.revisions.revisions.any { revision -> revision.changes.any { it.field == "title" } })
        assertTrue(detail.revisions.revisions.none { revision -> revision.changes.any { it.field == "comment" } })
    }

    @Test
    fun ticketRevisionsRejectStaleWritesAndRestoreSnapshotsAsNewVersions() {
        val services = newServices()
        val admin = admin(services)
        val original = services.queries.bootstrap(admin).tickets.first()

        val changed = services.tickets.update(
            admin,
            original.id,
            UpdateTicketCommand(
                expectedVersion = original.version,
                title = "Revised title",
                description = null,
                typeId = null,
                priority = null,
                assigneeId = null,
                clearAssignee = false,
                labels = null,
                dueDate = null,
                estimate = null,
                clearDueDate = false,
                clearEstimate = false,
                statusId = null
            )
        )
        val conflict = assertFailsWith<TicketVersionConflictFailure> {
            services.tickets.update(
                admin,
                original.id,
                UpdateTicketCommand(
                    expectedVersion = original.version,
                    title = "Stale title",
                    description = null,
                    typeId = null,
                    priority = null,
                    assigneeId = null,
                    clearAssignee = false,
                    labels = null,
                    dueDate = null,
                    estimate = null,
                    clearDueDate = false,
                    clearEstimate = false,
                    statusId = null
                )
            )
        }

        val restored = services.tickets.restoreRevision(
            admin,
            original.id,
            original.version,
            RestoreTicketCommand(changed.version)
        )
        val history = services.queries.ticketDetail(admin, original.id).revisions.revisions

        assertEquals(changed.version, conflict.currentVersion)
        assertEquals(original.title, restored.title)
        assertEquals(changed.version + 1, restored.version)
        assertEquals(original.version, history.first().sourceVersion)
        assertEquals(TicketRevisionAction.REVISION_RESTORED, history.first().action)
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

        val moved = services.tickets.transition(admin, todoTicket.id, TransitionTicketCommand("status-done", todoTicket.version))
        val failure = assertFailsWith<QueueDosFailure> {
            services.tickets.transition(admin, moved.id, TransitionTicketCommand("status-todo", moved.version))
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
        var ticketRefs = bootstrap.tickets.take(2).map { VersionedTicketRef(it.id, it.version) }
        val ticketIds = ticketRefs.map { it.id }

        val reassigned = services.tickets.bulkUpdate(
            admin,
            BulkUpdateTicketsCommand(ticketRefs, "user-member", clearAssignee = false, priority = Priority.LOW)
        )
        assertTrue(reassigned.all { it.assigneeId == "user-member" && it.priority == Priority.LOW })

        ticketRefs = reassigned.map { VersionedTicketRef(it.id, it.version) }
        val cleared = services.tickets.bulkUpdate(
            admin,
            BulkUpdateTicketsCommand(ticketRefs, assigneeId = null, clearAssignee = true, priority = null)
        )
        assertTrue(cleared.all { it.assigneeId == null })

        val emptySelection = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(admin, BulkUpdateTicketsCommand(emptyList(), null, false, Priority.HIGH))
        }
        val emptyMutation = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(admin, BulkUpdateTicketsCommand(cleared.map { VersionedTicketRef(it.id, it.version) }, null, false, null))
        }
        val beforeInvalid = services.queries.bootstrap(admin).tickets.filter { it.id in ticketIds }
        val invalidAssignee = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(
                admin,
                BulkUpdateTicketsCommand(cleared.map { VersionedTicketRef(it.id, it.version) }, "user-outside", false, Priority.CRITICAL)
            )
        }
        assertEquals(beforeInvalid, services.queries.bootstrap(admin).tickets.filter { it.id in ticketIds })

        services.projects.update(admin, project.id, UpdateProjectCommand(null, null, null, archived = true))
        val archivedFailure = assertFailsWith<QueueDosFailure> {
            services.tickets.bulkUpdate(admin, BulkUpdateTicketsCommand(cleared.map { VersionedTicketRef(it.id, it.version) }, null, false, Priority.HIGH))
        }

        assertEquals(FailureKind.BAD_REQUEST, emptySelection.kind)
        assertEquals(FailureKind.BAD_REQUEST, emptyMutation.kind)
        assertEquals(FailureKind.NOT_FOUND, invalidAssignee.kind)
        assertEquals(FailureKind.CONFLICT, archivedFailure.kind)
        assertEquals(beforeInvalid, services.queries.bootstrap(admin).tickets.filter { it.id in ticketIds })
    }

    @Test
    fun usersCanCommitAndAdminsCanRestoreDeletedTickets() {
        val services = newServices()
        val admin = admin(services)
        val member = services.auth.login(LoginCommand("member@queuedos.local", "member")).user
        val ticket = services.queries.bootstrap(admin).tickets.first()

        val committed = services.tickets.saveCommitment(member, ticket.id, SaveTicketCommitmentCommand(true, ticket.version))
        services.tickets.delete(admin, ticket.id, committed.version)

        assertTrue(member.id in committed.committedUserIds)
        assertTrue(services.queries.bootstrap(admin).tickets.none { it.id == ticket.id })
        assertEquals(ticket.id, services.queries.bootstrap(admin).deletedTickets.single { it.id == ticket.id }.id)
        assertTrue(services.queries.bootstrap(member).deletedTickets.isEmpty())

        val deleted = services.queries.bootstrap(admin).deletedTickets.single { it.id == ticket.id }
        val restored = services.tickets.restore(admin, ticket.id, RestoreTicketCommand(deleted.version))
        assertEquals(ticket.id, restored.id)
        assertTrue(services.queries.ticketDetail(admin, ticket.id).revisions.revisions.any { it.action == TicketRevisionAction.RESTORED })
    }

    @Test
    fun slackActivityHooksRenderConfiguredMessagesAfterComments() {
        val sender = RecordingSlackSender()
        val services = PostgresTestBackend.create(slackSender = sender).backend.services
        val admin = admin(services)
        val ticket = services.queries.bootstrap(admin).tickets.first()

        services.activityHooks.create(
            admin,
            CreateActivityHookCommand(
                ActivityEventType.COMMENT_ADDED,
                "https://hooks.slack.com/services/test",
                "{{actorName}} commented on {{ticketKey}}: {{comment}}",
                active = true
            )
        )
        services.tickets.addComment(admin, ticket.id, AddTicketCommentCommand("Ready for review."))

        assertEquals(
            listOf("https://hooks.slack.com/services/test" to "QueueDos Admin commented on QDOS-1: Ready for review."),
            sender.messages
        )
    }

    @Test
    fun azureOnlyUsersCanReceiveATemporaryPasswordAndMustReplaceIt() {
        val services = newServices()
        val admin = admin(services)
        val user = services.users.create(
            admin,
            CreateUserCommand("azure-only@example.com", "Azure Only", Role.MEMBER, password = null)
        )

        assertFalse(user.localLoginEnabled)
        assertFalse(user.mustChangePassword)
        assertFailsWith<UnauthorizedFailure> {
            services.auth.login(LoginCommand(user.email, "unknown-password"))
        }

        val temporaryPassword = services.users.generateTemporaryPassword(admin, user.id)
        val pending = services.queries.bootstrap(admin).users.single { it.id == user.id }
        assertTrue(pending.localLoginEnabled)
        assertTrue(pending.mustChangePassword)

        val temporaryLogin = services.auth.login(LoginCommand(user.email, temporaryPassword))
        assertTrue(temporaryLogin.passwordChangeRequired)
        assertNull(services.auth.userByToken(temporaryLogin.token))
        val passwordChangeUser = services.auth.userByPasswordChangeToken(temporaryLogin.token)
        assertEquals(user.id, passwordChangeUser?.id)

        val session = services.auth.changePassword(passwordChangeUser!!, "new-password")
        assertFalse(session.passwordChangeRequired)
        assertEquals(user.id, services.auth.userByToken(session.token)?.id)
        assertNull(services.auth.userByPasswordChangeToken(temporaryLogin.token))
        assertFalse(services.auth.login(LoginCommand(user.email, "new-password")).passwordChangeRequired)
        assertFailsWith<UnauthorizedFailure> {
            services.auth.login(LoginCommand(user.email, temporaryPassword))
        }
    }

    @Test
    fun optionalAndDirectPasswordsEnableLocalLoginWithoutForcedChange() {
        val services = newServices()
        val admin = admin(services)
        val local = services.users.create(
            admin,
            CreateUserCommand("local@example.com", "Local User", Role.MEMBER, "initial-password")
        )
        assertTrue(local.localLoginEnabled)
        assertFalse(local.mustChangePassword)
        assertFalse(services.auth.login(LoginCommand(local.email, "initial-password")).passwordChangeRequired)

        val azureOnly = services.users.create(
            admin,
            CreateUserCommand("later-local@example.com", "Later Local", Role.MEMBER, null)
        )
        val updated = services.users.update(
            admin,
            azureOnly.id,
            UpdateUserCommand(null, null, null, "direct-password")
        )
        assertTrue(updated.localLoginEnabled)
        assertFalse(updated.mustChangePassword)
        assertFalse(services.auth.login(LoginCommand(updated.email, "direct-password")).passwordChangeRequired)
    }

    @Test
    fun inactiveUsersAndAdminSafetyRulesAreEnforced() {
        val services = newServices()
        val firstAdmin = admin(services)
        val secondAdmin = services.users.create(
            firstAdmin,
            CreateUserCommand("second-admin@example.com", "Second Admin", Role.ADMIN, "admin-password")
        )
        val member = services.users.create(
            firstAdmin,
            CreateUserCommand("disabled@example.com", "Disabled User", Role.MEMBER, "member-password")
        )

        assertFailsWith<ConflictFailure> {
            services.users.update(firstAdmin, firstAdmin.id, UpdateUserCommand(null, Role.MEMBER, null, null))
        }
        assertFailsWith<ConflictFailure> {
            services.users.update(firstAdmin, firstAdmin.id, UpdateUserCommand(null, null, false, null))
        }

        services.users.update(firstAdmin, member.id, UpdateUserCommand(null, null, false, null))
        assertFailsWith<UnauthorizedFailure> {
            services.auth.login(LoginCommand(member.email, "member-password"))
        }
        assertFailsWith<UnauthorizedFailure> {
            services.auth.loginMicrosoft(MicrosoftUserInfo(member.email, member.displayName), setOf("example.com"))
        }

        val executor = Executors.newFixedThreadPool(2)
        val results = try {
            executor.invokeAll(
                listOf(
                    Callable { runCatching { services.users.update(firstAdmin, secondAdmin.id, UpdateUserCommand(null, Role.MEMBER, null, null)) } },
                    Callable { runCatching { services.users.update(secondAdmin, firstAdmin.id, UpdateUserCommand(null, Role.MEMBER, null, null)) } }
                )
            ).map { it.get() }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1, results.count(Result<User>::isSuccess))
        assertEquals(1, results.count(Result<User>::isFailure))
        val activeAdmins = services.queries.bootstrap(admin(services)).users.count { it.active && it.role == Role.ADMIN }
        assertEquals(1, activeAdmins)
    }

    @Test
    fun microsoftSsoAuthenticatesExistingActiveUsers() {
        val backend = QueueDosBackend.create(
            PostgresTestBackend.freshDataSource(),
            de.ljunker.queuedos.config.appJson,
            AuthTokenCodec("microsoft-test-secret-that-is-long-enough"),
            microsoftSsoSettings = MicrosoftSsoSettings(
                "client",
                "secret",
                "http://localhost/callback",
                allowedDomains = setOf("queuedos.local")
            ),
            microsoftIdentityClient = object : MicrosoftIdentityClient {
                override fun authorizationUrl(state: String, codeChallenge: String): String =
                    "https://login.example/$state"

                override fun userInfo(code: String, codeVerifier: String): MicrosoftUserInfo =
                    MicrosoftUserInfo("member@queuedos.local", "QueueDos Member")
            }
        )

        val authenticated = backend.services.microsoftSso.login("code", "verifier")

        assertEquals("user-member", authenticated.user.id)
    }

    @Test
    fun microsoftSsoCreatesUnknownMembersAndReusesTheirAccount() {
        val backend = microsoftBackend(MicrosoftUserInfo(" New.User@Example.com ", "  New User  "))

        val first = backend.services.microsoftSso.login("code", "verifier")
        val second = backend.services.microsoftSso.login("code", "verifier")

        assertEquals(first.user.id, second.user.id)
        assertEquals("org-default", first.user.organizationId)
        assertEquals("new.user@example.com", first.user.email)
        assertEquals("New User", first.user.displayName)
        assertEquals(Role.MEMBER, first.user.role)
        assertTrue(first.user.active)
        assertFalse(first.user.localLoginEnabled)
        assertFalse(first.user.mustChangePassword)
        assertEquals(first.user.id, backend.services.auth.userByToken(first.token)?.id)
        val users = backend.services.queries.bootstrap(admin(backend.services)).users
        assertEquals(1, users.count { it.email == "new.user@example.com" })
    }

    @Test
    fun concurrentMicrosoftLoginsCreateOnlyOneAccount() {
        val backend = microsoftBackend(MicrosoftUserInfo("parallel@example.com", "Parallel User"))
        val executor = Executors.newFixedThreadPool(2)

        val users = try {
            executor.invokeAll(
                listOf(
                    Callable { backend.services.microsoftSso.login("first", "verifier").user },
                    Callable { backend.services.microsoftSso.login("second", "verifier").user }
                )
            ).map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, users.map(User::id).distinct().size)
        val storedUsers = backend.services.queries.bootstrap(admin(backend.services)).users
        assertEquals(1, storedUsers.count { it.email == "parallel@example.com" })
    }

    @Test
    fun microsoftDomainAllowlistAppliesToExistingAndUnknownUsersExactly() {
        val services = newServices()

        assertFailsWith<UnauthorizedFailure> {
            services.auth.loginMicrosoft(
                MicrosoftUserInfo("member@queuedos.local", "Existing Member"),
                setOf("example.com")
            )
        }
        assertFailsWith<UnauthorizedFailure> {
            services.auth.loginMicrosoft(
                MicrosoftUserInfo("unknown@team.example.com", "Unknown Member"),
                setOf("example.com")
            )
        }

        assertTrue(services.queries.bootstrap(admin(services)).users.none { it.email == "unknown@team.example.com" })
    }

    @Test
    fun microsoftLoginDoesNotReactivateInactiveUsers() {
        val fixture = PostgresTestBackend.create()
        fixture.sql {
            prepareStatement("UPDATE queuedos_users SET active = false WHERE email = ?").use {
                it.setString(1, "member@queuedos.local")
                it.executeUpdate()
            }
        }

        assertFailsWith<UnauthorizedFailure> {
            fixture.backend.services.auth.loginMicrosoft(
                MicrosoftUserInfo("member@queuedos.local", "QueueDos Member"),
                setOf("queuedos.local")
            )
        }

        val users = fixture.backend.services.queries.bootstrap(admin(fixture.backend.services)).users
        assertFalse(users.single { it.email == "member@queuedos.local" }.active)
    }

    @Test
    fun microsoftLoginFallsBackToEmailPrefixForMissingDisplayName() {
        val services = newServices()

        val authenticated = services.auth.loginMicrosoft(
            MicrosoftUserInfo("fallback@example.com", "   "),
            setOf("example.com")
        )

        assertEquals("fallback", authenticated.user.displayName)
    }

    @Test
    fun microsoftLoginReportsMissingDefaultOrganization() {
        val fixture = PostgresTestBackend.create()
        fixture.sql {
            createStatement().use { it.executeUpdate("DELETE FROM queuedos_organizations") }
        }

        val failure = assertFailsWith<BadRequestFailure> {
            fixture.backend.services.auth.loginMicrosoft(
                MicrosoftUserInfo("new@example.com", "New User"),
                setOf("example.com")
            )
        }

        assertTrue(failure.message.orEmpty().contains("org-default"))
    }

    @Test
    fun microsoftSsoIsDisabledWithoutAllowedDomains() {
        val backend = microsoftBackend(
            MicrosoftUserInfo("new@example.com", "New User"),
            allowedDomains = emptySet()
        )

        assertFalse(backend.services.microsoftSso.enabled)
        assertFailsWith<NotFoundFailure> {
            backend.services.microsoftSso.authorizationUrl("state", "challenge")
        }
    }

    @Test
    fun microsoftAllowedDomainsAreNormalizedAndValidated() {
        assertEquals(
            setOf("example.com", "example.org"),
            parseMicrosoftAllowedDomains(" Example.com,EXAMPLE.ORG ")
        )
        assertTrue(parseMicrosoftAllowedDomains(null).isEmpty())
        assertTrue(parseMicrosoftAllowedDomains("  ").isEmpty())
        assertFailsWith<BadRequestFailure> {
            parseMicrosoftAllowedDomains("example.com,not a domain")
        }
    }

    private fun microsoftBackend(
        userInfo: MicrosoftUserInfo,
        allowedDomains: Set<String> = setOf("example.com")
    ): QueueDosBackend = QueueDosBackend.create(
        PostgresTestBackend.freshDataSource(),
        de.ljunker.queuedos.config.appJson,
        AuthTokenCodec("microsoft-test-secret-that-is-long-enough"),
        microsoftSsoSettings = MicrosoftSsoSettings(
            "client",
            "secret",
            "http://localhost/callback",
            allowedDomains = allowedDomains
        ),
        microsoftIdentityClient = object : MicrosoftIdentityClient {
            override fun authorizationUrl(state: String, codeChallenge: String): String =
                "https://login.example/$state"

            override fun userInfo(code: String, codeVerifier: String): MicrosoftUserInfo = userInfo
        }
    )

    private fun newServices(): QueueDosServices = PostgresTestBackend.create().backend.services

    private fun admin(services: QueueDosServices) =
        services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user

    private fun validTypes() = listOf(CreateProjectTicketTypeCommand("Task", "", "#2563eb"))

    private fun validStatuses() = listOf(
        CreateProjectStatusCommand("Open", "TODO"),
        CreateProjectStatusCommand("Done", "DONE")
    )

    private class RecordingSlackSender : SlackMessageSender {
        val messages = mutableListOf<Pair<String, String>>()

        override fun send(webhookUrl: String, text: String) {
            messages += webhookUrl to text
        }
    }
}
