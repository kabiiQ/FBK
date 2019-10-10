group = "moe.kabii"

plugins {
    kotlin("jvm") version "1.3.40"
    id("com.github.johnrengelman.shadow") version "5.1.0"
    idea
}

repositories {
    mavenCentral()
    jcenter()
    maven {
        name = "jcenter-snapshot"
        url = uri("https://oss.jfrog.org/artifactory/libs-release")
    }
    maven {
        name = "jitpack.io"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "exposed-repo"
        url = uri("https://dl.bintray.com/kotlin/exposed")
    }
}

dependencies {
    // kotlin
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0-M2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.11.1")

    // structure
    implementation("moe.kabii:rusty-kotlin:3421f51")

    implementation("com.discord4j:discord4j-core:3.0.7") // discord websocket and api
    implementation("com.sedmelluq:lavaplayer:1.3.20") // opus audio streams for music bot
    implementation("com.github.twitch4j:twitch4j:1.0.0-alpha.11") // twitch irc

    // all other api calls
    implementation("com.squareup.okhttp3:okhttp:4.0.0")
    implementation("com.beust:klaxon:5.0.7")

    // thumbnail file server
    val ktor = "1.2.2"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")

    // database i/o
    implementation("org.litote.kmongo:kmongo-coroutine:3.10.2") // mongodb per-guild configurations, tracked streams, tracked anime lists
    implementation("org.jetbrains.exposed:exposed:0.16.1") // postgresql user data, message history
    implementation("org.postgresql:postgresql:42.2.6")
    api("com.uchuhimo:konf:0.13.3") // .toml token configuration

    // logging
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha4")

    // other
    implementation("commons-validator:commons-validator:1.6")
    implementation("com.j256.two-factor-auth:two-factor-auth:1.0")
}

val updateVersion = task("updateVersion") {
    // custom script to create version file name and increment build number
    val versionsFile = file("build.version")
    val versions = versionsFile.readLines()
    val (major, minor, build, flag) = versions.map { line -> line.substring(line.indexOf(':') + 1, line.length).trim() }
    val buildFlag = if(flag.isNotBlank()) "-$flag" else ""
    val buildCount = build.toInt() + 1
    version = "$major.$minor$buildFlag Build #$buildCount"

    versionsFile.bufferedWriter().use { output ->
        output.write("major: $major\n")
        output.write("minor: $minor\n")
        output.write("build: $buildCount\n")
        output.write("flag: $flag")
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
        }
    }

    build {
        dependsOn(updateVersion)
    }

    jar {
        manifest.attributes("Main-Class" to "moe.kabii.KizunaAiKt")
        // include build version in jar for bot self-info command
        from(".") {
            include("build.version")
        }
        dependsOn(shadowJar)
    }

    shadowJar {
        // KizunaAi-deploy.jar
        baseName = "KizunaAi"
        classifier = ""
        version = "deploy"
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}