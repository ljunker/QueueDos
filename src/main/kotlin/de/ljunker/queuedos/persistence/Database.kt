package de.ljunker.queuedos.persistence

import org.flywaydb.core.Flyway
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource

interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

class JdbcTransactionRunner(
    private val dataSource: DataSource
) : TransactionRunner {
    private val current = ThreadLocal<Connection?>()

    override fun <T> inTransaction(block: () -> T): T {
        current.get()?.let { return block() }

        dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            current.set(connection)
            try {
                val result = block()
                connection.commit()
                return result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                current.remove()
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    internal fun connection(): Connection =
        current.get() ?: error("Repository access must run inside a database transaction.")
}

class DriverManagerDataSource(
    private val jdbcUrl: String,
    private val username: String?,
    private val password: String?
) : DataSource {
    override fun getConnection(): Connection =
        if (username.isNullOrBlank()) DriverManager.getConnection(jdbcUrl) else DriverManager.getConnection(
            jdbcUrl,
            username,
            password ?: ""
        )

    override fun getConnection(username: String?, password: String?): Connection =
        DriverManager.getConnection(jdbcUrl, username, password)

    override fun getLogWriter(): PrintWriter? = DriverManager.getLogWriter()

    override fun setLogWriter(out: PrintWriter?) {
        DriverManager.setLogWriter(out)
    }

    override fun setLoginTimeout(seconds: Int) {
        DriverManager.setLoginTimeout(seconds)
    }

    override fun getLoginTimeout(): Int = DriverManager.getLoginTimeout()

    override fun getParentLogger(): Logger = Logger.getLogger("de.ljunker.queuedos.persistence")

    override fun <T> unwrap(iface: Class<T>?): T =
        throw SQLException("DriverManagerDataSource does not unwrap ${iface?.name}.")

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}

object DatabaseMigrator {
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()
    }
}
