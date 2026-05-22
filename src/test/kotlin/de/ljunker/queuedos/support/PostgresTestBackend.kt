package de.ljunker.queuedos.support

import de.ljunker.queuedos.application.QueueDosBackend
import de.ljunker.queuedos.application.SlackMessageSender
import de.ljunker.queuedos.config.appJson
import de.ljunker.queuedos.persistence.DriverManagerDataSource
import de.ljunker.queuedos.security.AuthTokenCodec
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import javax.sql.DataSource

object PostgresTestBackend {
    private val container = PostgreSQLContainer("postgres:17-alpine").apply {
        start()
    }

    fun create(
        tokenCodec: AuthTokenCodec = AuthTokenCodec("test-session-secret-that-is-long-enough"),
        slackSender: SlackMessageSender? = null
    ): TestBackend {
        val dataSource = freshDataSource()
        return TestBackend(
            dataSource,
            if (slackSender == null) {
                QueueDosBackend.create(dataSource, appJson, tokenCodec)
            } else {
                QueueDosBackend.create(dataSource, appJson, tokenCodec, slackSender = slackSender)
            }
        )
    }

    fun freshDataSource(): DataSource =
        DriverManagerDataSource(container.jdbcUrl, container.username, container.password).also(::reset)

    private fun reset(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("DROP SCHEMA public CASCADE")
                it.execute("CREATE SCHEMA public")
            }
        }
    }
}

data class TestBackend(
    val dataSource: DataSource,
    val backend: QueueDosBackend
) {
    fun sql(block: Connection.() -> Unit) {
        dataSource.connection.use(block)
    }
}
