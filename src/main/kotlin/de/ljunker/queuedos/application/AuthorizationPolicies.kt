package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.Role
import de.ljunker.queuedos.domain.User

object AuthorizationPolicies {
    fun requireAdmin(actor: User) {
        if (actor.role != Role.ADMIN) {
            throw ForbiddenFailure("Admin role required.")
        }
    }
}
