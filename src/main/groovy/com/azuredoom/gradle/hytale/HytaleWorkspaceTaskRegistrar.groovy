package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer

final class HytaleWorkspaceTaskRegistrar {
	private HytaleWorkspaceTaskRegistrar() {}

	static void register(Project project) {
		if (project != project.rootProject) {
			return
		}

		project.gradle.projectsEvaluated {
			List<String> projectPaths = resolveWorkspaceProjectPaths(project)
			WorkspaceMetadata metadata = collectWorkspaceMetadata(project, projectPaths)
			String hostPath = resolveHostProjectPath(project, projectPaths)

			def hostProject = project.project(hostPath)
			def hostExt = hostProject.extensions.getByType(HytaleExtension)
			def hostVineServerJar = hostProject.configurations.named('vineServerJar')
			def hostResolvedVersion = HytaleVersionResolver.resolvedServerVersion(
					hostProject,
					hostExt.hytaleVersion,
					hostVineServerJar
					)

			def hostPatchline = hostExt.patchline
			def gradleUserHomeDir = project.gradle.gradleUserHomeDir

			def assetsZipFileProvider = hostResolvedVersion.zip(hostPatchline) { resolvedVersion, patchline ->
				new File(
						gradleUserHomeDir,
						"caches/hytale-assets/${patchline}-${resolvedVersion}-Assets.zip"
						)
			}

			project.tasks.register('updateAllPluginManifests') { t ->
				t.group = 'hytale'
				t.description = 'Updates manifests for all Hytale subprojects.'
				t.dependsOn(projectPaths.collect { path ->
					project.project(path).tasks.named('updatePluginManifest')
				})
			}

			project.tasks.register('validateAllManifests') { t ->
				t.group = 'hytale'
				t.description = 'Validates manifests for all Hytale subprojects.'
				t.dependsOn(projectPaths.collect { path ->
					project.project(path).tasks.named('validateManifest')
				})
			}

			def workspaceRuntimeClasspathDirectory =
					project.layout.buildDirectory.dir(
					'hytale-vine-runtime/workspace-classpath'
					)

			def stageAllModAssets = project.tasks.register(
					'stageAllModAssets',
					StageAllModAssetsTask
					) { t ->
						t.group = 'hytale'
						t.description =
								'Stages all Hytale workspace plugins and external dependencies.'

						t.outputs.upToDateWhen { false }

						t.projectPaths.set(metadata.projectPaths)
						t.manifestGroups.set(metadata.manifestGroups)
						t.modIds.set(metadata.modIds)
						t.assetSourceDirectoryPaths.set(
								metadata.assetSourceDirectoryPaths
								)
						t.classOutputDirectoryPaths.set(
								metadata.classOutputDirectoryPaths
								)
						t.classOutputDirectoryCounts.set(
								metadata.classOutputDirectoryCounts
								)
						t.expectedHytaleVersion.set(metadata.expectedHytaleVersion)
						t.expectedPatchline.set(metadata.expectedPatchline)

						t.runDirectory.set(
								project.layout.projectDirectory.dir('run')
								)

						t.modsDirectory.set(
								project.layout.projectDirectory.dir('run/mods')
								)

						t.vineRuntimeClasspathDirectory.set(
								workspaceRuntimeClasspathDirectory
								)

						projectPaths.each { path ->
							def modProject = project.project(path)

							t.vineModJars.from(
									modProject.configurations.named('vineModJars')
									)

							t.vineImplementationJars.from(
									modProject.configurations.named(
									'vineImplementationJars'
									)
									)
						}
					}

			stageAllModAssets.configure { t ->
				t.dependsOn(projectPaths.collect { path ->
					project.project(path).tasks.named(
							JavaPlugin.CLASSES_TASK_NAME
							)
				})

				t.dependsOn(projectPaths.collect { path ->
					project.project(path).tasks.named(
							'validateManifest'
							)
				})
			}

			def hostDownloadAssetsZip = hostProject.tasks.named('downloadAssetsZip')

			project.tasks.register('runAllMods', RunAllModsTask) { t ->
				t.group = 'hytale'
				t.description = 'Runs one Hytale server with all Hytale workspace plugins.'

				t.dependsOn(stageAllModAssets)
				t.dependsOn(project.provider {
					hostExt.hytaleHomeOverride.orNull?.trim() ? [] : [hostDownloadAssetsZip]
				})

				t.projectPaths.set(metadata.projectPaths)
				t.expectedHytaleVersion.set(metadata.expectedHytaleVersion)
				t.expectedPatchline.set(metadata.expectedPatchline)
				t.hostProjectPath.set(hostPath)
				t.runDirectory.set(project.layout.projectDirectory.dir('run'))

				t.hytaleHomeOverride.set(hostExt.hytaleHomeOverride)
				t.patchline.set(hostExt.patchline)
				t.fallbackAssetsZip.set(project.layout.file(assetsZipFileProvider))

				t.mainClass.set('com.hypixel.hytale.Main')
				t.jvmArgs('--enable-native-access=ALL-UNNAMED')
				t.modularity.inferModulePath.set(true)

				projectPaths.each { path ->
					def subproject = project.project(path)
					def sourceSets =
							subproject.extensions.getByType(SourceSetContainer)
					def main = sourceSets.named('main').get()

					def vineImplementationJars =
							subproject.configurations.named(
							'vineImplementationJars'
							).get()

					def runtimeWithoutMainOutput =
							main.runtimeClasspath -
							main.output -
							vineImplementationJars

					t.classpath(
							subproject.files(
							main.output.classesDirs,
							main.resources.srcDirs,
							runtimeWithoutMainOutput
							)
							)
				}

				t.classpath(project.provider {
					File root = workspaceRuntimeClasspathDirectory.get().asFile

					if (!root.isDirectory()) {
						return []
					}

					File[] directories = root.listFiles()

					if (directories == null) {
						return []
					}

					return directories
							.findAll { it.isDirectory() }
							.sort { left, right -> left.name <=> right.name }
							.toList()
				})

				t.classpath(
						hostProject.configurations.named('vineServerJar').get()
						)
			}
		}
	}

