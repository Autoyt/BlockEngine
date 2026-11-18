plugins { java }

group = "dev.auto"
version = "1.0.0"

// Independent example builds must not race the owning repositories' class outputs.
project(":blockengine-api").layout.buildDirectory = layout.buildDirectory.dir("dependencies/blockengine-api")
project(":flare-api").layout.buildDirectory = layout.buildDirectory.dir("dependencies/flare-api")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":blockengine-api"))
    compileOnly(project(":flare-api"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.255-stable")

    testImplementation(project(":blockengine-api"))
    testImplementation(project(":flare-api"))
    testImplementation("io.papermc.paper:paper-api:26.3.build.255-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.3")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

val inspectorPack = tasks.register<Zip>("inspectorPack") {
    from(layout.projectDirectory.dir("pack"))
    archiveFileName = "blockengine-inspector.zip"
    destinationDirectory = layout.buildDirectory.dir("generated-resources")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    from(inspectorPack)
    filesMatching("paper-plugin.yml") { expand("version" to project.version) }
}

tasks.test { useJUnitPlatform() }
