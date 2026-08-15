package db.migration

import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.persistence.LegacySnapshotImporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V2__import_legacy_snapshot : BaseJavaMigration() {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun migrate(context: Context) {
        val connection = context.connection
        if (connection.organizationsExist() || !connection.legacyTableExists()) return
        val state =
            connection.prepareStatement("SELECT state::text FROM queuedos_state ORDER BY id LIMIT 1").use { statement ->
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else null
                }
            } ?: return
        val root = json.parseToJsonElement(state).jsonObject
        val migratedUsers = root["users"]?.jsonArray?.map { element ->
            val user = element.jsonObject
            val legacyRole = user["role"]?.jsonPrimitive?.content
            val currentRole = user["systemRole"]?.jsonPrimitive?.content
            JsonObject(
                user.filterKeys { it != "role" && it != "systemRole" } +
                    ("systemRole" to JsonPrimitive(
                        currentRole ?: if (legacyRole == "ADMIN") "SYSTEM_ADMIN" else "USER"
                    ))
            )
        } ?: emptyList()
        val compatibleState = JsonObject(root + ("users" to JsonArray(migratedUsers)))
        LegacySnapshotImporter.insert(connection, json.decodeFromJsonElement<AppData>(compatibleState), json)
    }

    private fun java.sql.Connection.organizationsExist(): Boolean =
        createStatement().use { statement ->
            statement.executeQuery("SELECT EXISTS (SELECT 1 FROM queuedos_organizations) AS exists").use {
                it.next()
                it.getBoolean("exists")
            }
        }

    private fun java.sql.Connection.legacyTableExists(): Boolean =
        createStatement().use { statement ->
            statement.executeQuery("SELECT to_regclass('public.queuedos_state') IS NOT NULL AS exists").use {
                it.next()
                it.getBoolean("exists")
            }
        }
}
