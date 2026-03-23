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
        project.pluginManager.apply('com.azuredoom.hytale-tools')

        then:
        HytaleExtension.isInstance(project.extensions.findByName('hytaleTools'))

        and: 'custom configurations exist'
        project.configurations.findByName('vineImplementation') != null
        project.configurations.findByName('vineCompileOnly') != null
        project.configurations.findByName('vineDecompileTargets') != null
        project.configurations.findByName('vineDecompileClasspath') != null
        project.configurations.findByName('vineServerJar') != null
        project.configurations.findByName('vineDependencyJars') != null
        project.configurations.findByName('vineflowerTool') != null

        and: 'base tasks exist'
        UpdatePluginManifestTask.isInstance(project.tasks.named('updatePluginManifest').get())
        DecompileServerJarTask.isInstance(project.tasks.named('decompileServerJar').get())
        PrepareRunServerTask.isInstance(project.tasks.named('prepareRunServer').get())
        DownloadAssetsZipTask.isInstance(project.tasks.named('downloadAssetsZip').get())
        CreateModSkeletonTask.isInstance(project.tasks.named('createModSkeleton').get())
        ValidateManifestTask.isInstance(project.tasks.named('validateManifest').get())

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
        project.pluginManager.apply('java')
        project.pluginManager.apply('com.azuredoom.hytale-tools')

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

        and: 'updatePluginManifest depends on createModSkeleton'
        def updatePluginManifest = project.tasks.named('updatePluginManifest').get()
        def updateDeps = updatePluginManifest.taskDependencies
                .getDependencies(updatePluginManifest)*.name

        updateDeps.contains('createModSkeleton')

        and: 'compileJava depends on createModSkeleton'
        def compileJava = project.tasks.named('compileJava').get()
        def compileDeps = compileJava.taskDependencies
                .getDependencies(compileJava)*.name

        compileDeps.contains('createModSkeleton')
    }

    def "defaults extension values from project when gradle properties are absent"() {
        given:
        project = ProjectBuilder.builder().withName('demo-mod').build()
        project.group = 'com.example'
        project.version = '1.2.3'

        when:
        project.pluginManager.apply('com.azuredoom.hytale-tools')
        def ext = project.extensions.getByType(HytaleExtension)

        then:
        ext.javaVersion.get() == 21
        ext.patchline.get() == 'pre-release'
        ext.manifestGroup.get() == 'com.example'
        ext.modId.get() == 'demo-mod'
        !ext.disabledByDefault.get()
        !ext.includesPack.get()
    }
}