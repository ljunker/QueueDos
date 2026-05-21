package de.ljunker.queuedos.persistence

import java.time.Instant

class DatabaseSeeder(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun seedIfEmpty() {
        transactions.inTransaction {
            if (repositories.organizations.count() > 0) return@inTransaction
            val seed = seedData { Instant.now().toString() }
            seed.organizations.forEach(repositories.organizations::insert)
            seed.users.forEach(repositories.users::insert)
            seed.projects.forEach(repositories.projects::insert)
            seed.ticketTypes.forEach(repositories.ticketTypes::insert)
            seed.workflows.forEach(repositories.workflows::insert)
            seed.tickets.forEach(repositories.tickets::insert)
        }
    }
}
