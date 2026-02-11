import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.kotlinls"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    
    // Language Server Protocol (LSP4J)
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.2")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.21.2")
    
    // SQLite for persistence
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

application {
    mainClass.set("com.kotlinls.server.MainKt")
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
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.shadowJar {
    archiveBaseName.set("kotlin-language-server")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    
    manifest {
        attributes["Main-Class"] = "com.kotlinls.server.MainKt"
    }
    
    // Avoid conflicts with signed JARs
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    
    mergeServiceFiles()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Task to verify build
tasks.register("verify") {
    dependsOn("build", "test")
    doLast {
        println("✅ Build and test verification completed successfully!")
    }
}
