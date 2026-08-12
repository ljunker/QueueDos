package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.domain.TicketRevision
import de.ljunker.queuedos.domain.TicketRevisionAction
import java.time.Instant
import java.util.UUID

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
            seed.tickets.forEach { ticket ->
                repositories.tickets.insert(ticket)
                repositories.tickets.insertRevision(
                    TicketRevision(
                        id = "revision-${UUID.randomUUID()}",
                        organizationId = ticket.organizationId,
                        ticketId = ticket.id,
                        version = ticket.version,
                        actorId = ticket.reporterId,
                        action = TicketRevisionAction.CREATED,
                        snapshot = ticket,
                        createdAt = ticket.createdAt
                    )
                )
            }
        }
    }
}
