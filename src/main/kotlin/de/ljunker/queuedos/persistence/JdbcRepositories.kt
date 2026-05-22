package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class JdbcQueueRepositories(
    private val transactionRunner: JdbcTransactionRunner,
    private val json: Json
) {
    fun repositories(): QueueRepositories =
        QueueRepositories(
            organizations = JdbcOrganizationRepository(transactionRunner),
            users = JdbcUserRepository(transactionRunner),
            projects = JdbcProjectRepository(transactionRunner),
            ticketTypes = JdbcTicketTypeRepository(transactionRunner),
            workflows = JdbcWorkflowRepository(transactionRunner),
            tickets = JdbcTicketRepository(transactionRunner),
            savedTicketFilters = JdbcSavedTicketFilterRepository(transactionRunner, json),
            activityHooks = JdbcActivityHookRepository(transactionRunner)
        )
}

private class JdbcOrganizationRepository(
    private val transactions: JdbcTransactionRunner
) : OrganizationRepository {
    override fun count(): Int =
        connection().queryOne("SELECT count(*) AS count FROM queuedos_organizations") { it.getInt("count") } ?: 0

    override fun listById(organizationId: String): List<Organization> =
        connection().query("SELECT id, name FROM queuedos_organizations WHERE id = ? ORDER BY id", organizationId) {
            Organization(it.getString("id"), it.getString("name"))
        }

    override fun insert(organization: Organization) {
        connection().execute(
            "INSERT INTO queuedos_organizations (id, name) VALUES (?, ?)",
            organization.id,
            organization.name
        )
    }

    private fun connection() = transactions.connection()
}

