package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer

final class HytaleRunTaskRegistrar {
	private HytaleRunTaskRegistrar() {}

	static void register(
			Project project,
			HytaleExtension ext,
			def assetsZipFileProvider,
			NamedDomainObjectProvider<Configuration> vineServerJar
	) {
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

				mainClassName.set('com.hypixel.hytale.Main')
				runtimeClasspath.from(project.files(
						sourceSets.named('main').get().output,
						sourceSets.named('main').get().runtimeClasspath,
						vineServerJar
						))
				workingDirectory.set(ext.runDirectory.map { it.asFile })

				serverArgs.set(ext.serverArgs)
				serverJvmArgs.set(ext.serverJvmArgs)
				assetsZip.set(project.layout.file(assetsZipFileProvider))

				debugEnabled.set(ext.debugEnabled)
				debugPort.set(ext.debugPort)
				debugSuspend.set(ext.debugSuspend)
				hotSwapEnabled.set(ext.hotSwapEnabled)
				requireDcevm.set(ext.requireDcevm)
				useHotswapAgent.set(ext.useHotswapAgent)
				jbrHome.set(ext.jbrHome)
			}

			project.tasks.register('hytaleJvmDoctor', HytaleJvmDoctorTask) {
				group = 'hytale'
				description = 'Prints JVM hot swap diagnostics for the configured runtime'
				jbrHome.set(ext.jbrHome)
			}

			project.afterEvaluate {
				def preRunTaskName = ext.preRunTask.orNull
				if (preRunTaskName != null && !preRunTaskName.trim().isEmpty()) {
					project.tasks.named('runServer').configure {
						dependsOn(preRunTaskName)
					}
				}
			}
		}
	}
}