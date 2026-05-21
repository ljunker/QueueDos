package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.*

data class BootstrapData(
    val currentUser: User,
    val organizations: List<Organization>,
    val users: List<User>,
    val projects: List<Project>,
    val ticketTypes: List<TicketType>,
    val workflows: List<Workflow>,
    val tickets: List<Ticket>,
    val comments: List<TicketComment>,
    val changes: List<TicketChange>,
    val savedTicketFilters: List<SavedTicketFilter>
)

data class TicketDetailData(
    val ticket: Ticket,
    val comments: List<TicketComment>,
    val changes: List<TicketChange>
)

data class LoginCommand(val email: String, val password: String)

data class CreateProjectCommand(val key: String, val name: String, val description: String)

data class UpdateProjectCommand(
    val key: String?,
    val name: String?,
    val description: String?,
    val archived: Boolean?
)

data class CreateUserCommand(
    val email: String,
    val displayName: String,
    val role: Role,
    val password: String
)

data class UpdateUserCommand(
    val displayName: String?,
    val role: Role?,
    val active: Boolean?,
    val password: String?
)

data class CreateTicketTypeCommand(
    val projectId: String,
    val name: String,
    val description: String,
    val color: String
)

data class UpdateTicketTypeCommand(
    val name: String?,
    val description: String?,
    val color: String?
)

data class SaveWorkflowCommand(
    val statuses: List<WorkflowStatus>,
    val transitions: List<WorkflowTransition>
)

data class CreateTicketCommand(
    val projectId: String,
    val title: String,
    val description: String,
    val typeId: String,
    val priority: Priority,
    val assigneeId: String?,
    val statusId: String?,
    val labels: List<String>,
    val dueDate: String?,
    val estimate: Int?
)

data class UpdateTicketCommand(
    val title: String?,
    val description: String?,
    val typeId: String?,
    val priority: Priority?,
    val assigneeId: String?,
    val labels: List<String>?,
    val dueDate: String?,
    val estimate: Int?,
    val clearDueDate: Boolean,
    val clearEstimate: Boolean
)

data class TransitionTicketCommand(val toStatusId: String)

data class AddTicketCommentCommand(val body: String)

data class BulkUpdateTicketsCommand(
    val ticketIds: List<String>,
    val assigneeId: String?,
    val clearAssignee: Boolean,
    val priority: Priority?
)

data class CreateSavedTicketFilterCommand(
    val name: String,
    val view: SavedTicketFilterView,
    val projectId: String?,
    val filters: SavedTicketFilterCriteria
)

data class UpdateSavedTicketFilterCommand(
    val name: String?,
    val filters: SavedTicketFilterCriteria?
)
