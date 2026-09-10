package de.ljunker.queuedos.mcp

import java.net.URI

data class McpHttpSettings(
    val allowedHosts: List<String>,
    val allowedOrigins: List<String>
) {
    companion object {
        fun fromEnvironment(port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080): McpHttpSettings {
            val publicBaseUrl = System.getenv("QUEUEDOS_PUBLIC_BASE_URL")?.trimEnd('/')
                ?: "http://localhost:$port"
            val publicHost = runCatching { URI(publicBaseUrl).host }.getOrNull()
            val allowedHosts = commaSeparated("QUEUEDOS_MCP_ALLOWED_HOSTS")
                ?: listOfNotNull(publicHost, "localhost", "127.0.0.1", "[::1]").distinct()
            val allowedOrigins = commaSeparated("QUEUEDOS_MCP_ALLOWED_ORIGINS")
                ?: listOf(publicBaseUrl, "http://localhost", "http://127.0.0.1", "http://[::1]").distinct()
            return McpHttpSettings(allowedHosts, allowedOrigins)
        }

        private fun commaSeparated(name: String): List<String>? =
            System.getenv(name)?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.takeIf(List<String>::isNotEmpty)
    }
}
