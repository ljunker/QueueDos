package de.ljunker.queuedos.api

import de.ljunker.queuedos.domain.Priority
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.SavedTicketFilterView
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
    val user: UserResponse
)

@Serializable
data class BootstrapResponse(
    val currentUser: UserResponse,
    val organizations: List<OrganizationResponse>,
    val users: List<UserResponse>,
    val projects: List<ProjectResponse>,
    val ticketTypes: List<TicketTypeResponse>,
    val workflows: List<WorkflowResponse>,
    val tickets: List<TicketResponse>,
    val comments: List<TicketCommentResponse> = emptyList(),
    val ticketChanges: List<TicketChangeResponse> = emptyList(),
    val savedTicketFilters: List<SavedTicketFilterResponse> = emptyList(),
    val priorities: List<Priority> = Priority.entries.toList()
)

@Serializable
data class TicketDetailResponse(
    val ticket: TicketResponse,
    val comments: List<TicketCommentResponse>,
    val changes: List<TicketChangeResponse>
)

@Serializable
data class OrganizationResponse(
    val id: String,
    val name: String
)

@Serializable
data class UserResponse(
    val id: String,
    val organizationId: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean
)

@Serializable
data class ProjectResponse(
    val id: String,
    val organizationId: String,
    val key: String,
    val name: String,
    val description: String = "",
    val nextTicketNumber: Int = 1,
    val archived: Boolean = false
)

@Serializable
data class TicketTypeResponse(
    val id: String,
    val organizationId: String,
    val projectId: String,
    val name: String,
    val description: String = "",
    val color: String = "#2563eb"
)

@Serializable
data class WorkflowStatusDto(
    val id: String,
    val name: String,
    val category: String = "TODO",
    val sortOrder: Int = 0
)

@Serializable
data class WorkflowTransitionDto(
    val id: String,
    val fromStatusId: String? = null,
    val toStatusId: String,
    val allowedRoles: List<Role> = listOf(Role.ADMIN, Role.MEMBER),
    val requiredFields: List<String> = emptyList(),
    val globalTransition: Boolean = false,
    val allowBackward: Boolean = true
)

@Serializable
data class WorkflowResponse(
    val id: String,
    val organizationId: String,
    val projectId: String,
    val statuses: List<WorkflowStatusDto>,
    val transitions: List<WorkflowTransitionDto>
)

@Serializable
data class TicketResponse(
    val id: String,
    val organizationId: String,
    val projectId: String,
    val number: Int,
    val key: String,
    val title: String,
    val description: String = "",
    val statusId: String,
    val typeId: String,
    val priority: Priority,
    val assigneeId: String? = null,
    val labels: List<String> = emptyList(),
    val dueDate: String? = null,
    val estimate: Int? = null,
    val reporterId: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TicketCommentResponse(
    val id: String,
    val organizationId: String,
    val ticketId: String,
    val authorId: String,
    val body: String,
    val createdAt: String
)

@Serializable
data class TicketChangeResponse(
    val id: String,
    val organizationId: String,
    val ticketId: String,
    val actorId: String,
    val field: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAt: String
)

@Serializable
data class SavedTicketFilterCriteriaDto(
    val projectId: String? = null,
    val q: String = "",
    val statusId: String = "",
    val typeId: String = "",
    val priority: Priority? = null,
    val assigneeId: String = "",
    val label: String = "",
    val sort: String = "number"
)

@Serializable
data class SavedTicketFilterResponse(
    val id: String,
    val organizationId: String,
    val ownerId: String,
    val name: String,
    val view: SavedTicketFilterView,
    val projectId: String? = null,
    val filters: SavedTicketFilterCriteriaDto = SavedTicketFilterCriteriaDto()
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
    val statuses: List<WorkflowStatusDto>,
    val transitions: List<WorkflowTransitionDto>
)

@Serializable
data class CreateSavedTicketFilterRequest(
    val name: String,
    val view: SavedTicketFilterView,
    val projectId: String? = null,
    val filters: SavedTicketFilterCriteriaDto = SavedTicketFilterCriteriaDto()
)

@Serializable
data class UpdateSavedTicketFilterRequest(
    val name: String? = null,
    val filters: SavedTicketFilterCriteriaDto? = null
)

@Serializable
data class BulkUpdateTicketsRequest(
    val ticketIds: List<String>,
    val assigneeId: String? = null,
    val clearAssignee: Boolean = false,
    val priority: Priority? = null
)
