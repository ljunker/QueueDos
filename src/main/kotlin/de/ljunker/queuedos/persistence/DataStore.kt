package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.api.ApiException
import de.ljunker.queuedos.api.BootstrapResponse
import de.ljunker.queuedos.api.CreateProjectRequest
import de.ljunker.queuedos.api.CreateTicketRequest
import de.ljunker.queuedos.api.CreateTicketTypeRequest
import de.ljunker.queuedos.api.CreateUserRequest
import de.ljunker.queuedos.api.LoginRequest
import de.ljunker.queuedos.api.LoginResponse
import de.ljunker.queuedos.api.SaveWorkflowRequest
import de.ljunker.queuedos.api.TransitionTicketRequest
import de.ljunker.queuedos.api.UpdateProjectRequest
import de.ljunker.queuedos.api.UpdateTicketRequest
import de.ljunker.queuedos.api.UpdateTicketTypeRequest
import de.ljunker.queuedos.api.UpdateUserRequest
import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.domain.Priority
import de.ljunker.queuedos.domain.Project
import de.ljunker.queuedos.domain.PublicUser
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.TicketType
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.domain.Workflow
import de.ljunker.queuedos.domain.WorkflowStatus
import de.ljunker.queuedos.domain.publicView
import de.ljunker.queuedos.security.hashPassword
import de.ljunker.queuedos.validation.normalizeColor
import de.ljunker.queuedos.validation.normalizeEmail
import de.ljunker.queuedos.validation.normalizeProjectKey
import de.ljunker.queuedos.validation.normalizeStatuses
import de.ljunker.queuedos.validation.normalizeTransitions
import de.ljunker.queuedos.validation.requireName
import de.ljunker.queuedos.validation.requirePassword
import de.ljunker.queuedos.validation.validateRequiredFields
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

class DataStore(
    private val dataFile: Path,
    private val json: Json
) {
    private val lock = Any()
    private val random = SecureRandom()
    private val sessions = mutableMapOf<String, String>()
    private var data: AppData = loadOrSeed()

    fun login(request: LoginRequest): LoginResponse = synchronized(lock) {
        val email = request.email.trim().lowercase(Locale.ROOT)
        val user = data.users.firstOrNull { it.email.lowercase(Locale.ROOT) == email && it.active }
            ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid email or password.")

        if (hashPassword(request.password, user.passwordSalt) != user.passwordHash) {
            throw ApiException(HttpStatusCode.Unauthorized, "Invalid email or password.")
        }

        val token = randomToken()
        sessions[token] = user.id
        LoginResponse(token, user.publicView())
    }

    fun userByToken(token: String): User? = synchronized(lock) {
        val userId = sessions[token]
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
        val salt = randomSalt()
        val user = User(
            id = id("user"),
            organizationId = actor.organizationId,
            email = email,
            displayName = requireName(request.displayName, "Display name"),
            role = request.role,
            active = true,
            passwordSalt = salt,
            passwordHash = hashPassword(requirePassword(request.password), salt)
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
            val salt = randomSalt()
            salt to hashPassword(requirePassword(it), salt)
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
        sort: String?
    ): List<Ticket> = synchronized(lock) {
        var tickets = data.tickets.asSequence().filter { it.organizationId == actor.organizationId }
        if (!projectId.isNullOrBlank()) tickets = tickets.filter { it.projectId == projectId }
        if (!query.isNullOrBlank()) {
            val needle = query.trim().lowercase(Locale.ROOT)
            tickets = tickets.filter {
                it.key.lowercase(Locale.ROOT).contains(needle) ||
                    it.title.lowercase(Locale.ROOT).contains(needle) ||
                    it.description.lowercase(Locale.ROOT).contains(needle)
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
            reporterId = actor.id,
            createdAt = now(),
            updatedAt = now()
        )
        data = data.copy(
            projects = data.projects.map { if (it.id == project.id) it.copy(nextTicketNumber = nextNumber + 1) else it },
            tickets = data.tickets + ticket
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
        val updated = current.copy(
            title = request.title?.let { requireName(it, "Ticket title") } ?: current.title,
            description = request.description?.trim() ?: current.description,
            typeId = typeId,
            priority = request.priority ?: current.priority,
            assigneeId = if (request.assigneeId == null) current.assigneeId else assigneeId,
            updatedAt = now()
        )
        data = data.copy(tickets = data.tickets.map { if (it.id == ticketId) updated else it })
        saveLocked()
        updated
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
            it.fromStatusId == current.statusId && it.toStatusId == request.toStatusId
        } ?: throw ApiException(HttpStatusCode.Conflict, "This workflow transition is not allowed.")
        if (actor.role !in transition.allowedRoles) {
            throw ApiException(HttpStatusCode.Forbidden, "Your role cannot perform this workflow transition.")
        }
        validateRequiredFields(current, transition.requiredFields)
        val updated = current.copy(statusId = request.toStatusId, updatedAt = now())
        data = data.copy(tickets = data.tickets.map { if (it.id == ticketId) updated else it })
        saveLocked()
        updated
    }

    fun deleteTicket(actor: User, ticketId: String) = synchronized(lock) {
        requireAdmin(actor)
        val current = requireTicket(actor, ticketId)
        data = data.copy(tickets = data.tickets.filterNot { it.id == current.id })
        saveLocked()
    }

    private fun loadOrSeed(): AppData {
        if (Files.exists(dataFile)) {
            return json.decodeFromString(Files.readString(dataFile))
        }
        val seeded = seedData(::now)
        writeData(seeded)
        return seeded
    }

    private fun saveLocked() {
        writeData(data)
    }

    private fun writeData(snapshot: AppData) {
        dataFile.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            dataFile,
            json.encodeToString(snapshot),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
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

    private fun statusSortKey(ticket: Ticket): Int {
        val workflow = data.workflows.firstOrNull { it.projectId == ticket.projectId }
        return workflow?.statuses?.firstOrNull { it.id == ticket.statusId }?.sortOrder ?: Int.MAX_VALUE
    }

    private fun projectKey(projectId: String): String =
        data.projects.firstOrNull { it.id == projectId }?.key ?: ""

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun randomSalt(): String = UUID.randomUUID().toString()

    private fun id(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun now(): String = Instant.now().toString()
}
