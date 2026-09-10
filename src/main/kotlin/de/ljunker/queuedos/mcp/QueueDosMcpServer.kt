package de.ljunker.queuedos.mcp

import de.ljunker.queuedos.application.*
import de.ljunker.queuedos.domain.*
import de.ljunker.queuedos.validation.normalizeProjectKey
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.server.DnsRebindingProtection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import java.util.Locale

fun Application.configureMcp(services: QueueDosServices, settings: McpHttpSettings) {
    routing {
        route("/mcp") {
            install(ContentNegotiation) {
                json(McpJson)
            }
            install(DnsRebindingProtection) {
                allowedHosts = settings.allowedHosts
                allowedOrigins = settings.allowedOrigins
            }

            post {
                val authorization = call.request.header(HttpHeaders.Authorization)
                val rawToken = authorization?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                    ?.substringAfter(' ')
                    ?.trim()
                val actor = rawToken?.let(services.mcpAccessTokens::authenticate)
                    ?: throw UnauthorizedFailure("Valid MCP bearer token required.")
                val transport = StreamableHttpServerTransport(
                    StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
                ).also { it.setSessionIdGenerator(null) }
                val session = QueueDosMcpTools(services, actor).server().createSession(transport)
                try {
                    transport.handleRequest(null, call)
                } finally {
                    session.close()
                }
            }
            get {
                call.response.header(HttpHeaders.Allow, "POST")
                call.respondText(
                    McpJson.encodeToString(
                        JSONRPCError(
                            id = null,
                            error = RPCError(RPCError.ErrorCode.CONNECTION_CLOSED, "Method not allowed.")
                        )
                    ),
                    ContentType.Application.Json,
                    HttpStatusCode.MethodNotAllowed
                )
            }
            delete {
                call.response.header(HttpHeaders.Allow, "POST")
                call.respondText(
                    McpJson.encodeToString(
                        JSONRPCError(
                            id = null,
                            error = RPCError(RPCError.ErrorCode.CONNECTION_CLOSED, "Method not allowed.")
                        )
                    ),
                    ContentType.Application.Json,
                    HttpStatusCode.MethodNotAllowed
                )
            }
        }
    }
}

