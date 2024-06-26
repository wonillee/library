plugins {
    id("org.springframework.boot") version "3.3.0" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0" apply false
    kotlin("plugin.serialization") version "2.0.0"
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

allprojects {
    group = "io.chrislee"

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "kotlin")
    apply(plugin = "com.google.devtools.ksp")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
        implementation(platform("io.arrow-kt:arrow-stack:1.2.4"))
        implementation("io.arrow-kt:arrow-core")
        implementation("io.arrow-kt:arrow-core-serialization")
        implementation("io.arrow-kt:arrow-integrations-jackson-module:0.14.1")
        implementation("io.arrow-kt:arrow-fx-coroutines")
        implementation("io.arrow-kt:arrow-optics")
        ksp("io.arrow-kt:arrow-optics-ksp-plugin:1.2.4")
        testImplementation("io.mockk:mockk:1.13.11")
        testImplementation(platform("io.kotest:kotest-bom:5.9.0"))
        testImplementation("io.kotest:kotest-runner-junit5-jvm")
        testImplementation("io.kotest:kotest-property")
        testImplementation("io.kotest:kotest-assertions-core")
        testImplementation("io.strikt:strikt-core:0.34.1")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
        testImplementation("com.ninja-squad:springmockk:4.0.2")
    }

    kotlin {
        jvmToolchain(21)
    }

    tasks.getByName<Jar>("jar") {
        enabled = true
    }

    tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        enabled = false
    }

    tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
        enabled = false
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.2.1")
        debug.set(true)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        }
        filter {
            // https://github.com/JLLeitschuh/ktlint-gradle/issues/751
            exclude { element ->
                val path = element.file.path
                path.contains("\\generated\\") || path.contains("/generated/")
            }
        }
    }
}
