package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.ActivityEventType
import de.ljunker.queuedos.domain.ActivityHook
import de.ljunker.queuedos.domain.Ticket
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.persistence.QueueRepositories
import de.ljunker.queuedos.persistence.TransactionRunner
import de.ljunker.queuedos.validation.normalizeActivityTemplate
import de.ljunker.queuedos.validation.normalizeWebhookUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

data class TicketActivity(
    val eventType: ActivityEventType,
    val ticket: Ticket,
    val actor: User,
    val values: Map<String, String> = emptyMap()
)

interface ActivityNotifier {
    fun publish(activity: TicketActivity)
}

object NoOpActivityNotifier : ActivityNotifier {
    override fun publish(activity: TicketActivity) = Unit
}

class SlackActivityNotifier(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories,
    private val sender: SlackMessageSender
) : ActivityNotifier {
    private val logger = LoggerFactory.getLogger(SlackActivityNotifier::class.java)

    override fun publish(activity: TicketActivity) {
        val hooks = transactions.inTransaction {
            repositories.activityHooks.listActive(activity.actor.organizationId, activity.eventType)
        }
        hooks.forEach { hook ->
            runCatching { sender.send(hook.webhookUrl, render(hook.messageTemplate, activity)) }
                .onFailure { logger.warn("Slack activity hook {} failed.", hook.id, it) }
        }
    }

    private fun render(template: String, activity: TicketActivity): String {
        val replacements = mapOf(
            "event" to activity.eventType.name,
            "ticketKey" to activity.ticket.key,
            "ticketTitle" to activity.ticket.title,
            "actorName" to activity.actor.displayName,
            "actorEmail" to activity.actor.email
        ) + activity.values
        return replacements.entries.fold(template) { message, (key, value) ->
            message.replace("{{$key}}", value)
        }
    }
}

interface SlackMessageSender {
    fun send(webhookUrl: String, text: String)
}

class JdkSlackMessageSender(
    private val json: Json,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
) : SlackMessageSender {
    override fun send(webhookUrl: String, text: String) {
        val request = HttpRequest.newBuilder(URI.create(webhookUrl))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(SlackPayload(text))))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) {
            error("Slack webhook returned HTTP ${response.statusCode()}.")
        }
    }

    @Serializable
    private data class SlackPayload(val text: String)
}

class ActivityHookService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories
) {
    fun create(actor: User, command: CreateActivityHookCommand): ActivityHook =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            ActivityHook(
                id = "hook-${UUID.randomUUID()}",
                organizationId = actor.organizationId,
                eventType = command.eventType,
                webhookUrl = normalizeWebhookUrl(command.webhookUrl),
                messageTemplate = normalizeActivityTemplate(command.messageTemplate),
                active = command.active
            ).also(repositories.activityHooks::insert)
        }

    fun update(actor: User, hookId: String, command: UpdateActivityHookCommand): ActivityHook =
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            if (
                command.eventType == null &&
                command.webhookUrl == null &&
                command.messageTemplate == null &&
                command.active == null
            ) {
                throw BadRequestFailure("Activity hook update needs a change.")
            }
            val current = requireHook(actor, hookId)
            current.copy(
                eventType = command.eventType ?: current.eventType,
                webhookUrl = command.webhookUrl?.let(::normalizeWebhookUrl) ?: current.webhookUrl,
                messageTemplate = command.messageTemplate?.let(::normalizeActivityTemplate) ?: current.messageTemplate,
                active = command.active ?: current.active
            ).also(repositories.activityHooks::update)
        }

    fun delete(actor: User, hookId: String) {
        transactions.inTransaction {
            AuthorizationPolicies.requireAdmin(actor)
            repositories.activityHooks.delete(requireHook(actor, hookId).id)
        }
    }

    private fun requireHook(actor: User, hookId: String): ActivityHook =
        repositories.activityHooks.findById(actor.organizationId, hookId)
            ?: throw NotFoundFailure("Activity hook not found.")
}