private class JdbcUserRepository(
    private val transactions: JdbcTransactionRunner
) : UserRepository {
    override fun findActiveByEmail(email: String): User? =
        connection().queryOne(
            """
            SELECT id, organization_id, email, display_name, role, active, password_salt, password_hash
            FROM queuedos_users
            WHERE lower(email) = lower(?) AND active = true
            ORDER BY organization_id
            LIMIT 1
            """.trimIndent(),
            email
        ) { user(it) }

    override fun findActiveById(userId: String): User? =
        connection().queryOne(
            """
            SELECT id, organization_id, email, display_name, role, active, password_salt, password_hash
            FROM queuedos_users
            WHERE id = ? AND active = true
            """.trimIndent(),
            userId
        ) { user(it) }

    override fun findById(organizationId: String, userId: String): User? =
        connection().queryOne(
            """
            SELECT id, organization_id, email, display_name, role, active, password_salt, password_hash
            FROM queuedos_users
            WHERE organization_id = ? AND id = ?
            """.trimIndent(),
            organizationId,
            userId
        ) { user(it) }

    override fun listByOrganization(organizationId: String): List<User> =
        connection().query(
            """
            SELECT id, organization_id, email, display_name, role, active, password_salt, password_hash
            FROM queuedos_users
            WHERE organization_id = ?
            ORDER BY email
            """.trimIndent(),
            organizationId
        ) { user(it) }

    override fun emailExists(organizationId: String, email: String): Boolean =
        connection().exists(
            "SELECT 1 FROM queuedos_users WHERE organization_id = ? AND lower(email) = lower(?)",
            organizationId,
            email
        )

    override fun insert(user: User) {
        connection().execute(
            """
            INSERT INTO queuedos_users
                (id, organization_id, email, display_name, role, active, password_salt, password_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            user.id,
            user.organizationId,
            user.email,
            user.displayName,
            user.role.name,
            user.active,
            user.passwordSalt,
            user.passwordHash
        )
    }

    override fun update(user: User) {
        connection().execute(
            """
            UPDATE queuedos_users
            SET email = ?, display_name = ?, role = ?, active = ?, password_salt = ?, password_hash = ?
            WHERE id = ? AND organization_id = ?
            """.trimIndent(),
            user.email,
            user.displayName,
            user.role.name,
            user.active,
            user.passwordSalt,
            user.passwordHash,
            user.id,
            user.organizationId
        )
    }

    private fun connection() = transactions.connection()
}

private class JdbcProjectRepository(
    private val transactions: JdbcTransactionRunner
) : ProjectRepository {
    override fun listByOrganization(organizationId: String): List<Project> =
        connection().query(
            """
            SELECT id, organization_id, key, name, description, next_ticket_number, archived
            FROM queuedos_projects
            WHERE organization_id = ?
            ORDER BY key
            """.trimIndent(),
            organizationId
        ) { project(it) }

    override fun findById(organizationId: String, projectId: String): Project? =
        findById(organizationId, projectId, forUpdate = false)

    override fun findByIdForUpdate(organizationId: String, projectId: String): Project? =
        findById(organizationId, projectId, forUpdate = true)

    override fun keyExists(organizationId: String, key: String): Boolean =
        connection().exists(
            "SELECT 1 FROM queuedos_projects WHERE organization_id = ? AND key = ?",
            organizationId,
            key
        )

    override fun hasTickets(projectId: String): Boolean =
        connection().exists("SELECT 1 FROM queuedos_tickets WHERE project_id = ?", projectId)

    override fun insert(project: Project) {
        connection().execute(
            """
            INSERT INTO queuedos_projects
                (id, organization_id, key, name, description, next_ticket_number, archived)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            project.id,
            project.organizationId,
            project.key,
            project.name,
            project.description,
            project.nextTicketNumber,
            project.archived
        )
    }

    override fun update(project: Project) {
        connection().execute(
            """
            UPDATE queuedos_projects
            SET key = ?, name = ?, description = ?, next_ticket_number = ?, archived = ?
            WHERE id = ? AND organization_id = ?
            """.trimIndent(),
            project.key,
            project.name,
            project.description,
            project.nextTicketNumber,
            project.archived,
            project.id,
            project.organizationId
        )
    }

    private fun findById(organizationId: String, projectId: String, forUpdate: Boolean): Project? =
        connection().queryOne(
            """
            SELECT id, organization_id, key, name, description, next_ticket_number, archived
            FROM queuedos_projects
            WHERE organization_id = ? AND id = ?
            ${if (forUpdate) "FOR UPDATE" else ""}
            """.trimIndent(),
            organizationId,
            projectId
        ) { project(it) }

    private fun connection() = transactions.connection()
}