internal class QueueDosMcpTools(
    private val services: QueueDosServices,
    private val actor: User,
    private val transitionEvaluator: WorkflowTransitionEvaluator = WorkflowTransitionEvaluator()
) {
    fun server(): Server = Server(
        serverInfo = Implementation(
            name = "queuedos",
            version = QueueDosMcpTools::class.java.`package`.implementationVersion ?: "dev",
            title = "QueueDos"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
        ),
        instructions = "Use QueueDos tools to inspect and maintain tickets. Read the current ticket version before updating or transitioning it."
    ).apply {
        registerTool(
            name = "queuedos_list_projects",
            description = "List every QueueDos project the authenticated user can access.",
            schema = schema(),
            annotations = readOnlyAnnotations("List QueueDos projects")
        ) {
            val projects = snapshot().projects.map { project ->
                buildJsonObject {
                    put("key", project.key)
                    put("name", project.name)
                    put("description", project.description)
                    put("archived", project.archived)
                }
            }
            toolSuccess("Found ${projects.size} accessible projects.", buildJsonObject { put("projects", JsonArray(projects)) })
        }

        registerTool(
            name = "queuedos_get_project_context",
            description = "Get ticket types, workflow statuses and transitions, and valid assignees for one accessible project.",
            schema = schema(
                properties = mapOf("projectKey" to stringProperty("QueueDos project key, for example QDOS.")),
                required = listOf("projectKey")
            ),
            annotations = readOnlyAnnotations("Get QueueDos project context")
        ) { arguments ->
            val context = snapshot()
            val project = context.requireProject(arguments.requiredString("projectKey"))
            val workflow = context.workflow(project.id)
            val types = context.ticketTypes.filter { it.projectId == project.id }.map { type ->
                buildJsonObject {
                    put("name", type.name)
                    put("description", type.description)
                }
            }
            val statuses = workflow.statuses.sortedBy { it.sortOrder }.map { status ->
                buildJsonObject {
                    put("name", status.name)
                    put("category", status.category)
                    put("sortOrder", status.sortOrder)
                }
            }
            val statusNames = workflow.statuses.associate { it.id to it.name }
            val transitions = workflow.transitions.map { transition ->
                buildJsonObject {
                    putNullable("fromStatus", transition.fromStatusId?.let(statusNames::get))
                    put("toStatus", statusNames[transition.toStatusId] ?: transition.toStatusId)
                    put("global", transition.globalTransition)
                    put("allowBackward", transition.allowBackward)
                    put("allowedRoles", JsonArray(transition.allowedRoles.map { JsonPrimitive(it.name) }))
                    put(
                        "requiredFields",
                        JsonArray(transition.requiredFields.map { JsonPrimitive(it.toMcpFieldName()) })
                    )
                }
            }
            val members = context.assignableMembers(project.id).map { (user, membership) ->
                buildJsonObject {
                    put("email", user.email)
                    put("displayName", user.displayName)
                    put("role", membership.role.name)
                }
            }
            toolSuccess(
                "Loaded context for ${project.key}.",
                buildJsonObject {
                    put("project", buildJsonObject {
                        put("key", project.key)
                        put("name", project.name)
                        put("description", project.description)
                        put("archived", project.archived)
                    })
                    put("priorities", JsonArray(Priority.entries.map { JsonPrimitive(it.name) }))
                    put("ticketTypes", JsonArray(types))
                    put("statuses", JsonArray(statuses))
                    put("transitions", JsonArray(transitions))
                    put("assignees", JsonArray(members))
                }
            )
        }

        registerTool(
            name = "queuedos_search_tickets",
            description = "Search accessible, non-deleted QueueDos tickets. Status and type name filters require projectKey.",
            schema = schema(
                properties = mapOf(
                    "projectKey" to stringProperty("Optional project key."),
                    "query" to stringProperty("Optional free-text search."),
                    "statusName" to stringProperty("Optional exact status name; requires projectKey."),
                    "typeName" to stringProperty("Optional exact ticket type name; requires projectKey."),
                    "priority" to enumProperty(Priority.entries.map { it.name }),
                    "assigneeEmail" to stringProperty("Exact assignee email, or 'unassigned'."),
                    "label" to stringProperty("Exact label."),
                    "sort" to enumProperty(listOf("number", "title", "priority", "status", "updated")),
                    "offset" to integerProperty(
                        "Zero-based result offset.",
                        minimum = 0,
                        maximum = Int.MAX_VALUE
                    ),
                    "limit" to integerProperty("Page size from 1 to 100.", minimum = 1, maximum = 100)
                )
            ),
            annotations = readOnlyAnnotations("Search QueueDos tickets")
        ) { arguments ->
            val context = snapshot()
            val project = arguments.optionalString("projectKey")?.let(context::requireProject)
            if (project == null && (arguments.optionalString("statusName") != null || arguments.optionalString("typeName") != null)) {
                throw BadRequestFailure("projectKey is required when filtering by statusName or typeName.")
            }
            val statusId = arguments.optionalString("statusName")?.let { context.requireStatus(project!!, it).id }
            val typeId = arguments.optionalString("typeName")?.let { context.requireType(project!!, it).id }
            val assigneeId = arguments.optionalString("assigneeEmail")?.let {
                if (it.equals("unassigned", ignoreCase = true)) "unassigned" else context.requireUserByEmail(it).id
            }
            val priority = arguments.optionalString("priority")?.let(::priority)
            val tickets = services.queries.tickets(
                actor = actor,
                projectId = project?.id,
                query = arguments.optionalString("query"),
                statusId = statusId,
                typeId = typeId,
                priority = priority,
                assigneeId = assigneeId,
                label = arguments.optionalString("label"),
                sort = arguments.optionalString("sort")
            )
            val offset = arguments.optionalInt("offset") ?: 0
            val limit = arguments.optionalInt("limit") ?: 25
            val page = tickets.drop(offset).take(limit).map { context.ticketJson(it, includeDescription = false) }
            val nextOffset = (offset + page.size).takeIf { it < tickets.size }
            toolSuccess(
                "Found ${tickets.size} tickets; returned ${page.size}.",
                buildJsonObject {
                    put("tickets", JsonArray(page))
                    put("total", tickets.size)
                    put("offset", offset)
                    put("limit", limit)
                    putNullable("nextOffset", nextOffset)
                }
            )
        }

        registerTool(
            name = "queuedos_get_ticket",
            description = "Get one accessible QueueDos ticket with resolved names, its newest 50 comments, version, and currently allowed transitions.",
            schema = ticketKeySchema(),
            annotations = readOnlyAnnotations("Get QueueDos ticket")
        ) { arguments ->
            val context = snapshot()
            val ticket = context.requireTicket(arguments.requiredString("ticketKey"))
            val detail = services.queries.ticketDetail(actor, ticket.id)
            toolSuccess(
                "Loaded ${ticket.key} at version ${ticket.version}.",
                buildJsonObject {
                    put("ticket", context.ticketJson(detail.ticket, includeDescription = true))
                    put("comments", JsonArray(detail.comments.takeLast(50).map { context.commentJson(it) }))
                    put("availableTransitions", JsonArray(context.availableTransitions(detail.ticket).map(::JsonPrimitive)))
                }
            )
        }

        registerTool(
            name = "queuedos_create_ticket",
            description = "Create a QueueDos ticket in an accessible, non-archived project.",
            schema = schema(
                properties = ticketMutationProperties() + mapOf(
                    "projectKey" to stringProperty("QueueDos project key."),
                    "title" to stringProperty("Ticket title."),
                    "typeName" to stringProperty("Exact ticket type name."),
                    "statusName" to stringProperty("Optional initial workflow status name.")
                ),
                required = listOf("projectKey", "title", "typeName")
            ),
            annotations = additiveAnnotations("Create QueueDos ticket")
        ) { arguments ->
            val context = snapshot()
            val project = context.requireProject(arguments.requiredString("projectKey"))
            val type = context.requireType(project, arguments.requiredString("typeName"))
            val status = arguments.optionalString("statusName")?.let { context.requireStatus(project, it) }
            val assignee = arguments.optionalString("assigneeEmail")?.let { context.requireAssignableMember(project.id, it).first }
            val created = services.tickets.create(
                actor,
                CreateTicketCommand(
                    projectId = project.id,
                    title = arguments.requiredString("title"),
                    description = arguments.optionalString("description") ?: "",
                    typeId = type.id,
                    priority = arguments.optionalString("priority")?.let(::priority) ?: Priority.MEDIUM,
                    assigneeId = assignee?.id,
                    statusId = status?.id,
                    labels = arguments.optionalStringList("labels") ?: emptyList(),
                    dueDate = arguments.optionalString("dueDate"),
                    estimate = arguments.optionalInt("estimate")
                )
            )
            val updatedContext = snapshot()
            toolSuccess("Created ${created.key}.", buildJsonObject { put("ticket", updatedContext.ticketJson(created, true)) })
        }

        registerTool(
            name = "queuedos_update_ticket",
            description = "Update fields on a QueueDos ticket using optimistic concurrency. Omit fields to keep them unchanged; null clears assigneeEmail, dueDate, or estimate.",
            schema = schema(
                properties = ticketMutationProperties(nullableClearFields = true) + mapOf(
                    "ticketKey" to stringProperty("QueueDos ticket key."),
                    "expectedVersion" to integerProperty("Version returned by the latest read.", minimum = 1),
                    "title" to stringProperty("Replacement ticket title."),
                    "typeName" to stringProperty("Replacement ticket type name.")
                ),
                required = listOf("ticketKey", "expectedVersion")
            ),
            annotations = mutatingAnnotations("Update QueueDos ticket")
        ) { arguments ->
            val context = snapshot()
            val ticket = context.requireTicket(arguments.requiredString("ticketKey"))
            val project = context.project(ticket.projectId)
            val typeId = arguments.optionalString("typeName")?.let { context.requireType(project, it).id }
            val assigneePresent = arguments.containsKey("assigneeEmail")
            val assigneeId = arguments.nonNullString("assigneeEmail")?.let { context.requireAssignableMember(project.id, it).first.id }
            val dueDatePresent = arguments.containsKey("dueDate")
            val estimatePresent = arguments.containsKey("estimate")
            val updated = services.tickets.update(
                actor,
                ticket.id,
                UpdateTicketCommand(
                    expectedVersion = arguments.requiredLong("expectedVersion"),
                    title = arguments.optionalString("title"),
                    description = arguments.optionalString("description"),
                    typeId = typeId,
                    priority = arguments.optionalString("priority")?.let(::priority),
                    assigneeId = assigneeId,
                    clearAssignee = assigneePresent && arguments["assigneeEmail"] is JsonNull,
                    labels = arguments.optionalStringList("labels"),
                    dueDate = arguments.nonNullString("dueDate"),
                    estimate = arguments.nonNullInt("estimate"),
                    clearDueDate = dueDatePresent && arguments["dueDate"] is JsonNull,
                    clearEstimate = estimatePresent && arguments["estimate"] is JsonNull,
                    statusId = null
                )
            )
            val updatedContext = snapshot()
            toolSuccess("Updated ${updated.key} to version ${updated.version}.", buildJsonObject {
                put("ticket", updatedContext.ticketJson(updated, true))
            })
        }

        registerTool(
            name = "queuedos_transition_ticket",
            description = "Move a QueueDos ticket to another workflow status using optimistic concurrency.",
            schema = schema(
                properties = mapOf(
                    "ticketKey" to stringProperty("QueueDos ticket key."),
                    "expectedVersion" to integerProperty("Version returned by the latest read.", minimum = 1),
                    "toStatusName" to stringProperty("Exact target workflow status name.")
                ),
                required = listOf("ticketKey", "expectedVersion", "toStatusName")
            ),
            annotations = mutatingAnnotations("Transition QueueDos ticket")
        ) { arguments ->
            val context = snapshot()
            val ticket = context.requireTicket(arguments.requiredString("ticketKey"))
            val project = context.project(ticket.projectId)
            val status = context.requireStatus(project, arguments.requiredString("toStatusName"))
            val transitioned = services.tickets.transition(
                actor,
                ticket.id,
                TransitionTicketCommand(status.id, arguments.requiredLong("expectedVersion"))
            )
            val updatedContext = snapshot()
            toolSuccess("Moved ${transitioned.key} to ${status.name} at version ${transitioned.version}.", buildJsonObject {
                put("ticket", updatedContext.ticketJson(transitioned, true))
            })
        }

        registerTool(
            name = "queuedos_add_comment",
            description = "Add a comment to an accessible QueueDos ticket as the authenticated user.",
            schema = schema(
                properties = mapOf(
                    "ticketKey" to stringProperty("QueueDos ticket key."),
                    "body" to stringProperty("Comment text.")
                ),
                required = listOf("ticketKey", "body")
            ),
            annotations = additiveAnnotations("Comment on QueueDos ticket")
        ) { arguments ->
            val context = snapshot()
            val ticket = context.requireTicket(arguments.requiredString("ticketKey"))
            val comment = services.tickets.addComment(actor, ticket.id, AddTicketCommentCommand(arguments.requiredString("body")))
            toolSuccess("Added a comment to ${ticket.key}.", buildJsonObject { put("comment", context.commentJson(comment)) })
        }
    }

    private fun Server.registerTool(
        name: String,
        description: String,
        schema: ToolSchema,
        annotations: ToolAnnotations,
        handler: suspend (JsonObject) -> CallToolResult
    ) {
        addTool(name, description, schema, toolAnnotations = annotations) { request ->
            try {
                val arguments = request.arguments ?: buildJsonObject {}
                validateToolArguments(name, arguments, schema)
                handler(arguments)
            } catch (failure: QueueDosFailure) {
                toolFailure(failure)
            } catch (failure: IllegalArgumentException) {
                toolFailure(BadRequestFailure(failure.message ?: "Invalid tool arguments."))
            }
        }
    }

    private fun snapshot(): McpSnapshot = McpSnapshot(services.queries.bootstrap(actor), actor, transitionEvaluator)
}

