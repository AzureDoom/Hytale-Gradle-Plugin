package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

import java.nio.file.Files
import java.nio.file.Path

final class HytaleWorkspaceTaskRegistrar {
	private HytaleWorkspaceTaskRegistrar() {}

	static void register(Project project) {
		if (project != project.rootProject) {
			return
		}

		def hytaleProjectsProvider = project.providers.provider {
			project.subprojects.findAll { it.plugins.hasPlugin('com.azuredoom.hytale-tools') }
		}

		def validatedHytaleProjectsProvider = project.providers.provider {
			def projects = hytaleProjectsProvider.get()
			if (projects.isEmpty()) {
				throw new GradleException(
				"No subprojects apply 'com.azuredoom.hytale-tools'. " +
				"Apply the plugin to each mod project you want included in workspace tasks."
				)
			}
			projects
		}

		def hostProjectProvider = project.providers.provider {
			validatedHytaleProjectsProvider.get().sort { it.path }.first()
		}

		project.tasks.register('updateAllPluginManifests') {
			group = 'hytale'
			description = 'Updates manifests for all Hytale subprojects.'

			dependsOn(validatedHytaleProjectsProvider.map { projects ->
				projects.collect { it.tasks.named('updatePluginManifest') }
			})
		}

		project.tasks.register('validateAllManifests') {
			group = 'hytale'
			description = 'Validates manifests for all Hytale subprojects.'

			dependsOn(validatedHytaleProjectsProvider.map { projects ->
				projects.collect { it.tasks.named('validateManifest') }
			})
		}

		def stageAllModAssets = project.tasks.register('stageAllModAssets') {
			group = 'hytale'
			description = 'Stages all Hytale mod asset packs into the root run directory.'

			doFirst {
				validateWorkspaceCompatibility(validatedHytaleProjectsProvider.get())
			}

			doLast {
				def runDir = new File(project.rootDir, 'run')
				def modsDir = new File(runDir, 'mods')

				runDir.mkdirs()
				modsDir.mkdirs()

				validatedHytaleProjectsProvider.get().each { modProject ->
					def ext = modProject.extensions.getByType(HytaleExtension)

					File sourceDirFile = ext.assetPackSourceDirectory.get().asFile
					if (!sourceDirFile.exists()) {
						sourceDirFile = new File(modProject.projectDir, 'src/main/resources')
					}

					if (!sourceDirFile.exists()) {
						throw new GradleException(
						"Asset pack source directory does not exist: ${sourceDirFile}"
						)
					}

					Path sourceDir = sourceDirFile.toPath().toAbsolutePath().normalize()
					Path targetDir = new File(
							modsDir,
							"${ext.manifestGroup.get().replace('.', '_')}_${ext.modId.get()}"
							).toPath().toAbsolutePath().normalize()

					if (Files.exists(targetDir)) {
						project.delete(targetDir.toFile())
					}

					targetDir.parent.toFile().mkdirs()
					createLinkJunctionOrCopy(project, sourceDir, targetDir)
				}
			}
		}

		project.tasks.register('runAllMods', JavaExec) { task ->
			group = 'hytale'
			description = 'Runs one Hytale server with all Hytale mod subprojects on the classpath.'

			dependsOn(stageAllModAssets)
			dependsOn(validatedHytaleProjectsProvider.map { projects ->
				projects.collect { it.tasks.named('classes') }
			})
			dependsOn(hostProjectProvider.map { host ->
				def downloadTask = host.tasks.named('downloadAssetsZip')
				downloadTask.configure {
					mustRunAfter(stageAllModAssets)
				}
				downloadTask
			})

			mainClass.set('com.hypixel.hytale.Main')
			workingDir = new File(project.rootDir, 'run')
			standardInput = System.in
			jvmArgs('--enable-native-access=ALL-UNNAMED')
			modularity.inferModulePath.set(true)

			doFirst {
				def projects = validatedHytaleProjectsProvider.get()
				validateWorkspaceCompatibility(projects)

				def host = projects.sort { it.path }.first()
				def hostExt = host.extensions.getByType(HytaleExtension)

				def assetsZip = new File(
						project.gradle.gradleUserHomeDir,
						"caches/hytale-assets/${hostExt.patchline.get()}-${hostExt.hytaleVersion.get()}-Assets.zip"
						)

				if (!assetsZip.exists() || assetsZip.length() == 0) {
					throw new GradleException("Assets zip not found or empty: ${assetsZip}")
				}

				def combinedRuntime = project.files(
						projects.collect { modProject ->
							def sourceSets = modProject.extensions.getByType(SourceSetContainer)
							sourceSets.named('main').get().runtimeClasspath
						}
						)

				task.classpath = project.files(
						combinedRuntime,
						host.configurations.named('vineServerJar').get()
						)

				task.setArgs([
					"--assets=${assetsZip.absolutePath}",
					'--allow-op',
					'--disable-sentry'
				])
			}
		}
	}

	private static void validateWorkspaceCompatibility(Iterable<Project> projects) {
		def projectList = projects.toList()
		def firstExt = projectList.first().extensions.getByType(HytaleExtension)

		if (!firstExt.hytaleVersion.isPresent()) {
			throw new GradleException("runAllMods requires hytaleVersion to be set on all Hytale subprojects.")
		}

		def expectedVersion = firstExt.hytaleVersion.orNull
		def expectedPatchline = firstExt.patchline.orNull

		projectList.each { modProject ->
			def ext = modProject.extensions.getByType(HytaleExtension)

			if (ext.hytaleVersion.orNull != expectedVersion) {
				throw new GradleException(
				"All Hytale subprojects must use the same hytaleVersion for runAllMods. " +
				"Expected '${expectedVersion}' but '${modProject.path}' uses '${ext.hytaleVersion.orNull}'."
				)
			}

			if (ext.patchline.orNull != expectedPatchline) {
				throw new GradleException(
				"All Hytale subprojects must use the same patchline for runAllMods. " +
				"Expected '${expectedPatchline}' but '${modProject.path}' uses '${ext.patchline.orNull}'."
				)
			}
		}
	}

	private static void createLinkJunctionOrCopy(Project project, Path sourceDir, Path targetDir) {
		try {
			def relativeSource = targetDir.parent.relativize(sourceDir)
			Files.createSymbolicLink(targetDir, relativeSource)
			project.logger.lifecycle("Created symlink ${targetDir} -> ${relativeSource}")
			return
		} catch (Exception ex) {
			project.logger.warn("Symlink creation failed, attempting fallback: ${ex.message}")
		}

		if (System.getProperty('os.name').toLowerCase().contains('win')) {
			def process = new ProcessBuilder(
					'cmd', '/c', 'mklink', '/J',
					targetDir.toString(),
					sourceDir.toString()
					).redirectErrorStream(true).start()

			def output = process.inputStream.text
			def code = process.waitFor()

			if (code == 0 && Files.exists(targetDir)) {
				project.logger.lifecycle("Created junction ${targetDir} -> ${sourceDir}")
				return
			}

			project.logger.warn(
					"Junction creation failed, falling back to copy.\n" +
					"Target: ${targetDir}\n" +
					"Source: ${sourceDir}\n" +
					"Output:\n${output}"
					)
		}

		project.copy {
			from sourceDir.toFile()
			into targetDir.toFile()
		}
		project.logger.lifecycle("Copied asset pack ${sourceDir} -> ${targetDir}")
	}
}