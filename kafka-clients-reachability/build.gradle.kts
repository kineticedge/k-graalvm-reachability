
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import java.io.File

plugins {
    // Remove the application plugin - not needed!
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}


val kafkaVersion: String = project.findProperty("kafkaVersion")?.toString()
    ?: (project.properties["kafka_version"]?.toString() ?: "4.1.1")

dependencies {

    implementation(project(":metrics-reporter"))
    implementation(project(":clusters"))

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation(platform("org.testcontainers:testcontainers-bom:2.0.3"))
    implementation("org.testcontainers:testcontainers-kafka")
    implementation("com.github.dasniko:testcontainers-keycloak:4.1.1")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("org.bitbucket.b_c:jose4j:0.9.6")


}

val graalvmHome = providers.environmentVariable("GRAALVM_HOME")
    .orElse(providers.environmentVariable("GRAALVM25_HOME"))

fun requireGraalTool(name: String): String {
    val home = graalvmHome.orNull ?: error("Set GRAALVM_HOME (or GRAALVM25_HOME) to a GraalVM install")
    val f = file("$home/bin/$name")
    require(f.exists()) { "Missing $name at: ${f.path}. Install a GraalVM distribution that includes native-image tools." }
    return f.absolutePath
}

val agentScenariosRootDir = layout.buildDirectory.dir("native-image-agent/scenario")
val metadataDir = layout.buildDirectory.dir("native-image-agent/metadata")

/**
 * Define scenarios as a map of name -> fully-qualified class name
 */
fun scenariosFromFQNs(vararg fqns: String): Map<String, String> {
    return fqns.associate { fqn ->
        fqn.substringAfterLast(".") to fqn
    }.toSortedMap()
}

val scenarios = scenariosFromFQNs(
    "io.kineticedge.kgr.scenarios.PlaintextCompressionScenario",
    "io.kineticedge.kgr.scenarios.SaslPlaintextScenario",
    "io.kineticedge.kgr.scenarios.SaslPlaintextScram256Scenario",
    "io.kineticedge.kgr.scenarios.SaslPlaintextScram512Scenario",
    "io.kineticedge.kgr.scenarios.SaslPlaintextOAuthScenario",
    "io.kineticedge.kgr.scenarios.SslScenario",
    "io.kineticedge.kgr.scenarios.SaslSslScenario",
)

/**
 * Dynamically create a task for each scenario
 */
val scenarioTasks = scenarios.mapValues { (scenarioName, mainClass) ->
    tasks.register<JavaExec>("run${scenarioName}WithAgent") {
        group = "native"
        description = "Runs $scenarioName under native-image-agent"

        dependsOn(tasks.named("classes"))

        setExecutable(requireGraalTool("java"))
        classpath = sourceSets.main.get().runtimeClasspath
        this.mainClass.set(mainClass)

        val outDir = agentScenariosRootDir.map { it.dir(scenarioName) }
        jvmArgs("-agentlib:native-image-agent=config-output-dir=${outDir.get().asFile.absolutePath}")

        outputs.dir(outDir)
    }
}

/**
 * Aggregate: run all scenarios dynamically
 */
val runAllScenariosWithAgent = tasks.register("runAllScenariosWithAgent") {
    group = "native"
    description = "Runs all scenarios under native-image-agent (generated dynamically)."

    dependsOn(scenarioTasks.values)
}

/**
 * Merge and process all scenario outputs directly into JAR structure
 */
val mergeAndProcessNativeConfig = tasks.register<Exec>("mergeAndProcessNativeConfig") {
    group = "native"
    description = "Merges all scenario configs, filters, and outputs to JAR-ready structure"

    dependsOn(runAllScenariosWithAgent)

    val inputDirs = scenarios.keys.map { scenarioName ->
        "--input-dir=${agentScenariosRootDir.get().dir(scenarioName).asFile.absolutePath}"
    }

    val outputPath = metadataDir.get().asFile.apply { mkdirs() }

    commandLine(
        requireGraalTool("native-image-configure"),
        "generate",
        *inputDirs.toTypedArray(),
        "--output-dir=${outputPath.absolutePath}",
    )

    outputs.dir(outputPath)

    doLast {
        // Filter unwanted packages after merge
        val reachabilityFile = outputPath.resolve("reachability-metadata.json")
        if (reachabilityFile.exists()) {
            try {
                val jsonText = reachabilityFile.readText()
                val jsonSlurper = groovy.json.JsonSlurper()
                @Suppress("UNCHECKED_CAST")
                val config = jsonSlurper.parseText(jsonText) as MutableMap<String, Any>

                fun containsPattern(obj: Any?): Boolean {
                    return when (obj) {
                        is String -> obj.contains("com.github.dockerjava") || obj.contains("testcontainers")
                        is Map<*, *> -> obj.values.any { containsPattern(it) }
                        is List<*> -> obj.any { containsPattern(it) }
                        else -> false
                    }
                }

                // Filter reflection and resources arrays
                listOf("reflection", "resources").forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    (config[key] as? List<Any>)?.let { list ->
                        config[key] = list.filterNot { containsPattern(it) }
                    }
                }

                val output = groovy.json.JsonOutput.toJson(config)
                val prettified = groovy.json.JsonOutput.prettyPrint(output)
                reachabilityFile.writeText(prettified)

                println("✓ Merged all scenarios and filtered unwanted packages")
            } catch (e: Exception) {
                println("⚠ Warning: Could not parse JSON for filtering: ${e.message}")
                println("Skipping filter step")
            }
        }
    }
}

/**
 * Reorganize to JAR-ready structure: META-INF/resources/io/kineticedge/clients-reachability/
 */
val prepareMetadataForJar = tasks.register("prepareMetadataForJar") {
    group = "native"
    description = "Reorganizes merged metadata into JAR-ready structure"

    dependsOn(mergeAndProcessNativeConfig)

    doLast {
        val sourceFile = metadataDir.get().asFile.resolve("reachability-metadata.json")
        if (sourceFile.exists()) {
            val jarReadyDir = File(metadataDir.get().asFile.parentFile, "jar-metadata").apply { mkdirs() }
            val resourcesDir = File(jarReadyDir, "META-INF/resources/io/kineticedge/clients-reachability").apply { mkdirs() }
            val destFile = File(resourcesDir, "reachability-metadata.json")

            sourceFile.copyTo(destFile, overwrite = true)
            println("✓ Metadata prepared for JAR at: ${destFile.path}")
        }
    }

    outputs.dir(File(metadataDir.get().asFile.parentFile, "jar-metadata"))
}

/**
 * 🎯 Create metadata-only JAR artifact
 */
val metadataJar = tasks.register<Jar>("metadataJar") {
    group = "native"
    description = "Creates a JAR containing only the scenario-based reachability metadata"

    dependsOn(prepareMetadataForJar)

    archiveClassifier.set("reachability-metadata")
    from(File(metadataDir.get().asFile.parentFile, "jar-metadata"))
}

// Make metadataJar run by default during build
tasks.named("build") {
    dependsOn(metadataJar)
}


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
            artifact(metadataJar)

            pom {
                name.set("clients-reachability")
                description.set("GraalVM Reachability Metadata for Kafka Clients v$kafkaVersion")
                url.set("https://github.com/${System.getenv("GITHUB_REPOSITORY")}")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }

            artifactId = "clients-reachability-kafka${kafkaVersion.replace(".", "")}"
        }
    }
}