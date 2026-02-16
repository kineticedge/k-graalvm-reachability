rootProject.name = "k-graalvm-reachability"

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
    }
}

include("metrics-reporter")
include("clusters")
include("kafka-clients-reachability")
include("kafka-streams-reachability")
