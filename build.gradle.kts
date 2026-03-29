import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "one.bitby"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")
    }
}

intellijPlatformTesting {
    runIde {
        register("runIde2026") {
            type = IntelliJPlatformType.IntellijIdeaUltimate
            version = "2026.1"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
