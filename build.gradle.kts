plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.kotlinx.atomicfu") version "0.28.0-beta"
}

group = "github.io"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:lincheck:2.39")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}