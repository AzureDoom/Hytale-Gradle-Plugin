package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

final class HytaleRunTaskRegistrar {
    private HytaleRunTaskRegistrar() {}

    static void register(Project project, HytaleExtension ext, def assetsZipFileProvider, def vineServerJar) {
        project.pluginManager.withPlugin('java') {
            project.tasks.named('processResources').configure {
                dependsOn('validateManifest')
            }

            project.tasks.matching { it.name == 'sourcesJar' }.configureEach {
                dependsOn('validateManifest')
            }

            project.tasks.matching { it.name == 'javadocJar' }.configureEach {
                dependsOn('validateManifest')
            }

            project.tasks.register('prepareRunServer', PrepareRunServerTask) {
                group = null
                description = 'Prepares the run directory for launching the Hytale server'

                dependsOn('validateManifest')

                runDirectory.set(ext.runDirectory)
                assetPackSourceDirectory.set(ext.assetPackSourceDirectory)
                assetPackRunDirectory.set(ext.assetPackRunDirectory)
            }

            project.tasks.register('runServer', RunServerTask) {
                group = 'hytale'
                description = 'Launches a local Hytale server with this project and its dependencies'

                dependsOn('prepareRunServer', 'downloadAssetsZip')

                def sourceSets = project.extensions.getByType(SourceSetContainer)
                mainClass.set('com.hypixel.hytale.Main')
                classpath = project.files(
                        sourceSets.named('main').get().output,
                        sourceSets.named('main').get().runtimeClasspath,
                        vineServerJar
                )
                modularity.inferModulePath.set(true)
                workingDir = ext.runDirectory.get().asFile
                standardInput = System.in
                jvmArgs('--enable-native-access=ALL-UNNAMED')
                assetsZip.set(project.layout.file(assetsZipFileProvider))
            }
        }
    }
}