private class McpSnapshot(
    private val data: BootstrapData,
    private val actor: User,
    private val transitionEvaluator: WorkflowTransitionEvaluator
) {
    val projects: List<Project> get() = data.projects
    val ticketTypes: List<TicketType> get() = data.ticketTypes

    fun requireProject(key: String): Project {
        val normalized = normalizeProjectKey(key)
        return projects.firstOrNull { it.key.equals(normalized, ignoreCase = true) }
            ?: throw NotFoundFailure("Project not found.")
    }

    fun project(projectId: String): Project = projects.firstOrNull { it.id == projectId }
        ?: throw NotFoundFailure("Project not found.")

    fun workflow(projectId: String): Workflow = data.workflows.firstOrNull { it.projectId == projectId }
        ?: throw NotFoundFailure("Workflow not found.")

    fun requireType(project: Project, name: String): TicketType =
        ticketTypes.singleOrNull { it.projectId == project.id && it.name.equals(name.trim(), ignoreCase = true) }
            ?: throw NotFoundFailure("Ticket type not found.")

    fun requireStatus(project: Project, name: String): WorkflowStatus =
        workflow(project.id).statuses.singleOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: throw NotFoundFailure("Workflow status not found.")

    fun requireUserByEmail(email: String): User = data.users.singleOrNull { it.email.equals(email.trim(), ignoreCase = true) }
        ?: throw NotFoundFailure("User not found.")

    fun assignableMembers(projectId: String): List<Pair<User, ProjectMembership>> =
        data.projectMemberships.filter { it.projectId == projectId }.mapNotNull { membership ->
            data.users.firstOrNull { it.id == membership.userId && it.active }?.let { it to membership }
        }.sortedBy { it.first.email.lowercase(Locale.ROOT) }

    fun requireAssignableMember(projectId: String, email: String): Pair<User, ProjectMembership> =
        assignableMembers(projectId).singleOrNull { it.first.email.equals(email.trim(), ignoreCase = true) }
            ?: throw NotFoundFailure("Assignee not found.")

    fun requireTicket(key: String): Ticket {
        val normalized = key.trim().uppercase(Locale.ROOT)
        if (normalized.isBlank()) throw BadRequestFailure("ticketKey is required.")
        return data.tickets.firstOrNull { it.key.equals(normalized, ignoreCase = true) }
            ?: throw NotFoundFailure("Ticket not found.")
    }

    fun ticketJson(ticket: Ticket, includeDescription: Boolean): JsonObject {
        val project = project(ticket.projectId)
        val workflow = workflow(project.id)
        val type = ticketTypes.firstOrNull { it.id == ticket.typeId }
        val status = workflow.statuses.firstOrNull { it.id == ticket.statusId }
        return buildJsonObject {
            put("key", ticket.key)
            put("projectKey", project.key)
            put("title", ticket.title)
            if (includeDescription) put("description", ticket.description)
            put("typeName", type?.name ?: ticket.typeId)
            put("statusName", status?.name ?: ticket.statusId)
            put("priority", ticket.priority.name)
            putNullable("assigneeEmail", user(ticket.assigneeId)?.email)
            put("labels", JsonArray(ticket.labels.map(::JsonPrimitive)))
            putNullable("dueDate", ticket.dueDate)
            putNullable("estimate", ticket.estimate)
            put("reporterEmail", user(ticket.reporterId)?.email ?: ticket.reporterId)
            put("createdAt", ticket.createdAt)
            put("updatedAt", ticket.updatedAt)
            put("version", ticket.version)
        }
    }

    fun commentJson(comment: TicketComment): JsonObject = buildJsonObject {
        val author = user(comment.authorId)
        put("authorEmail", author?.email ?: comment.authorId)
        put("authorName", author?.displayName ?: comment.authorId)
        put("body", comment.body)
        put("createdAt", comment.createdAt)
    }

    fun availableTransitions(ticket: Ticket): List<String> {
        val workflow = workflow(ticket.projectId)
        val role = if (actor.systemRole == SystemRole.SYSTEM_ADMIN) {
            ProjectRole.ADMIN
        } else {
            data.projectMemberships.firstOrNull { it.projectId == ticket.projectId && it.userId == actor.id }?.role
                ?: return emptyList()
        }
        return workflow.statuses.filter { status ->
            runCatching { transitionEvaluator.resolve(workflow, ticket, status.id, role) }
                .getOrNull() == WorkflowTransitionResolution.Allowed
        }.sortedBy { it.sortOrder }.map { it.name }
    }

    private fun user(userId: String?): User? = data.users.firstOrNull { it.id == userId }
}

