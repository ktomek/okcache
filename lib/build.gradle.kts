import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.kotlin.dsl.withType

plugins {
    kotlin("jvm") version "2.1.20"
    jacoco
    id("io.gitlab.arturbosch.detekt") version ("1.23.8")
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
