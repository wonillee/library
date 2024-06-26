plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "library"
include("common-domain")
include("common-event")
include("common-infrastructure")
include("catalog")
include("lending")
