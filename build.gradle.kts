group = "moe.kabii"

plugins {
    val kotlinVer = "1.4.10"
    kotlin("jvm") version kotlinVer
    kotlin("kapt") version kotlinVer
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
    val coroutinesVer = "1.3.9"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVer")

    //implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.0-SNAPSHOT") // can update once d4j 3.2 is available
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.0.2.RELEASE") // reactor kotlin coroutine compat
    implementation("io.projectreactor:reactor-core")

    implementation("moe.kabii:rusty-kotlin:3421f51") // custom functional style error handling

    implementation("com.discord4j:discord4j-core:3.1.0") // discord websocket and api

    // twitch irc
    implementation("com.github.twitch4j:twitch4j:1.1.1")
    //implementation("com.github.philippheuer.events4j:events4j-handler-reactor:0.9.0") // use reactor with twitch4j - NOT WORKING WITH LATEST REACTOR

    // music bot
    implementation("com.sedmelluq:lavaplayer:1.3.50") // discord audio library
    implementation("com.github.natanbc:lavadsp:0.5.2") // some lavaplayer audio filters

    implementation("com.squareup.okhttp3:okhttp:4.8.1") // other api - http calls

    // other api - json response parsing
    val moshiVer = "1.9.3"
    implementation("com.squareup.moshi:moshi:$moshiVer")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVer")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:$moshiVer")

    // emote parsing
    implementation("com.kcthota:emoji4j:6.0")

    // thumbnail file server
    val ktor = "1.4.0"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")

    // database i/o
    // mongodb per-guild configurations
    implementation("org.litote.kmongo:kmongo-coroutine:4.1.2")

    // postgresql user data, message history, tracked streams
    val exposedVer = "0.27.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVer")
    implementation("org.postgresql:postgresql:42.2.16")

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