package de.ljunker.queuedos.api

import de.ljunker.queuedos.domain.Organization
import de.ljunker.queuedos.domain.Priority
import de.ljunker.queuedos.domain.Project
import de.ljunker.queuedos.domain.PublicUser
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.TicketChange
import de.ljunker.queuedos.domain.TicketComment
import de.ljunker.queuedos.domain.TicketType
import de.ljunker.queuedos.domain.Workflow
import de.ljunker.queuedos.domain.WorkflowStatus
import de.ljunker.queuedos.domain.WorkflowTransition
import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val message: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: PublicUser
)

@Serializable
data class BootstrapResponse(
    val currentUser: PublicUser,
    val organizations: List<Organization>,
    val users: List<PublicUser>,
    val projects: List<Project>,
    val ticketTypes: List<TicketType>,
    val workflows: List<Workflow>,
    val tickets: List<Ticket>,
    val comments: List<TicketComment> = emptyList(),
    val ticketChanges: List<TicketChange> = emptyList(),
    val priorities: List<Priority> = Priority.entries.toList()
)

@Serializable
data class TicketDetailResponse(
    val ticket: Ticket,
    val comments: List<TicketComment>,
    val changes: List<TicketChange>
)

@Serializable
data class CreateProjectRequest(
    val key: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class UpdateProjectRequest(
    val key: String? = null,
    val name: String? = null,
    val description: String? = null,
    val archived: Boolean? = null
)

@Serializable
data class CreateUserRequest(
    val email: String,
    val displayName: String,
    val role: Role = Role.MEMBER,
    val password: String
)

@Serializable
data class UpdateUserRequest(
    val displayName: String? = null,
    val role: Role? = null,
    val active: Boolean? = null,
    val password: String? = null
)

@Serializable
data class CreateTicketTypeRequest(
    val projectId: String,
    val name: String,
    val description: String = "",
    val color: String = "#2563eb"
)

@Serializable
data class UpdateTicketTypeRequest(
    val name: String? = null,
    val description: String? = null,
    val color: String? = null
)

@Serializable
data class CreateTicketRequest(
    val projectId: String,
    val title: String,
    val description: String = "",
    val typeId: String,
    val priority: Priority = Priority.MEDIUM,
    val assigneeId: String? = null,
    val statusId: String? = null,
    val labels: List<String> = emptyList(),
    val dueDate: String? = null,
    val estimate: Int? = null
)

@Serializable
data class UpdateTicketRequest(
    val title: String? = null,
    val description: String? = null,
    val typeId: String? = null,
    val priority: Priority? = null,
    val assigneeId: String? = null,
    val labels: List<String>? = null,
    val dueDate: String? = null,
    val estimate: Int? = null,
    val clearDueDate: Boolean = false,
    val clearEstimate: Boolean = false
)

@Serializable
data class TransitionTicketRequest(
    val toStatusId: String
)

@Serializable
data class CreateTicketCommentRequest(
    val body: String
)

@Serializable
data class SaveWorkflowRequest(
    val statuses: List<WorkflowStatus>,
    val transitions: List<WorkflowTransition>
)
