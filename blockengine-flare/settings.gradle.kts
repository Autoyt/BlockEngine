rootProject.name = "blockengine-flare"

include(":blockengine-api", ":flare-api")
project(":blockengine-api").projectDir = file("../blockengine-api")
project(":flare-api").projectDir = file(providers.gradleProperty("flareApiDir")
    .getOrElse("../../flare/flare-api"))
