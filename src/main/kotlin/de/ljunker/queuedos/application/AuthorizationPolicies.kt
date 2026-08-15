package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.ProjectRole
import de.ljunker.queuedos.domain.SystemRole
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.persistence.QueueRepositories

object AuthorizationPolicies {
    fun isSystemAdmin(actor: User): Boolean = actor.systemRole == SystemRole.SYSTEM_ADMIN

    fun requireSystemAdmin(actor: User) {
        if (!isSystemAdmin(actor)) {
            throw ForbiddenFailure("System administrator role required.")
        }
    }

    fun projectRole(actor: User, projectId: String, repositories: QueueRepositories): ProjectRole? =
        if (isSystemAdmin(actor)) ProjectRole.ADMIN
        else repositories.projectMemberships.find(actor.organizationId, projectId, actor.id)?.role

    fun requireProjectAccess(actor: User, projectId: String, repositories: QueueRepositories): ProjectRole =
        projectRole(actor, projectId, repositories) ?: throw NotFoundFailure("Project not found.")

    fun requireProjectAdmin(actor: User, projectId: String, repositories: QueueRepositories) {
        val role = requireProjectAccess(actor, projectId, repositories)
        if (role != ProjectRole.ADMIN) throw ForbiddenFailure("Project administrator role required.")
    }
}
