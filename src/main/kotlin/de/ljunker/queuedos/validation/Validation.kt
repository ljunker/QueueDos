package de.ljunker.queuedos.validation

import de.ljunker.queuedos.api.ApiException
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.WorkflowStatus
import de.ljunker.queuedos.domain.WorkflowTransition
import io.ktor.http.HttpStatusCode
import java.util.Locale

private val projectKeyPattern = Regex("^[A-Z][A-Z0-9]{1,9}$")
private val hexColorPattern = Regex("^#[0-9a-fA-F]{6}$")

internal fun normalizeEmail(value: String): String {
    val email = value.trim().lowercase(Locale.ROOT)
    if (!email.contains("@") || email.length > 190) {
        throw ApiException(HttpStatusCode.BadRequest, "Valid email required.")
    }
    return email
}

internal fun normalizeProjectKey(value: String): String {
    val key = value.trim().uppercase(Locale.ROOT)
    if (!projectKeyPattern.matches(key)) {
        throw ApiException(HttpStatusCode.BadRequest, "Project key must be 2-10 uppercase letters or numbers.")
    }
    return key
}

internal fun normalizeColor(value: String): String {
    val color = value.trim()
    if (!hexColorPattern.matches(color)) {
        throw ApiException(HttpStatusCode.BadRequest, "Color must be a hex value like #2563eb.")
    }
    return color
}

internal fun requireName(value: String, label: String): String {
    val name = value.trim()
    if (name.isBlank() || name.length > 160) {
        throw ApiException(HttpStatusCode.BadRequest, "$label is required.")
    }
    return name
}

internal fun requirePassword(value: String): String {
    if (value.length < 4) {
        throw ApiException(HttpStatusCode.BadRequest, "Password must have at least 4 characters.")
    }
    return value
}

internal fun normalizeStatuses(
    statuses: List<WorkflowStatus>,
    nextId: (String) -> String
): List<WorkflowStatus> {
    if (statuses.isEmpty()) {
        throw ApiException(HttpStatusCode.BadRequest, "Workflow needs at least one status.")
    }
    val normalized = statuses.mapIndexed { index, status ->
        status.copy(
            id = status.id.ifBlank { nextId("status") },
            name = requireName(status.name, "Status name"),
            category = status.category.ifBlank { "TODO" },
            sortOrder = index
        )
    }
    if (normalized.map { it.id }.distinct().size != normalized.size) {
        throw ApiException(HttpStatusCode.BadRequest, "Workflow status IDs must be unique.")
    }
    if (normalized.map { it.name.lowercase(Locale.ROOT) }.distinct().size != normalized.size) {
        throw ApiException(HttpStatusCode.BadRequest, "Workflow status names must be unique.")
    }
    return normalized
}

internal fun normalizeTransitions(
    transitions: List<WorkflowTransition>,
    statusIds: Set<String>,
    nextId: (String) -> String
): List<WorkflowTransition> {
    return transitions
        .filter { it.fromStatusId != it.toStatusId }
        .map {
            if (it.fromStatusId !in statusIds || it.toStatusId !in statusIds) {
                throw ApiException(HttpStatusCode.BadRequest, "Workflow transition points to an unknown status.")
            }
            val allowedRoles = it.allowedRoles.distinct().ifEmpty {
                throw ApiException(HttpStatusCode.BadRequest, "Workflow transition needs at least one role.")
            }
            it.copy(
                id = it.id.ifBlank { nextId("transition") },
                allowedRoles = allowedRoles,
                requiredFields = it.requiredFields.distinct()
            )
        }
        .distinctBy { it.fromStatusId to it.toStatusId }
}

internal fun validateRequiredFields(ticket: Ticket, requiredFields: List<String>) {
    requiredFields.forEach { field ->
        val missing = when (field) {
            "title" -> ticket.title.isBlank()
            "description" -> ticket.description.isBlank()
            "typeId" -> ticket.typeId.isBlank()
            "priority" -> false
            "assigneeId" -> ticket.assigneeId.isNullOrBlank()
            "reporterId" -> ticket.reporterId.isBlank()
            else -> false
        }
        if (missing) {
            throw ApiException(HttpStatusCode.Conflict, "Required field '$field' is missing.")
        }
    }
}
