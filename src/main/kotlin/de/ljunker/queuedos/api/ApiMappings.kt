package de.ljunker.queuedos.api

import de.ljunker.queuedos.application.*
import de.ljunker.queuedos.domain.*

internal fun LoginRequest.toCommand() = LoginCommand(email, password)

internal fun CreateProjectRequest.toCommand() = CreateProjectCommand(key, name, description)

internal fun UpdateProjectRequest.toCommand() = UpdateProjectCommand(key, name, description, archived)

internal fun CreateUserRequest.toCommand() = CreateUserCommand(email, displayName, role, password)

internal fun UpdateUserRequest.toCommand() = UpdateUserCommand(displayName, role, active, password)

internal fun CreateTicketTypeRequest.toCommand() = CreateTicketTypeCommand(projectId, name, description, color)

internal fun UpdateTicketTypeRequest.toCommand() = UpdateTicketTypeCommand(name, description, color)

internal fun CreateTicketRequest.toCommand() =
    CreateTicketCommand(
        projectId,
        title,
        description,
        typeId,
        priority,
        assigneeId,
        statusId,
        labels,
        dueDate,
        estimate
    )

internal fun UpdateTicketRequest.toCommand() =
    UpdateTicketCommand(
        title,
        description,
        typeId,
        priority,
        assigneeId,
        labels,
        dueDate,
        estimate,
        clearDueDate,
        clearEstimate
    )

internal fun TransitionTicketRequest.toCommand() = TransitionTicketCommand(toStatusId)

internal fun CreateTicketCommentRequest.toCommand() = AddTicketCommentCommand(body)

internal fun SaveTicketCommitmentRequest.toCommand() = SaveTicketCommitmentCommand(committed)

internal fun BulkUpdateTicketsRequest.toCommand() =
    BulkUpdateTicketsCommand(ticketIds, assigneeId, clearAssignee, priority)

internal fun SaveWorkflowRequest.toCommand() =
    SaveWorkflowCommand(statuses.map(WorkflowStatusDto::toDomain), transitions.map(WorkflowTransitionDto::toDomain))

internal fun CreateSavedTicketFilterRequest.toCommand() =
    CreateSavedTicketFilterCommand(name, view, projectId, filters.toDomain())

internal fun UpdateSavedTicketFilterRequest.toCommand() =
    UpdateSavedTicketFilterCommand(name, filters?.toDomain())

internal fun CreateActivityHookRequest.toCommand() =
    CreateActivityHookCommand(eventType, webhookUrl, messageTemplate, active)

internal fun UpdateActivityHookRequest.toCommand() =
    UpdateActivityHookCommand(eventType, webhookUrl, messageTemplate, active)

internal fun AuthenticatedUser.toResponse() = LoginResponse(token, user.toResponse())

internal fun BootstrapData.toResponse() =
    BootstrapResponse(
        currentUser = currentUser.toResponse(),
        organizations = organizations.map(Organization::toResponse),
        users = users.map(User::toResponse),
        projects = projects.map(Project::toResponse),
        ticketTypes = ticketTypes.map(TicketType::toResponse),
        workflows = workflows.map(Workflow::toResponse),
        tickets = tickets.map(Ticket::toResponse),
        deletedTickets = deletedTickets.map(Ticket::toResponse),
        comments = comments.map(TicketComment::toResponse),
        ticketChanges = changes.map(TicketChange::toResponse),
        savedTicketFilters = savedTicketFilters.map(SavedTicketFilter::toResponse),
        activityHooks = activityHooks.map(ActivityHook::toResponse)
    )

internal fun TicketDetailData.toResponse() =
    TicketDetailResponse(
        ticket = ticket.toResponse(),
        comments = comments.map(TicketComment::toResponse),
        changes = changes.map(TicketChange::toResponse)
    )

internal fun Organization.toResponse() = OrganizationResponse(id, name)

internal fun User.toResponse() = UserResponse(id, organizationId, email, displayName, role, active)

internal fun Project.toResponse() =
    ProjectResponse(id, organizationId, key, name, description, nextTicketNumber, archived)

internal fun TicketType.toResponse() =
    TicketTypeResponse(id, organizationId, projectId, name, description, color)

internal fun WorkflowStatus.toResponse() = WorkflowStatusDto(id, name, category, sortOrder)

internal fun WorkflowStatusDto.toDomain() = WorkflowStatus(id, name, category, sortOrder)

internal fun WorkflowTransition.toResponse() =
    WorkflowTransitionDto(id, fromStatusId, toStatusId, allowedRoles, requiredFields, globalTransition, allowBackward)

internal fun WorkflowTransitionDto.toDomain() =
    WorkflowTransition(id, fromStatusId, toStatusId, allowedRoles, requiredFields, globalTransition, allowBackward)

internal fun Workflow.toResponse() =
    WorkflowResponse(
        id,
        organizationId,
        projectId,
        statuses.map(WorkflowStatus::toResponse),
        transitions.map(WorkflowTransition::toResponse)
    )

internal fun Ticket.toResponse() =
    TicketResponse(
        id,
        organizationId,
        projectId,
        number,
        key,
        title,
        description,
        statusId,
        typeId,
        priority,
        assigneeId,
        committedUserIds,
        labels,
        dueDate,
        estimate,
        reporterId,
        createdAt,
        updatedAt,
        deletedAt,
        deletedById
    )

internal fun TicketComment.toResponse() =
    TicketCommentResponse(id, organizationId, ticketId, authorId, body, createdAt)

internal fun TicketChange.toResponse() =
    TicketChangeResponse(id, organizationId, ticketId, actorId, field, oldValue, newValue, createdAt)

internal fun SavedTicketFilterCriteria.toResponse() =
    SavedTicketFilterCriteriaDto(projectId, q, statusId, typeId, priority, assigneeId, label, sort)

internal fun SavedTicketFilterCriteriaDto.toDomain() =
    SavedTicketFilterCriteria(projectId, q, statusId, typeId, priority, assigneeId, label, sort)

internal fun SavedTicketFilter.toResponse() =
    SavedTicketFilterResponse(id, organizationId, ownerId, name, view, projectId, filters.toResponse())

internal fun ActivityHook.toResponse() =
    ActivityHookResponse(id, organizationId, eventType, webhookUrl, messageTemplate, active)
