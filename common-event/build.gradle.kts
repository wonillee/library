version = "1.0-SNAPSHOT"

plugins {
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api(platform("com.google.protobuf:protobuf-bom:4.27.1"))
    api("com.google.protobuf:protobuf-java")
    api("com.google.protobuf:protobuf-kotlin")
    api("org.springframework.kafka:spring-kafka:3.2.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.27.0"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin") {
                    outputSubDir = "kotlin"
                }
            }
        }
    }
}

sourceSets {
    main {
        val protoBuildOutputDir = "${project.layout.buildDirectory}/generated/source/proto/main"
        proto {
            srcDir("src/main/proto")
        }
        java {
            srcDir("$protoBuildOutputDir/java")
        }
        kotlin {
            srcDirs("src/main/kotlin", "$protoBuildOutputDir/kotlin")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.withType<com.google.protobuf.gradle.GenerateProtoTask>())
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
