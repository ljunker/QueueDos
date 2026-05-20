package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.domain.AppData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.DriverManager

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
    private val rowId: String = "default"
) : AppDataStorage {
    override fun load(): AppData? {
        ensureSchema()
        connection().use { connection ->
            connection.prepareStatement("SELECT state::text FROM queuedos_state WHERE id = ?").use { statement ->
                statement.setString(1, rowId)
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return json.decodeFromString(result.getString(1))
                }
            }
        }
    }

    override fun save(snapshot: AppData) {
        ensureSchema()
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO queuedos_state (id, state, updated_at)
                VALUES (?, ?::jsonb, now())
                ON CONFLICT (id)
                DO UPDATE SET state = EXCLUDED.state, updated_at = EXCLUDED.updated_at
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, rowId)
                statement.setString(2, json.encodeToString(snapshot))
                statement.executeUpdate()
            }
        }
    }

    private fun ensureSchema() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS queuedos_state (
                        id varchar(64) PRIMARY KEY,
                        state jsonb NOT NULL,
                        updated_at timestamptz NOT NULL DEFAULT now()
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun connection() =
        if (username.isNullOrBlank()) {
            DriverManager.getConnection(jdbcUrl)
        } else {
            DriverManager.getConnection(jdbcUrl, username, password ?: "")
        }
}
