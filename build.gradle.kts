/*
 Copyright 2023 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import org.jetbrains.changelog.Changelog
import java.io.FileInputStream
import java.util.*

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.grammarKit) // Grammar-Kit plugin
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

val releasePropertiesFile = File(rootProject.rootDir, "release.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.exists()) {
        load(FileInputStream(releasePropertiesFile))
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
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"), useInstaller = false)

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        jetbrainsRuntime()

        instrumentationTools()
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = """
        <h1>Syntax highlighting for .diff files and .patch files</h1>
        <p>Supports the common formats: normal, contextual, unified, git patch.</p>
        <p>Does not support the formats: side-by-side, diff3, ed, if-else, RCS.</p>
        <br/>
        <hr/>
        <p>Copyright 2023 Thomas Rosenau</p>
        <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this software except in
            compliance with the License. You may obtain a copy of the License at</p>
        <br/>
        <p><a href="https://www.apache.org/licenses/LICENSE-2.0">https://www.apache.org/licenses/LICENSE-2.0</a></p>
        <br/>
        <p>Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
        on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
        the specific language governing permissions and limitations under the License.</p>
        <hr/>
        <br/>
        """

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    val intellijPublishToken = releaseProperties.getProperty("intellijPublishToken")
        ?: "see https://plugins.jetbrains.com/docs/intellij/deployment.html"
    publishing {
        token = intellijPublishToken
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}

val namespacePath = "de/thomasrosenau/diffplugin"

val genSrc = File("src/main/generated-java")
idea.module.generatedSourceDirs.add(genSrc)

tasks {
    sourceSets.main {
        java.srcDir(genSrc)
    }
    generateLexer {
        sourceFile.set(File("src/main/java/${namespacePath}/lexer/Diff.flex"))
        targetOutputDir.set(File(genSrc, namespacePath))
        purgeOldFiles.set(true)
    }
    generateParser {
        sourceFile.set(File("src/main/java/${namespacePath}/parser/Diff.bnf"))
        targetRootOutputDir.set(genSrc)
        pathToParser.set("DiffParser.java")
        pathToPsiRoot.set("${namespacePath}/psi")
        purgeOldFiles.set(true)
    }
    compileJava {
        dependsOn(generateLexer, generateParser)
    }
    clean {
        delete(genSrc)
    }
}
