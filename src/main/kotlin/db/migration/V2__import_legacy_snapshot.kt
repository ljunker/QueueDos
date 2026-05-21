package db.migration

import de.ljunker.queuedos.domain.AppData
import de.ljunker.queuedos.persistence.LegacySnapshotImporter
import kotlinx.serialization.json.Json
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
        LegacySnapshotImporter.insert(connection, json.decodeFromString<AppData>(state), json)
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
