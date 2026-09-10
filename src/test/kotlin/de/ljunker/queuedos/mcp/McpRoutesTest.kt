package de.ljunker.queuedos.mcp

import de.ljunker.queuedos.api.CreateMcpAccessTokenRequest
import de.ljunker.queuedos.api.CreatedMcpAccessTokenResponse
import de.ljunker.queuedos.api.LoginRequest
import de.ljunker.queuedos.api.LoginResponse
import de.ljunker.queuedos.api.McpAccessTokenResponse
import de.ljunker.queuedos.api.UpdateUserRequest
import de.ljunker.queuedos.application.CreateProjectCommand
import de.ljunker.queuedos.application.LoginCommand
import de.ljunker.queuedos.application.SaveWorkflowCommand
import de.ljunker.queuedos.application.UpdateProjectCommand
import de.ljunker.queuedos.domain.ProjectRole
import de.ljunker.queuedos.module
import de.ljunker.queuedos.support.PostgresTestBackend
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpRoutesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val settings = McpHttpSettings(
        allowedHosts = listOf("localhost", "queue.test"),
        allowedOrigins = listOf("http://localhost", "https://queue.test")
    )

    @Test
    fun personalTokenLifecycleIsOwnerScopedHashedAndImmediatelyRevocable() = testApplication {
        val fixture = PostgresTestBackend.create()
        application { module(fixture.backend, settings) }
        val client = apiClient()
        val adminSession = login(client, "admin@queuedos.local", "admin")
        val memberSession = login(client, "member@queuedos.local", "member")

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/mcp-tokens").status)
        val created = client.post("/api/mcp-tokens") {
            bearer(adminSession)
            jsonBody(CreateMcpAccessTokenRequest("Laptop assistant"))
        }.body<CreatedMcpAccessTokenResponse>()

        assertTrue(created.token.matches(Regex("^qdos_mcp_[A-Za-z0-9_-]{43}$")))
        assertEquals("Laptop assistant", created.accessToken.name)
        assertEquals(null, created.accessToken.lastUsedAt)
        assertEquals(
            Duration.ofDays(90),
            Duration.between(Instant.parse(created.accessToken.createdAt), Instant.parse(created.accessToken.expiresAt))
        )
        fixture.sql {
            prepareStatement("SELECT token_hash, token_hint FROM queuedos_mcp_tokens WHERE id = ?").use { statement ->
                statement.setString(1, created.accessToken.id)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    val storedHash = result.getString("token_hash")
                    assertEquals(64, storedHash.length)
                    assertNotEquals(created.token, storedHash)
                    assertFalse(result.getString("token_hint").contains(created.token))
                }
            }
        }

        val listedResponse = client.get("/api/mcp-tokens") { bearer(adminSession) }
        val listedJson = json.parseToJsonElement(listedResponse.bodyAsText()).jsonArray.single().jsonObject
        assertEquals(JsonNull, listedJson["lastUsedAt"])
        assertFalse(listedJson.containsKey("token"))
        assertTrue(client.get("/api/mcp-tokens") { bearer(memberSession) }.body<List<McpAccessTokenResponse>>().isEmpty())
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/api/mcp-tokens/${created.accessToken.id}") { bearer(memberSession) }.status
        )

        assertEquals(HttpStatusCode.OK, initialize(client, created.token).status)
        val used = client.get("/api/mcp-tokens") { bearer(adminSession) }.body<List<McpAccessTokenResponse>>().single()
        assertNotNull(used.lastUsedAt)

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/api/mcp-tokens/${created.accessToken.id}") { bearer(adminSession) }.status
        )
        val revoked = initialize(client, created.token)
        assertEquals(HttpStatusCode.Unauthorized, revoked.status)
        assertEquals("Bearer", revoked.headers[HttpHeaders.WWWAuthenticate])

        val expiring = client.post("/api/mcp-tokens") {
            bearer(adminSession)
            jsonBody(CreateMcpAccessTokenRequest("Expired assistant"))
        }.body<CreatedMcpAccessTokenResponse>()
        fixture.sql {
            prepareStatement("UPDATE queuedos_mcp_tokens SET expires_at = '2000-01-01T00:00:00Z' WHERE id = ?").use {
                it.setString(1, expiring.accessToken.id)
                it.executeUpdate()
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, initialize(client, expiring.token).status)
        assertTrue(client.get("/api/mcp-tokens") { bearer(adminSession) }.body<List<McpAccessTokenResponse>>().isEmpty())

        val memberToken = client.post("/api/mcp-tokens") {
            bearer(memberSession)
            jsonBody(CreateMcpAccessTokenRequest("Member assistant"))
        }.body<CreatedMcpAccessTokenResponse>()
        client.put("/api/users/user-member") {
            bearer(adminSession)
            jsonBody(UpdateUserRequest(active = false))
        }
        assertEquals(HttpStatusCode.Unauthorized, initialize(client, memberToken.token).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/mcp-tokens") { bearer(memberSession) }.status)
    }

    @Test
    fun transportAuthenticatesInitializesListsToolsAndValidatesHostAndOrigin() = testApplication {
        val fixture = PostgresTestBackend.create()
        val admin = fixture.backend.services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
        val token = fixture.backend.services.mcpAccessTokens.create(admin, "Transport test").token
        application { module(fixture.backend, settings) }
        val client = apiClient()

        val missing = initialize(client, null)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("Bearer", missing.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(HttpStatusCode.Unauthorized, initialize(client, "qdos_mcp_invalid").status)
        assertEquals(HttpStatusCode.Forbidden, initialize(client, token, host = "evil.example").status)
        assertEquals(HttpStatusCode.Forbidden, initialize(client, token, origin = "https://evil.example").status)

        val initialized = initialize(client, token)
        assertEquals(HttpStatusCode.OK, initialized.status)
        val initializeResult = responseResult(initialized)
        assertEquals("queuedos", initializeResult["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertNotNull(initializeResult["capabilities"]?.jsonObject?.get("tools"))

        val tools = responseResult(mcpRequest(client, token, "tools/list"))["tools"]!!.jsonArray
        assertEquals(
            setOf(
                "queuedos_list_projects",
                "queuedos_get_project_context",
                "queuedos_search_tickets",
                "queuedos_get_ticket",
                "queuedos_create_ticket",
                "queuedos_update_ticket",
                "queuedos_transition_ticket",
                "queuedos_add_comment"
            ),
            tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        )
        assertEquals(HttpStatusCode.MethodNotAllowed, client.get("/mcp") { header(HttpHeaders.Host, "localhost") }.status)
        assertEquals(HttpStatusCode.MethodNotAllowed, client.delete("/mcp") { header(HttpHeaders.Host, "localhost") }.status)
    }

    @Test
    fun projectContextUsesMcpNamesForRequiredTransitionFields() = testApplication {
        val fixture = PostgresTestBackend.create()
        val services = fixture.backend.services
        val admin = services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
        val project = services.projects.create(admin, CreateProjectCommand("FIELDS", "Field mapping", "MCP field names"))
        val workflow = services.queries.bootstrap(admin).workflows.first { it.projectId == project.id }
        services.workflows.save(
            admin,
            project.id,
            SaveWorkflowCommand(
                statuses = workflow.statuses,
                transitions = workflow.transitions.mapIndexed { index, transition ->
                    if (index == 0) {
                        transition.copy(requiredFields = listOf("typeId", "assigneeId", "dueDate"))
                    } else {
                        transition
                    }
                }
            )
        )
        val token = services.mcpAccessTokens.create(admin, "Field mapping test").token
        application { module(fixture.backend, settings) }
        val client = apiClient()

        val context = structured(
            callTool(client, token, "queuedos_get_project_context", obj("projectKey" to JsonPrimitive("fields")))
        )
        val requiredFields = context["transitions"]!!.jsonArray
            .map { it.jsonObject["requiredFields"]!!.jsonArray }
            .first { it.isNotEmpty() }
            .map { it.jsonPrimitive.content }

        assertEquals(listOf("typeName", "assigneeEmail", "dueDate"), requiredFields)
    }

    @Test
    fun toolArgumentsRejectUnknownFieldsDisallowedNullsEnumsAndOutOfRangeValues() = testApplication {
        val fixture = PostgresTestBackend.create()
        val services = fixture.backend.services
        val admin = services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
        val project = services.projects.create(admin, CreateProjectCommand("STRICT", "Strict tools", "Argument validation"))
        val token = services.mcpAccessTokens.create(admin, "Strict argument test").token
        application { module(fixture.backend, settings) }
        val client = apiClient()
        val context = structured(
            callTool(client, token, "queuedos_get_project_context", obj("projectKey" to JsonPrimitive(project.key)))
        )
        val typeName = context["ticketTypes"]!!.jsonArray.first().jsonObject["name"]!!.jsonPrimitive.content

        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_create_ticket",
                obj(
                    "projectKey" to JsonPrimitive(project.key),
                    "title" to JsonNull,
                    "typeName" to JsonPrimitive(typeName)
                )
            ),
            "title must not be null"
        )
        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_create_ticket",
                obj(
                    "projectKey" to JsonPrimitive(project.key),
                    "title" to JsonPrimitive("Unknown argument"),
                    "typeName" to JsonPrimitive(typeName),
                    "statusId" to JsonPrimitive("status-todo")
                )
            ),
            "Unknown argument"
        )

        val created = structured(
            callTool(
                client,
                token,
                "queuedos_create_ticket",
                obj(
                    "projectKey" to JsonPrimitive(project.key),
                    "title" to JsonPrimitive("Strict ticket"),
                    "typeName" to JsonPrimitive(typeName)
                )
            )
        )["ticket"]!!.jsonObject
        val ticketKey = created["key"]!!.jsonPrimitive.content

        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_update_ticket",
                obj(
                    "ticketKey" to JsonPrimitive(ticketKey),
                    "expectedVersion" to JsonPrimitive(1),
                    "title" to JsonNull
                )
            ),
            "title must not be null"
        )
        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_update_ticket",
                obj(
                    "ticketKey" to JsonPrimitive(ticketKey),
                    "expectedVersion" to JsonPrimitive(1),
                    "labels" to JsonNull
                )
            ),
            "labels must not be null"
        )
        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_update_ticket",
                obj(
                    "ticketKey" to JsonPrimitive(ticketKey),
                    "expectedVersion" to JsonPrimitive(1),
                    "estimate" to JsonPrimitive(1000)
                )
            ),
            "estimate must be at most 999"
        )
        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_search_tickets",
                obj("sort" to JsonPrimitive("newest"))
            ),
            "sort must be one of"
        )
        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_search_tickets",
                obj("limit" to JsonPrimitive(101))
            ),
            "limit must be at most 100"
        )
        assertBadRequest(
            callTool(
                client,
                token,
                "queuedos_search_tickets",
                obj("priority" to JsonPrimitive("URGENT"))
            ),
            "priority must be one of"
        )

        val unchanged = structured(
            callTool(client, token, "queuedos_get_ticket", obj("ticketKey" to JsonPrimitive(ticketKey)))
        )["ticket"]!!.jsonObject
        assertEquals(1, unchanged["version"]!!.jsonPrimitive.int)
    }

    @Test
    fun toolsUseProjectRolesNamesKeysWorkflowServicesAndOptimisticVersions() = testApplication {
        val fixture = PostgresTestBackend.create()
        val services = fixture.backend.services
        val admin = services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
        val member = services.auth.login(LoginCommand("member@queuedos.local", "member")).user
        val visible = services.projects.create(admin, CreateProjectCommand("MCP", "MCP project", "Visible to member"))
        services.projects.create(admin, CreateProjectCommand("HIDE", "Hidden project", "Not visible to member"))
        services.projectMemberships.save(admin, visible.id, member.id, ProjectRole.MEMBER)
        val token = services.mcpAccessTokens.create(member, "Tool integration").token
        application { module(fixture.backend, settings) }
        val client = apiClient()

        val projects = structured(callTool(client, token, "queuedos_list_projects"))["projects"]!!.jsonArray
        val projectKeys = projects.map { it.jsonObject["key"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("MCP", "QDOS"), projectKeys)
        assertFalse("HIDE" in projectKeys)
        val hidden = callTool(client, token, "queuedos_get_project_context", obj("projectKey" to JsonPrimitive("hide")))
        assertTrue(hidden["isError"]!!.jsonPrimitive.content.toBoolean())

        val context = structured(
            callTool(client, token, "queuedos_get_project_context", obj("projectKey" to JsonPrimitive("mcp")))
        )
        val typeName = context["ticketTypes"]!!.jsonArray.first().jsonObject["name"]!!.jsonPrimitive.content
        assertTrue(context["assignees"]!!.jsonArray.any {
            it.jsonObject["email"]!!.jsonPrimitive.content == "member@queuedos.local"
        })

        val unknownType = callTool(
            client,
            token,
            "queuedos_create_ticket",
            obj(
                "projectKey" to JsonPrimitive("MCP"),
                "title" to JsonPrimitive("Must not be created"),
                "typeName" to JsonPrimitive("Unknown type")
            )
        )
        assertTrue(unknownType["isError"]!!.jsonPrimitive.content.toBoolean())

        val created = structured(
            callTool(
                client,
                token,
                "queuedos_create_ticket",
                obj(
                    "projectKey" to JsonPrimitive("mcp"),
                    "title" to JsonPrimitive("Created through MCP"),
                    "typeName" to JsonPrimitive(typeName.lowercase()),
                    "assigneeEmail" to JsonPrimitive("MEMBER@QUEUEDOS.LOCAL"),
                    "labels" to JsonArray(listOf(JsonPrimitive("MCP")))
                )
            )
        )["ticket"]!!.jsonObject
        val ticketKey = created["key"]!!.jsonPrimitive.content
        assertEquals(1, created["version"]!!.jsonPrimitive.int)
        assertEquals("MEDIUM", created["priority"]!!.jsonPrimitive.content)

        val searched = structured(
            callTool(
                client,
                token,
                "queuedos_search_tickets",
                obj(
                    "projectKey" to JsonPrimitive("MCP"),
                    "query" to JsonPrimitive("through"),
                    "statusName" to created["statusName"]!!,
                    "typeName" to JsonPrimitive(typeName.lowercase()),
                    "priority" to JsonPrimitive("MEDIUM"),
                    "assigneeEmail" to JsonPrimitive("member@queuedos.local"),
                    "label" to JsonPrimitive("mcp"),
                    "sort" to JsonPrimitive("updated"),
                    "offset" to JsonPrimitive(0),
                    "limit" to JsonPrimitive(25)
                )
            )
        )
        assertEquals(1, searched["total"]!!.jsonPrimitive.int)

        val updated = structured(
            callTool(
                client,
                token,
                "queuedos_update_ticket",
                obj(
                    "ticketKey" to JsonPrimitive(ticketKey.lowercase()),
                    "expectedVersion" to JsonPrimitive(1),
                    "title" to JsonPrimitive("Updated through MCP"),
                    "labels" to JsonArray(listOf(JsonPrimitive("MCP"), JsonPrimitive("Agent"))),
                    "dueDate" to JsonPrimitive("2026-12-31"),
                    "estimate" to JsonPrimitive(8)
                )
            )
        )["ticket"]!!.jsonObject
        assertEquals(2, updated["version"]!!.jsonPrimitive.int)
        assertEquals(listOf("mcp", "agent"), updated["labels"]!!.jsonArray.map { it.jsonPrimitive.content })

        val cleared = structured(
            callTool(
                client,
                token,
                "queuedos_update_ticket",
                obj(
                    "ticketKey" to JsonPrimitive(ticketKey),
                    "expectedVersion" to JsonPrimitive(2),
                    "assigneeEmail" to JsonNull,
                    "labels" to JsonArray(emptyList()),
                    "dueDate" to JsonNull,
                    "estimate" to JsonNull
                )
            )
        )["ticket"]!!.jsonObject
        assertEquals(3, cleared["version"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, cleared["assigneeEmail"])
        assertEquals(JsonArray(emptyList()), cleared["labels"])
        assertEquals(JsonNull, cleared["dueDate"])
        assertEquals(JsonNull, cleared["estimate"])

        val conflict = callTool(
            client,
            token,
            "queuedos_update_ticket",
            obj(
                "ticketKey" to JsonPrimitive(ticketKey),
                "expectedVersion" to JsonPrimitive(1),
                "title" to JsonPrimitive("Stale update")
            )
        )
        assertTrue(conflict["isError"]!!.jsonPrimitive.content.toBoolean())
        val conflictError = conflict["structuredContent"]!!.jsonObject["error"]!!.jsonObject
        assertEquals("TICKET_VERSION_CONFLICT", conflictError["code"]!!.jsonPrimitive.content)
        assertEquals(3L, conflictError["currentVersion"]!!.jsonPrimitive.long)

        val detail = structured(
            callTool(client, token, "queuedos_get_ticket", obj("ticketKey" to JsonPrimitive(ticketKey.lowercase())))
        )
        val transitionName = detail["availableTransitions"]!!.jsonArray.first().jsonPrimitive.content
        val transitioned = structured(
            callTool(
                client,
                token,
                "queuedos_transition_ticket",
                obj(
                    "ticketKey" to JsonPrimitive(ticketKey),
                    "expectedVersion" to JsonPrimitive(3),
                    "toStatusName" to JsonPrimitive(transitionName.lowercase())
                )
            )
        )["ticket"]!!.jsonObject
        assertEquals(4, transitioned["version"]!!.jsonPrimitive.int)
        assertEquals(transitionName, transitioned["statusName"]!!.jsonPrimitive.content)

        val comment = structured(
            callTool(
                client,
                token,
                "queuedos_add_comment",
                obj("ticketKey" to JsonPrimitive(ticketKey), "body" to JsonPrimitive("Agent note"))
            )
        )["comment"]!!.jsonObject
        assertEquals("member@queuedos.local", comment["authorEmail"]!!.jsonPrimitive.content)
        val withComment = structured(
            callTool(client, token, "queuedos_get_ticket", obj("ticketKey" to JsonPrimitive(ticketKey)))
        )
        assertTrue(withComment["comments"]!!.jsonArray.any { it.jsonObject["body"]!!.jsonPrimitive.content == "Agent note" })

        services.projects.update(admin, visible.id, UpdateProjectCommand(null, null, null, true))
        val archivedFailure = callTool(
            client,
            token,
            "queuedos_create_ticket",
            obj(
                "projectKey" to JsonPrimitive("MCP"),
                "title" to JsonPrimitive("Must not be created"),
                "typeName" to JsonPrimitive(typeName)
            )
        )
        assertTrue(archivedFailure["isError"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            "CONFLICT",
            archivedFailure["structuredContent"]!!.jsonObject["error"]!!.jsonObject["kind"]!!.jsonPrimitive.content
        )

        services.projectMemberships.delete(admin, visible.id, member.id)
        val afterRoleRemoval = structured(callTool(client, token, "queuedos_list_projects"))["projects"]!!.jsonArray
        assertFalse(afterRoleRemoval.any { it.jsonObject["key"]!!.jsonPrimitive.content == "MCP" })
    }

    private fun ApplicationTestBuilder.apiClient(): HttpClient = createClient {
        install(ClientContentNegotiation) { json(json) }
    }

    private suspend fun login(client: HttpClient, email: String, password: String): String =
        client.post("/api/auth/login") { jsonBody(LoginRequest(email, password)) }.body<LoginResponse>().token

    private suspend fun initialize(
        client: HttpClient,
        token: String?,
        host: String? = null,
        origin: String? = null
    ): HttpResponse = mcpRequest(
        client,
        token,
        "initialize",
        buildJsonObject {
            put("protocolVersion", "2025-11-25")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", "QueueDos test")
                put("version", "1")
            })
        },
        host,
        origin,
        includeProtocolHeader = false
    )

    private suspend fun callTool(client: HttpClient, token: String, name: String, arguments: JsonObject = obj()): JsonObject =
        responseResult(
            mcpRequest(
                client,
                token,
                "tools/call",
                buildJsonObject {
                    put("name", name)
                    put("arguments", arguments)
                }
            )
        )

    private suspend fun mcpRequest(
        client: HttpClient,
        token: String?,
        method: String,
        params: JsonObject = buildJsonObject {},
        host: String? = null,
        origin: String? = null,
        includeProtocolHeader: Boolean = true
    ): HttpResponse = client.post("/mcp") {
        if (token != null) bearer(token)
        header(HttpHeaders.Host, host ?: "localhost")
        origin?.let { header(HttpHeaders.Origin, it) }
        if (includeProtocolHeader) header("MCP-Protocol-Version", "2025-11-25")
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Accept, "application/json, text/event-stream")
        setBody(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", method)
                put("params", params)
            }.toString()
        )
    }

    private suspend fun responseResult(response: HttpResponse): JsonObject {
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.parseToJsonElement(response.bodyAsText()).jsonObject["result"]!!.jsonObject
    }

    private fun structured(callResult: JsonObject): JsonObject {
        assertEquals(false, callResult["isError"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        return callResult["structuredContent"]!!.jsonObject
    }

    private fun assertBadRequest(callResult: JsonObject, messagePart: String) {
        assertTrue(callResult["isError"]!!.jsonPrimitive.content.toBoolean())
        val error = callResult["structuredContent"]!!.jsonObject["error"]!!.jsonObject
        assertEquals("BAD_REQUEST", error["kind"]!!.jsonPrimitive.content)
        assertTrue(error["message"]!!.jsonPrimitive.content.contains(messagePart))
    }

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun obj(vararg entries: Pair<String, kotlinx.serialization.json.JsonElement>): JsonObject =
        JsonObject(mapOf(*entries))
}
