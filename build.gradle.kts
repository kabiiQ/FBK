group = "moe.kabii"

plugins {
    val KOTLIN_VER = "1.3.71"
    kotlin("jvm") version KOTLIN_VER
    kotlin("kapt") version KOTLIN_VER
    id("com.github.johnrengelman.shadow") version "5.2.0"
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
    maven {
        name = "sonatype-snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    // kotlin
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))

    // kotlin libs
    val coroutines = "1.3.3"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutines")

    implementation("moe.kabii:rusty-kotlin:3421f51") // custom functional style error handling

    implementation("com.discord4j:discord4j-core:3.0.14-SNAPSHOT") // discord websocket and api
    implementation("com.github.twitch4j:twitch4j:1.0.0-alpha.17") // twitch irc

    // music bot
    implementation("com.sedmelluq:lavaplayer:1.3.32") // discord audio library
    implementation("com.github.natanbc:lavadsp:0.5.2") // some lavaplayer audio filters

    implementation("com.squareup.okhttp3:okhttp:4.2.2") // other api - http calls

    // other api - json response parsing
    val moshi = "1.9.2"
    implementation("com.squareup.moshi:moshi:$moshi")
    implementation("com.squareup.moshi:moshi-kotlin:$moshi")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:$moshi")

    // thumbnail file server
    val ktor = "1.3.0-rc2"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")

    // database i/o
    implementation("org.litote.kmongo:kmongo-coroutine:3.11.2") // mongodb per-guild configurations
    // postgresql user data, message history, tracked streams
    implementation("org.jetbrains.exposed:exposed:0.17.7")
    implementation("org.postgresql:postgresql:42.2.9")
    api("com.uchuhimo:konf:0.22.1") // .toml token configuration

    // logging
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha4")

    // other
    implementation("commons-validator:commons-validator:1.6")
    implementation("org.reflections:reflections:0.9.11") // command detection and registration
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