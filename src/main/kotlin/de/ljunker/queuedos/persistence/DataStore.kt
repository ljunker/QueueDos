package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.api.ApiException
import de.ljunker.queuedos.api.BootstrapResponse
import de.ljunker.queuedos.api.BulkUpdateTicketsRequest
import de.ljunker.queuedos.api.CreateSavedTicketFilterRequest
import de.ljunker.queuedos.api.CreateTicketCommentRequest
import de.ljunker.queuedos.api.CreateProjectRequest
import de.ljunker.queuedos.api.CreateTicketRequest
import de.ljunker.queuedos.api.CreateTicketTypeRequest
import de.ljunker.queuedos.api.CreateUserRequest
import de.ljunker.queuedos.api.LoginRequest
import de.ljunker.queuedos.api.LoginResponse
import de.ljunker.queuedos.api.SaveWorkflowRequest
import de.ljunker.queuedos.api.TicketDetailResponse
import de.ljunker.queuedos.api.TransitionTicketRequest
import de.ljunker.queuedos.api.UpdateProjectRequest
import de.ljunker.queuedos.api.UpdateSavedTicketFilterRequest
import de.ljunker.queuedos.api.UpdateTicketRequest
import de.ljunker.queuedos.api.UpdateTicketTypeRequest
import de.ljunker.queuedos.api.UpdateUserRequest
import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.domain.Priority
import de.ljunker.queuedos.domain.Project
import de.ljunker.queuedos.domain.PublicUser
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.SavedTicketFilter
import de.ljunker.queuedos.domain.SavedTicketFilterCriteria
import de.ljunker.queuedos.domain.SavedTicketFilterView
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.TicketChange
import de.ljunker.queuedos.domain.TicketComment
import de.ljunker.queuedos.domain.TicketType
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.domain.Workflow
import de.ljunker.queuedos.domain.WorkflowStatus
import de.ljunker.queuedos.domain.publicView
import de.ljunker.queuedos.security.AuthTokenCodec
import de.ljunker.queuedos.security.BCRYPT_PASSWORD_MARKER
import de.ljunker.queuedos.security.hashPassword
import de.ljunker.queuedos.security.passwordNeedsRehash
import de.ljunker.queuedos.security.verifyPassword
import de.ljunker.queuedos.validation.normalizeColor
import de.ljunker.queuedos.validation.normalizeDueDate
import de.ljunker.queuedos.validation.normalizeEstimate
import de.ljunker.queuedos.validation.normalizeLabels
import de.ljunker.queuedos.validation.normalizeEmail
import de.ljunker.queuedos.validation.normalizeProjectKey
import de.ljunker.queuedos.validation.normalizeStatuses
import de.ljunker.queuedos.validation.normalizeTransitions
import de.ljunker.queuedos.validation.requireName
import de.ljunker.queuedos.validation.requirePassword
import de.ljunker.queuedos.validation.validateRequiredFields
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.UUID

