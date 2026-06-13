package com.azuredoom.gradle.hytale

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.ArchiveOperations
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
		def archiveOperations = project.services.get(ArchiveOperations)

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
		project.dependencies.add('vineflowerTool', 'org.vineflower:vineflower:1.12.0')

		def vineServerJar = project.configurations.named('vineServerJar')
		def vineDependencyJars = project.configurations.named('vineDependencyJars')
		def vineImplementation = project.configurations.named('vineImplementation')
		def vineCompileOnly = project.configurations.named('vineCompileOnly')
		def vineDecompileTargets = project.configurations.named('vineDecompileTargets')
		def hytaleBundledRuntime = project.configurations.named('hytaleBundledRuntime')

		vineServerJar.get().defaultDependencies { deps ->
			if (ext.hytaleVersion.isPresent()) {
				def serverDep = project.dependencies.create("com.hypixel.hytale:Server:${ext.hytaleVersion.get()}")
				if (serverDep instanceof ExternalModuleDependency) {
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

		project.tasks.named('jar', Jar).configure { Jar jarTask ->
			duplicatesStrategy = DuplicatesStrategy.EXCLUDE

			if (ext.bundleAssetEditorRuntime.getOrElse(true)) {
				from(project.providers.provider {
					hytaleBundledRuntime.get()
							.resolve()
							.findAll { it.name.endsWith('.jar') }
							.collect { archiveOperations.zipTree(it) }
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
				assetsZipFileProvider,
				resolvedServerVersionProvider
				)

		def assetsBinaryJar = project.layout.buildDirectory.file('generated-ide-binaries/assets/hytale-assets.jar')
		project.dependencies.add('compileOnly', project.files(assetsBinaryJar))

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
		configureIdeaSourcesAndJavadocs(project, ext, generatedSourcesMavenRepoDir)
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

	private static void configureIdeaSourcesAndJavadocs(Project project, HytaleExtension ext, def generatedSourcesMavenRepoDir) {
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

							def sourcesNode = library.children().find { it.name() == 'SOURCES' }
							if (sourcesNode == null) {
								sourcesNode = library.appendNode('SOURCES')
							}
							sourcesNode.children().clear()

							def sourcesJarFile = findServerSourcesJar(classUrls, generatedSourcesMavenRepoDir.get().asFile)
							if (sourcesJarFile != null && sourcesJarFile.exists()) {
								sourcesNode.appendNode('root', [
									url: "jar://${sourcesJarFile.absolutePath.replace('\\', '/')}!/"
								])
							}

							def javadocs = library.children().find { it.name() == 'JAVADOC' }
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

	private static File findServerSourcesJar(List<String> classUrls, File mavenRepoDir) {
		for (String url : classUrls) {
			if (url == null) continue

				String path = url
			if (path.startsWith('jar://')) path = path.substring('jar://'.length())
			if (path.endsWith('!/')) path = path.substring(0, path.length() - 2)

			File binaryJar = new File(path)

			if (!binaryJar.absolutePath.startsWith(mavenRepoDir.absolutePath)) {
				continue
			}

			String name = binaryJar.name
			if (!name.endsWith('.jar')) continue

				String base = name.substring(0, name.length() - '.jar'.length())
			File sourcesJar = new File(binaryJar.parentFile, "${base}-sources.jar")
			if (sourcesJar.exists()) {
				return sourcesJar
			}
		}

		if (mavenRepoDir.exists()) {
			File serverDir = new File(mavenRepoDir, 'com/hypixel/hytale/Server')
			if (serverDir.isDirectory()) {
				def found = null
				serverDir.eachFileRecurse { f ->
					if (f.name.endsWith('-sources.jar') && found == null) {
						found = f
					}
				}
				if (found != null) return found
			}
		}

		return null
	}
}