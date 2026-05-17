package com.azuredoom.gradle.hytale

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
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
				generatedSourcesIvyRepoDir,
				ext.patchline
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
				def serverDep = project.dependencies.create("com.hypixel.hytale:Server:${ext.hytaleVersion.get()}")
				if (serverDep instanceof org.gradle.api.artifacts.ExternalModuleDependency) {
					serverDep.changing = true
				}
				deps.add(serverDep)
			}
		}

		vineServerJar.configure { config ->
			config.resolutionStrategy { strategy ->
				strategy.cacheDynamicVersionsFor(10, 'minutes')
				strategy.cacheChangingModulesFor(10, 'minutes')
			}
		}

		def resolvedServerVersionProvider = HytaleVersionResolver.resolvedServerVersion(
				project,
				ext.hytaleVersion,
				vineServerJar
				)

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
			def version = resolvedServerVersionProvider.getOrElse(ext.hytaleVersion.get())
			new File(assetsCacheDir, "${ext.patchline.get()}-${version}.jar")
		}
		def assetsZipFileProvider = project.providers.provider {
			def version = resolvedServerVersionProvider.getOrElse(ext.hytaleVersion.get())
			new File(assetsCacheDir, "${ext.patchline.get()}-${version}-Assets.zip")
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
				vineDecompileTargets,
				assetsZipFileProvider
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
				prepareDecompiledSourcesForIde,
				resolvedServerVersionProvider
				)

		HytaleRunTaskRegistrar.register(
				project,
				ext,
				assetsZipFileProvider,
				vineServerJar
				)

		configureJavadocs(project, ext)
		configureIdeaJavadocs(project, ext)
	}

	private static void configureJavadocs(Project project, HytaleExtension ext) {
		project.plugins.withId('java') {
			project.tasks.withType(Javadoc).configureEach { Javadoc task ->
				task.doFirst {
					if (task.options instanceof StandardJavadocDocletOptions) {
						task.options.links(ext.serverJavadocsUrl.get())
					}
				}
			}
		}
	}

	private static void configureIdeaJavadocs(Project project, HytaleExtension ext) {
		project.plugins.withId('idea') {
			project.idea {
				module {
					iml.withXml { provider ->
						def root = provider.asNode()

						def component = root.children().find {
							it.name() == 'component' && it.attribute('name') == 'NewModuleRootManager'
						}

						if (component == null) {
							return
						}

						component.children().findAll {
							it.name() == 'orderEntry' && it.attribute('type') == 'module-library'
						}.each { orderEntry ->
							def library = orderEntry.children().find {
								it.name() == 'library'
							}

							if (library == null) {
								return
							}

							def classes = library.children().find {
								it.name() == 'CLASSES'
							}

							def classUrls = classes == null
									? []
									: classes.children()
									.findAll { it.name() == 'root' }
									.collect { it.attribute('url')?.toString() }

							boolean isHytaleServerLibrary = classUrls.any {
								it != null && (
										it.contains('com.hypixel.hytale') ||
										it.contains('/Server/') ||
										it.contains('\\Server\\') ||
										it.contains('Server-')
										)
							}

							if (!isHytaleServerLibrary) {
								return
							}

							def javadocs = library.children().find {
								it.name() == 'JAVADOC'
							}

							if (javadocs == null) {
								javadocs = library.appendNode('JAVADOC')
							}

							javadocs.children().clear()
							javadocs.appendNode('root', [
								url: ext.serverJavadocsUrl.get()
							])
						}
					}
				}
			}
		}
	}
}