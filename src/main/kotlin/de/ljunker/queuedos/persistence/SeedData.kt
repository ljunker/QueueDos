package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.domain.Organization
import de.ljunker.queuedos.domain.Priority
import de.ljunker.queuedos.domain.Project
import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.TicketType
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.domain.Workflow
import de.ljunker.queuedos.domain.WorkflowStatus
import de.ljunker.queuedos.domain.WorkflowTransition
import de.ljunker.queuedos.security.hashPassword

internal fun seedData(now: () -> String): AppData {
    val organization = Organization("org-default", "Default Organization")
    val adminSalt = "seed-admin"
    val memberSalt = "seed-member"
    val admin = User(
        id = "user-admin",
        organizationId = organization.id,
        email = "admin@queuedos.local",
        displayName = "QueueDos Admin",
        role = Role.ADMIN,
        active = true,
        passwordSalt = adminSalt,
        passwordHash = hashPassword("admin", adminSalt)
    )
    val member = User(
        id = "user-member",
        organizationId = organization.id,
        email = "member@queuedos.local",
        displayName = "QueueDos Member",
        role = Role.MEMBER,
        active = true,
        passwordSalt = memberSalt,
        passwordHash = hashPassword("member", memberSalt)
    )
    val project = Project(
        id = "project-queuedos",
        organizationId = organization.id,
        key = "QDOS",
        name = "QueueDos",
        description = "Jira-like MVP board",
        nextTicketNumber = 4
    )
    val ticketTypes = defaultTicketTypes(organization.id, project.id)
    val workflow = defaultWorkflow(organization.id, project.id)
    val tickets = listOf(
        Ticket(
            id = "ticket-1",
            organizationId = organization.id,
            projectId = project.id,
            number = 1,
            key = "QDOS-1",
            title = "Configure project workflow",
            description = "Model statuses, transitions, and role-based movement rules.",
            statusId = "status-in-progress",
            typeId = "type-task-${project.id}",
            priority = Priority.HIGH,
            assigneeId = admin.id,
            reporterId = admin.id,
            createdAt = now(),
            updatedAt = now()
        ),
        Ticket(
            id = "ticket-2",
            organizationId = organization.id,
            projectId = project.id,
            number = 2,
            key = "QDOS-2",
            title = "Create first Kanban board",
            description = "Render tickets by workflow status and allow drag and drop transitions.",
            statusId = "status-todo",
            typeId = "type-story-${project.id}",
            priority = Priority.MEDIUM,
            assigneeId = member.id,
            reporterId = admin.id,
            createdAt = now(),
            updatedAt = now()
        ),
        Ticket(
            id = "ticket-3",
            organizationId = organization.id,
            projectId = project.id,
            number = 3,
            key = "QDOS-3",
            title = "Prepare Docker runtime",
            description = "Package the Kotlin service for local Docker execution.",
            statusId = "status-done",
            typeId = "type-task-${project.id}",
            priority = Priority.CRITICAL,
            assigneeId = admin.id,
            reporterId = admin.id,
            createdAt = now(),
            updatedAt = now()
        )
    )
    return AppData(
        organizations = listOf(organization),
        users = listOf(admin, member),
        projects = listOf(project),
        ticketTypes = ticketTypes,
        workflows = listOf(workflow),
        tickets = tickets
    )
}

internal fun defaultTicketTypes(organizationId: String, projectId: String) = listOf(
    TicketType("type-task-$projectId", organizationId, projectId, "Task", "Work item", "#2563eb"),
    TicketType("type-bug-$projectId", organizationId, projectId, "Bug", "Defect or regression", "#dc2626"),
    TicketType("type-story-$projectId", organizationId, projectId, "Story", "User-facing capability", "#16a34a"),
    TicketType("type-epic-$projectId", organizationId, projectId, "Epic", "Large feature area", "#7c3aed")
)

internal fun defaultWorkflow(organizationId: String, projectId: String): Workflow {
    val statuses = listOf(
        WorkflowStatus("status-backlog", "Backlog", "TODO", 0),
        WorkflowStatus("status-todo", "Todo", "TODO", 1),
        WorkflowStatus("status-in-progress", "In Progress", "IN_PROGRESS", 2),
        WorkflowStatus("status-review", "Review", "IN_PROGRESS", 3),
        WorkflowStatus("status-done", "Done", "DONE", 4)
    )
    val transitions = statuses.flatMap { from ->
        statuses.filter { it.id != from.id }.map { to ->
            WorkflowTransition(
                id = "transition-${from.id}-${to.id}",
                fromStatusId = from.id,
                toStatusId = to.id,
                allowedRoles = listOf(Role.ADMIN, Role.MEMBER)
            )
        }
    }
    return Workflow(
        id = "workflow-$projectId",
        organizationId = organizationId,
        projectId = projectId,
        statuses = statuses,
        transitions = transitions
    )
}
