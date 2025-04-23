plugins {
    kotlin("jvm") version "2.1.0"
    application
    kotlin("plugin.serialization") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
application {
    mainClass.set("org.example.MainKt")
}
group = "org.example"
version = "1"

repositories {
    mavenCentral()
}
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.13")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.13")
    testImplementation("io.ktor:ktor-client-websockets:2.3.13")

    implementation("io.ktor:ktor-server-core:2.3.13")
    implementation("io.ktor:ktor-server-tests-jvm:2.3.13")
    implementation("io.ktor:ktor-server-cio:2.3.13")
    implementation("io.ktor:ktor-server-auth:2.3.13")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.13")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-server-websockets:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")

    implementation("org.mindrot:jbcrypt:0.4")

    implementation("org.postgresql:postgresql:42.7.5")
        implementation("org.flywaydb:flyway-core:9.22.3")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }
}
tasks.named("distZip") {
    dependsOn(tasks.shadowJar)
}
tasks.named("distTar") {
    dependsOn(tasks.shadowJar)
}
tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}

kotlin {
    jvmToolchain(17)
}