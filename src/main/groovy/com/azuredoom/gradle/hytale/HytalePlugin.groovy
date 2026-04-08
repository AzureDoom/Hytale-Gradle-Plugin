package com.azuredoom.gradle.hytale

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

class HytalePlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		project.pluginManager.apply('java')

		def ext = project.extensions.create('hytaleTools', HytaleExtension)
		HytaleExtensionDefaults.apply(project, ext)

		def generatedSourcesMavenRepoDir = project.layout.buildDirectory.dir('generated-sources-m2')
		def generatedSourcesIvyRepoDir = project.layout.buildDirectory.dir('generated-sources-ivy')

		HytaleRepositoryConfigurer.configure(
				project,
				generatedSourcesMavenRepoDir,
				generatedSourcesIvyRepoDir
				)
		HytaleConfigurationConfigurer.configure(project)

		def vineflowerTool = project.configurations.maybeCreate('vineflowerTool')
		vineflowerTool.canBeConsumed = false
		vineflowerTool.canBeResolved = true
		project.dependencies.add('vineflowerTool', 'org.vineflower:vineflower:1.11.2')

		def vineServerJar = project.configurations.named('vineServerJar')
		def vineDependencyJars = project.configurations.named('vineDependencyJars')
		def vineImplementation = project.configurations.named('vineImplementation')
		def vineCompileOnly = project.configurations.named('vineCompileOnly')
		def vineDecompileTargets = project.configurations.named('vineDecompileTargets')
		def hytaleBundledRuntime = project.configurations.named('hytaleBundledRuntime')

		vineServerJar.get().defaultDependencies { deps ->
			if (ext.hytaleVersion.isPresent()) {
				deps.add(project.dependencies.create("com.hypixel.hytale:Server:${ext.hytaleVersion.get()}"))
			}
		}

		hytaleBundledRuntime.get().defaultDependencies { deps ->
			deps.add(project.dependencies.create(
					'com.azuredoom.hytale:hytale-asset-editor-runtime:0.2.0'
					))
		}

		project.tasks.named('jar', Jar).configure {
			duplicatesStrategy = DuplicatesStrategy.EXCLUDE

			if (ext.bundleAssetEditorRuntime.getOrElse(true)) {
				from({
					hytaleBundledRuntime.get()
							.resolve()
							.findAll { it.name.endsWith('.jar') }
							.collect { project.zipTree(it) }
				})
			}
		}

		def assetsCacheDir = new File(project.gradle.gradleUserHomeDir, 'caches/hytale-assets')
		def authCacheDir = new File(project.gradle.gradleUserHomeDir, 'caches/hytale-auth')

		def wrapperFileProvider = project.providers.provider {
			new File(assetsCacheDir, "${ext.patchline.get()}-${ext.hytaleVersion.get()}.jar")
		}
		def assetsZipFileProvider = project.providers.provider {
			new File(assetsCacheDir, "${ext.patchline.get()}-${ext.hytaleVersion.get()}-Assets.zip")
		}
		def tokenFileProvider = project.providers.provider {
			new File(authCacheDir, 'tokens.json')
		}

		def prepareDecompiledSourcesForIde = HytaleIdeSourceConfigurer.register(
				project,
				ext,
				generatedSourcesMavenRepoDir,
				generatedSourcesIvyRepoDir,
				vineflowerTool,
				vineServerJar,
				vineDependencyJars,
				vineImplementation,
				vineCompileOnly,
				vineDecompileTargets
				)

		HytaleCoreTaskRegistrar.register(
				project,
				ext,
				wrapperFileProvider,
				assetsZipFileProvider,
				tokenFileProvider,
				generatedSourcesMavenRepoDir,
				vineServerJar,
				vineImplementation,
				vineCompileOnly,
				vineDecompileTargets,
				prepareDecompiledSourcesForIde
				)

		project.dependencies.add(
				'hytaleAssets',
				project.files(project.provider {
					def assetsZip = assetsZipFileProvider.get()
					assetsZip.exists() ? [assetsZip] : []
				})
				)

		HytaleRunTaskRegistrar.register(
				project,
				ext,
				assetsZipFileProvider,
				vineServerJar
				)
	}
}