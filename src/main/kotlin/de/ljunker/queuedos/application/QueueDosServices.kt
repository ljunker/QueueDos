package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.*
import de.ljunker.queuedos.persistence.QueueRepositories
import de.ljunker.queuedos.persistence.TransactionRunner
import de.ljunker.queuedos.persistence.defaultTicketTypes
import de.ljunker.queuedos.persistence.defaultWorkflow
import de.ljunker.queuedos.security.*
import de.ljunker.queuedos.validation.*
import java.time.Instant
import java.util.*

class QueueDosServices(
    val auth: AuthenticationService,
    val microsoftSso: MicrosoftSsoService,
    val queries: WorkspaceQueryService,
    val projects: ProjectService,
    val users: UserService,
    val ticketTypes: TicketTypeService,
    val workflows: WorkflowService,
    val tickets: TicketService,
    val savedTicketFilters: SavedTicketFilterService,
    val activityHooks: ActivityHookService
)

data class AuthenticatedUser(
    val token: String,
    val user: User
)

class AuthenticationService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories,
    private val tokenCodec: AuthTokenCodec
) {
    fun login(command: LoginCommand): AuthenticatedUser =
        transactions.inTransaction {
            val email = command.email.trim().lowercase(Locale.ROOT)
            val user = repositories.users.findActiveByEmail(email)
                ?: throw UnauthorizedFailure("Invalid email or password.")
            if (!verifyPassword(command.password, user.passwordSalt, user.passwordHash)) {
                throw UnauthorizedFailure("Invalid email or password.")
            }
            val authenticatedUser = if (passwordNeedsRehash(user.passwordHash)) {
                user.copy(passwordSalt = BCRYPT_PASSWORD_MARKER, passwordHash = hashPassword(command.password))
                    .also(repositories.users::update)
            } else {
                user
            }
            AuthenticatedUser(tokenCodec.createToken(authenticatedUser.id), authenticatedUser)
        }

    fun loginMicrosoft(email: String): AuthenticatedUser =
        transactions.inTransaction {
            val user = repositories.users.findActiveByEmail(normalizeEmail(email))
                ?: throw UnauthorizedFailure("Microsoft account is not linked to an active QueueDos user.")
            AuthenticatedUser(tokenCodec.createToken(user.id), user)
        }

    fun userByToken(token: String): User? =
        transactions.inTransaction {
            tokenCodec.userIdFromToken(token)?.let(repositories.users::findActiveById)
        }
}

