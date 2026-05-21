package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.domain.SavedTicketFilter
import de.ljunker.queuedos.domain.Workflow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

object LegacySnapshotImporter {
    fun insert(connection: Connection, data: AppData, json: Json) {
        organizations(connection, data)
        users(connection, data)
        projects(connection, data)
        ticketTypes(connection, data)
        workflows(connection, data.workflows)
        tickets(connection, data)
        comments(connection, data)
        changes(connection, data)
        savedFilters(connection, data.savedTicketFilters, json)
    }

    private fun organizations(connection: Connection, data: AppData) {
        connection.batch("INSERT INTO queuedos_organizations (id, name) VALUES (?, ?)") { statement ->
            data.organizations.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.name)
                statement.addBatch()
            }
        }
    }

    private fun users(connection: Connection, data: AppData) {
        connection.batch(
            """
            INSERT INTO queuedos_users
                (id, organization_id, email, display_name, role, active, password_salt, password_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            data.users.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.email)
                statement.setString(4, it.displayName)
                statement.setString(5, it.role.name)
                statement.setBoolean(6, it.active)
                statement.setString(7, it.passwordSalt)
                statement.setString(8, it.passwordHash)
                statement.addBatch()
            }
        }
    }

    private fun projects(connection: Connection, data: AppData) {
        connection.batch(
            """
            INSERT INTO queuedos_projects
                (id, organization_id, key, name, description, next_ticket_number, archived)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            data.projects.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.key)
                statement.setString(4, it.name)
                statement.setString(5, it.description)
                statement.setInt(6, it.nextTicketNumber)
                statement.setBoolean(7, it.archived)
                statement.addBatch()
            }
        }
    }

    private fun ticketTypes(connection: Connection, data: AppData) {
        connection.batch(
            """
            INSERT INTO queuedos_ticket_types
                (id, organization_id, project_id, name, description, color)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            data.ticketTypes.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.projectId)
                statement.setString(4, it.name)
                statement.setString(5, it.description)
                statement.setString(6, it.color)
                statement.addBatch()
            }
        }
    }

    private fun workflows(connection: Connection, workflows: List<Workflow>) {
        connection.batch("INSERT INTO queuedos_workflows (id, organization_id, project_id) VALUES (?, ?, ?)") { statement ->
            workflows.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.projectId)
                statement.addBatch()
            }
        }
        connection.batch(
            """
            INSERT INTO queuedos_workflow_statuses
                (workflow_id, id, name, category, sort_order)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                workflow.statuses.forEach {
                    statement.setString(1, workflow.id)
                    statement.setString(2, it.id)
                    statement.setString(3, it.name)
                    statement.setString(4, it.category)
                    statement.setInt(5, it.sortOrder)
                    statement.addBatch()
                }
            }
        }
        connection.batch(
            """
            INSERT INTO queuedos_workflow_transitions
                (workflow_id, id, from_status_id, to_status_id, global_transition, allow_backward)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                workflow.transitions.forEach {
                    statement.setString(1, workflow.id)
                    statement.setString(2, it.id)
                    statement.setNullableString(3, it.fromStatusId)
                    statement.setString(4, it.toStatusId)
                    statement.setBoolean(5, it.globalTransition)
                    statement.setBoolean(6, it.allowBackward)
                    statement.addBatch()
                }
            }
        }
        connection.batch(
            """
            INSERT INTO queuedos_workflow_transition_roles
                (workflow_id, transition_id, role, sort_order)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                workflow.transitions.forEach { transition ->
                    transition.allowedRoles.forEachIndexed { index, role ->
                        statement.setString(1, workflow.id)
                        statement.setString(2, transition.id)
                        statement.setString(3, role.name)
                        statement.setInt(4, index)
                        statement.addBatch()
                    }
                }
            }
        }
        connection.batch(
            """
            INSERT INTO queuedos_workflow_transition_required_fields
                (workflow_id, transition_id, field_name, sort_order)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                workflow.transitions.forEach { transition ->
                    transition.requiredFields.forEachIndexed { index, field ->
                        statement.setString(1, workflow.id)
                        statement.setString(2, transition.id)
                        statement.setString(3, field)
                        statement.setInt(4, index)
                        statement.addBatch()
                    }
                }
            }
        }
    }

    private fun tickets(connection: Connection, data: AppData) {
        connection.batch(
            """
            INSERT INTO queuedos_tickets
                (id, organization_id, project_id, number, key, title, description, status_id, type_id,
                 priority, assignee_id, due_date, estimate, reporter_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            data.tickets.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.projectId)
                statement.setInt(4, it.number)
                statement.setString(5, it.key)
                statement.setString(6, it.title)
                statement.setString(7, it.description)
                statement.setString(8, it.statusId)
                statement.setString(9, it.typeId)
                statement.setString(10, it.priority.name)
                statement.setNullableString(11, it.assigneeId)
                statement.setNullableString(12, it.dueDate)
                statement.setNullableInt(13, it.estimate)
                statement.setString(14, it.reporterId)
                statement.setString(15, it.createdAt)
                statement.setString(16, it.updatedAt)
                statement.addBatch()
            }
        }
        connection.batch("INSERT INTO queuedos_ticket_labels (ticket_id, label, sort_order) VALUES (?, ?, ?)") { statement ->
            data.tickets.forEach { ticket ->
                ticket.labels.forEachIndexed { index, label ->
                    statement.setString(1, ticket.id)
                    statement.setString(2, label)
                    statement.setInt(3, index)
                    statement.addBatch()
                }
            }
        }
    }

    private fun comments(connection: Connection, data: AppData) {
        connection.batch(
            """
            INSERT INTO queuedos_ticket_comments
                (id, organization_id, ticket_id, author_id, body, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            data.comments.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.ticketId)
                statement.setString(4, it.authorId)
                statement.setString(5, it.body)
                statement.setString(6, it.createdAt)
                statement.addBatch()
            }
        }
    }

    private fun changes(connection: Connection, data: AppData) {
        connection.batch(
            """
            INSERT INTO queuedos_ticket_changes
                (id, organization_id, ticket_id, actor_id, field_name, old_value, new_value, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            data.ticketChanges.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.ticketId)
                statement.setString(4, it.actorId)
                statement.setString(5, it.field)
                statement.setNullableString(6, it.oldValue)
                statement.setNullableString(7, it.newValue)
                statement.setString(8, it.createdAt)
                statement.addBatch()
            }
        }
    }

    private fun savedFilters(connection: Connection, filters: List<SavedTicketFilter>, json: Json) {
        connection.batch(
            """
            INSERT INTO queuedos_saved_ticket_filters
                (id, organization_id, owner_id, name, view, project_id, filters)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            filters.forEach {
                statement.setString(1, it.id)
                statement.setString(2, it.organizationId)
                statement.setString(3, it.ownerId)
                statement.setString(4, it.name)
                statement.setString(5, it.view.name)
                statement.setNullableString(6, it.projectId)
                statement.setString(7, json.encodeToString(it.filters))
                statement.addBatch()
            }
        }
    }

    private fun Connection.batch(sql: String, bind: (PreparedStatement) -> Unit) {
        prepareStatement(sql).use {
            bind(it)
            it.executeBatch()
        }
    }

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
    }

    private fun PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value == null) setNull(index, Types.INTEGER) else setInt(index, value)
    }
}
