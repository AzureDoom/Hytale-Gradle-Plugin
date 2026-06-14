package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc

final class HytaleCoreTaskRegistrar {
	private HytaleCoreTaskRegistrar() {}

	static void register(
			Project project,
			HytaleExtension ext,
			Provider<File> wrapperFileProvider,
			Provider<File> assetsZipFileProvider,
			Provider<File> tokenFileProvider,
			def generatedSourcesMavenRepoDir,
			NamedDomainObjectProvider<Configuration> vineServerJar,
			NamedDomainObjectProvider<Configuration> vineImplementation,
			NamedDomainObjectProvider<Configuration> vineCompileOnly,
			NamedDomainObjectProvider<Configuration> vineDecompileTargets,
			TaskProvider<? extends Task> prepareDecompiledSourcesForIde,
			Provider<String> resolvedServerVersionProvider
	) {
		project.tasks.register('createManifestIfMissing', CreateManifestIfMissingTask) {
			group = null
			description = 'Creates src/main/resources/manifest.json with a default structure when it is missing.'
			manifestFile.set(ext.manifestFile)
		}

		project.tasks.register('updatePluginManifest', UpdatePluginManifestTask) {
			group = 'hytale'
			description = 'Updates src/main/resources/manifest.json from Gradle properties and plugin extension values.'

			manifestFile.set(ext.manifestFile)
			manifestGroup.set(ext.manifestGroup)
			modId.set(ext.modId)
			versionString.set(project.provider { project.version.toString() })
			modDescription.set(ext.modDescription)
			modCredits.set(ext.modCredits)
			modUrl.set(ext.modUrl)
			hytaleVersion.set(ext.hytaleVersion)
			manifestServerVersion.set(
					ext.manifestServerVersion
					.orElse(resolvedServerVersionProvider.map { ">=${it}" })
					)
			resolvedServerVersion.set(resolvedServerVersionProvider)
			manifestDependencies.set(ext.manifestDependencies)
			manifestOptionalDependencies.set(ext.manifestOptionalDependencies)
			disabledByDefault.set(ext.disabledByDefault)
			mainClass.set(ext.mainClass)
			includesPack.set(ext.includesPack)
			curseforgeId.set(ext.curseforgeId)
			subPlugins.set(ext.subPlugins)
		}

		project.tasks.named('updatePluginManifest').configure {
			dependsOn('createManifestIfMissing')
		}

		project.tasks.register('validateManifest', ValidateManifestTask) {
			group = null
			description = 'Validates src/main/resources/manifest.json against required fields and plugin configuration.'

			manifestFile.set(ext.manifestFile)
			manifestGroup.set(ext.manifestGroup)
			modId.set(ext.modId)
			mainClass.set(ext.mainClass)
			hytaleVersion.set(ext.hytaleVersion)
			manifestServerVersion.set(
					ext.manifestServerVersion
					.orElse(resolvedServerVersionProvider.map { ">=${it}" })
					)
			resolvedServerVersion.set(resolvedServerVersionProvider)
			manifestDependencies.set(ext.manifestDependencies)
			manifestOptionalDependencies.set(ext.manifestOptionalDependencies)
			includesPack.set(ext.includesPack)
		}

		project.tasks.named('validateManifest').configure {
			dependsOn('updatePluginManifest')
		}

		def directAssetsZipFileProvider = project.providers.provider {
			HytaleAssetsResolver.resolveDirectOverrideAssetsZip(
					ext.hytaleHomeOverride.orNull,
					ext.patchline.get()
					)
		}

		def effectiveAssetsZipFileProvider = project.providers.provider {
			def directAssetsZip = directAssetsZipFileProvider.getOrNull()
			if (directAssetsZip != null) {
				return directAssetsZip
			}

			def cached = assetsZipFileProvider.get()
			return cached instanceof File ? cached : cached.asFile
		}

		def downloadAssetsZipTask = project.tasks.register('downloadAssetsZip', DownloadAssetsZipTask) {
			group = null
			description = 'Downloads the authenticated Hytale asset wrapper and extracts the inner Assets.zip'

			hytaleVersion.set(resolvedServerVersionProvider.orElse(ext.hytaleVersion))
			patchline.set(ext.patchline)
			oauthBaseUrl.set(ext.oauthBaseUrl)
			accountBaseUrl.set(ext.accountBaseUrl)
			hytaleHomeOverride.set(ext.hytaleHomeOverride)
			resolvedAssetsWrapper.set(project.layout.file(wrapperFileProvider))
			resolvedAssetsZip.set(project.layout.file(assetsZipFileProvider))
			tokenCacheFile.set(project.layout.file(tokenFileProvider))
		}

		project.tasks.withType(JavaCompile).configureEach {
			mustRunAfter(downloadAssetsZipTask)
		}

		project.tasks.withType(Javadoc).configureEach {
			mustRunAfter(downloadAssetsZipTask)
		}

		project.tasks.register('hytaleDoctor', HytaleDoctorTask) {
			group = 'hytale'
			description = 'Prints a diagnostic summary of Hytale plugin configuration and resolution.'

			hytaleVersion.set(ext.hytaleVersion)
			patchline.set(ext.patchline)
			manifestFile.set(ext.manifestFile)
			runDirectory.set(ext.runDirectory)
			assetsZip.fileProvider(effectiveAssetsZipFileProvider)
			wrapperFile.set(project.layout.file(wrapperFileProvider))
			tokenCacheFile.set(project.layout.file(tokenFileProvider))
			vineServerJarFiles.from(vineServerJar)
			vineImplementationDependencies.set(project.provider {
				vineImplementation.get().allDependencies.collect { HytaleDependencySupport.dependencyNotation(it) }
			})
			vineCompileOnlyDependencies.set(project.provider {
				vineCompileOnly.get().allDependencies.collect { HytaleDependencySupport.dependencyNotation(it) }
			})
			vineDecompileTargetDependencies.set(project.provider {
				vineDecompileTargets.get().allDependencies.collect { HytaleDependencySupport.dependencyNotation(it) }
			})
		}

		project.tasks.register('setupHytaleDev', SetupHytaleDevTask) {
			group = 'hytale'
			description = 'Prepares local development by validating configuration, generating IDE sources, and downloading assets.'

			dependsOn(prepareDecompiledSourcesForIde)
			dependsOn(project.provider {
				directAssetsZipFileProvider.getOrNull() == null ? [downloadAssetsZipTask] : []
			})

			hytaleVersion.set(resolvedServerVersionProvider.orElse(ext.hytaleVersion))
			assetsZip.fileProvider(effectiveAssetsZipFileProvider)
			generatedSourcesMavenRepo.set(generatedSourcesMavenRepoDir)
			vineServerJarDependencies.set(project.provider {
				vineServerJar.get().allDependencies.collect { HytaleDependencySupport.dependencyNotation(it) }
			})
		}

		project.tasks.register('configureVSCodeHytaleRun', ConfigureVSCodeHytaleRunTask) {
			group = 'hytale'
			description = 'Generates VS Code workspace settings, extension recommendations, and Java debugger attach config.'

			vscodeDirectory.set(project.rootProject.layout.projectDirectory.dir('.vscode'))

			recommendedExtensions.set([
				'redhat.java',
				'vscjava.vscode-gradle',
				'vscjava.vscode-java-debug'
			])

			settings.automaticGradleBuildConfigUpdate.set(true)
			settings.importGradleEnabled.set(true)
			settings.useGradleWrapper.set(true)

			launchConfig.name.set('Attach to Hytale Server')
			launchConfig.hostName.set('localhost')
			launchConfig.port.set(5005)
		}

		project.tasks.register('generateDevContainer', GenerateDevContainerTask) {
			group = 'hytale'
			description = 'Generates an optional VS Code Dev Container for Hytale plugin development.'

			devContainerDirectory.set(project.rootProject.layout.projectDirectory.dir('.devcontainer'))

			containerName.set("${project.rootProject.name} Hytale Dev")
			baseImage.set('mcr.microsoft.com/devcontainers/java:1-25-bookworm')

			vscodeExtensions.set([
				'redhat.java',
				'vscjava.vscode-gradle',
				'vscjava.vscode-java-debug'
			])

			generateDockerfile.set(true)
			runSetupAfterCreate.set(false)
			mountGradleCache.set(true)
		}
	}
}