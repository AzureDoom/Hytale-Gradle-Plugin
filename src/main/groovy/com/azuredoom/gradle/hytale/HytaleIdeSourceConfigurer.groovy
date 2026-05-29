package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

final class HytaleIdeSourceConfigurer {
	private HytaleIdeSourceConfigurer() {}

	static TaskProvider<? extends Task> register(
			Project project,
			HytaleExtension ext,
			def generatedSourcesMavenRepoDir,
			def generatedSourcesIvyRepoDir,
			Configuration vineflowerTool,
			NamedDomainObjectProvider<Configuration> vineServerJar,
			NamedDomainObjectProvider<Configuration> vineDependencyJars,
			NamedDomainObjectProvider<Configuration> vineImplementation,
			NamedDomainObjectProvider<Configuration> vineCompileOnly,
			NamedDomainObjectProvider<Configuration> vineDecompileTargets,
			def assetsZipFileProvider
	) {
		def vineflowerJarFile = project.layout.file(project.provider { vineflowerTool.singleFile } as Provider<File>)
		def serverJarFile = project.layout.file(project.provider { vineServerJar.get().singleFile } as Provider<File>)
		def decompiledServerDir = project.layout.buildDirectory.dir('vineflower/hytale-server')
		def directAssetsZipFile = project.providers.provider {
			HytaleAssetsResolver.resolveDirectOverrideAssetsZip(
					ext.hytaleHomeOverride.orNull,
					ext.patchline.get()
					)
		}
		def assetsZipFile = project.layout.file(project.provider {
			def direct = directAssetsZipFile.getOrNull()
			if (direct != null) {
				return direct
			}

			def fallback = assetsZipFileProvider.get()
			return fallback.hasProperty('asFile') ? fallback.asFile : fallback
		} as Provider<File>)
		def serverBinaryJar = project.layout.buildDirectory.file('generated-ide-binaries/server/hytale-server.jar')
		def assetsBinaryJar = project.layout.buildDirectory.file('generated-ide-binaries/assets/hytale-assets.jar')

		project.tasks.register('decompileServerJar', DecompileServerJarTask) {
			group = null
			description = 'Decompile only com/hypixel/hytale from the Hytale Server jar into build/vineflower/hytale-server/'

			serverJar.set(serverJarFile)
			vineflowerJar.set(vineflowerJarFile)
			decompileClasspath.from(vineServerJar)
			outputDirectory.set(decompiledServerDir)
			tempDirectoryRoot.set(project.layout.buildDirectory.dir('tmp/vineflower-server'))
			javaVersion.set(ext.javaVersion)
		}

		def injectServerJavadocsIntoDecompiledSources = project.tasks.register(
				'injectServerJavadocsIntoDecompiledSources',
				InjectServerJavadocsIntoDecompiledSourcesTask
				) {
					group = null
					description = 'Injects hosted Hytale Server API Javadocs into the Vineflower-generated server sources.'

					dependsOn('decompileServerJar')

					sourceDirectory.set(decompiledServerDir)
					outputDirectory.set(decompiledServerDir)
					serverJavadocsUrl.set(ext.serverJavadocsUrl)
					patchline.set(ext.patchline)
					gradleUserHomeDirectory.set(project.layout.dir(project.provider {
						project.gradle.gradleUserHomeDir
					}))
				}

		def copyServerBinary = project.tasks.register('copyServerBinary', CopyServerBinaryTask) {
			group = null
			description = 'Copies the resolved Hytale server jar to the IDE binary output location.'

			inputJar.set(serverJarFile)
			outputJar.set(serverBinaryJar)
		}

		def generateAssetsBinary = project.tasks.register('generateAssetsBinary', GenerateAssetsBinaryTask) {
			group = null
			description = 'Packages Assets.zip into a dedicated hytale-assets.jar for IDE External Libraries.'

			dependsOn(project.provider {
				directAssetsZipFile.getOrNull() != null ? [] : ['downloadAssetsZip']
			})

			assetsZip.set(assetsZipFile)
			outputJar.set(assetsBinaryJar)
		}

		Provider<Set<ResolvedArtifactResult>> serverArtifactsProvider = project.providers.provider {
			def configuration = vineServerJar.get()
			def artifactView = configuration.incoming.artifactView { view ->
				view.lenient(true)
			}
			artifactView.artifacts.artifacts.findAll {
				it instanceof ResolvedArtifactResult
			} as Set<ResolvedArtifactResult>
		}

		def installDependencySourcesToRepo = project.tasks.register('installDependencyDecompiledSourcesToRepo') {
			group = null
			description = 'Installs generated dependency sources jars into local repos for IDE attachment.'
		}

		def serverSourcesJar = project.tasks.register('serverDecompiledSourcesJar', Jar) {
			group = null
			description = 'Packages decompiled Hytale server sources as a sources jar.'

			dependsOn(injectServerJavadocsIntoDecompiledSources)

			archiveBaseName.set('hytale-server-decompiled')
			archiveVersion.set(project.provider { project.version.toString() })
			archiveClassifier.set('sources')

			destinationDirectory.set(project.layout.buildDirectory.dir('generated-sources-jars/server'))
			from(decompiledServerDir)
		}

		def installServerSourcesToRepo = project.tasks.register(
				'installServerDecompiledSourcesToRepo',
				InstallModuleSourcesToRepoTask
				) {
					group = null
					description = 'Installs generated server sources jar into local repos for IDE attachment.'

					dependsOn(serverSourcesJar)

					def artifactProvider = project.providers.provider {
						def artifacts = serverArtifactsProvider.get()
						if (!artifacts || artifacts.size() != 1) {
							throw new GradleException("Expected exactly one resolved artifact in vineServerJar, got ${artifacts?.size() ?: 0}")
						}

						def artifact = artifacts.iterator().next()
						def componentId = artifact.id.componentIdentifier

						if (!(componentId instanceof ModuleComponentIdentifier)) {
							throw new GradleException("vineServerJar artifact is not a module component: ${componentId}")
						}

						[
							group  : componentId.group,
							module : componentId.module,
							version: componentId.version,
							file   : artifact.file
						]
					}

					groupId.set(artifactProvider.map { it.group })
					moduleId.set(artifactProvider.map { it.module })
					moduleVersion.set(artifactProvider.map { it.version })
					binaryJar.set(project.layout.file(artifactProvider.map { it.file }))
					sourcesJar.set(serverSourcesJar.flatMap { it.archiveFile })

					mavenRepoDir.set(generatedSourcesMavenRepoDir)
					ivyRepoDir.set(generatedSourcesIvyRepoDir)
				}

		def seenDependencyTaskKeys = [] as Set<String>
		def registerDeclaredDependency = { ExternalModuleDependency declaredDep ->
			HytaleDependencySupport.validateDecompileDependency(declaredDep)

			def taskKey = "${declaredDep.group}:${declaredDep.name}:${declaredDep.version}"
			if (!seenDependencyTaskKeys.add(taskKey)) {
				return
			}

			registerDependencyInstallTasks(
					project,
					declaredDep,
					vineflowerJarFile,
					vineDependencyJars,
					generatedSourcesMavenRepoDir,
					generatedSourcesIvyRepoDir,
					installDependencySourcesToRepo,
					ext
					)
		}

		[
			vineDecompileTargets,
			vineCompileOnly,
			vineImplementation
		].each { cfg ->
			cfg.configure { configuration ->
				configuration.dependencies
						.withType(ExternalModuleDependency)
						.configureEach { dep ->
							registerDeclaredDependency(dep)
						}
			}
		}

		def prepareDecompiledSourcesForIde = project.tasks.register('prepareDecompiledSourcesForIde') {
			group = null
			description = 'Builds and installs generated decompiled sources for IDE source attachment.'

			dependsOn(installServerSourcesToRepo)
			dependsOn(project.provider { ext.generateAssetsBinary.get() ? [generateAssetsBinary] : [] })
			dependsOn(installDependencySourcesToRepo)
		}

		project.pluginManager.withPlugin('idea') {
			project.tasks.named('idea').configure {
				dependsOn(prepareDecompiledSourcesForIde)
			}
		}

		prepareDecompiledSourcesForIde
	}