class WorkspaceQueryService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun bootstrap(actor: User): BootstrapData =
        transactions.inTransaction {
            BootstrapData(
                currentUser = actor,
                organizations = repositories.organizations.listById(actor.organizationId),
                users = repositories.users.listByOrganization(actor.organizationId),
                projects = repositories.projects.listByOrganization(actor.organizationId),
                ticketTypes = repositories.ticketTypes.listByOrganization(actor.organizationId),
                workflows = repositories.workflows.listByOrganization(actor.organizationId),
                tickets = repositories.tickets.listByOrganization(actor.organizationId),
                deletedTickets = if (actor.role == Role.ADMIN) {
                    repositories.tickets.listDeletedByOrganization(actor.organizationId)
                } else {
                    emptyList()
                },
                comments = repositories.tickets.comments(actor.organizationId),
                changes = repositories.tickets.changes(actor.organizationId),
                savedTicketFilters = repositories.savedTicketFilters.listForOwner(actor.organizationId, actor.id),
                activityHooks = if (actor.role == Role.ADMIN) {
                    repositories.activityHooks.listByOrganization(actor.organizationId)
                } else {
                    emptyList()
                }
            )
        }

    fun tickets(
        actor: User,
        projectId: String?,
        query: String?,
        statusId: String?,
        typeId: String?,
        priority: Priority?,
        assigneeId: String?,
        label: String?,
        sort: String?
    ): List<Ticket> =
        transactions.inTransaction {
            var tickets = repositories.tickets.listByOrganization(actor.organizationId).asSequence()
            if (!projectId.isNullOrBlank()) tickets = tickets.filter { it.projectId == projectId }
            if (!query.isNullOrBlank()) {
                val needle = query.trim().lowercase(Locale.ROOT)
                tickets = tickets.filter {
                    it.key.lowercase(Locale.ROOT).contains(needle) ||
                            it.title.lowercase(Locale.ROOT).contains(needle) ||
                            it.description.lowercase(Locale.ROOT).contains(needle) ||
                            it.labels.any { ticketLabel -> ticketLabel.contains(needle) }
                }
            }
            if (!statusId.isNullOrBlank()) tickets = tickets.filter { it.statusId == statusId }
            if (!typeId.isNullOrBlank()) tickets = tickets.filter { it.typeId == typeId }
            if (priority != null) tickets = tickets.filter { it.priority == priority }
            if (!assigneeId.isNullOrBlank()) {
                tickets = if (assigneeId == "unassigned") {
                    tickets.filter { it.assigneeId == null }
                } else {
                    tickets.filter { it.assigneeId == assigneeId }
                }
            }
            if (!label.isNullOrBlank()) {
                val normalizedLabel = label.trim().lowercase(Locale.ROOT)
                tickets = tickets.filter { normalizedLabel in it.labels }
            }
            val result = tickets.toList()
            val workflows = repositories.workflows.listByOrganization(actor.organizationId).associateBy { it.projectId }
            val projectKeys =
                repositories.projects.listByOrganization(actor.organizationId).associate { it.id to it.key }
            when (sort ?: "number") {
                "title" -> result.sortedBy { it.title.lowercase(Locale.ROOT) }
                "priority" -> result.sortedByDescending { it.priority.ordinal }
                "status" -> result.sortedBy {
                    workflows[it.projectId]?.statuses?.firstOrNull { status -> status.id == it.statusId }?.sortOrder
                        ?: Int.MAX_VALUE
                }

                "updated" -> result.sortedByDescending { it.updatedAt }
                else -> result.sortedWith(compareBy<Ticket> { projectKeys[it.projectId] ?: "" }.thenBy { it.number })
            }
        }

    fun ticketDetail(actor: User, ticketId: String): TicketDetailData =
        transactions.inTransaction {
            val ticket = requireTicket(actor, ticketId)
            TicketDetailData(
                ticket = ticket,
                comments = repositories.tickets.comments(actor.organizationId, ticket.id),
                changes = repositories.tickets.changes(actor.organizationId, ticket.id)
            )
        }

    private fun requireTicket(actor: User, ticketId: String): Ticket =
        repositories.tickets.findById(actor.organizationId, ticketId)
            ?: throw NotFoundFailure("Ticket not found.")
}

class ProjectService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun create(actor: User, command: CreateProjectCommand): Project =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val key = normalizeProjectKey(command.key)
            if (repositories.projects.keyExists(actor.organizationId, key)) {
                throw ConflictFailure("A project with this key already exists.")
            }
            val project = Project(
                id = id("project"),
                organizationId = actor.organizationId,
                key = key,
                name = requireName(command.name, "Project name"),
                description = command.description.trim()
            )
            repositories.projects.insert(project)
            defaultTicketTypes(actor.organizationId, project.id).forEach(repositories.ticketTypes::insert)
            repositories.workflows.insert(defaultWorkflow(actor.organizationId, project.id))
            project
        }

    fun update(actor: User, projectId: String, command: UpdateProjectCommand): Project =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val project = requireProject(actor, projectId)
            val nextKey = command.key?.let(::normalizeProjectKey) ?: project.key
            if (nextKey != project.key) {
                if (repositories.projects.hasTickets(project.id)) {
                    throw ConflictFailure("Project key cannot be changed after tickets exist.")
                }
                if (repositories.projects.keyExists(actor.organizationId, nextKey)) {
                    throw ConflictFailure("A project with this key already exists.")
                }
            }
            project.copy(
                key = nextKey,
                name = command.name?.let { requireName(it, "Project name") } ?: project.name,
                description = command.description?.trim() ?: project.description,
                archived = command.archived ?: project.archived
            ).also(repositories.projects::update)
        }

    private fun requireProject(actor: User, projectId: String): Project =
        repositories.projects.findById(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Project not found.")
}

