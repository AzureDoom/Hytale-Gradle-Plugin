package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class HytalePluginTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.group = 'com.example'
        project.version = '1.2.3'
    }

    def "applies extension, repositories, configurations, and base tasks"() {
        when:
        project.pluginManager.apply(HytalePlugin)

        then:
        project.extensions.findByType(HytaleExtension) != null

        and: 'custom configurations exist'
        project.configurations.named('vineImplementation').present
        project.configurations.named('vineCompileOnly').present
        project.configurations.named('vineDecompileTargets').present
        project.configurations.named('vineDecompileClasspath').present
        project.configurations.named('vineServerJar').present
        project.configurations.named('vineDependencyJars').present
        project.configurations.named('vineflowerTool').present

        and: 'compileOnly inherits vineServerJar so one vineServerJar declaration is enough'
        project.configurations.named('compileOnly').get()
                .extendsFrom
                .contains(project.configurations.named('vineServerJar').get())

        and: 'base tasks exist'
        CreateManifestIfMissingTask.isInstance(project.tasks.named('createManifestIfMissing').get())
        UpdatePluginManifestTask.isInstance(project.tasks.named('updatePluginManifest').get())
        DecompileServerJarTask.isInstance(project.tasks.named('decompileServerJar').get())
        PrepareRunServerTask.isInstance(project.tasks.named('prepareRunServer').get())
        DownloadAssetsZipTask.isInstance(project.tasks.named('downloadAssetsZip').get())
        ValidateManifestTask.isInstance(project.tasks.named('validateManifest').get())

        and: 'updatePluginManifest depends on createManifestIfMissing'
        def updatePluginManifest = project.tasks.named('updatePluginManifest').get()
        def updateDeps = updatePluginManifest.taskDependencies
                .getDependencies(updatePluginManifest)*.name
        updateDeps.contains('createManifestIfMissing')

        and: 'expected repositories were added'
        project.repositories.find { it.name == 'Hytale Server Release' } != null
        project.repositories.find { it.name == 'Hytale Server Pre-Release' } != null
        project.repositories.find { it.name == 'Hytale-Mods.info Maven' } != null
        project.repositories.find { it.name == 'PlaceholderAPI' } != null
        project.repositories.find { it.name == 'CurseMaven' } != null
        project.repositories.find { it.name == 'AzureDoom Maven' } != null
        project.repositories.find { it.name == 'Hytale Modding Maven' } != null
    }

    def "java plugin adds runServer and wires task dependencies"() {
        when:
        project.pluginManager.apply(HytalePlugin)

        then:
        RunServerTask.isInstance(project.tasks.named('runServer').get())

        and: 'processResources depends on validateManifest'
        def processResources = project.tasks.named('processResources').get()
        def processResourcesDeps = processResources.taskDependencies
                .getDependencies(processResources)*.name

        processResourcesDeps.contains('validateManifest')

        and: 'validateManifest depends on updatePluginManifest'
        def validateManifest = project.tasks.named('validateManifest').get()
        def validateDeps = validateManifest.taskDependencies
                .getDependencies(validateManifest)*.name

        validateDeps.contains('updatePluginManifest')
    }

    def "defaults extension values from project when gradle properties are absent"() {
        given:
        project = ProjectBuilder.builder().withName('demo-mod').build()
        project.group = 'com.example'
        project.version = '1.2.3'

        when:
        project.pluginManager.apply(HytalePlugin)
        def ext = project.extensions.getByType(HytaleExtension)

        then:
        ext.javaVersion.get() == 25
        ext.patchline.get() == 'release'
        ext.manifestGroup.get() == 'com.example'
        ext.modId.get() == 'demo-mod'
        !ext.disabledByDefault.get()
        !ext.includesPack.get()
    }
}