	private static void registerDependencyInstallTasks(
			Project project,
			ExternalModuleDependency declaredDep,
			Provider<?> vineflowerJarFile,
			NamedDomainObjectProvider<Configuration> vineDependencyJars,
			def generatedSourcesMavenRepoDir,
			def generatedSourcesIvyRepoDir,
			def installDependencySourcesToRepo,
			HytaleExtension ext
	) {
		def artifactGroup = declaredDep.group
		def artifactModule = declaredDep.name
		def artifactVersion = declaredDep.version

		def safeName = "${artifactGroup}__${artifactModule}__${artifactVersion}".replaceAll('[^A-Za-z0-9_.-]', '_')
		def perArtifactDir = project.layout.buildDirectory.dir("vineflower/dependencies/${safeName}")

		def resolvedBinaryJarConfiguration = HytaleDependencySupport.detachedNonTransitiveConfiguration(
				project,
				declaredDep
				)

		def resolvedBinaryJar = project.layout.file(project.provider {
			HytaleDependencySupport.singleFile(
					resolvedBinaryJarConfiguration,
					"${artifactGroup}:${artifactModule}:${artifactVersion}"
					)
		})

		def decompileTask = project.tasks.register("decompile_${safeName}", DecompileDependencyJarTask) {
			group = null
			description = "Internal: Decompile dependency ${artifactGroup}:${artifactModule}:${artifactVersion}"

			inputJar.set(resolvedBinaryJar)
			vineflowerJar.set(vineflowerJarFile)
			decompileClasspath.from(vineDependencyJars)
			outputDirectory.set(perArtifactDir)
			tempDirectoryRoot.set(project.layout.buildDirectory.dir("tmp/vineflower-deps/${safeName}"))
			javaVersion.set(ext.javaVersion)
		}

		def sourcesJarTask = project.tasks.register("sourcesJar_${safeName}", Jar) {
			group = null
			description = "Internal: Package decompiled sources for ${artifactGroup}:${artifactModule}:${artifactVersion}"

			dependsOn(decompileTask)

			archiveBaseName.set(artifactModule)
			archiveVersion.set(artifactVersion)
			archiveClassifier.set('sources')

			destinationDirectory.set(project.layout.buildDirectory.dir("generated-sources-jars/dependencies/${safeName}"))
			from(perArtifactDir)
		}

		def installTask = project.tasks.register(
				"installSources_${safeName}",
				InstallModuleSourcesToRepoTask
				) {
					group = null
					description = "Internal: Install generated sources for ${artifactGroup}:${artifactModule}:${artifactVersion} into local repos"

					dependsOn(sourcesJarTask)

					groupId.set(artifactGroup)
					moduleId.set(artifactModule)
					moduleVersion.set(artifactVersion)

					binaryJar.set(resolvedBinaryJar)
					sourcesJar.set(sourcesJarTask.flatMap { it.archiveFile })

					mavenRepoDir.set(generatedSourcesMavenRepoDir)
					ivyRepoDir.set(generatedSourcesIvyRepoDir)
				}

		installDependencySourcesToRepo.configure {
			dependsOn(installTask)
		}
	}
}