private fun ticketKeySchema(): ToolSchema = schema(
    properties = mapOf("ticketKey" to stringProperty("QueueDos ticket key, for example QDOS-1.")),
    required = listOf("ticketKey")
)

private fun ticketMutationProperties(nullableClearFields: Boolean = false): Map<String, JsonObject> = mapOf(
    "description" to stringProperty("Ticket description."),
    "priority" to enumProperty(Priority.entries.map { it.name }),
    "assigneeEmail" to if (nullableClearFields) nullable(stringProperty("Exact project member email.")) else stringProperty("Exact project member email."),
    "labels" to arrayProperty("Complete replacement list of labels."),
    "dueDate" to if (nullableClearFields) nullable(stringProperty("Due date in YYYY-MM-DD format.")) else stringProperty("Due date in YYYY-MM-DD format."),
    "estimate" to if (nullableClearFields) nullable(integerProperty("Estimate from 0 to 999.", 0, 999)) else integerProperty("Estimate from 0 to 999.", 0, 999)
)

private fun schema(properties: Map<String, JsonObject> = emptyMap(), required: List<String> = emptyList()): ToolSchema =
    ToolSchema(
        properties = buildJsonObject { properties.forEach { (name, definition) -> put(name, definition) } },
        required = required.takeIf(List<String>::isNotEmpty)
    )

