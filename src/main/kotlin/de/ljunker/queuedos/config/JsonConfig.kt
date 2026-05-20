package de.ljunker.queuedos.config

import kotlinx.serialization.json.Json

internal val appJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
