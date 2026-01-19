import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("java")
}

group = "com.attachme"
version = "1.2.11"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
      intellijIdea("2025.3.1.1")
        bundledPlugin("com.intellij.java")
    }
}

dependencies {
}

tasks {
    patchPluginXml {
        changeNotes.set("")
        sinceBuild.set("253")
        untilBuild.set("253.*")
    }

    publishPlugin {
        token.set(System.getenv("ATTACHME_PUBLISH_TOKEN"))
    }

    named<ProcessResources>("processResources") {

        // Explicitly declare inputs and outputs
        inputs.files(fileTree("src/main/resources/conf.sh"))
        inputs.property("version", version)

        outputs.dir(layout.buildDirectory.dir("resources/main"))

        // Task configuration

        from("src/main/resources/conf.sh") {
            filter<ReplaceTokens>(mapOf("tokens" to mapOf("ATTACHME_VERSION" to version.toString())))
        }

        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

}

