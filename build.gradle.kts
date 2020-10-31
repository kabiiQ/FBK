import java.io.IOException
import java.net.URL
import java.util.zip.ZipInputStream

group = "moe.kabii"
version = "deploy"

plugins {
    val kotlinVer = "1.4.10"
    kotlin("jvm") version kotlinVer
    kotlin("kapt") version kotlinVer
    application
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

    implementation("com.discord4j:discord4j-core:3.1.1") // discord websocket and api

    // twitch irc
    implementation("com.github.twitch4j:twitch4j:1.1.1")
    //implementation("com.github.philippheuer.events4j:events4j-handler-reactor:0.9.0") // use reactor with twitch4j - NOT WORKING WITH LATEST REACTOR

    // music bot
    //implementation("com.sedmelluq:lavaplayer:1.3.50") // discord audio library
    implementation("com.github.Devoxin:lavaplayer:1.3.60")
    implementation("com.github.natanbc:lavadsp:0.5.2") // some lavaplayer audio filters

    // webscraper
    implementation("org.seleniumhq.selenium:selenium-chrome-driver:4.0.0-alpha-6")

    // other api - http calls
    implementation("com.squareup.okhttp3:okhttp:4.8.1")

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
    //val buildFlag = if(flag.isNotBlank()) "-$flag" else ""
    val buildCount = build.toInt() + 1

    versionsFile.bufferedWriter().use { output ->
        output.write("major: $major\n")
        output.write("minor: $minor\n")
        output.write("build: $buildCount\n")
        output.write("flag: $flag")
    }
}

val getWebDriver = task("getWebDriver") {
    // custom script to download latest chromedriver binaries
    val webDriverRepo = "https://chromedriver.storage.googleapis.com"

    val driverDir = File("chromedriver")
    driverDir.mkdirs()

    val currentVersionFile = File(driverDir, "VERSION")
    val currentVersion = if(currentVersionFile.exists()) {
        currentVersionFile.readText()
    } else null

    val webDriverVersion = URL("$webDriverRepo/LATEST_RELEASE").readText()
    if(webDriverVersion == currentVersion) {
        println("chromedriver up-to-date: $currentVersion")
        return@task
    }

    val webDriverPath = "$webDriverRepo/$webDriverVersion"

    arrayOf("chromedriver_win32.zip", "chromedriver_linux64.zip").forEach { target ->
        // download each required distribution's driver
        val driverLocation = URL("$webDriverPath/$target")
        println("Updating driver: $target -> $webDriverVersion")

        try {
            ZipInputStream(driverLocation.openStream()).use { stream ->
                val zipEntry = stream.nextEntry

                val localTarget = File(driverDir, zipEntry.name)

                println("Downloading driver '$target' from $driverLocation")
                localTarget.outputStream().use { out ->
                    var byte = stream.read()
                    while (byte != -1) {
                        out.write(byte)
                        byte = stream.read()
                    }
                }
                stream.closeEntry()
            }

        } catch (e: IOException) {
            println("Unable to get driver '$target': ${e.message}")
            return@task
        }
    }

    // if successful, update current version file
    currentVersionFile.writeText(webDriverVersion)
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    java.targetCompatibility = JavaVersion.VERSION_14

    build {
        dependsOn(updateVersion)
    }

    jar {
        // include build version in jar for bot self-info command
        from(".") {
            include("build.version")
        }

        dependsOn(getWebDriver)
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
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}