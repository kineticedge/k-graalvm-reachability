plugins {
    id("java")
}

group = "io.kineticedge.k"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:4.1.1")
    implementation("org.apache.kafka:kafka-streams:4.1.1")
    implementation(platform("org.testcontainers:testcontainers-bom:2.0.3"))
    implementation("org.testcontainers:testcontainers-kafka")
    implementation("com.github.dasniko:testcontainers-keycloak:4.1.1")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("org.bitbucket.b_c:jose4j:0.9.6")

    implementation(project(":metrics-reporter"))
}



tasks.test {
    useJUnitPlatform()
}