class UserService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun create(actor: User, command: CreateUserCommand): User =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val email = normalizeEmail(command.email)
            if (repositories.users.emailExists(actor.organizationId, email)) {
                throw ConflictFailure("A user with this email already exists.")
            }
            User(
                id = id("user"),
                organizationId = actor.organizationId,
                email = email,
                displayName = requireName(command.displayName, "Display name"),
                role = command.role,
                active = true,
                passwordSalt = BCRYPT_PASSWORD_MARKER,
                passwordHash = hashPassword(requirePassword(command.password))
            ).also(repositories.users::insert)
        }

    fun update(actor: User, userId: String, command: UpdateUserCommand): User =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val current = repositories.users.findById(actor.organizationId, userId)
                ?: throw NotFoundFailure("User not found.")
            if (current.id == actor.id && command.active == false) {
                throw ConflictFailure("You cannot deactivate your own account.")
            }
            val passwordHash = command.password?.takeIf { it.isNotBlank() }?.let {
                BCRYPT_PASSWORD_MARKER to hashPassword(requirePassword(it))
            }
            current.copy(
                displayName = command.displayName?.let { requireName(it, "Display name") } ?: current.displayName,
                role = command.role ?: current.role,
                active = command.active ?: current.active,
                passwordSalt = passwordHash?.first ?: current.passwordSalt,
                passwordHash = passwordHash?.second ?: current.passwordHash
            ).also(repositories.users::update)
        }
}

class TicketTypeService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun create(actor: User, command: CreateTicketTypeCommand): TicketType =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val project = requireProject(actor, command.projectId)
            val name = requireName(command.name, "Ticket type name")
            if (repositories.ticketTypes.nameExists(project.id, name)) {
                throw ConflictFailure("A ticket type with this name already exists.")
            }
            TicketType(
                id = id("type"),
                organizationId = actor.organizationId,
                projectId = project.id,
                name = name,
                description = command.description.trim(),
                color = normalizeColor(command.color)
            ).also(repositories.ticketTypes::insert)
        }

    fun update(actor: User, typeId: String, command: UpdateTicketTypeCommand): TicketType =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val current = requireTicketType(actor, typeId)
            val nextName = command.name?.let { requireName(it, "Ticket type name") } ?: current.name
            if (!nextName.equals(current.name, ignoreCase = true) &&
                repositories.ticketTypes.nameExists(current.projectId, nextName, current.id)
            ) {
                throw ConflictFailure("A ticket type with this name already exists.")
            }
            current.copy(
                name = nextName,
                description = command.description?.trim() ?: current.description,
                color = command.color?.let(::normalizeColor) ?: current.color
            ).also(repositories.ticketTypes::update)
        }

    fun delete(actor: User, typeId: String) {
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val current = requireTicketType(actor, typeId)
            if (repositories.ticketTypes.isUsed(current.id)) {
                throw ConflictFailure("Ticket type is used by existing tickets.")
            }
            repositories.ticketTypes.delete(current.id)
        }
    }

    private fun requireProject(actor: User, projectId: String): Project =
        repositories.projects.findById(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Project not found.")

    private fun requireTicketType(actor: User, typeId: String): TicketType =
        repositories.ticketTypes.findById(actor.organizationId, typeId)
            ?: throw NotFoundFailure("Ticket type not found.")
}

class WorkflowService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun save(actor: User, projectId: String, command: SaveWorkflowCommand): Workflow =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val project = requireProject(actor, projectId)
            val statuses = normalizeStatuses(command.statuses, ::id)
            val statusIds = statuses.map { it.id }.toSet()
            val removedStatusTicket = repositories.tickets.listByOrganization(actor.organizationId)
                .firstOrNull { it.projectId == project.id && it.statusId !in statusIds }
            if (removedStatusTicket != null) {
                throw ConflictFailure("Status is still used by ${removedStatusTicket.key}; move tickets before removing it.")
            }
            val transitions = normalizeTransitions(command.transitions, statusIds, ::id)
            val current = repositories.workflows.findByProject(actor.organizationId, project.id)
            val workflow = Workflow(
                id = current?.id ?: id("workflow"),
                organizationId = actor.organizationId,
                projectId = project.id,
                statuses = statuses,
                transitions = transitions
            )
            if (current == null) repositories.workflows.insert(workflow) else repositories.workflows.replace(workflow)
            workflow
        }

    private fun requireProject(actor: User, projectId: String): Project =
        repositories.projects.findById(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Project not found.")
}

