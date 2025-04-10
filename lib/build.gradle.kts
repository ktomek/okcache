import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.kotlin.dsl.withType

plugins {
    kotlin("jvm") version "2.1.20"
    id("jacoco")
    id("io.gitlab.arturbosch.detekt") version("1.23.8")
}

group = "com.github.ktomek.okcache"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okio:okio-jvm:3.4.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // for API interface annotations
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
    testImplementation("com.squareup.retrofit2:converter-scalars:2.9.0")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

jacoco {
    toolVersion = "0.8.10" // You can check for latest version
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report

    reports {
        xml.required.set(true)
        html.required.set(false)
        csv.required.set(false)
    }

    // Optional: Include only your code packages
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory}/classes/kotlin/main") {
            exclude("**/generated/**")
        }
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(files("${layout.buildDirectory}/jacoco/test.exec"))
}

// Kotlin DSL
tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("../config/detekt-config.yml"))
    buildUponDefaultConfig = true
    autoCorrect = true
}
