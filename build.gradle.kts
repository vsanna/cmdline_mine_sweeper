import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("application")
    id("com.diffplug.gradle.spotless") version "4.5.1"
}

group = "dev.ishikawa"
version = "1.0-SNAPSHOT"

application {
    mainClassName = "dev.ishikawa.cmd_ms.ApplicationKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.31")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}
