package de.ljunker.queuedos.application

import de.ljunker.queuedos.persistence.DatabaseMigrator
import de.ljunker.queuedos.persistence.DatabaseSeeder
import de.ljunker.queuedos.persistence.JdbcQueueRepositories
import de.ljunker.queuedos.persistence.JdbcTransactionRunner
import de.ljunker.queuedos.security.AuthTokenCodec
import kotlinx.serialization.json.Json
import javax.sql.DataSource

class QueueDosBackend private constructor(
    val services: QueueDosServices
) {
    companion object {
        fun create(
            dataSource: DataSource,
            json: Json,
            tokenCodec: AuthTokenCodec,
            migrate: Boolean = true,
            seed: Boolean = true
        ): QueueDosBackend {
            if (migrate) DatabaseMigrator.migrate(dataSource)
            val transactions = JdbcTransactionRunner(dataSource)
            val repositories = JdbcQueueRepositories(transactions, json).repositories()
            if (seed) DatabaseSeeder(transactions, repositories).seedIfEmpty()
            return QueueDosBackend(
                QueueDosServices(
                    auth = AuthenticationService(transactions, repositories, tokenCodec),
                    queries = WorkspaceQueryService(transactions, repositories),
                    projects = ProjectService(transactions, repositories),
                    users = UserService(transactions, repositories),
                    ticketTypes = TicketTypeService(transactions, repositories),
                    workflows = WorkflowService(transactions, repositories),
                    tickets = TicketService(transactions, repositories),
                    savedTicketFilters = SavedTicketFilterService(transactions, repositories)
                )
            )
        }
    }
}
