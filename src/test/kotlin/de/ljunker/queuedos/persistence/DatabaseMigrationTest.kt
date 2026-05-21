package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.application.LoginCommand
import de.ljunker.queuedos.application.QueueDosBackend
import de.ljunker.queuedos.config.appJson
import de.ljunker.queuedos.security.AuthTokenCodec
import de.ljunker.queuedos.support.PostgresTestBackend
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseMigrationTest {
    @Test
    fun freshDatabaseMigratesAndSeeds() {
        val dataSource = PostgresTestBackend.freshDataSource()

        val backend = backend(dataSource)
        val admin = backend.services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user

        assertEquals("user-admin", admin.id)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM flyway_schema_history").use {
                    assertTrue(it.next())
                    assertTrue(it.getInt(1) >= 2)
                }
            }
        }
    }

    @Test
    fun legacyPostgresSnapshotIsImportedOnce() {
        val dataSource = PostgresTestBackend.freshDataSource()
        val legacy = seedData { "2026-05-21T00:00:00Z" }
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE queuedos_state (id text PRIMARY KEY, state jsonb NOT NULL)")
            }
            connection.prepareStatement("INSERT INTO queuedos_state (id, state) VALUES (?, ?::jsonb)").use {
                it.setString(1, "default")
                it.setString(2, appJson.encodeToString(legacy))
                it.executeUpdate()
            }
        }

        val backend = backend(dataSource)
        val admin = backend.services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
        val bootstrap = backend.services.queries.bootstrap(admin)

        assertEquals("QueueDos", bootstrap.projects.single().name)
        assertEquals(listOf("QDOS-1", "QDOS-2", "QDOS-3"), bootstrap.tickets.map { it.key })
    }

    @Test
    fun existingRelationalTablesAreBaselinedWithoutLosingData() {
        val dataSource = PostgresTestBackend.freshDataSource()
        val firstBackend = backend(dataSource)
        val admin = firstBackend.services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user
        val before = firstBackend.services.queries.bootstrap(admin).tickets.map { it.key }
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("DROP TABLE flyway_schema_history") }
        }

        val secondBackend = backend(dataSource)
        val nextAdmin = secondBackend.services.auth.login(LoginCommand("admin@queuedos.local", "admin")).user

        assertEquals(before, secondBackend.services.queries.bootstrap(nextAdmin).tickets.map { it.key })
    }

    private fun backend(dataSource: javax.sql.DataSource): QueueDosBackend =
        QueueDosBackend.create(dataSource, appJson, AuthTokenCodec("migration-test-session-secret"))
}