	private static List<String> resolveWorkspaceProjectPaths(Project root) {
		def workspaceExt = root.extensions.findByType(HytaleWorkspaceExtension)

		List<String> paths
		if (workspaceExt != null &&
				workspaceExt.modProjects.present &&
				!workspaceExt.modProjects.get().isEmpty()) {
			paths = workspaceExt.modProjects.get()
		} else {
			paths = root.subprojects
					.findAll { it.plugins.hasPlugin('com.azuredoom.hytale-tools') }
					.collect { it.path }
		}

		if (paths == null || paths.isEmpty()) {
			throw new GradleException(
			"No Hytale workspace mod projects were found. " +
			"Set hytaleWorkspace.modProjects or apply " +
			"'com.azuredoom.hytale-tools' to subprojects you want included."
			)
		}

		return paths
	}

	private static WorkspaceMetadata collectWorkspaceMetadata(
			Project root,
			List<String> projectPaths
	) {
		def projects = projectPaths.collect { root.project(it) }
		def firstExt = projects.first().extensions.getByType(HytaleExtension)

		if (!firstExt.hytaleVersion.isPresent()) {
			throw new GradleException(
			"runAllMods requires hytaleVersion to be set on all Hytale subprojects."
			)
		}

		String expectedVersion = firstExt.hytaleVersion.get()
		String expectedPatchline = firstExt.patchline.orNull

		List<String> manifestGroups = []
		List<String> modIds = []
		List<String> assetSourceDirectoryPaths = []
		List<String> classOutputDirectoryPaths = []
		List<Integer> classOutputDirectoryCounts = []
		Set<String> pluginIdentifiers = new LinkedHashSet<>()

		projects.each { modProject ->
			def ext = modProject.extensions.getByType(HytaleExtension)

			if (ext.hytaleVersion.orNull != expectedVersion) {
				throw new GradleException(
				"All Hytale subprojects must use the same hytaleVersion for runAllMods. " +
				"Expected '${expectedVersion}' but '${modProject.path}' uses " +
				"'${ext.hytaleVersion.orNull}'."
				)
			}

			if (ext.patchline.orNull != expectedPatchline) {
				throw new GradleException(
				"All Hytale subprojects must use the same patchline for runAllMods. " +
				"Expected '${expectedPatchline}' but '${modProject.path}' uses " +
				"'${ext.patchline.orNull}'."
				)
			}

			String manifestGroup = ext.manifestGroup.get()
			String modId = ext.modId.get()
			String pluginIdentifier = "${manifestGroup}:${modId}"

			if (!pluginIdentifiers.add(pluginIdentifier)) {
				throw new GradleException(
				"Duplicate workspace plugin identifier '${pluginIdentifier}'. " +
				"Each workspace project must use a unique manifest group and mod ID."
				)
			}

			def sourceSets = modProject.extensions.getByType(SourceSetContainer)
			def main = sourceSets.named('main').get()

			List<String> classDirectories = main.output.classesDirs.files
					.collect { File directory -> directory.absolutePath }
					.sort()

			if (classDirectories.isEmpty()) {
				throw new GradleException(
				"Workspace project '${modProject.path}' has no main class output directories."
				)
			}

			classOutputDirectoryCounts.add(classDirectories.size())
			classOutputDirectoryPaths.addAll(classDirectories)

			File sourceDirFile = ext.assetPackSourceDirectory.get().asFile
			if (!sourceDirFile.exists()) {
				sourceDirFile = new File(modProject.projectDir, 'src/main/resources')
			}

			manifestGroups.add(manifestGroup)
			modIds.add(modId)
			assetSourceDirectoryPaths.add(sourceDirFile.absolutePath)
		}

		return new WorkspaceMetadata(
				projectPaths: projectPaths,
				expectedHytaleVersion: expectedVersion,
				expectedPatchline: expectedPatchline,
				manifestGroups: manifestGroups,
				modIds: modIds,
				assetSourceDirectoryPaths: assetSourceDirectoryPaths,
				classOutputDirectoryPaths: classOutputDirectoryPaths,
				classOutputDirectoryCounts: classOutputDirectoryCounts
				)
	}

	private static String resolveHostProjectPath(Project root, List<String> projectPaths) {
		def workspaceExt = root.extensions.findByType(HytaleWorkspaceExtension)

		if (workspaceExt != null &&
				workspaceExt.hostProject.present &&
				workspaceExt.hostProject.get()?.trim()) {
			String hostPath = workspaceExt.hostProject.get().trim()

			if (!projectPaths.contains(hostPath)) {
				throw new GradleException(
				"The configured hytaleWorkspace.hostProject '${hostPath}' " +
				"is not included in the workspace mod projects: ${projectPaths}"
				)
			}

			return hostPath
		}

		return projectPaths.min()
	}

	private static final class WorkspaceMetadata implements Serializable {
		List<String> projectPaths
		String expectedHytaleVersion
		String expectedPatchline
		List<String> manifestGroups
		List<String> modIds
		List<String> assetSourceDirectoryPaths
		List<String> classOutputDirectoryPaths
		List<Integer> classOutputDirectoryCounts
	}
}
