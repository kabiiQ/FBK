group = "moe.kabii"

plugins {
    val KOTLIN_VER = "1.3.72"
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
    // reactor-kotlin-extensions
    maven {
        name = "spring.io-snapshots"
        url = uri("https://repo.spring.io/snapshot")
    }
}

dependencies {
    // kotlin
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))

    // kotlin libs
    val coroutines = "1.3.7"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutines")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.0-SNAPSHOT") // reactor kotlin coroutine compat

    implementation("moe.kabii:rusty-kotlin:3421f51") // custom functional style error handling

    implementation("com.discord4j:discord4j-core:3.1.0.RC2") // discord websocket and api

    // twitch irc
    implementation("com.github.twitch4j:twitch4j:1.0.0-alpha.19")
    //implementation("com.github.philippheuer.events4j:events4j-handler-reactor:0.9.0") // use reactor with twitch4j - NOT WORKING WITH LATEST REACTOR

    // music bot
    implementation("com.sedmelluq:lavaplayer:1.3.50") // discord audio library
    implementation("com.github.natanbc:lavadsp:0.5.2") // some lavaplayer audio filters

    implementation("com.squareup.okhttp3:okhttp:4.7.2") // other api - http calls

    // other api - json response parsing
    val moshi = "1.9.3"
    implementation("com.squareup.moshi:moshi:$moshi")
    implementation("com.squareup.moshi:moshi-kotlin:$moshi")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:$moshi")

    // emote parsing
    implementation("com.kcthota:emoji4j:6.0")

    // thumbnail file server
    val ktor = "1.3.1"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")

    // database i/o
    // mongodb per-guild configurations
    implementation("org.litote.kmongo:kmongo-coroutine:4.0.2")

    // postgresql user data, message history, tracked streams
    implementation("org.jetbrains.exposed:exposed-core:0.26.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.26.1")
    implementation("org.jetbrains.exposed:exposed-jodatime:0.26.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.26.1")
    implementation("org.postgresql:postgresql:42.2.14")

    // .toml token configuration
    implementation("com.uchuhimo:konf:0.22.1")

    // logging
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha4")

    // other
    implementation("commons-validator:commons-validator:1.6")
    implementation("org.reflections:reflections:0.9.12") // command detection and registration
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
        kotlinOptions.jvmTarget = "11"
    }
    java.targetCompatibility = JavaVersion.VERSION_11

    build {
        dependsOn(updateVersion)
    }

    jar {
        manifest.attributes("Main-Class" to "moe.kabii.FBKKt")
        // include build version in jar for bot self-info command
        from(".") {
            include("build.version")
        }
        dependsOn(shadowJar)
    }

    shadowJar {
        // FBK-deploy.jar
        archiveBaseName.set("FBK")
        archiveClassifier.set("")
        archiveVersion.set("deploy")
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}