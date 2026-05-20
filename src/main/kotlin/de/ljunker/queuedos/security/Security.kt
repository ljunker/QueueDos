package de.ljunker.queuedos.security

import java.security.MessageDigest

fun hashPassword(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
