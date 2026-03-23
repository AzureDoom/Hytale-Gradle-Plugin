package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CreateModSkeletonTask extends DefaultTask {

    @OutputDirectory
    abstract DirectoryProperty getJavaSourceDirectory()

    @OutputDirectory
    abstract DirectoryProperty getResourcesDirectory()

    @OutputFile
    abstract RegularFileProperty getManifestFile()

    @Input
    abstract Property<String> getManifestGroup()

    @Input
    abstract Property<String> getModId()

    @Input
    abstract Property<String> getMainClass()

    @Input
    abstract Property<String> getModDescription()

    @Input
    abstract Property<String> getModUrl()

    @Input
    abstract Property<String> getModCredits()

    @Input
    abstract Property<String> getHytaleVersion()

    @Input
    abstract Property<Boolean> getIncludesPack()

    @Input
    abstract Property<Boolean> getDisabledByDefault()

    @TaskAction
    void createSkeleton() {
        def javaDir = javaSourceDirectory.get().asFile
        def resourcesDir = resourcesDirectory.get().asFile
        def manifest = manifestFile.get().asFile

        javaDir.mkdirs()
        resourcesDir.mkdirs()
        manifest.parentFile.mkdirs()

        def packageName = manifestGroup.get()
        def simpleClassName = resolveSimpleClassName(mainClass.orNull, modId.get())
        def effectiveMainClass = mainClass.present && mainClass.get().trim()
                ? mainClass.get().trim()
                : "${packageName}.${simpleClassName}"

        def packagePath = packageName.replace('.', '/')
        def javaFile = new File(javaDir, "${packagePath}/${simpleClassName}.java")
        javaFile.parentFile.mkdirs()

        if (!javaFile.exists()) {
            javaFile.text = """package ${packageName};

public class ${simpleClassName} {
    public ${simpleClassName}() {
    }
}
"""
        }

        if (!manifest.exists()) {
            def manifestJson = [
                    Group               : manifestGroup.get(),
                    Name                : modId.get(),
                    Version             : '0.1.0',
                    Description         : modDescription.orNull ?: '',
                    Authors             : ManifestUtils.parseAuthors(modCredits.orNull),
                    Website             : modUrl.orNull ?: '',
                    ServerVersion       : hytaleVersion.orNull ?: '',
                    Dependencies        : [:],
                    OptionalDependencies: [:],
                    DisabledByDefault   : disabledByDefault.get(),
                    Main                : effectiveMainClass,
                    IncludesAssetPack   : includesPack.get(),
                    UpdateChecker       : [:]
            ]

            manifest.text = JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson))
        }

        if (includesPack.get()) {
            def packDir = new File(resourcesDir, 'pack')
            packDir.mkdirs()
            def placeholder = new File(packDir, '.gitkeep')
            if (!placeholder.exists()) {
                placeholder.text = ''
            }
        }

        logger.lifecycle("Created Hytale mod skeleton at {}", project.projectDir)
    }

    private static String resolveSimpleClassName(String configuredMainClass, String modId) {
        if (configuredMainClass?.trim()) {
            return configuredMainClass.tokenize('.').last()
        }

        def cleaned = (modId ?: 'ExampleMod')
                .replaceAll(/[^A-Za-z0-9]+/, ' ')
                .trim()

        if (!cleaned) {
            return 'ExampleMod'
        }

        return cleaned
                .split(/\s+/)
                .collect { it.substring(0, 1).toUpperCase() + it.substring(1) }
                .join('')
    }
}