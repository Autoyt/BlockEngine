import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.github.retrooper:packetevents-spigot:2.13.0")
    implementation(project(":turtle-api"))
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

val shadowJarTask = tasks.named<Jar>("shadowJar")
val serverDir = layout.projectDirectory.dir("server")
val pluginsDir = serverDir.dir("plugins")

tasks {
    build {
        dependsOn(shadowJarTask)
        finalizedBy("deployToLocalServer")
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    register<Copy>("copyPluginJar") {
        group = "deployment"
        description = "Builds the shaded Turtle jar and copies it into server/plugins."
        dependsOn(shadowJarTask)
        from(shadowJarTask.flatMap { it.archiveFile })
        into(pluginsDir)
    }

    register<Exec>("restartLocalServer") {
        group = "deployment"
        description = "Opens a new terminal window and runs server/run.bat on Windows."
        workingDir(serverDir)
        commandLine("cmd", "/c", "start", "\"\"", "cmd", "/k", "run.bat")
        onlyIf("Local restart is only supported on Windows.") {
            System.getProperty("os.name").lowercase().contains("win")
        }
    }

    register("deployToLocalServer") {
        group = "deployment"
        description = "Builds the plugin, copies it into server/plugins, and starts the local server."
        dependsOn("copyPluginJar")
        finalizedBy("restartLocalServer")
    }
}
