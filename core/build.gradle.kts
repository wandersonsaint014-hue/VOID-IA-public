plugins {
 kotlin("jvm") version "1.9.22"
}

group = "io.void"
version = "0.1.0"

repositories {
 mavenCentral()
}

dependencies {
 implementation(platform("com.fasterxml.jackson:jackson-bom:2.16.1"))
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
 implementation("com.fasterxml.jackson.core:jackson-databind")
 implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
 implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
 testImplementation(kotlin("test"))
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin {
 jvmToolchain(17)
}
