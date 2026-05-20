package de.ljunker.queuedos.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val BCRYPT_PASSWORD_MARKER = "bcrypt"

fun hashPassword(password: String): String =
    BCrypt.hashpw(password, BCrypt.gensalt(12))

fun verifyPassword(password: String, salt: String, storedHash: String): Boolean {
    if (storedHash.startsWith("\$2a\$") || storedHash.startsWith("\$2b\$") || storedHash.startsWith("\$2y\$")) {
        return runCatching { BCrypt.checkpw(password, storedHash) }.getOrDefault(false)
    }
    return legacySha256Hash(password, salt) == storedHash
}

fun passwordNeedsRehash(storedHash: String): Boolean =
    !(storedHash.startsWith("\$2a\$") || storedHash.startsWith("\$2b\$") || storedHash.startsWith("\$2y\$"))

fun legacySha256Hash(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

class AuthTokenCodec(
    private val secret: String,
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofHours(12),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    init {
        require(secret.isNotBlank()) { "Session secret must not be blank." }
    }

    fun createToken(userId: String): String {
        val now = clock.instant().epochSecond
        val header = JwtHeader()
        val payload = JwtPayload(
            subject = userId,
            issuedAt = now,
            expiresAt = now + ttl.seconds
        )
        val unsigned = "${encode(json.encodeToString(header))}.${encode(json.encodeToString(payload))}"
        return "$unsigned.${signature(unsigned)}"
    }

    fun userIdFromToken(token: String): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null

        val unsigned = "${parts[0]}.${parts[1]}"
        if (!MessageDigest.isEqual(signature(unsigned).toByteArray(), parts[2].toByteArray())) return null

        return runCatching {
            val header = json.decodeFromString<JwtHeader>(decode(parts[0]))
            if (header.algorithm != "HS256" || header.type != "JWT") return null

            val payload = json.decodeFromString<JwtPayload>(decode(parts[1]))
            if (payload.expiresAt < clock.instant().epochSecond) return null
            payload.subject
        }.getOrNull()
    }

    private fun signature(unsignedToken: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return encoder.encodeToString(mac.doFinal(unsignedToken.toByteArray(Charsets.UTF_8)))
    }

    private fun encode(value: String): String =
        encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        try {
            decoder.decode(value).toString(Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw SerializationException("Invalid base64url token part.")
        }
}

@Serializable
private data class JwtHeader(
    @kotlinx.serialization.SerialName("alg")
    val algorithm: String = "HS256",
    @kotlinx.serialization.SerialName("typ")
    val type: String = "JWT"
)

@Serializable
private data class JwtPayload(
    @kotlinx.serialization.SerialName("sub")
    val subject: String,
    @kotlinx.serialization.SerialName("iat")
    val issuedAt: Long,
    @kotlinx.serialization.SerialName("exp")
    val expiresAt: Long
)
