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
    val deletedTickets: List<Ticket>,
    val savedTicketFilters: List<SavedTicketFilter>,
    val activityHooks: List<ActivityHook>
)

data class TicketDetailData(
    val ticket: Ticket,
    val comments: List<TicketComment>,
    val revisions: TicketRevisionPage,
    val legacyChanges: List<TicketChange>
)

data class TicketRevisionPage(
    val revisions: List<TicketRevision>,
    val nextBeforeVersion: Long?
)

data class LoginCommand(val email: String, val password: String)

data class CreateProjectTicketTypeCommand(
    val name: String,
    val description: String,
    val color: String
)

data class CreateProjectStatusCommand(
    val name: String,
    val category: String
)

data class CreateProjectCommand(
    val key: String,
    val name: String,
    val description: String,
    val color: String = "#2563eb",
    val ticketTypes: List<CreateProjectTicketTypeCommand>? = null,
    val statuses: List<CreateProjectStatusCommand>? = null
)

data class UpdateProjectCommand(
    val key: String?,
    val name: String?,
    val description: String?,
    val archived: Boolean?,
    val color: String? = null
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
    val expectedVersion: Long,
    val title: String?,
    val description: String?,
    val typeId: String?,
    val priority: Priority?,
    val assigneeId: String?,
    val clearAssignee: Boolean,
    val labels: List<String>?,
    val dueDate: String?,
    val estimate: Int?,
    val clearDueDate: Boolean,
    val clearEstimate: Boolean,
    val statusId: String?
)

data class TransitionTicketCommand(val toStatusId: String, val expectedVersion: Long)

data class AddTicketCommentCommand(val body: String)

data class VersionedTicketRef(val id: String, val expectedVersion: Long)

data class BulkUpdateTicketsCommand(
    val tickets: List<VersionedTicketRef>,
    val assigneeId: String?,
    val clearAssignee: Boolean,
    val priority: Priority?
)

data class SaveTicketCommitmentCommand(val committed: Boolean, val expectedVersion: Long)

data class RestoreTicketCommand(val expectedVersion: Long)

data class CreateActivityHookCommand(
    val eventType: ActivityEventType,
    val webhookUrl: String,
    val messageTemplate: String,
    val active: Boolean
)

data class UpdateActivityHookCommand(
    val eventType: ActivityEventType?,
    val webhookUrl: String?,
    val messageTemplate: String?,
    val active: Boolean?
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
