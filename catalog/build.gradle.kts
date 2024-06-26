version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common-domain"))
    implementation(project(":common-event"))
    implementation(project(":common-infrastructure"))
    testImplementation(testFixtures(project(":common-infrastructure")))
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    enabled = true
    imageName = "chrisleed/${rootProject.name}-${project.name}"
}
