plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "de.ljunker.queuedos"

val applicationVersion = providers.fileContents(layout.projectDirectory.file("VERSION")).asText.get().trim()
val semanticVersionPattern = Regex(
    """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?$"""
)
require(semanticVersionPattern.matches(applicationVersion)) {
    "VERSION must contain a SemVer value without a leading 'v' or build metadata, but was '$applicationVersion'."
}
version = applicationVersion

application {
    mainClass.set("de.ljunker.queuedos.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.13")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.13")
    implementation("io.ktor:ktor-server-auth-jvm:2.3.13")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.13")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.13")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.13")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.13")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:2.3.13")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.test {
    useJUnitPlatform()
}
