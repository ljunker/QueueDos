package de.ljunker.queuedos.application

import de.ljunker.queuedos.domain.McpAccessToken
import de.ljunker.queuedos.domain.User
import de.ljunker.queuedos.persistence.QueueRepositories
import de.ljunker.queuedos.persistence.TransactionRunner
import de.ljunker.queuedos.validation.requireName
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class CreatedMcpAccessToken(
    val accessToken: McpAccessToken,
    val token: String
)

class McpAccessTokenService(
    private val transactions: TransactionRunner,
    private val repositories: QueueRepositories,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val ttl: Duration = Duration.ofDays(90)
) {
    fun list(actor: User): List<McpAccessToken> =
        transactions.inTransaction {
            val now = clock.instant()
            repositories.mcpAccessTokens.listActiveForOwner(actor.organizationId, actor.id)
                .filter { runCatching { Instant.parse(it.expiresAt).isAfter(now) }.getOrDefault(false) }
        }

    fun create(actor: User, name: String): CreatedMcpAccessToken =
        transactions.inTransaction {
            val normalizedName = requireName(name, "MCP token name")
            val secret = ByteArray(32).also(random::nextBytes)
            val rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
            val createdAt = clock.instant()
            val accessToken = McpAccessToken(
                id = "mcp-token-${UUID.randomUUID()}",
                organizationId = actor.organizationId,
                ownerId = actor.id,
                name = normalizedName,
                tokenHash = hash(rawToken),
                tokenHint = "$TOKEN_PREFIX…${rawToken.takeLast(6)}",
                createdAt = createdAt.toString(),
                expiresAt = createdAt.plus(ttl).toString()
            )
            repositories.mcpAccessTokens.insert(accessToken)
            CreatedMcpAccessToken(accessToken, rawToken)
        }

    fun revoke(actor: User, tokenId: String) {
        transactions.inTransaction {
            val token = repositories.mcpAccessTokens.findForOwner(actor.organizationId, actor.id, tokenId)
                ?.takeIf { it.revokedAt == null }
                ?: throw NotFoundFailure("MCP token not found.")
            repositories.mcpAccessTokens.revoke(token.id, clock.instant().toString())
        }
    }

    fun authenticate(rawToken: String): User? {
        if (!rawToken.startsWith(TOKEN_PREFIX) || rawToken.length <= TOKEN_PREFIX.length) return null
        return transactions.inTransaction {
            val token = repositories.mcpAccessTokens.findActiveByHash(hash(rawToken)) ?: return@inTransaction null
            val now = clock.instant()
            val expiresAt = runCatching { Instant.parse(token.expiresAt) }.getOrNull() ?: return@inTransaction null
            if (!expiresAt.isAfter(now)) return@inTransaction null
            val user = repositories.users.findActiveById(token.ownerId)
                ?.takeIf { it.organizationId == token.organizationId }
                ?: return@inTransaction null
            repositories.mcpAccessTokens.markUsed(token.id, now.toString())
            user
        }
    }

    private fun hash(rawToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val TOKEN_PREFIX = "qdos_mcp_"
    }
}
