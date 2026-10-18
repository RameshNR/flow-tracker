plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.example"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Use your installed IDE instead of downloading 2024.3
        local("C:/Users/RameshNR/AppData/Local/Programs/IntelliJ IDEA OSS")
        bundledPlugin("com.intellij.java")
    }

    // Unit tests for the parts that don't need a running IDE (src/test): .\gradlew.bat test
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.test {
    useJUnitPlatform()
}

// Web folder that holds the screenshots for the plugin description (gradle.properties -> pluginImagesUrl).
val pluginImagesUrl = providers.gradleProperty("pluginImagesUrl").orElse("")

intellijPlatform {
    pluginConfiguration {
        // The Overview text in Settings -> Plugins. Its images need a web address: with pluginImagesUrl set,
        // @IMAGES@ becomes that address; without it, the image blocks are left out.
        description = providers.fileContents(layout.projectDirectory.file("src/main/plugin-description.html")).asText
            .zip(pluginImagesUrl) { html, url ->
                val body = html.replaceFirst(Regex("(?s)^\\s*<!--.*?-->\\s*"), "")
                if (url.isBlank()) body.replace(Regex("(?s)<!--img-->.*?<!--/img-->\\s*"), "")
                else body.replace("@IMAGES@", url.trimEnd('/')).replace("<!--img-->", "").replace("<!--/img-->", "")
            }
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }

    // JetBrains Marketplace (see README → Publish to JetBrains Marketplace). Nothing is read until
    // signPlugin / publishPlugin run; the values come from environment variables, never from this file.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
