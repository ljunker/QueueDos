package de.ljunker.queuedos.persistence

import de.ljunker.queuedos.domain.*

interface OrganizationRepository {
    fun count(): Int
    fun listById(organizationId: String): List<Organization>
    fun insert(organization: Organization)
}

interface UserRepository {
    fun findActiveByEmail(email: String): User?
    fun findActiveById(userId: String): User?
    fun findById(organizationId: String, userId: String): User?
    fun listByOrganization(organizationId: String): List<User>
    fun emailExists(organizationId: String, email: String): Boolean
    fun insert(user: User)
    fun update(user: User)
}

interface ProjectRepository {
    fun listByOrganization(organizationId: String): List<Project>
    fun findById(organizationId: String, projectId: String): Project?
    fun findByIdForUpdate(organizationId: String, projectId: String): Project?
    fun keyExists(organizationId: String, key: String): Boolean
    fun hasTickets(projectId: String): Boolean
    fun insert(project: Project)
    fun update(project: Project)
}

interface TicketTypeRepository {
    fun listByOrganization(organizationId: String): List<TicketType>
    fun findById(organizationId: String, typeId: String): TicketType?
    fun findForProject(organizationId: String, projectId: String, typeId: String): TicketType?
    fun nameExists(projectId: String, name: String, ignoredId: String? = null): Boolean
    fun isUsed(typeId: String): Boolean
    fun insert(ticketType: TicketType)
    fun update(ticketType: TicketType)
    fun delete(typeId: String)
}

interface WorkflowRepository {
    fun listByOrganization(organizationId: String): List<Workflow>
    fun findByProject(organizationId: String, projectId: String): Workflow?
    fun insert(workflow: Workflow)
    fun replace(workflow: Workflow)
}

interface TicketRepository {
    fun listByOrganization(organizationId: String): List<Ticket>
    fun listDeletedByOrganization(organizationId: String): List<Ticket>
    fun findById(organizationId: String, ticketId: String): Ticket?
    fun findDeletedById(organizationId: String, ticketId: String): Ticket?
    fun insert(ticket: Ticket)
    fun update(ticket: Ticket)
    fun setCommitment(ticketId: String, userId: String, committed: Boolean)
    fun comments(organizationId: String, ticketId: String? = null): List<TicketComment>
    fun changes(organizationId: String, ticketId: String? = null): List<TicketChange>
    fun insertComment(comment: TicketComment)
    fun insertChanges(changes: List<TicketChange>)
}

interface ActivityHookRepository {
    fun listByOrganization(organizationId: String): List<ActivityHook>
    fun listActive(organizationId: String, eventType: ActivityEventType): List<ActivityHook>
    fun findById(organizationId: String, hookId: String): ActivityHook?
    fun insert(hook: ActivityHook)
    fun update(hook: ActivityHook)
    fun delete(hookId: String)
}

interface SavedTicketFilterRepository {
    fun listForOwner(organizationId: String, ownerId: String): List<SavedTicketFilter>
    fun findForOwner(organizationId: String, ownerId: String, filterId: String): SavedTicketFilter?
    fun nameExists(
        organizationId: String,
        ownerId: String,
        view: String,
        projectId: String?,
        name: String,
        ignoredId: String? = null
    ): Boolean

    fun insert(filter: SavedTicketFilter)
    fun update(filter: SavedTicketFilter)
    fun delete(filterId: String)
}

data class QueueRepositories(
    val organizations: OrganizationRepository,
    val users: UserRepository,
    val projects: ProjectRepository,
    val ticketTypes: TicketTypeRepository,
    val workflows: WorkflowRepository,
    val tickets: TicketRepository,
    val savedTicketFilters: SavedTicketFilterRepository,
    val activityHooks: ActivityHookRepository
)
