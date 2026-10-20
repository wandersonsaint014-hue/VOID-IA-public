plugins {
    kotlin("jvm") version "1.9.22"
}

group = "io.void"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

kotlin {
    jvmToolchain(17)
}
