plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
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
    implementation("io.ktor:ktor-server-core-jvm:3.5.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.1")
    implementation("io.ktor:ktor-server-auth-jvm:3.5.1")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.1")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.1")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.15.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.1")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.1")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version.toString()
    }
}
