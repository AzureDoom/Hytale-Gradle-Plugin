package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CreateManifestIfMissingTask extends DefaultTask {

    @OutputFile
    abstract RegularFileProperty getManifestFile()

    @TaskAction
    void createIfMissing() {
        def file = manifestFile.get().asFile

        if (file.exists()) {
            logger.lifecycle("Manifest already exists: {}", file)
            return
        }

        file.parentFile?.mkdirs()

        def manifestJson = [
                Group               : '',
                Name                : '',
                Version             : '',
                Description         : '',
                Authors             : [],
                Website             : '',
                ServerVersion       : '',
                Dependencies        : [:],
                OptionalDependencies: [:],
                DisabledByDefault   : false,
                Main                : '',
                IncludesAssetPack   : false,
                UpdateChecker       : [:]
        ]

        file.text = JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson))
        logger.lifecycle("Created missing manifest: {}", file)
    }
}