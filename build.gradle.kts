import org.gradle.api.JavaVersion.VERSION_25

import org.gradle.jvm.tasks.Jar

val logback_version: String by project
val kafka_version: String by project
val slf4j_version: String by project

val junit_pioneer_version: String by project
val junit_version: String by project

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://packages.confluent.io/maven/")
    }
}

subprojects {

    version = "1.0"

    apply(plugin = "java")

    // Java plugin extension
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = VERSION_25
        targetCompatibility = VERSION_25
    }

    // Dependencies
    dependencies {
        add("implementation", "org.apache.kafka:kafka-clients:$kafka_version")
        add("implementation", "org.slf4j:slf4j-api:$slf4j_version")
        add("implementation", "ch.qos.logback:logback-classic:$logback_version")
    }

    // Test configuration
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        environment(System.getenv())
    }

    // Only create JAR if the project has sources
    tasks.withType<Jar>().configureEach {
        onlyIf {
            val sources = project.extensions
                .getByType<SourceSetContainer>()["main"]
                .allSource
            !sources.isEmpty
        }
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

subprojects {
    if (project.name.endsWith("-reachability")) {
        apply(plugin = "maven-publish")

        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY")}")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: ""
                    }
                }
            }

            publications {
                register<MavenPublication>("gpr") {
                    from(components["java"])

                    val kafkaVersion: String = project.findProperty("kafkaVersion")?.toString()
                        ?: (project.properties["kafka_version"]?.toString() ?: "4.1.1")

                    // Extract the reachability type from project name
                    // e.g., "kafka-clients-reachability" → "clients-reachability"
                    val reachabilityType = project.name.replace("-reachability", "").substringAfter("-")
                    val baseName = "$reachabilityType-reachability"

                    pom {
                        name.set(baseName)
                        description.set("GraalVM Reachability Metadata for Kafka $reachabilityType v$kafkaVersion")
                        url.set("https://github.com/${System.getenv("GITHUB_REPOSITORY")}")
                        licenses {
                            license {
                                name.set("Apache License 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                    }

                    artifactId = "$baseName-kafka${kafkaVersion.replace(".", "")}"
                }
            }
        }
    }
}
