package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider

final class HytaleCoreTaskRegistrar {
	private HytaleCoreTaskRegistrar() {}

	static void register(
			Project project,
			HytaleExtension ext,
			def wrapperFileProvider,
			def assetsZipFileProvider,
			def tokenFileProvider,
			def generatedSourcesMavenRepoDir,
			NamedDomainObjectProvider<Configuration> vineServerJar,
			NamedDomainObjectProvider<Configuration> vineImplementation,
			NamedDomainObjectProvider<Configuration> vineCompileOnly,
			NamedDomainObjectProvider<Configuration> vineDecompileTargets,
			TaskProvider<? extends Task> prepareDecompiledSourcesForIde
	) {
		project.tasks.register('createManifestIfMissing', CreateManifestIfMissingTask) {
			group = 'hytale'
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
			manifestDependencies.set(ext.manifestDependencies)
			manifestOptionalDependencies.set(ext.manifestOptionalDependencies)
			disabledByDefault.set(ext.disabledByDefault)
			mainClass.set(ext.mainClass)
			includesPack.set(ext.includesPack)
			curseforgeId.set(ext.curseforgeId)
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
			manifestDependencies.set(ext.manifestDependencies)
			manifestOptionalDependencies.set(ext.manifestOptionalDependencies)
			includesPack.set(ext.includesPack)
		}

		project.tasks.named('validateManifest').configure {
			dependsOn('updatePluginManifest')
		}

		project.tasks.register('downloadAssetsZip', DownloadAssetsZipTask) {
			group = 'hytale'
			description = 'Downloads the authenticated Hytale asset wrapper and extracts the inner Assets.zip'

			hytaleVersion.set(ext.hytaleVersion)
			patchline.set(ext.patchline)
			oauthBaseUrl.set(ext.oauthBaseUrl)
			accountBaseUrl.set(ext.accountBaseUrl)
			hytaleHomeOverride.set(project.providers.gradleProperty('hytale_home'))
			resolvedAssetsWrapper.set(project.layout.file(wrapperFileProvider))
			resolvedAssetsZip.set(project.layout.file(assetsZipFileProvider))
			tokenCacheFile.set(project.layout.file(tokenFileProvider))
		}

		project.tasks.register('hytaleDoctor', HytaleDoctorTask) {
			group = 'hytale'
			description = 'Prints a diagnostic summary of Hytale plugin configuration and resolution.'

			hytaleVersion.set(ext.hytaleVersion)
			patchline.set(ext.patchline)
			manifestFile.set(ext.manifestFile)
			runDirectory.set(ext.runDirectory)
			assetsZip.set(project.layout.file(assetsZipFileProvider))
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

			dependsOn(prepareDecompiledSourcesForIde, 'downloadAssetsZip')

			hytaleVersion.set(ext.hytaleVersion)
			assetsZip.set(project.layout.file(assetsZipFileProvider))
			generatedSourcesMavenRepo.set(generatedSourcesMavenRepoDir)
			vineServerJarDependencies.set(project.provider {
				vineServerJar.get().allDependencies.collect { HytaleDependencySupport.dependencyNotation(it) }
			})
		}
	}
}