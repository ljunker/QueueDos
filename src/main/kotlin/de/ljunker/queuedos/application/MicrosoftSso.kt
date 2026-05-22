package de.ljunker.queuedos.application

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.*

data class MicrosoftSsoSettings(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val tenant: String = "common"
)

data class MicrosoftUserInfo(
    val email: String,
    val name: String
)

interface MicrosoftIdentityClient {
    fun authorizationUrl(state: String, codeChallenge: String): String
    fun userInfo(code: String, codeVerifier: String): MicrosoftUserInfo
}

class MicrosoftSsoService(
    private val settings: MicrosoftSsoSettings?,
    private val identityClient: MicrosoftIdentityClient?,
    private val authentication: AuthenticationService
) {
    val enabled: Boolean = settings != null && identityClient != null

    fun authorizationUrl(state: String, codeChallenge: String): String =
        client().authorizationUrl(state, codeChallenge)

    fun login(code: String, codeVerifier: String): AuthenticatedUser =
        authentication.loginMicrosoft(client().userInfo(code, codeVerifier).email)

    private fun client(): MicrosoftIdentityClient =
        identityClient ?: throw NotFoundFailure("Microsoft sign-in is not configured.")
}

class JdkMicrosoftIdentityClient(
    private val settings: MicrosoftSsoSettings,
    private val json: Json,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
) : MicrosoftIdentityClient {
    override fun authorizationUrl(state: String, codeChallenge: String): String =
        "https://login.microsoftonline.com/${encodePath(settings.tenant)}/oauth2/v2.0/authorize?" +
                form(
                    "client_id" to settings.clientId,
                    "response_type" to "code",
                    "response_mode" to "query",
                    "redirect_uri" to settings.redirectUri,
                    "scope" to "openid profile email",
                    "state" to state,
                    "code_challenge" to codeChallenge,
                    "code_challenge_method" to "S256"
                )

    override fun userInfo(code: String, codeVerifier: String): MicrosoftUserInfo {
        val tokenResponse = postForm(
            "https://login.microsoftonline.com/${encodePath(settings.tenant)}/oauth2/v2.0/token",
            form(
                "client_id" to settings.clientId,
                "client_secret" to settings.clientSecret,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to settings.redirectUri,
                "scope" to "openid profile email",
                "code_verifier" to codeVerifier
            )
        )
        val accessToken = json.decodeFromString<TokenResponse>(tokenResponse).accessToken
        val request = HttpRequest.newBuilder(URI.create("https://graph.microsoft.com/oidc/userinfo"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw UnauthorizedFailure("Microsoft sign-in could not read account information.")
        }
        val userInfo = json.decodeFromString<UserInfoResponse>(response.body())
        val email = userInfo.email?.takeIf { it.isNotBlank() }
            ?: throw UnauthorizedFailure("Microsoft account did not provide an email address.")
        return MicrosoftUserInfo(email, userInfo.name.orEmpty())
    }

    private fun postForm(url: String, body: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw UnauthorizedFailure("Microsoft sign-in did not return a token.")
        }
        return response.body()
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token")
        val accessToken: String
    )

    @Serializable
    private data class UserInfoResponse(
        val email: String? = null,
        val name: String? = null
    )
}

fun oauthSecret(): String {
    val bytes = ByteArray(48)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun pkceChallenge(verifier: String): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))

private fun form(vararg values: Pair<String, String>): String =
    values.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

private fun encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun encodePath(value: String): String =
    value.filter { it.isLetterOrDigit() || it == '-' || it == '.' }.ifBlank { "common" }