private fun stringProperty(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun integerProperty(description: String, minimum: Int? = null, maximum: Int? = null): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
}

private fun enumProperty(values: List<String>): JsonObject = buildJsonObject {
    put("type", "string")
    put("enum", JsonArray(values.map(::JsonPrimitive)))
}

private fun arrayProperty(description: String): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    put("items", buildJsonObject { put("type", "string") })
}

private fun nullable(property: JsonObject): JsonObject = buildJsonObject {
    property.forEach { (key, value) -> if (key != "type") put(key, value) }
    put("type", JsonArray(listOf(JsonPrimitive(property["type"]?.jsonPrimitive?.content ?: "string"), JsonPrimitive("null"))))
}

private fun readOnlyAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = true,
    destructiveHint = false,
    idempotentHint = true,
    openWorldHint = false
)

private fun additiveAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = false,
    destructiveHint = false,
    idempotentHint = false,
    openWorldHint = false
)

private fun mutatingAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = false,
    destructiveHint = true,
    idempotentHint = false,
    openWorldHint = false
)

private fun toolSuccess(message: String, data: JsonObject): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)), isError = false, structuredContent = data)

private fun toolFailure(failure: QueueDosFailure): CallToolResult {
    val error = buildJsonObject {
        put("kind", failure.kind.name)
        put("message", failure.message)
        failure.code?.let { put("code", it) }
        failure.currentVersion?.let { put("currentVersion", it) }
    }
    return CallToolResult(
        content = listOf(TextContent(error.toString())),
        isError = true,
        structuredContent = buildJsonObject { put("error", error) }
    )
}

