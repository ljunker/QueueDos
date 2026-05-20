package de.ljunker.queuedos.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    ADMIN,
    MEMBER
}

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class Organization(
    val id: String,
    val name: String
)

@Serializable
data class User(
    val id: String,
    val organizationId: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val passwordSalt: String,
    val passwordHash: String
)

@Serializable
data class PublicUser(
    val id: String,
    val organizationId: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean
)

fun User.publicView() = PublicUser(
    id = id,
    organizationId = organizationId,
    email = email,
    displayName = displayName,
    role = role,
    active = active
)

@Serializable
data class Project(
    val id: String,
    val organizationId: String,
    val key: String,
    val name: String,
    val description: String = "",
    val nextTicketNumber: Int = 1,
    val archived: Boolean = false
)

@Serializable
data class TicketType(
    val id: String,
    val organizationId: String,
    val projectId: String,
    val name: String,
    val description: String = "",
    val color: String = "#2563eb"
)

@Serializable
data class WorkflowStatus(
    val id: String,
    val name: String,
    val category: String = "TODO",
    val sortOrder: Int = 0
)

@Serializable
data class WorkflowTransition(
    val id: String,
    val fromStatusId: String,
    val toStatusId: String,
    val allowedRoles: List<Role> = listOf(Role.ADMIN, Role.MEMBER),
    val requiredFields: List<String> = emptyList()
)

@Serializable
data class Workflow(
    val id: String,
    val organizationId: String,
    val projectId: String,
    val statuses: List<WorkflowStatus>,
    val transitions: List<WorkflowTransition>
)

@Serializable
data class Ticket(
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
    val reporterId: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AppData(
    val organizations: List<Organization> = emptyList(),
    val users: List<User> = emptyList(),
    val projects: List<Project> = emptyList(),
    val ticketTypes: List<TicketType> = emptyList(),
    val workflows: List<Workflow> = emptyList(),
    val tickets: List<Ticket> = emptyList()
)
