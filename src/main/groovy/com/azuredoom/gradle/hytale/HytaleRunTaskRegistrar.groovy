package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
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
		def javaToolchains = project.extensions.getByType(JavaToolchainService)

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
				dependsOn('prepareRunServer')

				if (!ext.hytaleHomeOverride.present || !ext.hytaleHomeOverride.get().trim()) {
					dependsOn('downloadAssetsZip')
				}

				def sourceSets = project.extensions.getByType(SourceSetContainer)
				def main = sourceSets.named('main').get()

				def runtimeWithoutMainOutput = main.runtimeClasspath - main.output

				mainClass.set('com.hypixel.hytale.Main')
				classpath.from(project.files(
						main.output.classesDirs,
						main.resources.srcDirs,
						runtimeWithoutMainOutput,
						vineServerJar
						))
				projectDirectory.set(project.layout.projectDirectory)
				workingDir(ext.runDirectory.map { it.asFile })

				serverArgs.set(ext.serverArgs)
				serverJvmArgs.set(ext.serverJvmArgs)
				assetsZip.fileProvider(project.provider {
					def override = ext.hytaleHomeOverride.orNull?.trim()
					def patchline = ext.patchline.get()

					File resolvedFile

					if (override) {
						resolvedFile = HytaleAssetsResolver.findAssetsZip(override, patchline)
					} else {
						def cached = assetsZipFileProvider.get()
						resolvedFile = cached instanceof File ? cached : cached.asFile
					}

					if (resolvedFile == null) {
						throw new GradleException(
						"hytaleHomeOverride was set to '${override}', but no Assets.zip could be found"
						)
					}

					resolvedFile
				})

				debugEnabled.set(ext.debugEnabled)
				debugPort.set(ext.debugPort)
				debugSuspend.set(ext.debugSuspend)
				hotSwapEnabled.set(ext.hotSwapEnabled)
				requireDcevm.set(ext.requireDcevm)
				useHotswapAgent.set(ext.useHotswapAgent)
				hotswapAgentPath.set(ext.hotswapAgentPath)
				jbrHome.set(ext.jbrHome)

				javaLauncher.convention(javaToolchains.launcherFor {
					languageVersion = JavaLanguageVersion.of(ext.javaVersion.get())
				})
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