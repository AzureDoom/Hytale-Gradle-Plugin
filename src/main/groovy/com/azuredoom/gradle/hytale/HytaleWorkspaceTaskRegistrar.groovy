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

			def assetsZipFileProvider = hostResolvedVersion.map { resolvedVersion ->
				new File(
						project.gradle.gradleUserHomeDir,
						"caches/hytale-assets/${hostExt.patchline.get()}-${resolvedVersion}-Assets.zip"
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

			def stageAllModAssets = project.tasks.register('stageAllModAssets', StageAllModAssetsTask) { t ->
				t.group = 'hytale'
				t.description = 'Stages all Hytale mod asset packs into the root run directory.'

				t.projectPaths.set(metadata.projectPaths)
				t.manifestGroups.set(metadata.manifestGroups)
				t.modIds.set(metadata.modIds)
				t.assetSourceDirectoryPaths.set(metadata.assetSourceDirectoryPaths)
				t.expectedHytaleVersion.set(metadata.expectedHytaleVersion)
				t.expectedPatchline.set(metadata.expectedPatchline)
				t.runDirectory.set(project.layout.projectDirectory.dir('run'))
				t.modsDirectory.set(project.layout.projectDirectory.dir('run/mods'))
			}

			project.tasks.register('runAllMods', RunAllModsTask) { t ->
				t.group = 'hytale'
				t.description = 'Runs one Hytale server with all Hytale mod subprojects on the classpath.'

				t.dependsOn(stageAllModAssets)
				t.dependsOn(projectPaths.collect { path ->
					project.project(path).tasks.named(JavaPlugin.CLASSES_TASK_NAME)
				})
				t.dependsOn(hostProject.tasks.named('downloadAssetsZip'))

				t.projectPaths.set(metadata.projectPaths)
				t.expectedHytaleVersion.set(metadata.expectedHytaleVersion)
				t.expectedPatchline.set(metadata.expectedPatchline)
				t.hostProjectPath.set(hostPath)
				t.runDirectory.set(project.layout.projectDirectory.dir('run'))
				t.assetsZip.set(project.layout.file(project.provider {
					assetsZipFileProvider.get()
				}))

				t.mainClass.set('com.hypixel.hytale.Main')
				t.jvmArgs('--enable-native-access=ALL-UNNAMED')
				t.modularity.inferModulePath.set(true)

				projectPaths.each { path ->
					def subproject = project.project(path)
					def sourceSets = subproject.extensions.getByType(SourceSetContainer)
					t.classpath(sourceSets.named('main').get().runtimeClasspath)
				}

				t.classpath(hostProject.configurations.named('vineServerJar').get())

				t.doFirst {
					def assetsZipFile = assetsZipFileProvider.get()

					t.args(
							"--assets=${assetsZipFile.absolutePath}",
							'--allow-op',
							'--disable-sentry'
							)
				}
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
			"Set hytaleWorkspace.modProjects or apply 'com.azuredoom.hytale-tools' to subprojects you want included."
			)
		}

		return paths
	}

	private static WorkspaceMetadata collectWorkspaceMetadata(Project root, List<String> projectPaths) {
		def projects = projectPaths.collect { root.project(it) }

		def firstExt = projects.first().extensions.getByType(HytaleExtension)
		if (!firstExt.hytaleVersion.isPresent()) {
			throw new GradleException("runAllMods requires hytaleVersion to be set on all Hytale subprojects.")
		}

		String expectedVersion = firstExt.hytaleVersion.get()
		String expectedPatchline = firstExt.patchline.orNull

		List<String> manifestGroups = []
		List<String> modIds = []
		List<String> assetSourceDirectoryPaths = []

		projects.each { modProject ->
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

			File sourceDirFile = ext.assetPackSourceDirectory.get().asFile
			if (!sourceDirFile.exists()) {
				sourceDirFile = new File(modProject.projectDir, 'src/main/resources')
			}

			manifestGroups.add(ext.manifestGroup.get())
			modIds.add(ext.modId.get())
			assetSourceDirectoryPaths.add(sourceDirFile.absolutePath)
		}

		return new WorkspaceMetadata(
				projectPaths: projectPaths,
				expectedHytaleVersion: expectedVersion,
				expectedPatchline: expectedPatchline,
				manifestGroups: manifestGroups,
				modIds: modIds,
				assetSourceDirectoryPaths: assetSourceDirectoryPaths
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
	}
}