package de.ljunker.queuedos.security

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthTokenCodecTest {
    private val secret = "token-scope-test-secret-that-is-long-enough"

    @Test
    fun tokenScopesAreSeparatedAndPasswordChangeTokensExpireAfterFifteenMinutes() {
        val clock = MutableClock(Instant.parse("2026-08-13T12:00:00Z"))
        val codec = AuthTokenCodec(secret, clock)
        val session = codec.createToken("session-user")
        val passwordChange = codec.createPasswordChangeToken("password-user")

        assertEquals("session-user", codec.userIdFromToken(session))
        assertNull(codec.passwordChangeUserIdFromToken(session))
        assertEquals("password-user", codec.passwordChangeUserIdFromToken(passwordChange))
        assertNull(codec.userIdFromToken(passwordChange))

        clock.current = clock.current.plusSeconds(15 * 60 + 1)
        assertNull(codec.passwordChangeUserIdFromToken(passwordChange))
        assertEquals("session-user", codec.userIdFromToken(session))
    }

    @Test
    fun tokensWithoutScopeRemainNormalSessions() {
        val now = Instant.parse("2026-08-13T12:00:00Z")
        val codec = AuthTokenCodec(secret, Clock.fixed(now, ZoneOffset.UTC))
        val token = legacyToken("legacy-user", now.epochSecond, now.plusSeconds(3600).epochSecond)

        assertEquals("legacy-user", codec.userIdFromToken(token))
        assertNull(codec.passwordChangeUserIdFromToken(token))
    }

    private fun legacyToken(subject: String, issuedAt: Long, expiresAt: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun encode(value: String) = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
        val header = encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload = encode("""{"sub":"$subject","iat":$issuedAt,"exp":$expiresAt}""")
        val unsigned = "$header.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = encoder.encodeToString(mac.doFinal(unsigned.toByteArray(Charsets.UTF_8)))
        return "$unsigned.$signature"
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}
