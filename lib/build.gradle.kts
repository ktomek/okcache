import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.kotlin.dsl.withType
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "2.1.20"
    jacoco
    id("io.gitlab.arturbosch.detekt") version ("1.23.8")
}

group = "com.github.ktomek.okcache"
version = getGitTagVersion()

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okio:okio-jvm:3.4.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // for API interface annotations
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // auto-run after test
}
kotlin {
    jvmToolchain(21)
}

jacoco {
    toolVersion = "0.8.13" // You can check for latest version
    reportsDirectory = layout.buildDirectory.dir("reports/jacoco")
}

// Kotlin DSL
tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
}

tasks.named("check").configure {
    this.setDependsOn(
        this.dependsOn.filterNot {
            it is TaskProvider<*> && it.name == "detekt"
        }
    )
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("../config/detekt-config.yml"))
    buildUponDefaultConfig = true
    autoCorrect = true
}

tasks.jacocoTestReport {
    reports {
        html.required = false
        xml.required = true
        csv.required = false
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun getGitTagVersion(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine = listOf("git", "describe", "--tags", "--abbrev=0")
            standardOutput = stdout
        }
        stdout.toString().trim().removePrefix("v")
    } catch (_: Exception) {
        "0.0.1-SNAPSHOT" // fallback if no tag found
    }
}
