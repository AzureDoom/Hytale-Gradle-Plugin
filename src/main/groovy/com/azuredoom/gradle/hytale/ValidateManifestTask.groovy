package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Validation task with negligible build cache value")
abstract class ValidateManifestTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getManifestFile()

    @Input
    abstract Property<String> getManifestGroup()

    @Input
    abstract Property<String> getModId()

    @Input
    abstract Property<String> getMainClass()

    @Input
    abstract Property<String> getHytaleVersion()

    @Input
    abstract Property<String> getManifestDependencies()

    @Input
    abstract Property<String> getManifestOptionalDependencies()

    @Input
    abstract Property<Boolean> getIncludesPack()

    @TaskAction
    void validateManifest() {
        def file = manifestFile.get().asFile
        if (!file.exists()) {
            throw new GradleException("Manifest file does not exist: ${file}")
        }

        def json
        try {
            json = new JsonSlurper().parseText(file.text) as Map
        } catch (Exception e) {
            throw new GradleException("Manifest file is not valid JSON: ${file}", e)
        }

        def errors = []

        requireNonBlank(json, 'Group', errors)
        requireNonBlank(json, 'Name', errors)
        requireNonBlank(json, 'Version', errors)
        requireNonBlank(json, 'ServerVersion', errors)
        requireNonBlank(json, 'Main', errors)

        if (json.Group != manifestGroup.get()) {
            errors << "Manifest Group '${json.Group}' does not match configured manifestGroup '${manifestGroup.get()}'."
        }

        if (json.Name != modId.get()) {
            errors << "Manifest Name '${json.Name}' does not match configured modId '${modId.get()}'."
        }

        if (json.ServerVersion != hytaleVersion.get()) {
            errors << "Manifest ServerVersion '${json.ServerVersion}' does not match configured hytaleVersion '${hytaleVersion.get()}'."
        }

        if ((json.Main ?: '').toString().trim() != (mainClass.get() ?: '').trim()) {
            errors << "Manifest Main '${json.Main}' does not match configured mainClass '${mainClass.get()}'."
        }

        if (!(json.Dependencies instanceof Map)) {
            errors << "Manifest Dependencies must be an object."
        } else {
            validateDependencyMap(json.Dependencies as Map, 'Dependencies', errors)
        }

        if (!(json.OptionalDependencies instanceof Map)) {
            errors << "Manifest OptionalDependencies must be an object."
        } else {
            validateDependencyMap(json.OptionalDependencies as Map, 'OptionalDependencies', errors)
        }

        def expectedDeps = ManifestUtils.parseDepMap(manifestDependencies.orNull)
        def expectedOptionalDeps = ManifestUtils.parseDepMap(manifestOptionalDependencies.orNull)

        if ((json.Dependencies ?: [:]) != expectedDeps) {
            errors << "Manifest Dependencies do not match configured manifestDependencies."
        }

        if ((json.OptionalDependencies ?: [:]) != expectedOptionalDeps) {
            errors << "Manifest OptionalDependencies do not match configured manifestOptionalDependencies."
        }

        if ((json.IncludesAssetPack as boolean) != includesPack.get()) {
            errors << "Manifest IncludesAssetPack '${json.IncludesAssetPack}' does not match configured includesPack '${includesPack.get()}'."
        }

        if (json.UpdateChecker != null && !(json.UpdateChecker instanceof Map)) {
            errors << "Manifest UpdateChecker must be an object when present."
        }

        if (!errors.isEmpty()) {
            throw new GradleException("Manifest validation failed:\n - " + errors.join('\n - '))
        }

        logger.lifecycle("Manifest is valid: {}", file)
    }

    private static void requireNonBlank(Map json, String key, List<String> errors) {
        def value = json[key]
        if (value == null || value.toString().trim().isEmpty()) {
            errors << ("Manifest field '${key}' is missing or blank." as String)
        }
    }

    private static void validateDependencyMap(Map deps, String label, List<String> errors) {
        deps.each { k, v ->
            if (!k?.toString()?.trim()) {
                errors << ("${label} contains a blank dependency id." as String)
            }
            if (v == null || !v.toString().trim()) {
                errors << ("${label} entry '${k}' has a blank version." as String)
            }
        }
    }
}