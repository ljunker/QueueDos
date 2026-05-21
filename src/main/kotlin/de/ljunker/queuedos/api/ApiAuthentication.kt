package de.ljunker.queuedos.api

import de.ljunker.queuedos.application.AuthenticationService
import de.ljunker.queuedos.application.UnauthorizedFailure
import de.ljunker.queuedos.domain.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

internal data class ActorPrincipal(val user: User) : Principal

internal fun Application.configureApiAuthentication(auth: AuthenticationService) {
    install(Authentication) {
        bearer("queuedos") {
            realm = "QueueDos"
            authenticate { credentials ->
                auth.userByToken(credentials.token)?.let(::ActorPrincipal)
            }
        }
    }
}

internal fun Route.authenticated(build: Route.() -> Unit): Route =
    authenticate("queuedos", build = build)

internal fun ApplicationCall.actor(): User =
    principal<ActorPrincipal>()?.user ?: throw UnauthorizedFailure("Authentication required.")
