plugins {
    id("application")
    kotlin("jvm") version "1.2.51"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("com.palantir.docker") version "0.20.1"
    id("com.palantir.git-version") version "0.11.0"
    id("com.adarshr.test-logger") version "1.5.0"
    id("java-library")
}

apply {
    plugin("com.diffplug.gradle.spotless")
    plugin("com.adarshr.test-logger")
}

repositories {
    jcenter()
    maven(url = "http://packages.confluent.io/maven/")
}

val gitVersion: groovy.lang.Closure<Any> by extra
version = gitVersion()
group = "no.nav.dagpenger"

application {
    applicationName = "dagpenger-journalføring-skanning"
    mainClassName = "no.nav.dagpenger.journalføring.skanning.JournalføringSkanning"
}

docker {
    name = "navikt/${application.applicationName}"
    buildArgs(mapOf(
        "APP_NAME" to application.applicationName,
        "DIST_TAR" to "${application.applicationName}-${project.version}"
    ))
    files(tasks.findByName("distTar")?.outputs)
    pull(true)
    tags(project.version.toString())
}

val kotlinLoggingVersion = "1.4.9"

val kafkaVersion = "2.0.0"
val confluentVersion = "4.1.2"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("no.nav.dagpenger:streams:0.0.1")
    implementation("no.nav.dagpenger:events:0.0.1")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")

    api("org.apache.kafka:kafka-clients:$kafkaVersion")
    api("org.apache.kafka:kafka-streams:$kafkaVersion")
    api("io.confluent:kafka-streams-avro-serde:$confluentVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("au.com.dius:pact-jvm-consumer-java8_2.12:3.6.0-rc.0")
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint()
    }
}