class DataStore(
    private val storage: AppDataStorage,
    private val tokenCodec: AuthTokenCodec
) {
    constructor(
        dataFile: Path,
        json: Json,
        tokenCodec: AuthTokenCodec = AuthTokenCodec("queuedos-development-session-secret-change-me")
    ) : this(FileAppDataStorage(dataFile, json), tokenCodec)

    private val lock = Any()
    private var data: AppData = loadOrSeed()

    fun login(request: LoginRequest): LoginResponse = synchronized(lock) {
        val email = request.email.trim().lowercase(Locale.ROOT)
        val user = data.users.firstOrNull { it.email.lowercase(Locale.ROOT) == email && it.active }
            ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid email or password.")

        if (!verifyPassword(request.password, user.passwordSalt, user.passwordHash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "Invalid email or password.")
        }

        val authenticatedUser = if (passwordNeedsRehash(user.passwordHash)) {
            val upgraded = user.copy(
                passwordSalt = BCRYPT_PASSWORD_MARKER,
                passwordHash = hashPassword(request.password)
            )
            data = data.copy(users = data.users.map { if (it.id == user.id) upgraded else it })
            saveLocked()
            upgraded
        } else {
            user
        }

        LoginResponse(tokenCodec.createToken(authenticatedUser.id), authenticatedUser.publicView())
    }

    fun userByToken(token: String): User? = synchronized(lock) {
        val userId = tokenCodec.userIdFromToken(token) ?: return@synchronized null
        data.users.firstOrNull { it.id == userId && it.active }
    }

    fun bootstrap(user: User): BootstrapResponse = synchronized(lock) {
        val organizationId = user.organizationId
        BootstrapResponse(
            currentUser = user.publicView(),
            organizations = data.organizations.filter { it.id == organizationId },
            users = data.users.filter { it.organizationId == organizationId }.map { it.publicView() },
            projects = data.projects.filter { it.organizationId == organizationId },
            ticketTypes = data.ticketTypes.filter { it.organizationId == organizationId },
            workflows = data.workflows.filter { it.organizationId == organizationId },
            tickets = data.tickets.filter { it.organizationId == organizationId },
            comments = data.comments.filter { it.organizationId == organizationId },
            ticketChanges = data.ticketChanges.filter { it.organizationId == organizationId },
            savedTicketFilters = data.savedTicketFilters.filter {
                it.organizationId == organizationId && it.ownerId == user.id
            },
            priorities = Priority.entries.toList()
        )
    }

    fun createProject(actor: User, request: CreateProjectRequest): Project = synchronized(lock) {
        requireAdmin(actor)
        val key = normalizeProjectKey(request.key)
        requireUniqueProjectKey(actor.organizationId, key)
        val project = Project(
            id = id("project"),
            organizationId = actor.organizationId,
            key = key,
            name = requireName(request.name, "Project name"),
            description = request.description.trim()
        )
        val ticketTypes = defaultTicketTypes(actor.organizationId, project.id)
        val workflow = defaultWorkflow(actor.organizationId, project.id)
        data = data.copy(
            projects = data.projects + project,
            ticketTypes = data.ticketTypes + ticketTypes,
            workflows = data.workflows + workflow
        )
        saveLocked()
        project
    }

    fun updateProject(actor: User, projectId: String, request: UpdateProjectRequest): Project = synchronized(lock) {
        requireAdmin(actor)
        val project = requireProject(actor, projectId)
        val nextKey = request.key?.let { normalizeProjectKey(it) } ?: project.key
        if (nextKey != project.key) {
            if (data.tickets.any { it.projectId == project.id }) {
                throw ApiException(HttpStatusCode.Conflict, "Project key cannot be changed after tickets exist.")
            }
            requireUniqueProjectKey(actor.organizationId, nextKey)
        }
        val updated = project.copy(
            key = nextKey,
            name = request.name?.let { requireName(it, "Project name") } ?: project.name,
            description = request.description?.trim() ?: project.description,
            archived = request.archived ?: project.archived
        )
        data = data.copy(projects = data.projects.map { if (it.id == projectId) updated else it })
        saveLocked()
        updated
    }

    fun createUser(actor: User, request: CreateUserRequest): PublicUser = synchronized(lock) {
        requireAdmin(actor)
        val email = normalizeEmail(request.email)
        if (data.users.any { it.organizationId == actor.organizationId && it.email.lowercase(Locale.ROOT) == email }) {
            throw ApiException(HttpStatusCode.Conflict, "A user with this email already exists.")
        }
        val user = User(
            id = id("user"),
            organizationId = actor.organizationId,
            email = email,
            displayName = requireName(request.displayName, "Display name"),
            role = request.role,
            active = true,
            passwordSalt = BCRYPT_PASSWORD_MARKER,
            passwordHash = hashPassword(requirePassword(request.password))
        )
        data = data.copy(users = data.users + user)
        saveLocked()
        user.publicView()
    }

    fun updateUser(actor: User, userId: String, request: UpdateUserRequest): PublicUser = synchronized(lock) {
        requireAdmin(actor)
        val current = data.users.firstOrNull { it.id == userId && it.organizationId == actor.organizationId }
            ?: throw ApiException(HttpStatusCode.NotFound, "User not found.")
        if (current.id == actor.id && request.active == false) {
            throw ApiException(HttpStatusCode.Conflict, "You cannot deactivate your own account.")
        }
        val passwordUpdate = request.password?.takeIf { it.isNotBlank() }?.let {
            BCRYPT_PASSWORD_MARKER to hashPassword(requirePassword(it))
        }
        val updated = current.copy(
            displayName = request.displayName?.let { requireName(it, "Display name") } ?: current.displayName,
            role = request.role ?: current.role,
            active = request.active ?: current.active,
            passwordSalt = passwordUpdate?.first ?: current.passwordSalt,
            passwordHash = passwordUpdate?.second ?: current.passwordHash
        )
        data = data.copy(users = data.users.map { if (it.id == userId) updated else it })
        saveLocked()
        updated.publicView()
    }

    fun createTicketType(actor: User, request: CreateTicketTypeRequest): TicketType = synchronized(lock) {
        requireAdmin(actor)
        val project = requireProject(actor, request.projectId)
        val name = requireName(request.name, "Ticket type name")
        if (data.ticketTypes.any { it.projectId == project.id && it.name.equals(name, ignoreCase = true) }) {
            throw ApiException(HttpStatusCode.Conflict, "A ticket type with this name already exists.")
        }
        val ticketType = TicketType(
            id = id("type"),
            organizationId = actor.organizationId,
            projectId = project.id,
            name = name,
            description = request.description.trim(),
            color = normalizeColor(request.color)
        )
        data = data.copy(ticketTypes = data.ticketTypes + ticketType)
        saveLocked()
        ticketType
    }

    fun updateTicketType(actor: User, typeId: String, request: UpdateTicketTypeRequest): TicketType = synchronized(lock) {
        requireAdmin(actor)
        val current = requireTicketType(actor, typeId)
        val nextName = request.name?.let { requireName(it, "Ticket type name") } ?: current.name
        if (nextName != current.name && data.ticketTypes.any {
                it.projectId == current.projectId && it.id != current.id && it.name.equals(nextName, ignoreCase = true)
            }) {
            throw ApiException(HttpStatusCode.Conflict, "A ticket type with this name already exists.")
        }
        val updated = current.copy(
            name = nextName,
            description = request.description?.trim() ?: current.description,
            color = request.color?.let { normalizeColor(it) } ?: current.color
        )
        data = data.copy(ticketTypes = data.ticketTypes.map { if (it.id == typeId) updated else it })
        saveLocked()
        updated
    }

    fun deleteTicketType(actor: User, typeId: String) = synchronized(lock) {
        requireAdmin(actor)
        val current = requireTicketType(actor, typeId)
        if (data.tickets.any { it.typeId == current.id }) {
            throw ApiException(HttpStatusCode.Conflict, "Ticket type is used by existing tickets.")
        }
        data = data.copy(ticketTypes = data.ticketTypes.filterNot { it.id == typeId })
        saveLocked()
    }

    fun saveWorkflow(actor: User, projectId: String, request: SaveWorkflowRequest): Workflow = synchronized(lock) {
        requireAdmin(actor)
        val project = requireProject(actor, projectId)
        val statuses = normalizeStatuses(request.statuses, ::id)
        val statusIds = statuses.map { it.id }.toSet()
        val projectTickets = data.tickets.filter { it.projectId == project.id }
        val removedStatusTicket = projectTickets.firstOrNull { it.statusId !in statusIds }
        if (removedStatusTicket != null) {
            throw ApiException(
                HttpStatusCode.Conflict,
                "Status is still used by ${removedStatusTicket.key}; move tickets before removing it."
            )
        }

        val transitions = normalizeTransitions(request.transitions, statusIds, ::id)
        val current = data.workflows.firstOrNull { it.projectId == project.id }
        val workflow = Workflow(
            id = current?.id ?: id("workflow"),
            organizationId = actor.organizationId,
            projectId = project.id,
            statuses = statuses,
            transitions = transitions
        )
        data = data.copy(
            workflows = data.workflows.filterNot { it.projectId == project.id } + workflow
        )
        saveLocked()
        workflow
    }

    fun listTickets(
        actor: User,
        projectId: String?,
        query: String?,
        statusId: String?,
        typeId: String?,
        priority: Priority?,
        assigneeId: String?,
        label: String?,
        sort: String?
    ): List<Ticket> = synchronized(lock) {
        var tickets = data.tickets.asSequence().filter { it.organizationId == actor.organizationId }
        if (!projectId.isNullOrBlank()) tickets = tickets.filter { it.projectId == projectId }
        if (!query.isNullOrBlank()) {
            val needle = query.trim().lowercase(Locale.ROOT)
            tickets = tickets.filter {
                it.key.lowercase(Locale.ROOT).contains(needle) ||
                    it.title.lowercase(Locale.ROOT).contains(needle) ||
                    it.description.lowercase(Locale.ROOT).contains(needle) ||
                    it.labels.any { label -> label.contains(needle) }
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
        when (sort ?: "number") {
            "title" -> result.sortedBy { it.title.lowercase(Locale.ROOT) }
            "priority" -> result.sortedByDescending { it.priority.ordinal }
            "status" -> result.sortedBy { statusSortKey(it) }
            "updated" -> result.sortedByDescending { it.updatedAt }
            else -> result.sortedWith(compareBy<Ticket> { projectKey(it.projectId) }.thenBy { it.number })
        }
    }

    fun createTicket(actor: User, request: CreateTicketRequest): Ticket = synchronized(lock) {
        val project = requireProject(actor, request.projectId)
        if (project.archived) {
            throw ApiException(HttpStatusCode.Conflict, "Archived projects cannot receive new tickets.")
        }
        val workflow = requireWorkflow(project.id)
        val statusId = request.statusId?.takeIf { it.isNotBlank() } ?: workflow.statuses.first().id
        requireStatus(workflow, statusId)
        val type = requireTicketTypeForProject(actor, request.typeId, project.id)
        requireAssignee(actor, request.assigneeId)

        val nextNumber = project.nextTicketNumber
        val timestamp = now()
        val ticket = Ticket(
            id = id("ticket"),
            organizationId = actor.organizationId,
            projectId = project.id,
            number = nextNumber,
            key = "${project.key}-$nextNumber",
            title = requireName(request.title, "Ticket title"),
            description = request.description.trim(),
            statusId = statusId,
            typeId = type.id,
            priority = request.priority,
            assigneeId = request.assigneeId?.takeIf { it.isNotBlank() },
            labels = normalizeLabels(request.labels),
            dueDate = normalizeDueDate(request.dueDate),
            estimate = normalizeEstimate(request.estimate),
            reporterId = actor.id,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        val created = ticketChange(
            actor = actor,
            ticket = ticket,
            field = "ticket",
            oldValue = null,
            newValue = "created",
            createdAt = timestamp
        )
        data = data.copy(
            projects = data.projects.map { if (it.id == project.id) it.copy(nextTicketNumber = nextNumber + 1) else it },
            tickets = data.tickets + ticket,
            ticketChanges = data.ticketChanges + created
        )
        saveLocked()
        ticket
    }

    fun updateTicket(actor: User, ticketId: String, request: UpdateTicketRequest): Ticket = synchronized(lock) {
        val current = requireTicket(actor, ticketId)
        val project = requireProject(actor, current.projectId)
        if (project.archived) {
            throw ApiException(HttpStatusCode.Conflict, "Archived project tickets cannot be edited.")
        }
        val typeId = request.typeId ?: current.typeId
        requireTicketTypeForProject(actor, typeId, project.id)
        val assigneeId = request.assigneeId?.takeIf { it.isNotBlank() }
        requireAssignee(actor, assigneeId)
        val labels = request.labels?.let { normalizeLabels(it) } ?: current.labels
        val dueDate = if (request.clearDueDate) null else request.dueDate?.let { normalizeDueDate(it) } ?: current.dueDate
        val estimate = if (request.clearEstimate) null else request.estimate?.let { normalizeEstimate(it) } ?: current.estimate
        val timestamp = now()
        val updated = current.copy(
            title = request.title?.let { requireName(it, "Ticket title") } ?: current.title,
            description = request.description?.trim() ?: current.description,
            typeId = typeId,
            priority = request.priority ?: current.priority,
            assigneeId = if (request.assigneeId == null) current.assigneeId else assigneeId,
            labels = labels,
            dueDate = dueDate,
            estimate = estimate,
            updatedAt = timestamp
        )
        val changes = ticketChanges(actor, current, updated, timestamp)
        data = data.copy(
            tickets = data.tickets.map { if (it.id == ticketId) updated else it },
            ticketChanges = data.ticketChanges + changes
        )
        saveLocked()
        updated
    }

    fun bulkUpdateTickets(actor: User, request: BulkUpdateTicketsRequest): List<Ticket> = synchronized(lock) {
        val ticketIds = request.ticketIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ticketIds.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "At least one ticket is required.")
        }
        if (request.clearAssignee && !request.assigneeId.isNullOrBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "Bulk update cannot set and clear an assignee at once.")
        }
        val assigneeId = request.assigneeId?.takeIf { it.isNotBlank() }
        if (!request.clearAssignee && assigneeId == null && request.priority == null) {
            throw ApiException(HttpStatusCode.BadRequest, "Bulk update needs an assignee or priority change.")
        }
        requireAssignee(actor, assigneeId)

        val currentTickets = ticketIds.map { ticketId ->
            val ticket = requireTicket(actor, ticketId)
            val project = requireProject(actor, ticket.projectId)
            if (project.archived) {
                throw ApiException(HttpStatusCode.Conflict, "Archived project tickets cannot be edited.")
            }
            ticket
        }
        val timestamp = now()
        val updatedById = currentTickets.associate { ticket ->
            ticket.id to ticket.copy(
                priority = request.priority ?: ticket.priority,
                assigneeId = when {
                    request.clearAssignee -> null
                    assigneeId != null -> assigneeId
                    else -> ticket.assigneeId
                },
                updatedAt = timestamp
            )
        }
        val changes = currentTickets.flatMap { ticket ->
            ticketChanges(actor, ticket, updatedById.getValue(ticket.id), timestamp)
        }
        data = data.copy(
            tickets = data.tickets.map { updatedById[it.id] ?: it },
            ticketChanges = data.ticketChanges + changes
        )
        saveLocked()
        currentTickets.map { updatedById.getValue(it.id) }
    }

    fun transitionTicket(actor: User, ticketId: String, request: TransitionTicketRequest): Ticket = synchronized(lock) {
        val current = requireTicket(actor, ticketId)
        val project = requireProject(actor, current.projectId)
        if (project.archived) {
            throw ApiException(HttpStatusCode.Conflict, "Archived project tickets cannot move.")
        }
        if (current.statusId == request.toStatusId) return@synchronized current
        val workflow = requireWorkflow(project.id)
        requireStatus(workflow, request.toStatusId)
        val transition = workflow.transitions.firstOrNull {
            (it.globalTransition || it.fromStatusId == current.statusId) && it.toStatusId == request.toStatusId
        } ?: throw ApiException(HttpStatusCode.Conflict, "This workflow transition is not allowed.")
        if (actor.role !in transition.allowedRoles) {
            throw ApiException(HttpStatusCode.Forbidden, "Your role cannot perform this workflow transition.")
        }
        if (isBackwardTransition(workflow, current.statusId, request.toStatusId) && !transition.allowBackward) {
            throw ApiException(HttpStatusCode.Conflict, "This workflow transition cannot move tickets backwards.")
        }
        validateRequiredFields(current, transition.requiredFields)
        val timestamp = now()
        val updated = current.copy(statusId = request.toStatusId, updatedAt = timestamp)
        data = data.copy(
            tickets = data.tickets.map { if (it.id == ticketId) updated else it },
            ticketChanges = data.ticketChanges + ticketChange(
                actor = actor,
                ticket = current,
                field = "statusId",
                oldValue = current.statusId,
                newValue = request.toStatusId,
                createdAt = timestamp
            )
        )
        saveLocked()
        updated
    }

    fun ticketDetail(actor: User, ticketId: String): TicketDetailResponse = synchronized(lock) {
        val ticket = requireTicket(actor, ticketId)
        TicketDetailResponse(
            ticket = ticket,
            comments = data.comments.filter { it.ticketId == ticket.id && it.organizationId == actor.organizationId }
                .sortedBy { it.createdAt },
            changes = data.ticketChanges.filter { it.ticketId == ticket.id && it.organizationId == actor.organizationId }
                .sortedByDescending { it.createdAt }
        )
    }

    fun addComment(actor: User, ticketId: String, request: CreateTicketCommentRequest): TicketComment = synchronized(lock) {
        val ticket = requireTicket(actor, ticketId)
        val timestamp = now()
        val comment = TicketComment(
            id = id("comment"),
            organizationId = actor.organizationId,
            ticketId = ticket.id,
            authorId = actor.id,
            body = requireName(request.body, "Comment"),
            createdAt = timestamp
        )
        data = data.copy(
            comments = data.comments + comment,
            ticketChanges = data.ticketChanges + ticketChange(
                actor = actor,
                ticket = ticket,
                field = "comment",
                oldValue = null,
                newValue = "added",
                createdAt = timestamp
            )
        )
        saveLocked()
        comment
    }

    fun createSavedTicketFilter(actor: User, request: CreateSavedTicketFilterRequest): SavedTicketFilter = synchronized(lock) {
        val projectId = normalizeSavedFilterProjectContext(actor, request.view, request.projectId)
        val name = requireName(request.name, "Saved filter name")
        requireUniqueSavedFilterName(actor, request.view, projectId, name, null)
        val savedFilter = SavedTicketFilter(
            id = id("filter"),
            organizationId = actor.organizationId,
            ownerId = actor.id,
            name = name,
            view = request.view,
            projectId = projectId,
            filters = normalizeSavedFilterCriteria(actor, request.view, projectId, request.filters)
        )
        data = data.copy(savedTicketFilters = data.savedTicketFilters + savedFilter)
        saveLocked()
        savedFilter
    }

    fun updateSavedTicketFilter(
        actor: User,
        filterId: String,
        request: UpdateSavedTicketFilterRequest
    ): SavedTicketFilter = synchronized(lock) {
        if (request.name == null && request.filters == null) {
            throw ApiException(HttpStatusCode.BadRequest, "Saved filter update needs a name or filter state.")
        }
        val current = requireSavedTicketFilter(actor, filterId)
        val name = request.name?.let { requireName(it, "Saved filter name") } ?: current.name
        requireUniqueSavedFilterName(actor, current.view, current.projectId, name, current.id)
        val updated = current.copy(
            name = name,
            filters = request.filters?.let {
                normalizeSavedFilterCriteria(actor, current.view, current.projectId, it)
            } ?: current.filters
        )
        data = data.copy(
            savedTicketFilters = data.savedTicketFilters.map { if (it.id == current.id) updated else it }
        )
        saveLocked()
        updated
    }

    fun deleteSavedTicketFilter(actor: User, filterId: String) = synchronized(lock) {
        val current = requireSavedTicketFilter(actor, filterId)
        data = data.copy(savedTicketFilters = data.savedTicketFilters.filterNot { it.id == current.id })
        saveLocked()
    }

    fun deleteTicket(actor: User, ticketId: String) = synchronized(lock) {
        requireAdmin(actor)
        val current = requireTicket(actor, ticketId)
        data = data.copy(
            tickets = data.tickets.filterNot { it.id == current.id },
            comments = data.comments.filterNot { it.ticketId == current.id },
            ticketChanges = data.ticketChanges.filterNot { it.ticketId == current.id }
        )
        saveLocked()
    }

    private fun loadOrSeed(): AppData {
        storage.load()?.let { return it }
        val seeded = seedData(::now)
        storage.save(seeded)
        return seeded
    }

    private fun saveLocked() {
        storage.save(data)
    }

    private fun requireAdmin(user: User) {
        if (user.role != Role.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "Admin role required.")
        }
    }

    private fun requireProject(user: User, projectId: String): Project =
        data.projects.firstOrNull { it.id == projectId && it.organizationId == user.organizationId }
            ?: throw ApiException(HttpStatusCode.NotFound, "Project not found.")

    private fun requireWorkflow(projectId: String): Workflow =
        data.workflows.firstOrNull { it.projectId == projectId }
            ?: throw ApiException(HttpStatusCode.NotFound, "Workflow not found.")

    private fun requireTicketType(user: User, typeId: String): TicketType =
        data.ticketTypes.firstOrNull { it.id == typeId && it.organizationId == user.organizationId }
            ?: throw ApiException(HttpStatusCode.NotFound, "Ticket type not found.")

    private fun requireTicketTypeForProject(user: User, typeId: String, projectId: String): TicketType =
        data.ticketTypes.firstOrNull {
            it.id == typeId && it.projectId == projectId && it.organizationId == user.organizationId
        } ?: throw ApiException(HttpStatusCode.NotFound, "Ticket type not found.")

    private fun requireTicket(user: User, ticketId: String): Ticket =
        data.tickets.firstOrNull { it.id == ticketId && it.organizationId == user.organizationId }
            ?: throw ApiException(HttpStatusCode.NotFound, "Ticket not found.")

    private fun requireSavedTicketFilter(user: User, filterId: String): SavedTicketFilter =
        data.savedTicketFilters.firstOrNull {
            it.id == filterId && it.organizationId == user.organizationId && it.ownerId == user.id
        } ?: throw ApiException(HttpStatusCode.NotFound, "Saved filter not found.")

    private fun requireStatus(workflow: Workflow, statusId: String): WorkflowStatus =
        workflow.statuses.firstOrNull { it.id == statusId }
            ?: throw ApiException(HttpStatusCode.NotFound, "Workflow status not found.")

    private fun requireAssignee(user: User, assigneeId: String?) {
        if (assigneeId.isNullOrBlank()) return
        if (data.users.none { it.id == assigneeId && it.organizationId == user.organizationId && it.active }) {
            throw ApiException(HttpStatusCode.NotFound, "Assignee not found.")
        }
    }

    private fun requireUniqueProjectKey(organizationId: String, key: String) {
        if (data.projects.any { it.organizationId == organizationId && it.key == key }) {
            throw ApiException(HttpStatusCode.Conflict, "A project with this key already exists.")
        }
    }

    private fun normalizeSavedFilterProjectContext(
        actor: User,
        view: SavedTicketFilterView,
        projectId: String?
    ): String? =
        when (view) {
            SavedTicketFilterView.PROJECT_LIST -> {
                val projectContext = projectId?.takeIf { it.isNotBlank() }
                    ?: throw ApiException(HttpStatusCode.BadRequest, "Project list filters need a project.")
                requireProject(actor, projectContext).id
            }

            SavedTicketFilterView.MY_TICKETS -> {
                if (!projectId.isNullOrBlank()) {
                    throw ApiException(HttpStatusCode.BadRequest, "My Tickets filters do not use a project context.")
                }
                null
            }
        }

    private fun normalizeSavedFilterCriteria(
        actor: User,
        view: SavedTicketFilterView,
        projectContextId: String?,
        criteria: SavedTicketFilterCriteria
    ): SavedTicketFilterCriteria {
        val query = criteria.q.trim()
        if (query.length > 200) {
            throw ApiException(HttpStatusCode.BadRequest, "Saved filter search must be 200 characters or fewer.")
        }
        val label = criteria.label.trim().lowercase(Locale.ROOT)
        if (label.length > 32) {
            throw ApiException(HttpStatusCode.BadRequest, "Saved filter label must be 32 characters or fewer.")
        }
        val sort = normalizeSavedFilterSort(view, criteria.sort)
        return when (view) {
            SavedTicketFilterView.PROJECT_LIST -> {
                val projectId = projectContextId
                    ?: throw ApiException(HttpStatusCode.BadRequest, "Project list filters need a project.")
                if (!criteria.projectId.isNullOrBlank()) {
                    throw ApiException(HttpStatusCode.BadRequest, "Project list filter criteria cannot switch projects.")
                }
                if (criteria.statusId.isNotBlank()) {
                    requireStatus(requireWorkflow(projectId), criteria.statusId)
                }
                if (criteria.typeId.isNotBlank()) {
                    requireTicketTypeForProject(actor, criteria.typeId, projectId)
                }
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
                    throw ApiException(
                        HttpStatusCode.BadRequest,
                        "My Tickets filters only support project, search, priority, label, and sorting."
                    )
                }
                val projectId = criteria.projectId?.takeIf { it.isNotBlank() }?.let { requireProject(actor, it).id }
                criteria.copy(
                    projectId = projectId,
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

    private fun normalizeSavedFilterSort(view: SavedTicketFilterView, value: String): String {
        val sort = value.trim().ifBlank { "number" }
        val supported = when (view) {
            SavedTicketFilterView.PROJECT_LIST -> setOf("number", "title", "priority", "status", "updated")
            SavedTicketFilterView.MY_TICKETS -> setOf("number", "title", "priority", "updated")
        }
        if (sort !in supported) {
            throw ApiException(HttpStatusCode.BadRequest, "Saved filter sort is not supported.")
        }
        return sort
    }

    private fun requireUniqueSavedFilterName(
        actor: User,
        view: SavedTicketFilterView,
        projectId: String?,
        name: String,
        ignoredId: String?
    ) {
        if (data.savedTicketFilters.any {
                it.id != ignoredId &&
                    it.organizationId == actor.organizationId &&
                    it.ownerId == actor.id &&
                    it.view == view &&
                    it.projectId == projectId &&
                    it.name.equals(name, ignoreCase = true)
            }) {
            throw ApiException(HttpStatusCode.Conflict, "A saved filter with this name already exists.")
        }
    }

    private fun statusSortKey(ticket: Ticket): Int {
        val workflow = data.workflows.firstOrNull { it.projectId == ticket.projectId }
        return workflow?.statuses?.firstOrNull { it.id == ticket.statusId }?.sortOrder ?: Int.MAX_VALUE
    }

    private fun isBackwardTransition(workflow: Workflow, fromStatusId: String, toStatusId: String): Boolean {
        val fromOrder = workflow.statuses.firstOrNull { it.id == fromStatusId }?.sortOrder ?: return false
        val toOrder = workflow.statuses.firstOrNull { it.id == toStatusId }?.sortOrder ?: return false
        return toOrder < fromOrder
    }

    private fun projectKey(projectId: String): String =
        data.projects.firstOrNull { it.id == projectId }?.key ?: ""

    private fun ticketChanges(actor: User, current: Ticket, updated: Ticket, createdAt: String): List<TicketChange> =
        buildList {
            addIfChanged(actor, current, "title", current.title, updated.title, createdAt)
            addIfChanged(actor, current, "description", current.description, updated.description, createdAt)
            addIfChanged(actor, current, "typeId", current.typeId, updated.typeId, createdAt)
            addIfChanged(actor, current, "priority", current.priority.name, updated.priority.name, createdAt)
            addIfChanged(actor, current, "assigneeId", current.assigneeId, updated.assigneeId, createdAt)
            addIfChanged(actor, current, "labels", current.labels.joinToString(","), updated.labels.joinToString(","), createdAt)
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
        if (oldValue != newValue) {
            add(ticketChange(actor, ticket, field, oldValue, newValue, createdAt))
        }
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
}
