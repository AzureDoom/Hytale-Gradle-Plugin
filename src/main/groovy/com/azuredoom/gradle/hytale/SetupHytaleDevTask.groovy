package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Setup helper task that validates and summarizes local development readiness")
abstract class SetupHytaleDevTask extends DefaultTask {
    @Input
    abstract Property<String> getHytaleVersion()

    @Internal
    abstract RegularFileProperty getAssetsZip()

    @Internal
    abstract DirectoryProperty getGeneratedSourcesMavenRepo()

    @Input
    abstract ListProperty<String> getVineServerJarDependencies()

    @TaskAction
    void runSetupSummary() {
        def hasVersion = hytaleVersion.present && hytaleVersion.get()?.trim()
        def hasServerDep = !vineServerJarDependencies.get().isEmpty()

        if (!hasVersion && !hasServerDep) {
            throw new GradleException(
                    "setupHytaleDev requires either hytaleTools.hytaleVersion to be set " +
                            "or an explicit vineServerJar dependency."
            )
        }

        def green = "\u001B[32m"
        def yellow = "\u001B[33m"
        def cyan = "\u001B[36m"
        def bold = "\u001B[1m"
        def reset = "\u001B[0m"

        println ""
        println "${bold}${cyan}Hytale development setup${reset}"
        println "${cyan}------------------------${reset}"

        println "Hytale version: ${hytaleVersion.orNull ?: '(provided by vineServerJar)'}"

        println "Assets zip: " + (
                assetsZip.get().asFile.exists()
                        ? "${green}cached${reset}"
                        : "${yellow}will be prepared${reset}"
        )

        println "IDE sources: " + (
                generatedSourcesMavenRepo.get().asFile.exists()
                        ? "${green}ready${reset}"
                        : "${yellow}will be generated${reset}"
        )

        println ""
        println "${green}✓ Setup complete${reset}"
        println "${bold}Next:${reset} ./gradlew runServer"
        println ""
    }
}
