version = "1.0-SNAPSHOT"

plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":common-domain"))
    api(platform("org.springframework.boot:spring-boot-dependencies:3.3.1"))
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-aop")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-webflux")
    api("org.springframework.boot:spring-boot-starter-data-r2dbc")
    api("org.springframework.kafka:spring-kafka:3.2.0")
    api("org.springframework:spring-messaging:6.1.9")
    api("io.github.microutils:kotlin-logging-jvm:3.0.5")
    api("org.slf4j:slf4j-api:1.7.30")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    api(platform("io.micrometer:micrometer-bom:1.13.1"))
    api("io.micrometer:micrometer-tracing")
    api("io.micrometer:micrometer-tracing-bridge-brave")
    api("io.zipkin.reporter2:zipkin-reporter-brave:3.4.0")
    api("org.springframework.experimental:r2dbc-micrometer-spring-boot:1.0.2")
    runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
    testFixturesApi("io.mockk:mockk:1.13.11")
    testFixturesApi("org.springframework.boot:spring-boot-starter-test")
    testFixturesApi("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testFixturesApi("com.ninja-squad:springmockk:4.0.2")
    testFixturesApi("io.micrometer:micrometer-observation-test")
    testFixturesApi("io.micrometer:micrometer-tracing-test")
    testFixturesApi(platform("org.testcontainers:testcontainers-bom:1.19.8"))
    testFixturesApi("org.testcontainers:junit-jupiter")
    testFixturesApi("org.testcontainers:postgresql")
    testFixturesApi("org.testcontainers:kafka:1.19.8")
}