private fun String.toMcpFieldName(): String = when (this) {
    "typeId" -> "typeName"
    "assigneeId" -> "assigneeEmail"
    "reporterId" -> "reporterEmail"
    else -> this
}

private fun validateToolArguments(toolName: String, arguments: JsonObject, schema: ToolSchema) {
    val properties = schema.properties ?: buildJsonObject {}
    val unknown = arguments.keys.filterNot(properties::containsKey).sorted()
    if (unknown.isNotEmpty()) {
        val label = if (unknown.size == 1) "argument" else "arguments"
        throw BadRequestFailure("Unknown $label for $toolName: ${unknown.joinToString()}.")
    }
    schema.required.orEmpty().forEach { name ->
        if (!arguments.containsKey(name)) throw BadRequestFailure("$name is required.")
    }
    arguments.forEach { (name, value) ->
        validateToolArgument(name, value, properties.getValue(name).jsonObject)
    }
}

private fun validateToolArgument(name: String, value: JsonElement, definition: JsonObject) {
    val types = when (val type = definition["type"]) {
        is JsonPrimitive -> setOf(type.content)
        is JsonArray -> type.map { it.jsonPrimitive.content }.toSet()
        else -> emptySet()
    }
    if (value is JsonNull) {
        if ("null" !in types) throw BadRequestFailure("$name must not be null.")
        return
    }

    when {
        "string" in types -> {
            if (value !is JsonPrimitive || !value.isString) throw BadRequestFailure("$name must be a string.")
        }

        "integer" in types -> {
            val number = (value as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.longOrNull
                ?: throw BadRequestFailure("$name must be an integer.")
            definition["minimum"]?.jsonPrimitive?.longOrNull?.let { minimum ->
                if (number < minimum) throw BadRequestFailure("$name must be at least $minimum.")
            }
            definition["maximum"]?.jsonPrimitive?.longOrNull?.let { maximum ->
                if (number > maximum) throw BadRequestFailure("$name must be at most $maximum.")
            }
        }

        "array" in types -> {
            val array = value as? JsonArray ?: throw BadRequestFailure("$name must be an array.")
            val itemDefinition = definition["items"]?.jsonObject
            if (itemDefinition != null) {
                array.forEachIndexed { index, item -> validateToolArgument("$name[$index]", item, itemDefinition) }
            }
        }

        else -> throw BadRequestFailure("$name has an unsupported schema.")
    }

    val allowedValues = definition["enum"] as? JsonArray
    if (allowedValues != null && value !in allowedValues) {
        throw BadRequestFailure(
            "$name must be one of: ${allowedValues.joinToString { it.jsonPrimitive.content }}."
        )
    }
}

private fun JsonObject.requiredString(name: String): String =
    nonNullString(name)?.takeIf(String::isNotBlank) ?: throw BadRequestFailure("$name is required.")

private fun JsonObject.optionalString(name: String): String? = nonNullString(name)?.trim()?.takeIf(String::isNotEmpty)

private fun JsonObject.nonNullString(name: String): String? = when (val value = this[name]) {
    null, JsonNull -> null
    is JsonPrimitive -> if (value.isString) value.content else throw BadRequestFailure("$name must be a string.")
    else -> throw BadRequestFailure("$name must be a string.")
}

private fun JsonObject.requiredLong(name: String): Long =
    (this[name] as? JsonPrimitive)?.longOrNull ?: throw BadRequestFailure("$name must be an integer.")

private fun JsonObject.optionalInt(name: String): Int? = when (val value = this[name]) {
    null, JsonNull -> null
    is JsonPrimitive -> value.intOrNull ?: throw BadRequestFailure("$name must be an integer.")
    else -> throw BadRequestFailure("$name must be an integer.")
}

private fun JsonObject.nonNullInt(name: String): Int? = optionalInt(name)

private fun JsonObject.optionalStringList(name: String): List<String>? = when (val value = this[name]) {
    null, JsonNull -> null
    is JsonArray -> value.map {
        val item = it as? JsonPrimitive
        if (item == null || !item.isString) throw BadRequestFailure("$name must contain only strings.")
        item.content
    }
    else -> throw BadRequestFailure("$name must be an array of strings.")
}

private fun priority(value: String): Priority = runCatching { Priority.valueOf(value.trim().uppercase(Locale.ROOT)) }
    .getOrElse { throw BadRequestFailure("Unknown priority.") }

private fun JsonObjectBuilder.putNullable(name: String, value: String?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun JsonObjectBuilder.putNullable(name: String, value: Int?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}
