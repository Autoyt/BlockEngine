plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("blockengineApi") {
            from(components["java"])
            artifactId = "blockengine-api"

            pom {
                name = "BlockEngine API"
                description = "Public API for integrating with BlockEngine."
                url = "https://github.com/Autoyt/TurtleBlocks"

                licenses {
                    license {
                        name = "All Rights Reserved"
                    }
                }

                developers {
                    developer {
                        id = "Autoyt"
                        name = "Autoyt"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/Autoyt/TurtleBlocks.git"
                    developerConnection = "scm:git:https://github.com/Autoyt/TurtleBlocks.git"
                    url = "https://github.com/Autoyt/TurtleBlocks"
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Autoyt/TurtleBlocks")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}
