import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "moe.kabii"

plugins {
    val kotlinVer = "2.1.0-Beta2"
    kotlin("jvm") version kotlinVer
    kotlin("kapt") version kotlinVer
    id("com.bmuschko.docker-java-application") version "9.4.0"
    application
    idea
}

repositories {
    mavenCentral()
    // discord4j snapshots
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    // personal libs: rusty
    maven("https://jitpack.io")
    // lavaplayer-natives
    maven("https://m2.dv8tion.net/releases")
    // lavaplayer
    maven("https://maven.lavalink.dev/snapshots")
    maven("https://maven.lavalink.dev/releases")
}

dependencies {
    // kotlin
    api(kotlin("stdlib"))
    api(kotlin("reflect"))

    // kotlin libs
    val coroutinesVer = "1.7.1"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVer")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
    //implementation("io.projectreactor:reactor-core")

    implementation("moe.kabii:rusty-kotlin:3421f51") // custom functional style error handling

    implementation("com.discord4j:discord4j-core:3.3.0-RC1") // discord websocket and api

    // music bot
    implementation("dev.arbjerg:lavaplayer:2.2.1")
    implementation("dev.lavalink.youtube:v2:1.7.2")
    implementation("dev.arbjerg:lavaplayer-ext-youtube-rotator:2.1.1")
    implementation("com.github.JustRed23:lavadsp:0.7.7-1") // some lavaplayer audio filters
    implementation("org.apache.commons:commons-compress:1.26.2")

    // other api - http calls
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.10")

    // other api - json response parsing
    val moshiVer = "1.15.0"
    implementation("com.squareup.moshi:moshi:$moshiVer")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVer")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:$moshiVer")

    // emote parsing
    implementation("com.vdurmont:emoji-java:5.1.1")

    // thumbnail file server
    val ktor = "2.2.4" // hold - 'blocking primitive' issue on latest
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-apache:$ktor")
    implementation("io.ktor:ktor-server-html-builder:$ktor")

    // welcome banner image processing
    val imageIO = "3.9.4"
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:$imageIO")
    implementation("com.twelvemonkeys.imageio:imageio-psd:$imageIO")
    implementation("com.twelvemonkeys.imageio:imageio-bmp:$imageIO")

    // database i/o
    // mongodb per-guild configurations
    implementation("org.litote.kmongo:kmongo-coroutine:4.9.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

    // postgresql user data, message history, tracked streams
    val exposedVer = "0.41.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVer")
    implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng:0.8.9")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // language detection
    implementation("com.github.pemistahl:lingua:1.2.2")

    // .toml token configuration
    implementation("com.uchuhimo:konf:1.1.2")

    // logging
    implementation("ch.qos.logback:logback-classic:1.5.7")

    // other
    implementation("commons-validator:commons-validator:1.9.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.commons:commons-text:1.12.0")

    implementation("org.reflections:reflections:0.10.2") // command detection and registration

    // youtube xml parsing
    // https://github.com/gradle/gradle/issues/13656#issuecomment-658873625
    implementation("org.dom4j:dom4j:2.1.3")
    components {
        withModule("org.dom4j:dom4j") {
            allVariants { withDependencies { clear() } }
        }
    }
}

var buildFlag = ""

val updateVersion = task("updateVersion") {
    // custom script to create version file name and increment build number
    val versionsFile = file("build.version")
    val versions = versionsFile.readLines()
    val (major, minor, build, flag) = versions.map { line -> line.substring(line.indexOf(':') + 1, line.length).trim() }
    buildFlag = flag
    val buildCount = build.toInt() + 1

    // Set version for use in build output
    version = flag.ifBlank { "$major.$minor" }

    versionsFile.bufferedWriter().use { output ->
        output.write("major: $major\n")
        output.write("minor: $minor\n")
        output.write("build: $buildCount\n")
        output.write("flag: $flag")
    }
}

tasks {
    kotlin {
        jvmToolchain(22)
    }

    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_22)
    }

    java.targetCompatibility = JavaVersion.VERSION_22
    java.sourceCompatibility = JavaVersion.VERSION_22

    build {
        dependsOn(updateVersion)
    }

    jar {
        // include build version in jar for bot self-info command
        from(".") {
            include("build.version")
        }
    }

    // credit to https://github.com/gradle/gradle/issues/1989#issuecomment-550192866
    named<CreateStartScripts>("startScripts") {
        doLast {
            windowsScript.writeText(windowsScript.readText().replace(Regex("set CLASSPATH=.*"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*"))
        }
    }
}

application {
    mainClass.set("moe.kabii.FBKKt")
    applicationDefaultJvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

docker {
    javaApplication {
        baseImage.set("eclipse-temurin:23")
        maintainer.set("kabiiQ")
        ports.set(listOf(
            8001, // YouTube PubSub subscription callback
            8002, // TwitCasting WebHook callback
            8003, // Twitch API callback server (Internal, must reverse proxy from SSL :443)
            8010, // YouTube video API server
            8080, // File server
            8101, // Discord OAuth redirect
        ))

        images.set(
            listOfNotNull(
                "docker.kabii.moe/fbk:$version",
                // push "latest" if this is an unflagged build
                if(buildFlag.isBlank()) "docker.kabii.moe/fbk:latest" else null
            )
        )
    }
}

tasks.named<Dockerfile>("dockerCreateDockerfile") {
    // Setup pytchat environment within the fbk Docker image
    environmentVariable("PIP_ROOT_USER_ACTION", "ignore")
    instruction("COPY --from=python:3.12.7 / /")
    runCommand("pip install httpx==0.18.2 pytchat==0.5.5")
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}