private class JdbcTicketTypeRepository(
    private val transactions: JdbcTransactionRunner
) : TicketTypeRepository {
    override fun listByOrganization(organizationId: String): List<TicketType> =
        connection().query(
            """
            SELECT id, organization_id, project_id, name, description, color
            FROM queuedos_ticket_types
            WHERE organization_id = ?
            ORDER BY project_id, name
            """.trimIndent(),
            organizationId
        ) { ticketType(it) }

    override fun findById(organizationId: String, typeId: String): TicketType? =
        connection().queryOne(
            """
            SELECT id, organization_id, project_id, name, description, color
            FROM queuedos_ticket_types
            WHERE organization_id = ? AND id = ?
            """.trimIndent(),
            organizationId,
            typeId
        ) { ticketType(it) }

    override fun findForProject(organizationId: String, projectId: String, typeId: String): TicketType? =
        connection().queryOne(
            """
            SELECT id, organization_id, project_id, name, description, color
            FROM queuedos_ticket_types
            WHERE organization_id = ? AND project_id = ? AND id = ?
            """.trimIndent(),
            organizationId,
            projectId,
            typeId
        ) { ticketType(it) }

    override fun nameExists(projectId: String, name: String, ignoredId: String?): Boolean =
        if (ignoredId == null) {
            connection().exists(
                "SELECT 1 FROM queuedos_ticket_types WHERE project_id = ? AND lower(name) = lower(?)",
                projectId,
                name
            )
        } else {
            connection().exists(
                "SELECT 1 FROM queuedos_ticket_types WHERE project_id = ? AND lower(name) = lower(?) AND id <> ?",
                projectId,
                name,
                ignoredId
            )
        }

    override fun isUsed(typeId: String): Boolean =
        connection().exists("SELECT 1 FROM queuedos_tickets WHERE type_id = ?", typeId)

    override fun insert(ticketType: TicketType) {
        connection().execute(
            """
            INSERT INTO queuedos_ticket_types
                (id, organization_id, project_id, name, description, color)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            ticketType.id,
            ticketType.organizationId,
            ticketType.projectId,
            ticketType.name,
            ticketType.description,
            ticketType.color
        )
    }

    override fun update(ticketType: TicketType) {
        connection().execute(
            """
            UPDATE queuedos_ticket_types
            SET name = ?, description = ?, color = ?
            WHERE id = ? AND organization_id = ?
            """.trimIndent(),
            ticketType.name,
            ticketType.description,
            ticketType.color,
            ticketType.id,
            ticketType.organizationId
        )
    }

    override fun delete(typeId: String) {
        connection().execute("DELETE FROM queuedos_ticket_types WHERE id = ?", typeId)
    }

    private fun connection() = transactions.connection()
}

private class JdbcWorkflowRepository(
    private val transactions: JdbcTransactionRunner
) : WorkflowRepository {
    override fun listByOrganization(organizationId: String): List<Workflow> =
        connection().query(
            "SELECT id, organization_id, project_id FROM queuedos_workflows WHERE organization_id = ? ORDER BY project_id",
            organizationId
        ) { workflow(it) }

    override fun findByProject(organizationId: String, projectId: String): Workflow? =
        connection().queryOne(
            "SELECT id, organization_id, project_id FROM queuedos_workflows WHERE organization_id = ? AND project_id = ?",
            organizationId,
            projectId
        ) { workflow(it) }

    override fun insert(workflow: Workflow) {
        insertWorkflowRow(workflow)
        insertChildren(workflow)
    }

    override fun replace(workflow: Workflow) {
        connection().execute("DELETE FROM queuedos_workflow_transitions WHERE workflow_id = ?", workflow.id)
        connection().execute("DELETE FROM queuedos_workflow_statuses WHERE workflow_id = ?", workflow.id)
        connection().execute(
            "UPDATE queuedos_workflows SET organization_id = ?, project_id = ? WHERE id = ?",
            workflow.organizationId,
            workflow.projectId,
            workflow.id
        )
        insertChildren(workflow)
    }

    private fun workflow(result: ResultSet): Workflow {
        val workflowId = result.getString("id")
        return Workflow(
            id = workflowId,
            organizationId = result.getString("organization_id"),
            projectId = result.getString("project_id"),
            statuses = statuses(workflowId),
            transitions = transitions(workflowId)
        )
    }

    private fun statuses(workflowId: String): List<WorkflowStatus> =
        connection().query(
            """
            SELECT id, name, category, sort_order
            FROM queuedos_workflow_statuses
            WHERE workflow_id = ?
            ORDER BY sort_order
            """.trimIndent(),
            workflowId
        ) {
            WorkflowStatus(
                id = it.getString("id"),
                name = it.getString("name"),
                category = it.getString("category"),
                sortOrder = it.getInt("sort_order")
            )
        }

    private fun transitions(workflowId: String): List<WorkflowTransition> =
        connection().query(
            """
            SELECT id, from_status_id, to_status_id, global_transition, allow_backward
            FROM queuedos_workflow_transitions
            WHERE workflow_id = ?
            ORDER BY id
            """.trimIndent(),
            workflowId
        ) {
            val transitionId = it.getString("id")
            WorkflowTransition(
                id = transitionId,
                fromStatusId = it.getString("from_status_id"),
                toStatusId = it.getString("to_status_id"),
                allowedRoles = transitionRoles(workflowId, transitionId),
                requiredFields = transitionFields(workflowId, transitionId),
                globalTransition = it.getBoolean("global_transition"),
                allowBackward = it.getBoolean("allow_backward")
            )
        }

    private fun transitionRoles(workflowId: String, transitionId: String): List<Role> =
        connection().query(
            """
            SELECT role FROM queuedos_workflow_transition_roles
            WHERE workflow_id = ? AND transition_id = ?
            ORDER BY sort_order
            """.trimIndent(),
            workflowId,
            transitionId
        ) { Role.valueOf(it.getString("role")) }

    private fun transitionFields(workflowId: String, transitionId: String): List<String> =
        connection().query(
            """
            SELECT field_name FROM queuedos_workflow_transition_required_fields
            WHERE workflow_id = ? AND transition_id = ?
            ORDER BY sort_order
            """.trimIndent(),
            workflowId,
            transitionId
        ) { it.getString("field_name") }

    private fun insertWorkflowRow(workflow: Workflow) {
        connection().execute(
            "INSERT INTO queuedos_workflows (id, organization_id, project_id) VALUES (?, ?, ?)",
            workflow.id,
            workflow.organizationId,
            workflow.projectId
        )
    }

    private fun insertChildren(workflow: Workflow) {
        workflow.statuses.forEach { status ->
            connection().execute(
                """
                INSERT INTO queuedos_workflow_statuses
                    (workflow_id, id, name, category, sort_order)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                workflow.id,
                status.id,
                status.name,
                status.category,
                status.sortOrder
            )
        }
        workflow.transitions.forEach { transition ->
            connection().execute(
                """
                INSERT INTO queuedos_workflow_transitions
                    (workflow_id, id, from_status_id, to_status_id, global_transition, allow_backward)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                workflow.id,
                transition.id,
                transition.fromStatusId,
                transition.toStatusId,
                transition.globalTransition,
                transition.allowBackward
            )
            transition.allowedRoles.forEachIndexed { index, role ->
                connection().execute(
                    """
                    INSERT INTO queuedos_workflow_transition_roles
                        (workflow_id, transition_id, role, sort_order)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    workflow.id,
                    transition.id,
                    role.name,
                    index
                )
            }
            transition.requiredFields.forEachIndexed { index, field ->
                connection().execute(
                    """
                    INSERT INTO queuedos_workflow_transition_required_fields
                        (workflow_id, transition_id, field_name, sort_order)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    workflow.id,
                    transition.id,
                    field,
                    index
                )
            }
        }
    }

    private fun connection() = transactions.connection()
}

private class JdbcTicketRepository(
    private val transactions: JdbcTransactionRunner
) : TicketRepository {
    override fun listByOrganization(organizationId: String): List<Ticket> =
        connection().query(
            """
            SELECT id, organization_id, project_id, number, key, title, description, status_id, type_id,
                   priority, assignee_id, due_date, estimate, reporter_id, created_at, updated_at, deleted_at,
                   deleted_by_id
            FROM queuedos_tickets
            WHERE organization_id = ? AND deleted_at IS NULL
            ORDER BY project_id, number
            """.trimIndent(),
            organizationId
        ) { ticket(it) }

    override fun listDeletedByOrganization(organizationId: String): List<Ticket> =
        connection().query(
            """
            SELECT id, organization_id, project_id, number, key, title, description, status_id, type_id,
                   priority, assignee_id, due_date, estimate, reporter_id, created_at, updated_at, deleted_at,
                   deleted_by_id
            FROM queuedos_tickets
            WHERE organization_id = ? AND deleted_at IS NOT NULL
            ORDER BY deleted_at DESC, project_id, number
            """.trimIndent(),
            organizationId
        ) { ticket(it) }

    override fun findById(organizationId: String, ticketId: String): Ticket? =
        connection().queryOne(
            """
            SELECT id, organization_id, project_id, number, key, title, description, status_id, type_id,
                   priority, assignee_id, due_date, estimate, reporter_id, created_at, updated_at, deleted_at,
                   deleted_by_id
            FROM queuedos_tickets
            WHERE organization_id = ? AND id = ? AND deleted_at IS NULL
            """.trimIndent(),
            organizationId,
            ticketId
        ) { ticket(it) }

    override fun findDeletedById(organizationId: String, ticketId: String): Ticket? =
        connection().queryOne(
            """
            SELECT id, organization_id, project_id, number, key, title, description, status_id, type_id,
                   priority, assignee_id, due_date, estimate, reporter_id, created_at, updated_at, deleted_at,
                   deleted_by_id
            FROM queuedos_tickets
            WHERE organization_id = ? AND id = ? AND deleted_at IS NOT NULL
            """.trimIndent(),
            organizationId,
            ticketId
        ) { ticket(it) }

    override fun insert(ticket: Ticket) {
        connection().execute(
            """
            INSERT INTO queuedos_tickets
                (id, organization_id, project_id, number, key, title, description, status_id, type_id,
                 priority, assignee_id, due_date, estimate, reporter_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            ticket.id,
            ticket.organizationId,
            ticket.projectId,
            ticket.number,
            ticket.key,
            ticket.title,
            ticket.description,
            ticket.statusId,
            ticket.typeId,
            ticket.priority.name,
            ticket.assigneeId,
            ticket.dueDate,
            ticket.estimate,
            ticket.reporterId,
            ticket.createdAt,
            ticket.updatedAt
        )
        replaceLabels(ticket)
    }

    override fun update(ticket: Ticket) {
        connection().execute(
            """
            UPDATE queuedos_tickets
            SET title = ?, description = ?, status_id = ?, type_id = ?, priority = ?, assignee_id = ?,
                due_date = ?, estimate = ?, updated_at = ?, deleted_at = ?, deleted_by_id = ?
            WHERE id = ? AND organization_id = ?
            """.trimIndent(),
            ticket.title,
            ticket.description,
            ticket.statusId,
            ticket.typeId,
            ticket.priority.name,
            ticket.assigneeId,
            ticket.dueDate,
            ticket.estimate,
            ticket.updatedAt,
            ticket.deletedAt,
            ticket.deletedById,
            ticket.id,
            ticket.organizationId
        )
        replaceLabels(ticket)
    }

    override fun setCommitment(ticketId: String, userId: String, committed: Boolean) {
        if (committed) {
            connection().execute(
                "INSERT INTO queuedos_ticket_commitments (ticket_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                ticketId,
                userId
            )
        } else {
            connection().execute(
                "DELETE FROM queuedos_ticket_commitments WHERE ticket_id = ? AND user_id = ?",
                ticketId,
                userId
            )
        }
    }

    override fun comments(organizationId: String, ticketId: String?): List<TicketComment> =
        if (ticketId == null) {
            connection().query(
                """
                SELECT id, organization_id, ticket_id, author_id, body, created_at
                FROM queuedos_ticket_comments
                WHERE organization_id = ?
                ORDER BY ticket_id, created_at
                """.trimIndent(),
                organizationId
            ) { comment(it) }
        } else {
            connection().query(
                """
                SELECT id, organization_id, ticket_id, author_id, body, created_at
                FROM queuedos_ticket_comments
                WHERE organization_id = ? AND ticket_id = ?
                ORDER BY created_at
                """.trimIndent(),
                organizationId,
                ticketId
            ) { comment(it) }
        }

    override fun changes(organizationId: String, ticketId: String?): List<TicketChange> =
        if (ticketId == null) {
            connection().query(
                """
                SELECT id, organization_id, ticket_id, actor_id, field_name, old_value, new_value, created_at
                FROM queuedos_ticket_changes
                WHERE organization_id = ?
                ORDER BY ticket_id, created_at
                """.trimIndent(),
                organizationId
            ) { change(it) }
        } else {
            connection().query(
                """
                SELECT id, organization_id, ticket_id, actor_id, field_name, old_value, new_value, created_at
                FROM queuedos_ticket_changes
                WHERE organization_id = ? AND ticket_id = ?
                ORDER BY created_at DESC
                """.trimIndent(),
                organizationId,
                ticketId
            ) { change(it) }
        }

    override fun insertComment(comment: TicketComment) {
        connection().execute(
            """
            INSERT INTO queuedos_ticket_comments
                (id, organization_id, ticket_id, author_id, body, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            comment.id,
            comment.organizationId,
            comment.ticketId,
            comment.authorId,
            comment.body,
            comment.createdAt
        )
    }

    override fun insertChanges(changes: List<TicketChange>) {
        changes.forEach { change ->
            connection().execute(
                """
                INSERT INTO queuedos_ticket_changes
                    (id, organization_id, ticket_id, actor_id, field_name, old_value, new_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                change.id,
                change.organizationId,
                change.ticketId,
                change.actorId,
                change.field,
                change.oldValue,
                change.newValue,
                change.createdAt
            )
        }
    }

    private fun ticket(result: ResultSet): Ticket {
        val ticketId = result.getString("id")
        return Ticket(
            id = ticketId,
            organizationId = result.getString("organization_id"),
            projectId = result.getString("project_id"),
            number = result.getInt("number"),
            key = result.getString("key"),
            title = result.getString("title"),
            description = result.getString("description"),
            statusId = result.getString("status_id"),
            typeId = result.getString("type_id"),
            priority = Priority.valueOf(result.getString("priority")),
            assigneeId = result.getString("assignee_id"),
            committedUserIds = commitments(ticketId),
            labels = labels(ticketId),
            dueDate = result.getString("due_date"),
            estimate = result.getNullableInt("estimate"),
            reporterId = result.getString("reporter_id"),
            createdAt = result.getString("created_at"),
            updatedAt = result.getString("updated_at"),
            deletedAt = result.getString("deleted_at"),
            deletedById = result.getString("deleted_by_id")
        )
    }

    private fun labels(ticketId: String): List<String> =
        connection().query(
            "SELECT label FROM queuedos_ticket_labels WHERE ticket_id = ? ORDER BY sort_order",
            ticketId
        ) { it.getString("label") }

    private fun commitments(ticketId: String): List<String> =
        connection().query(
            "SELECT user_id FROM queuedos_ticket_commitments WHERE ticket_id = ? ORDER BY user_id",
            ticketId
        ) { it.getString("user_id") }

    private fun replaceLabels(ticket: Ticket) {
        connection().execute("DELETE FROM queuedos_ticket_labels WHERE ticket_id = ?", ticket.id)
        ticket.labels.forEachIndexed { index, label ->
            connection().execute(
                "INSERT INTO queuedos_ticket_labels (ticket_id, label, sort_order) VALUES (?, ?, ?)",
                ticket.id,
                label,
                index
            )
        }
    }

    private fun connection() = transactions.connection()
}

private class JdbcSavedTicketFilterRepository(
    private val transactions: JdbcTransactionRunner,
    private val json: Json
) : SavedTicketFilterRepository {
    override fun listForOwner(organizationId: String, ownerId: String): List<SavedTicketFilter> =
        connection().query(
            """
            SELECT id, organization_id, owner_id, name, view, project_id, filters
            FROM queuedos_saved_ticket_filters
            WHERE organization_id = ? AND owner_id = ?
            ORDER BY view, name
            """.trimIndent(),
            organizationId,
            ownerId
        ) { filter(it) }

    override fun findForOwner(organizationId: String, ownerId: String, filterId: String): SavedTicketFilter? =
        connection().queryOne(
            """
            SELECT id, organization_id, owner_id, name, view, project_id, filters
            FROM queuedos_saved_ticket_filters
            WHERE organization_id = ? AND owner_id = ? AND id = ?
            """.trimIndent(),
            organizationId,
            ownerId,
            filterId
        ) { filter(it) }

    override fun nameExists(
        organizationId: String,
        ownerId: String,
        view: String,
        projectId: String?,
        name: String,
        ignoredId: String?
    ): Boolean {
        val sql =
            """
            SELECT 1 FROM queuedos_saved_ticket_filters
            WHERE organization_id = ? AND owner_id = ? AND view = ?
              AND coalesce(project_id, '') = coalesce(?, '')
              AND lower(name) = lower(?)
            """.trimIndent()
        return if (ignoredId == null) {
            connection().exists(sql, organizationId, ownerId, view, projectId, name)
        } else {
            connection().exists("$sql AND id <> ?", organizationId, ownerId, view, projectId, name, ignoredId)
        }
    }

    override fun insert(filter: SavedTicketFilter) {
        connection().execute(
            """
            INSERT INTO queuedos_saved_ticket_filters
                (id, organization_id, owner_id, name, view, project_id, filters)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            filter.id,
            filter.organizationId,
            filter.ownerId,
            filter.name,
            filter.view.name,
            filter.projectId,
            json.encodeToString(filter.filters)
        )
    }

    override fun update(filter: SavedTicketFilter) {
        connection().execute(
            """
            UPDATE queuedos_saved_ticket_filters
            SET name = ?, filters = ?
            WHERE id = ? AND organization_id = ? AND owner_id = ?
            """.trimIndent(),
            filter.name,
            json.encodeToString(filter.filters),
            filter.id,
            filter.organizationId,
            filter.ownerId
        )
    }

    override fun delete(filterId: String) {
        connection().execute("DELETE FROM queuedos_saved_ticket_filters WHERE id = ?", filterId)
    }

    private fun filter(result: ResultSet): SavedTicketFilter =
        SavedTicketFilter(
            id = result.getString("id"),
            organizationId = result.getString("organization_id"),
            ownerId = result.getString("owner_id"),
            name = result.getString("name"),
            view = SavedTicketFilterView.valueOf(result.getString("view")),
            projectId = result.getString("project_id"),
            filters = json.decodeFromString<SavedTicketFilterCriteria>(result.getString("filters"))
        )

    private fun connection() = transactions.connection()
}

private class JdbcActivityHookRepository(
    private val transactions: JdbcTransactionRunner
) : ActivityHookRepository {
    override fun listByOrganization(organizationId: String): List<ActivityHook> =
        connection().query(
            """
            SELECT id, organization_id, event_type, webhook_url, message_template, active
            FROM queuedos_activity_hooks
            WHERE organization_id = ?
            ORDER BY event_type, id
            """.trimIndent(),
            organizationId
        ) { hook(it) }

    override fun listActive(organizationId: String, eventType: ActivityEventType): List<ActivityHook> =
        connection().query(
            """
            SELECT id, organization_id, event_type, webhook_url, message_template, active
            FROM queuedos_activity_hooks
            WHERE organization_id = ? AND event_type = ? AND active = true
            ORDER BY id
            """.trimIndent(),
            organizationId,
            eventType.name
        ) { hook(it) }

    override fun findById(organizationId: String, hookId: String): ActivityHook? =
        connection().queryOne(
            """
            SELECT id, organization_id, event_type, webhook_url, message_template, active
            FROM queuedos_activity_hooks
            WHERE organization_id = ? AND id = ?
            """.trimIndent(),
            organizationId,
            hookId
        ) { hook(it) }

    override fun insert(hook: ActivityHook) {
        connection().execute(
            """
            INSERT INTO queuedos_activity_hooks
                (id, organization_id, event_type, webhook_url, message_template, active)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            hook.id,
            hook.organizationId,
            hook.eventType.name,
            hook.webhookUrl,
            hook.messageTemplate,
            hook.active
        )
    }

    override fun update(hook: ActivityHook) {
        connection().execute(
            """
            UPDATE queuedos_activity_hooks
            SET event_type = ?, webhook_url = ?, message_template = ?, active = ?
            WHERE id = ? AND organization_id = ?
            """.trimIndent(),
            hook.eventType.name,
            hook.webhookUrl,
            hook.messageTemplate,
            hook.active,
            hook.id,
            hook.organizationId
        )
    }

    override fun delete(hookId: String) {
        connection().execute("DELETE FROM queuedos_activity_hooks WHERE id = ?", hookId)
    }

    private fun hook(result: ResultSet): ActivityHook =
        ActivityHook(
            id = result.getString("id"),
            organizationId = result.getString("organization_id"),
            eventType = ActivityEventType.valueOf(result.getString("event_type")),
            webhookUrl = result.getString("webhook_url"),
            messageTemplate = result.getString("message_template"),
            active = result.getBoolean("active")
        )

    private fun connection() = transactions.connection()
}

private fun user(result: ResultSet): User =
    User(
        id = result.getString("id"),
        organizationId = result.getString("organization_id"),
        email = result.getString("email"),
        displayName = result.getString("display_name"),
        role = Role.valueOf(result.getString("role")),
        active = result.getBoolean("active"),
        passwordSalt = result.getString("password_salt"),
        passwordHash = result.getString("password_hash")
    )

private fun project(result: ResultSet): Project =
    Project(
        id = result.getString("id"),
        organizationId = result.getString("organization_id"),
        key = result.getString("key"),
        name = result.getString("name"),
        description = result.getString("description"),
        nextTicketNumber = result.getInt("next_ticket_number"),
        archived = result.getBoolean("archived")
    )

private fun ticketType(result: ResultSet): TicketType =
    TicketType(
        id = result.getString("id"),
        organizationId = result.getString("organization_id"),
        projectId = result.getString("project_id"),
        name = result.getString("name"),
        description = result.getString("description"),
        color = result.getString("color")
    )

private fun comment(result: ResultSet): TicketComment =
    TicketComment(
        id = result.getString("id"),
        organizationId = result.getString("organization_id"),
        ticketId = result.getString("ticket_id"),
        authorId = result.getString("author_id"),
        body = result.getString("body"),
        createdAt = result.getString("created_at")
    )

private fun change(result: ResultSet): TicketChange =
    TicketChange(
        id = result.getString("id"),
        organizationId = result.getString("organization_id"),
        ticketId = result.getString("ticket_id"),
        actorId = result.getString("actor_id"),
        field = result.getString("field_name"),
        oldValue = result.getString("old_value"),
        newValue = result.getString("new_value"),
        createdAt = result.getString("created_at")
    )

private fun Connection.exists(sql: String, vararg values: Any?): Boolean =
    queryOne(sql, *values) { true } ?: false

private fun <T> Connection.queryOne(
    sql: String,
    vararg values: Any?,
    mapper: (ResultSet) -> T
): T? =
    prepareStatement(sql).use { statement ->
        statement.bind(values)
        statement.executeQuery().use { result ->
            if (result.next()) mapper(result) else null
        }
    }

private fun <T> Connection.query(
    sql: String,
    vararg values: Any?,
    mapper: (ResultSet) -> T
): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(values)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(mapper(result))
                }
            }
        }
    }

private fun Connection.execute(sql: String, vararg values: Any?) {
    prepareStatement(sql).use { statement ->
        statement.bind(values)
        statement.executeUpdate()
    }
}

private fun PreparedStatement.bind(values: Array<out Any?>) {
    values.forEachIndexed { index, value ->
        val parameter = index + 1
        when (value) {
            null -> setNull(parameter, Types.NULL)
            is Boolean -> setBoolean(parameter, value)
            is Int -> setInt(parameter, value)
            else -> setObject(parameter, value)
        }
    }
}

private fun ResultSet.getNullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}
