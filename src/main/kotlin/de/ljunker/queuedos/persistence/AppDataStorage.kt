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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

interface AppDataStorage {
    fun load(): AppData?
    fun save(snapshot: AppData)
}

class FileAppDataStorage(
    private val dataFile: Path,
    private val json: Json
) : AppDataStorage {
    override fun load(): AppData? {
        if (!Files.exists(dataFile)) return null
        return json.decodeFromString(Files.readString(dataFile))
    }

    override fun save(snapshot: AppData) {
        dataFile.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            dataFile,
            json.encodeToString(snapshot),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}

class PostgreSqlAppDataStorage(
    private val jdbcUrl: String,
    private val username: String?,
    private val password: String?,
    private val json: Json,
    private val legacySnapshotId: String = "default"
) : AppDataStorage {
    override fun load(): AppData? {
        ensureSchema()
        connection().use { connection ->
            val organizations = connection.query(
                "SELECT id, name FROM queuedos_organizations ORDER BY id"
            ) { result ->
                Organization(
                    id = result.getString("id"),
                    name = result.getString("name")
                )
            }
            if (organizations.isEmpty()) {
                val legacySnapshot = connection.loadLegacySnapshot()
                if (legacySnapshot != null) {
                    connection.replaceData(legacySnapshot)
                    return legacySnapshot
                }
                return null
            }

            val users = connection.query(
                """
                SELECT id, organization_id, email, display_name, role, active, password_salt, password_hash
                FROM queuedos_users
                ORDER BY organization_id, email
                """.trimIndent()
            ) { result ->
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
            }
            val projects = connection.query(
                """
                SELECT id, organization_id, key, name, description, next_ticket_number, archived
                FROM queuedos_projects
                ORDER BY organization_id, key
                """.trimIndent()
            ) { result ->
                Project(
                    id = result.getString("id"),
                    organizationId = result.getString("organization_id"),
                    key = result.getString("key"),
                    name = result.getString("name"),
                    description = result.getString("description"),
                    nextTicketNumber = result.getInt("next_ticket_number"),
                    archived = result.getBoolean("archived")
                )
            }
            val ticketTypes = connection.query(
                """
                SELECT id, organization_id, project_id, name, description, color
                FROM queuedos_ticket_types
                ORDER BY project_id, name
                """.trimIndent()
            ) { result ->
                TicketType(
                    id = result.getString("id"),
                    organizationId = result.getString("organization_id"),
                    projectId = result.getString("project_id"),
                    name = result.getString("name"),
                    description = result.getString("description"),
                    color = result.getString("color")
                )
            }
            val statusesByWorkflow = connection.query(
                """
                SELECT workflow_id, id, name, category, sort_order
                FROM queuedos_workflow_statuses
                ORDER BY workflow_id, sort_order
                """.trimIndent()
            ) { result ->
                WorkflowStatusRow(
                    workflowId = result.getString("workflow_id"),
                    status = WorkflowStatus(
                        id = result.getString("id"),
                        name = result.getString("name"),
                        category = result.getString("category"),
                        sortOrder = result.getInt("sort_order")
                    )
                )
            }.groupBy({ it.workflowId }, { it.status })
            val rolesByTransition = connection.query(
                """
                SELECT workflow_id, transition_id, role
                FROM queuedos_workflow_transition_roles
                ORDER BY workflow_id, transition_id, sort_order
                """.trimIndent()
            ) { result ->
                WorkflowTransitionRoleRow(
                    workflowId = result.getString("workflow_id"),
                    transitionId = result.getString("transition_id"),
                    role = Role.valueOf(result.getString("role"))
                )
            }.groupBy({ it.workflowId to it.transitionId }, { it.role })
            val requiredFieldsByTransition = connection.query(
                """
                SELECT workflow_id, transition_id, field_name
                FROM queuedos_workflow_transition_required_fields
                ORDER BY workflow_id, transition_id, sort_order
                """.trimIndent()
            ) { result ->
                WorkflowTransitionFieldRow(
                    workflowId = result.getString("workflow_id"),
                    transitionId = result.getString("transition_id"),
                    fieldName = result.getString("field_name")
                )
            }.groupBy({ it.workflowId to it.transitionId }, { it.fieldName })
            val transitionsByWorkflow = connection.query(
                """
                SELECT workflow_id, id, from_status_id, to_status_id
                FROM queuedos_workflow_transitions
                ORDER BY workflow_id, id
                """.trimIndent()
            ) { result ->
                val workflowId = result.getString("workflow_id")
                val transitionId = result.getString("id")
                WorkflowTransitionRow(
                    workflowId = workflowId,
                    transition = WorkflowTransition(
                        id = transitionId,
                        fromStatusId = result.getString("from_status_id"),
                        toStatusId = result.getString("to_status_id"),
                        allowedRoles = rolesByTransition[workflowId to transitionId].orEmpty(),
                        requiredFields = requiredFieldsByTransition[workflowId to transitionId].orEmpty()
                    )
                )
            }.groupBy({ it.workflowId }, { it.transition })
            val workflows = connection.query(
                """
                SELECT id, organization_id, project_id
                FROM queuedos_workflows
                ORDER BY organization_id, project_id
                """.trimIndent()
            ) { result ->
                val workflowId = result.getString("id")
                Workflow(
                    id = workflowId,
                    organizationId = result.getString("organization_id"),
                    projectId = result.getString("project_id"),
                    statuses = statusesByWorkflow[workflowId].orEmpty(),
                    transitions = transitionsByWorkflow[workflowId].orEmpty()
                )
            }
            val tickets = connection.query(
                """
                SELECT id, organization_id, project_id, number, key, title, description, status_id, type_id,
                       priority, assignee_id, reporter_id, created_at, updated_at
                FROM queuedos_tickets
                ORDER BY project_id, number
                """.trimIndent()
            ) { result ->
                Ticket(
                    id = result.getString("id"),
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
                    reporterId = result.getString("reporter_id"),
                    createdAt = result.getString("created_at"),
                    updatedAt = result.getString("updated_at")
                )
            }

            return AppData(
                organizations = organizations,
                users = users,
                projects = projects,
                ticketTypes = ticketTypes,
                workflows = workflows,
                tickets = tickets
            )
        }
    }

    override fun save(snapshot: AppData) {
        ensureSchema()
        connection().use { connection ->
            connection.replaceData(snapshot)
        }
    }

    private fun ensureSchema() {
        connection().use { connection ->
            schemaStatements.forEach { sql ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(sql)
                }
            }
        }
    }

    private fun Connection.replaceData(snapshot: AppData) {
        val previousAutoCommit = autoCommit
        autoCommit = false
        try {
            deleteData()
            insertOrganizations(snapshot.organizations)
            insertUsers(snapshot.users)
            insertProjects(snapshot.projects)
            insertTicketTypes(snapshot.ticketTypes)
            insertWorkflows(snapshot.workflows)
            insertWorkflowStatuses(snapshot.workflows)
            insertWorkflowTransitions(snapshot.workflows)
            insertWorkflowTransitionRoles(snapshot.workflows)
            insertWorkflowTransitionRequiredFields(snapshot.workflows)
            insertTickets(snapshot.tickets)
            commit()
        } catch (error: Exception) {
            rollback()
            throw error
        } finally {
            autoCommit = previousAutoCommit
        }
    }

    private fun Connection.deleteData() {
        listOf(
            "queuedos_tickets",
            "queuedos_workflow_transition_required_fields",
            "queuedos_workflow_transition_roles",
            "queuedos_workflow_transitions",
            "queuedos_workflow_statuses",
            "queuedos_workflows",
            "queuedos_ticket_types",
            "queuedos_projects",
            "queuedos_users",
            "queuedos_organizations"
        ).forEach { table ->
            createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM $table")
            }
        }
    }

    private fun Connection.insertOrganizations(organizations: List<Organization>) {
        batch("INSERT INTO queuedos_organizations (id, name) VALUES (?, ?)") { statement ->
            organizations.forEach { organization ->
                statement.setString(1, organization.id)
                statement.setString(2, organization.name)
                statement.addBatch()
            }
        }
    }

    private fun Connection.insertUsers(users: List<User>) {
        batch(
            """
            INSERT INTO queuedos_users
                (id, organization_id, email, display_name, role, active, password_salt, password_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            users.forEach { user ->
                statement.setString(1, user.id)
                statement.setString(2, user.organizationId)
                statement.setString(3, user.email)
                statement.setString(4, user.displayName)
                statement.setString(5, user.role.name)
                statement.setBoolean(6, user.active)
                statement.setString(7, user.passwordSalt)
                statement.setString(8, user.passwordHash)
                statement.addBatch()
            }
        }
    }

    private fun Connection.insertProjects(projects: List<Project>) {
        batch(
            """
            INSERT INTO queuedos_projects
                (id, organization_id, key, name, description, next_ticket_number, archived)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            projects.forEach { project ->
                statement.setString(1, project.id)
                statement.setString(2, project.organizationId)
                statement.setString(3, project.key)
                statement.setString(4, project.name)
                statement.setString(5, project.description)
                statement.setInt(6, project.nextTicketNumber)
                statement.setBoolean(7, project.archived)
                statement.addBatch()
            }
        }
    }

    private fun Connection.insertTicketTypes(ticketTypes: List<TicketType>) {
        batch(
            """
            INSERT INTO queuedos_ticket_types
                (id, organization_id, project_id, name, description, color)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            ticketTypes.forEach { ticketType ->
                statement.setString(1, ticketType.id)
                statement.setString(2, ticketType.organizationId)
                statement.setString(3, ticketType.projectId)
                statement.setString(4, ticketType.name)
                statement.setString(5, ticketType.description)
                statement.setString(6, ticketType.color)
                statement.addBatch()
            }
        }
    }

    private fun Connection.insertWorkflows(workflows: List<Workflow>) {
        batch(
            """
            INSERT INTO queuedos_workflows (id, organization_id, project_id)
            VALUES (?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                statement.setString(1, workflow.id)
                statement.setString(2, workflow.organizationId)
                statement.setString(3, workflow.projectId)
                statement.addBatch()
            }
        }
    }

    private fun Connection.insertWorkflowStatuses(workflows: List<Workflow>) {
        batch(
            """
            INSERT INTO queuedos_workflow_statuses
                (workflow_id, id, name, category, sort_order)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                workflow.statuses.forEach { status ->
                    statement.setString(1, workflow.id)
                    statement.setString(2, status.id)
                    statement.setString(3, status.name)
                    statement.setString(4, status.category)
                    statement.setInt(5, status.sortOrder)
                    statement.addBatch()
                }
            }
        }
    }

    private fun Connection.insertWorkflowTransitions(workflows: List<Workflow>) {
        batch(
            """
            INSERT INTO queuedos_workflow_transitions
                (workflow_id, id, from_status_id, to_status_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            workflows.forEach { workflow ->
                workflow.transitions.forEach { transition ->
                    statement.setString(1, workflow.id)
                    statement.setString(2, transition.id)
                    statement.setString(3, transition.fromStatusId)
                    statement.setString(4, transition.toStatusId)
                    statement.addBatch()
                }
            }
        }
    }

    private fun Connection.insertWorkflowTransitionRoles(workflows: List<Workflow>) {
        batch(
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
    }

    private fun Connection.insertWorkflowTransitionRequiredFields(workflows: List<Workflow>) {
        batch(
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

    private fun Connection.insertTickets(tickets: List<Ticket>) {
        batch(
            """
            INSERT INTO queuedos_tickets
                (id, organization_id, project_id, number, key, title, description, status_id, type_id,
                 priority, assignee_id, reporter_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { statement ->
            tickets.forEach { ticket ->
                statement.setString(1, ticket.id)
                statement.setString(2, ticket.organizationId)
                statement.setString(3, ticket.projectId)
                statement.setInt(4, ticket.number)
                statement.setString(5, ticket.key)
                statement.setString(6, ticket.title)
                statement.setString(7, ticket.description)
                statement.setString(8, ticket.statusId)
                statement.setString(9, ticket.typeId)
                statement.setString(10, ticket.priority.name)
                statement.setNullableString(11, ticket.assigneeId)
                statement.setString(12, ticket.reporterId)
                statement.setString(13, ticket.createdAt)
                statement.setString(14, ticket.updatedAt)
                statement.addBatch()
            }
        }
    }

    private fun Connection.loadLegacySnapshot(): AppData? {
        val hasLegacyTable = query("SELECT to_regclass('public.queuedos_state') IS NOT NULL AS exists") { result ->
            result.getBoolean("exists")
        }.firstOrNull() ?: false
        if (!hasLegacyTable) return null
        prepareStatement("SELECT state::text FROM queuedos_state WHERE id = ?").use { statement ->
            statement.setString(1, legacySnapshotId)
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                return json.decodeFromString(result.getString(1))
            }
        }
    }

    private fun <T> Connection.query(sql: String, mapper: (ResultSet) -> T): List<T> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList {
                    while (result.next()) {
                        add(mapper(result))
                    }
                }
            }
        }

    private fun Connection.batch(sql: String, bind: (PreparedStatement) -> Unit) {
        prepareStatement(sql).use { statement ->
            bind(statement)
            statement.executeBatch()
        }
    }

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value == null) {
            setNull(index, java.sql.Types.VARCHAR)
        } else {
            setString(index, value)
        }
    }

    private fun connection() =
        if (username.isNullOrBlank()) {
            DriverManager.getConnection(jdbcUrl)
        } else {
            DriverManager.getConnection(jdbcUrl, username, password ?: "")
        }

    private data class WorkflowStatusRow(
        val workflowId: String,
        val status: WorkflowStatus
    )

    private data class WorkflowTransitionRow(
        val workflowId: String,
        val transition: WorkflowTransition
    )

    private data class WorkflowTransitionRoleRow(
        val workflowId: String,
        val transitionId: String,
        val role: Role
    )

    private data class WorkflowTransitionFieldRow(
        val workflowId: String,
        val transitionId: String,
        val fieldName: String
    )

    private val schemaStatements = listOf(
        """
        CREATE TABLE IF NOT EXISTS queuedos_organizations (
            id text PRIMARY KEY,
            name text NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_users (
            id text PRIMARY KEY,
            organization_id text NOT NULL REFERENCES queuedos_organizations(id) ON DELETE CASCADE,
            email text NOT NULL,
            display_name text NOT NULL,
            role text NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
            active boolean NOT NULL,
            password_salt text NOT NULL,
            password_hash text NOT NULL,
            UNIQUE (organization_id, email)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_projects (
            id text PRIMARY KEY,
            organization_id text NOT NULL REFERENCES queuedos_organizations(id) ON DELETE CASCADE,
            key text NOT NULL,
            name text NOT NULL,
            description text NOT NULL DEFAULT '',
            next_ticket_number integer NOT NULL DEFAULT 1 CHECK (next_ticket_number > 0),
            archived boolean NOT NULL DEFAULT false,
            UNIQUE (organization_id, key)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_ticket_types (
            id text PRIMARY KEY,
            organization_id text NOT NULL REFERENCES queuedos_organizations(id) ON DELETE CASCADE,
            project_id text NOT NULL REFERENCES queuedos_projects(id) ON DELETE CASCADE,
            name text NOT NULL,
            description text NOT NULL DEFAULT '',
            color text NOT NULL,
            UNIQUE (project_id, name)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_workflows (
            id text PRIMARY KEY,
            organization_id text NOT NULL REFERENCES queuedos_organizations(id) ON DELETE CASCADE,
            project_id text NOT NULL REFERENCES queuedos_projects(id) ON DELETE CASCADE,
            UNIQUE (project_id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_workflow_statuses (
            workflow_id text NOT NULL REFERENCES queuedos_workflows(id) ON DELETE CASCADE,
            id text NOT NULL,
            name text NOT NULL,
            category text NOT NULL,
            sort_order integer NOT NULL,
            PRIMARY KEY (workflow_id, id),
            UNIQUE (workflow_id, name),
            UNIQUE (workflow_id, sort_order)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_workflow_transitions (
            workflow_id text NOT NULL REFERENCES queuedos_workflows(id) ON DELETE CASCADE,
            id text NOT NULL,
            from_status_id text NOT NULL,
            to_status_id text NOT NULL,
            PRIMARY KEY (workflow_id, id),
            UNIQUE (workflow_id, from_status_id, to_status_id),
            FOREIGN KEY (workflow_id, from_status_id)
                REFERENCES queuedos_workflow_statuses(workflow_id, id) ON DELETE CASCADE,
            FOREIGN KEY (workflow_id, to_status_id)
                REFERENCES queuedos_workflow_statuses(workflow_id, id) ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_workflow_transition_roles (
            workflow_id text NOT NULL,
            transition_id text NOT NULL,
            role text NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
            sort_order integer NOT NULL,
            PRIMARY KEY (workflow_id, transition_id, role),
            FOREIGN KEY (workflow_id, transition_id)
                REFERENCES queuedos_workflow_transitions(workflow_id, id) ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_workflow_transition_required_fields (
            workflow_id text NOT NULL,
            transition_id text NOT NULL,
            field_name text NOT NULL,
            sort_order integer NOT NULL,
            PRIMARY KEY (workflow_id, transition_id, field_name),
            FOREIGN KEY (workflow_id, transition_id)
                REFERENCES queuedos_workflow_transitions(workflow_id, id) ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS queuedos_tickets (
            id text PRIMARY KEY,
            organization_id text NOT NULL REFERENCES queuedos_organizations(id) ON DELETE CASCADE,
            project_id text NOT NULL REFERENCES queuedos_projects(id) ON DELETE CASCADE,
            number integer NOT NULL CHECK (number > 0),
            key text NOT NULL,
            title text NOT NULL,
            description text NOT NULL DEFAULT '',
            status_id text NOT NULL,
            type_id text NOT NULL REFERENCES queuedos_ticket_types(id),
            priority text NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
            assignee_id text REFERENCES queuedos_users(id),
            reporter_id text NOT NULL REFERENCES queuedos_users(id),
            created_at text NOT NULL,
            updated_at text NOT NULL,
            UNIQUE (project_id, number),
            UNIQUE (organization_id, key)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_queuedos_users_organization ON queuedos_users(organization_id)",
        "CREATE INDEX IF NOT EXISTS idx_queuedos_projects_organization ON queuedos_projects(organization_id)",
        "CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_types_project ON queuedos_ticket_types(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_queuedos_workflows_project ON queuedos_workflows(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_project ON queuedos_tickets(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_status ON queuedos_tickets(status_id)",
        "CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_assignee ON queuedos_tickets(assignee_id)"
    )
}
