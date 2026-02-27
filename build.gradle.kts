import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Instant

plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.kuju63.kotlin.lang"
version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.10")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.10")
    
    // Language Server Protocol (LSP4J)
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:1.0.0")
    
    // SQLite for persistence
    implementation("org.xerial:sqlite-jdbc:3.51.2.0")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    
    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.29")
    implementation("org.slf4j:slf4j-api:2.0.17")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

application {
    mainClass.set("io.github.kuju63.kotlin.lang.server.MainKt")
}

tasks.test {
    useJUnitPlatform()
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    
    // Increase memory for tests
    maxHeapSize = "2g"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
}

tasks.shadowJar {
    archiveBaseName.set("kotlin-language-server")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())

    manifest {
        attributes["Main-Class"] = "io.github.kuju63.kotlin.lang.server.MainKt"
        attributes["Implementation-Title"] = "Kotlin Language Server"
        attributes["Implementation-Version"] = version
        attributes["Implementation-Vendor"] = "io.github.kuju63"
        attributes["Build-Date"] = Instant.now().toString()
        attributes["Build-JDK"] = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
        attributes["Gradle-Version"] = gradle.gradleVersion
    }

    // Avoid conflicts with signed JARs
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")

    mergeServiceFiles()
}

// Fix task dependencies for shadow jar
tasks.named("distZip") {
    dependsOn(tasks.shadowJar)
}

tasks.named("distTar") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startShadowScripts") {
    dependsOn(tasks.jar)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Task to verify build
tasks.register("verify") {
    dependsOn("build", "test")
    doLast {
        println("✅ Build and test verification completed successfully!")
    }
}
