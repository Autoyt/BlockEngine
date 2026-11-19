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

val paperApi = "io.papermc.paper:paper-api:26.3.build.255-stable"
val packetEventsVersion = "2.14.0"

dependencies {
    compileOnly(paperApi)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    compileOnly("com.github.retrooper:packetevents-spigot:$packetEventsVersion")
    implementation(project(":blockengine-api"))
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation(paperApi)
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.3")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

val shadowJarTask = tasks.named<Jar>("shadowJar")
val serverDir = layout.projectDirectory.dir("server")
val pluginsDir = serverDir.dir("plugins")

tasks {
    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJarTask)
    }

    runServer {
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
        downloadPlugins {
            url("https://github.com/retrooper/packetevents/releases/download/v$packetEventsVersion/packetevents-spigot-$packetEventsVersion.jar")
        }
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)

        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    register<Copy>("copyPluginJar") {
        group = "deployment"
        description = "Builds the shaded BlockEngine jar and copies it into server/plugins."
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

    register<Exec>("buildStarlightDocs") {
        group = "documentation"
        description = "Builds the Starlight documentation site."
        workingDir(layout.projectDirectory.dir("docs"))
        commandLine(if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm", "run", "build")
    }

    register<Copy>("assembleGithubPagesDocs") {
        group = "documentation"
        description = "Assembles the GitHub Pages site with BlockEngine docs and API Javadocs."
        dependsOn("buildStarlightDocs", ":blockengine-api:javadoc")
        from(layout.projectDirectory.dir("docs/dist"))
        from(project(":blockengine-api").layout.buildDirectory.dir("docs/javadoc")) {
            into("api")
        }
        into(layout.buildDirectory.dir("github-pages"))
    }
}