class TicketService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories,
    private val activityNotifier: ActivityNotifier = NoOpActivityNotifier,
    private val transitionEvaluator: WorkflowTransitionEvaluator = WorkflowTransitionEvaluator()
) {
    fun create(actor: User, command: CreateTicketCommand): Ticket {
        val ticket = transactions.inTransaction {
            val project = repositories.projects.findByIdForUpdate(actor.organizationId, command.projectId)
                ?: throw NotFoundFailure("Project not found.")
            if (project.archived) {
                throw ConflictFailure("Archived projects cannot receive new tickets.")
            }
            val workflow = requireWorkflow(actor, project.id)
            val statusId = command.statusId?.takeIf { it.isNotBlank() } ?: workflow.statuses.first().id
            requireStatus(workflow, statusId)
            val type = requireTicketTypeForProject(actor, command.typeId, project.id)
            requireAssignee(actor, command.assigneeId)
            val timestamp = now()
            val ticket = Ticket(
                id = id("ticket"),
                organizationId = actor.organizationId,
                projectId = project.id,
                number = project.nextTicketNumber,
                key = "${project.key}-${project.nextTicketNumber}",
                title = requireName(command.title, "Ticket title"),
                description = command.description.trim(),
                statusId = statusId,
                typeId = type.id,
                priority = command.priority,
                assigneeId = command.assigneeId?.takeIf { it.isNotBlank() },
                labels = normalizeLabels(command.labels),
                dueDate = normalizeDueDate(command.dueDate),
                estimate = normalizeEstimate(command.estimate),
                reporterId = actor.id,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            repositories.projects.update(project.copy(nextTicketNumber = project.nextTicketNumber + 1))
            repositories.tickets.insert(ticket)
            repositories.tickets.insertChanges(
                listOf(ticketChange(actor, ticket, "ticket", null, "created", timestamp))
            )
            ticket
        }
        activityNotifier.publish(TicketActivity(ActivityEventType.TICKET_CREATED, ticket, actor))
        return ticket
    }

    fun update(actor: User, ticketId: String, command: UpdateTicketCommand): Ticket {
        val ticket = transactions.inTransaction {
            val current = requireTicket(actor, ticketId)
            val project = requireProject(actor, current.projectId)
            if (project.archived) {
                throw ConflictFailure("Archived project tickets cannot be edited.")
            }
            val typeId = command.typeId ?: current.typeId
            requireTicketTypeForProject(actor, typeId, project.id)
            val assigneeId = command.assigneeId?.takeIf { it.isNotBlank() }
            requireAssignee(actor, assigneeId)
            val timestamp = now()
            val updated = current.copy(
                title = command.title?.let { requireName(it, "Ticket title") } ?: current.title,
                description = command.description?.trim() ?: current.description,
                typeId = typeId,
                priority = command.priority ?: current.priority,
                assigneeId = if (command.assigneeId == null) current.assigneeId else assigneeId,
                labels = command.labels?.let(::normalizeLabels) ?: current.labels,
                dueDate = if (command.clearDueDate) null else command.dueDate?.let(::normalizeDueDate)
                    ?: current.dueDate,
                estimate = if (command.clearEstimate) null else command.estimate?.let(::normalizeEstimate)
                    ?: current.estimate,
                updatedAt = timestamp
            )
            repositories.tickets.update(updated)
            repositories.tickets.insertChanges(ticketChanges(actor, current, updated, timestamp))
            updated
        }
        activityNotifier.publish(TicketActivity(ActivityEventType.TICKET_UPDATED, ticket, actor))
        return ticket
    }

    fun bulkUpdate(actor: User, command: BulkUpdateTicketsCommand): List<Ticket> =
        transactions.inTransaction {
            val ticketIds = command.ticketIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (ticketIds.isEmpty()) throw BadRequestFailure("At least one ticket is required.")
            if (command.clearAssignee && !command.assigneeId.isNullOrBlank()) {
                throw BadRequestFailure("Bulk update cannot set and clear an assignee at once.")
            }
            val assigneeId = command.assigneeId?.takeIf { it.isNotBlank() }
            if (!command.clearAssignee && assigneeId == null && command.priority == null) {
                throw BadRequestFailure("Bulk update needs an assignee or priority change.")
            }
            requireAssignee(actor, assigneeId)
            val currentTickets = ticketIds.map { ticketId ->
                val ticket = requireTicket(actor, ticketId)
                if (requireProject(actor, ticket.projectId).archived) {
                    throw ConflictFailure("Archived project tickets cannot be edited.")
                }
                ticket
            }
            val timestamp = now()
            currentTickets.map { ticket ->
                ticket.copy(
                    priority = command.priority ?: ticket.priority,
                    assigneeId = when {
                        command.clearAssignee -> null
                        assigneeId != null -> assigneeId
                        else -> ticket.assigneeId
                    },
                    updatedAt = timestamp
                )
            }.also { updated ->
                updated.forEach(repositories.tickets::update)
                repositories.tickets.insertChanges(
                    currentTickets.zip(updated)
                        .flatMap { (current, next) -> ticketChanges(actor, current, next, timestamp) }
                )
            }
        }

    fun transition(actor: User, ticketId: String, command: TransitionTicketCommand): Ticket {
        var previousStatusId = ""
        val ticket = transactions.inTransaction {
            val current = requireTicket(actor, ticketId)
            previousStatusId = current.statusId
            val project = requireProject(actor, current.projectId)
            if (project.archived) {
                throw ConflictFailure("Archived project tickets cannot move.")
            }
            val workflow = requireWorkflow(actor, project.id)
            when (transitionEvaluator.resolve(workflow, current, command.toStatusId, actor.role)) {
                WorkflowTransitionResolution.Allowed -> Unit
                WorkflowTransitionResolution.Unchanged -> return@inTransaction current
                WorkflowTransitionResolution.MissingStatus -> throw NotFoundFailure("Workflow status not found.")
                WorkflowTransitionResolution.NotAllowed -> throw ConflictFailure("This workflow transition is not allowed.")
                WorkflowTransitionResolution.RoleDenied -> throw ForbiddenFailure("Your role cannot perform this workflow transition.")
                WorkflowTransitionResolution.BackwardDenied -> throw ConflictFailure("This workflow transition cannot move tickets backwards.")
            }
            val timestamp = now()
            current.copy(statusId = command.toStatusId, updatedAt = timestamp).also { updated ->
                repositories.tickets.update(updated)
                repositories.tickets.insertChanges(
                    listOf(ticketChange(actor, current, "statusId", current.statusId, command.toStatusId, timestamp))
                )
            }
        }
        if (previousStatusId != ticket.statusId) {
            activityNotifier.publish(
                TicketActivity(
                    ActivityEventType.TICKET_MOVED,
                    ticket,
                    actor,
                    mapOf("fromStatusId" to previousStatusId, "toStatusId" to ticket.statusId)
                )
            )
        }
        return ticket
    }

    fun addComment(actor: User, ticketId: String, command: AddTicketCommentCommand): TicketComment {
        lateinit var ticket: Ticket
        val comment = transactions.inTransaction {
            ticket = requireTicket(actor, ticketId)
            val timestamp = now()
            TicketComment(
                id = id("comment"),
                organizationId = actor.organizationId,
                ticketId = ticket.id,
                authorId = actor.id,
                body = requireName(command.body, "Comment"),
                createdAt = timestamp
            ).also { comment ->
                repositories.tickets.insertComment(comment)
                repositories.tickets.insertChanges(
                    listOf(ticketChange(actor, ticket, "comment", null, "added", timestamp))
                )
            }
        }
        activityNotifier.publish(
            TicketActivity(ActivityEventType.COMMENT_ADDED, ticket, actor, mapOf("comment" to comment.body))
        )
        return comment
    }

    fun delete(actor: User, ticketId: String) {
        val ticket = transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val current = requireTicket(actor, ticketId)
            val timestamp = now()
            current.copy(updatedAt = timestamp, deletedAt = timestamp, deletedById = actor.id).also { deleted ->
                repositories.tickets.update(deleted)
                repositories.tickets.insertChanges(
                    listOf(ticketChange(actor, current, "deletedAt", null, timestamp, timestamp))
                )
            }
        }
        activityNotifier.publish(TicketActivity(ActivityEventType.TICKET_DELETED, ticket, actor))
    }

    fun restore(actor: User, ticketId: String): Ticket {
        val ticket = transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            val current = requireDeletedTicket(actor, ticketId)
            val timestamp = now()
            current.copy(updatedAt = timestamp, deletedAt = null, deletedById = null).also { restored ->
                repositories.tickets.update(restored)
                repositories.tickets.insertChanges(
                    listOf(ticketChange(actor, restored, "deletedAt", current.deletedAt, null, timestamp))
                )
            }
        }
        activityNotifier.publish(TicketActivity(ActivityEventType.TICKET_RESTORED, ticket, actor))
        return ticket
    }

    fun saveCommitment(actor: User, ticketId: String, command: SaveTicketCommitmentCommand): Ticket {
        val ticket = transactions.inTransaction {
            val current = requireTicket(actor, ticketId)
            if (requireProject(actor, current.projectId).archived) {
                throw ConflictFailure("Archived project tickets cannot be edited.")
            }
            if (command.committed == (actor.id in current.committedUserIds)) {
                return@inTransaction current
            }
            val timestamp = now()
            repositories.tickets.setCommitment(current.id, actor.id, command.committed)
            val updated = current.copy(
                committedUserIds = if (command.committed) {
                    (current.committedUserIds + actor.id).distinct().sorted()
                } else {
                    current.committedUserIds - actor.id
                },
                updatedAt = timestamp
            )
            repositories.tickets.update(updated)
            repositories.tickets.insertChanges(
                listOf(
                    ticketChange(
                        actor,
                        updated,
                        "commitment",
                        if (command.committed) null else actor.id,
                        if (command.committed) actor.id else null,
                        timestamp
                    )
                )
            )
            updated
        }
        activityNotifier.publish(
            TicketActivity(
                ActivityEventType.COMMITMENT_CHANGED,
                ticket,
                actor,
                mapOf("commitment" to if (command.committed) "committed" else "left")
            )
        )
        return ticket
    }

    private fun requireProject(actor: User, projectId: String): Project =
        repositories.projects.findById(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Project not found.")

    private fun requireWorkflow(actor: User, projectId: String): Workflow =
        repositories.workflows.findByProject(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Workflow not found.")

    private fun requireStatus(workflow: Workflow, statusId: String): WorkflowStatus =
        workflow.statuses.firstOrNull { it.id == statusId }
            ?: throw NotFoundFailure("Workflow status not found.")

    private fun requireTicket(actor: User, ticketId: String): Ticket =
        repositories.tickets.findById(actor.organizationId, ticketId)
            ?: throw NotFoundFailure("Ticket not found.")

    private fun requireDeletedTicket(actor: User, ticketId: String): Ticket =
        repositories.tickets.findDeletedById(actor.organizationId, ticketId)
            ?: throw NotFoundFailure("Deleted ticket not found.")

    private fun requireTicketTypeForProject(actor: User, typeId: String, projectId: String): TicketType =
        repositories.ticketTypes.findForProject(actor.organizationId, projectId, typeId)
            ?: throw NotFoundFailure("Ticket type not found.")

    private fun requireAssignee(actor: User, assigneeId: String?) {
        if (assigneeId.isNullOrBlank()) return
        val assignee = repositories.users.findById(actor.organizationId, assigneeId)
        if (assignee?.active != true) {
            throw NotFoundFailure("Assignee not found.")
        }
    }
}

class SavedTicketFilterService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun create(actor: User, command: CreateSavedTicketFilterCommand): SavedTicketFilter =
        transactions.inTransaction {
            val projectId = normalizeProjectContext(actor, command.view, command.projectId)
            val name = requireName(command.name, "Saved filter name")
            requireUniqueName(actor, command.view, projectId, name, null)
            SavedTicketFilter(
                id = id("filter"),
                organizationId = actor.organizationId,
                ownerId = actor.id,
                name = name,
                view = command.view,
                projectId = projectId,
                filters = normalizeCriteria(actor, command.view, projectId, command.filters)
            ).also(repositories.savedTicketFilters::insert)
        }

    fun update(actor: User, filterId: String, command: UpdateSavedTicketFilterCommand): SavedTicketFilter =
        transactions.inTransaction {
            if (command.name == null && command.filters == null) {
                throw BadRequestFailure("Saved filter update needs a name or filter state.")
            }
            val current = requireFilter(actor, filterId)
            val name = command.name?.let { requireName(it, "Saved filter name") } ?: current.name
            requireUniqueName(actor, current.view, current.projectId, name, current.id)
            current.copy(
                name = name,
                filters = command.filters?.let { normalizeCriteria(actor, current.view, current.projectId, it) }
                    ?: current.filters
            ).also(repositories.savedTicketFilters::update)
        }

    fun delete(actor: User, filterId: String) {
        transactions.inTransaction {
            repositories.savedTicketFilters.delete(requireFilter(actor, filterId).id)
        }
    }

    private fun normalizeProjectContext(actor: User, view: SavedTicketFilterView, projectId: String?): String? =
        when (view) {
            SavedTicketFilterView.PROJECT_LIST -> {
                val context = projectId?.takeIf { it.isNotBlank() }
                    ?: throw BadRequestFailure("Project list filters need a project.")
                requireProject(actor, context).id
            }

            SavedTicketFilterView.MY_TICKETS -> {
                if (!projectId.isNullOrBlank()) {
                    throw BadRequestFailure("My Tickets filters do not use a project context.")
                }
                null
            }
        }

    private fun normalizeCriteria(
        actor: User,
        view: SavedTicketFilterView,
        projectContextId: String?,
        criteria: SavedTicketFilterCriteria
    ): SavedTicketFilterCriteria {
        val query = criteria.q.trim()
        if (query.length > 200) throw BadRequestFailure("Saved filter search must be 200 characters or fewer.")
        val label = criteria.label.trim().lowercase(Locale.ROOT)
        if (label.length > 32) throw BadRequestFailure("Saved filter label must be 32 characters or fewer.")
        val sort = normalizeSort(view, criteria.sort)
        return when (view) {
            SavedTicketFilterView.PROJECT_LIST -> {
                val projectId = projectContextId ?: throw BadRequestFailure("Project list filters need a project.")
                if (!criteria.projectId.isNullOrBlank()) {
                    throw BadRequestFailure("Project list filter criteria cannot switch projects.")
                }
                if (criteria.statusId.isNotBlank()) requireStatus(requireWorkflow(actor, projectId), criteria.statusId)
                if (criteria.typeId.isNotBlank()) requireTicketTypeForProject(actor, criteria.typeId, projectId)
                if (criteria.assigneeId.isNotBlank() && criteria.assigneeId != "unassigned") {
                    requireAssignee(actor, criteria.assigneeId)
                }
                criteria.copy(
                    projectId = null,
                    q = query,
                    statusId = criteria.statusId.trim(),
                    typeId = criteria.typeId.trim(),
                    assigneeId = criteria.assigneeId.trim(),
                    label = label,
                    sort = sort
                )
            }

            SavedTicketFilterView.MY_TICKETS -> {
                if (criteria.statusId.isNotBlank() || criteria.typeId.isNotBlank() || criteria.assigneeId.isNotBlank()) {
                    throw BadRequestFailure("My Tickets filters only support project, search, priority, label, and sorting.")
                }
                criteria.copy(
                    projectId = criteria.projectId?.takeIf { it.isNotBlank() }?.let { requireProject(actor, it).id },
                    q = query,
                    statusId = "",
                    typeId = "",
                    assigneeId = "",
                    label = label,
                    sort = sort
                )
            }
        }
    }

    private fun normalizeSort(view: SavedTicketFilterView, value: String): String {
        val sort = value.trim().ifBlank { "number" }
        val supported = when (view) {
            SavedTicketFilterView.PROJECT_LIST -> setOf("number", "title", "priority", "status", "updated")
            SavedTicketFilterView.MY_TICKETS -> setOf("number", "title", "priority", "updated")
        }
        if (sort !in supported) throw BadRequestFailure("Saved filter sort is not supported.")
        return sort
    }

    private fun requireUniqueName(
        actor: User,
        view: SavedTicketFilterView,
        projectId: String?,
        name: String,
        ignoredId: String?
    ) {
        if (repositories.savedTicketFilters.nameExists(
                actor.organizationId,
                actor.id,
                view.name,
                projectId,
                name,
                ignoredId
            )
        ) {
            throw ConflictFailure("A saved filter with this name already exists.")
        }
    }

    private fun requireFilter(actor: User, filterId: String): SavedTicketFilter =
        repositories.savedTicketFilters.findForOwner(actor.organizationId, actor.id, filterId)
            ?: throw NotFoundFailure("Saved filter not found.")

    private fun requireProject(actor: User, projectId: String): Project =
        repositories.projects.findById(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Project not found.")

    private fun requireWorkflow(actor: User, projectId: String): Workflow =
        repositories.workflows.findByProject(actor.organizationId, projectId)
            ?: throw NotFoundFailure("Workflow not found.")

    private fun requireStatus(workflow: Workflow, statusId: String) {
        if (workflow.statuses.none { it.id == statusId }) throw NotFoundFailure("Workflow status not found.")
    }

    private fun requireTicketTypeForProject(actor: User, typeId: String, projectId: String) {
        repositories.ticketTypes.findForProject(actor.organizationId, projectId, typeId)
            ?: throw NotFoundFailure("Ticket type not found.")
    }

    private fun requireAssignee(actor: User, assigneeId: String) {
        if (repositories.users.findById(actor.organizationId, assigneeId)?.active != true) {
            throw NotFoundFailure("Assignee not found.")
        }
    }
}

private fun ticketChanges(actor: User, current: Ticket, updated: Ticket, createdAt: String): List<TicketChange> =
    buildList {
        addIfChanged(actor, current, "title", current.title, updated.title, createdAt)
        addIfChanged(actor, current, "description", current.description, updated.description, createdAt)
        addIfChanged(actor, current, "typeId", current.typeId, updated.typeId, createdAt)
        addIfChanged(actor, current, "priority", current.priority.name, updated.priority.name, createdAt)
        addIfChanged(actor, current, "assigneeId", current.assigneeId, updated.assigneeId, createdAt)
        addIfChanged(
            actor,
            current,
            "labels",
            current.labels.joinToString(","),
            updated.labels.joinToString(","),
            createdAt
        )
        addIfChanged(actor, current, "dueDate", current.dueDate, updated.dueDate, createdAt)
        addIfChanged(actor, current, "estimate", current.estimate?.toString(), updated.estimate?.toString(), createdAt)
    }

private fun MutableList<TicketChange>.addIfChanged(
    actor: User,
    ticket: Ticket,
    field: String,
    oldValue: String?,
    newValue: String?,
    createdAt: String
) {
    if (oldValue != newValue) add(ticketChange(actor, ticket, field, oldValue, newValue, createdAt))
}

private fun ticketChange(
    actor: User,
    ticket: Ticket,
    field: String,
    oldValue: String?,
    newValue: String?,
    createdAt: String
): TicketChange =
    TicketChange(
        id = id("change"),
        organizationId = actor.organizationId,
        ticketId = ticket.id,
        actorId = actor.id,
        field = field,
        oldValue = oldValue,
        newValue = newValue,
        createdAt = createdAt
    )

private fun id(prefix: String): String = "$prefix-${UUID.randomUUID()}"

private fun now(): String = Instant.now().toString()
