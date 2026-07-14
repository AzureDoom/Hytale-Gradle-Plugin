package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

final class HytaleRepositoryConfigurer {
	private HytaleRepositoryConfigurer() {}

	static final String HYTALE_GROUP = 'com.hypixel.hytale'
	static final String RELEASE_REPO_URL = 'https://maven.hytale.com/release'
	static final String PRE_RELEASE_REPO_URL = 'https://maven.hytale.com/pre-release'
	static final String MODTALE_API_BASE = 'https://api.modtale.net'

	static void configure(Project project,
			Provider<?> generatedSourcesMavenRepoDir,
			Provider<?> generatedSourcesIvyRepoDir,
			Provider<String> patchlineProvider,
			HytaleExtension ext) {
		project.repositories.mavenCentral()

		project.repositories.maven { MavenArtifactRepository repo ->
			repo.name = 'Generated Decompiled Sources'
			repo.url = generatedSourcesMavenRepoDir.get().asFile.toURI()
		}

		project.repositories.ivy { IvyArtifactRepository repo ->
			repo.name = 'Generated Decompiled Sources Ivy'
			repo.url = generatedSourcesIvyRepoDir.get().asFile.toURI()
			repo.patternLayout { layout ->
				layout.ivy('[organisation]/[module]/[revision]/ivy-[revision].xml')
				layout.artifact('[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]')
			}
			repo.metadataSources { sources ->
				sources.ivyDescriptor()
				sources.artifact()
			}
		}

		addHytaleServerRepos(project, patchlineProvider)

		if (!ext.disableHytaleModsInfoMaven.get()) {
			addMavenRepo(project, 'Hytale-Mods.info Maven', 'https://maven.hytale-mods.dev/releases')
		}
		if (!ext.disablePlaceholderApiMaven.get()) {
			addMavenRepo(project, 'PlaceholderAPI', 'https://repo.helpch.at/releases/')
		}
		if (!ext.disableCurseMaven.get()) {
			addMavenRepo(project, 'CurseMaven', 'https://cursemaven.com')
		}
		if (!ext.disableAzureDoomMaven.get()) {
			addMavenRepo(project, 'AzureDoom Maven', 'https://maven.azuredoom.com/mods')
		}
		if (!ext.disableHytaleModdingMaven.get()) {
			addMavenRepo(project, 'Hytale Modding Maven', 'https://maven.hytalemodding.dev/releases')
		}

		if (!ext.disableModtaleResolver.get()) {
			setupModtaleResolver(project)
			applyAliasSubstitutions(project, 'modtale')
		}
		if (!ext.disableModifoldRepo.get()) {
			addModifoldRepo(project)
			applyAliasSubstitutions(project, 'modifold')
		}
	}

	private static void setupModtaleResolver(Project project) {
		File mavenCache = project.layout.buildDirectory.dir('modtale-maven-cache').get().asFile

		project.repositories.exclusiveContent { spec ->
			spec.forRepository {
				project.repositories.maven { MavenArtifactRepository repo ->
					repo.name = 'Modtale Local Cache'
					repo.url = mavenCache.toURI()
				}
			}
			spec.filter { filter ->
				filter.includeGroup('modtale')
			}
		}

		project.afterEvaluate {
			project.configurations.each { Configuration config ->
				config.dependencies.each { dep ->
					if (dep.group != 'modtale') return

						String rawModule = dep.name
					int underscore = rawModule.indexOf('_')
					String projectId = (underscore > 0 && underscore < rawModule.length() - 1)
							? rawModule.substring(underscore + 1)
							: rawModule
					String version = dep.version

					File artifactDir = new File(mavenCache, "modtale/${projectId}/${version}")
					File cachedJar   = new File(artifactDir, "${projectId}-${version}.jar")
					File cachedPom   = new File(artifactDir, "${projectId}-${version}.pom")

					if (cachedJar.exists() && cachedPom.exists()) return

						project.logger.lifecycle("[Modtale] Resolving ${projectId}:${version}...")

					try {
						String apiUrl = "${MODTALE_API_BASE}/api/v1/projects/${projectId}/versions/${version}/download-url"
						String downloadUrl = fetchJsonDownloadUrl(apiUrl, project)

						if (downloadUrl.startsWith('/')) {
							downloadUrl = "${MODTALE_API_BASE}/api/v1${downloadUrl}"
						}

						artifactDir.mkdirs()

						project.logger.lifecycle("[Modtale] Downloading from ${downloadUrl}")
						streamToFile(downloadUrl, cachedJar)

						cachedPom.text = """\
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>modtale</groupId>
  <artifactId>${projectId}</artifactId>
  <version>${version}</version>
  <packaging>jar</packaging>
</project>
"""
						project.logger.lifecycle("[Modtale] Cached to ${cachedJar}")
					} catch (Exception e) {
						throw new RuntimeException("[Modtale] Failed to resolve ${projectId}:${version}: ${e.message}", e)
					}
				}
			}
		}
	}

	private static String fetchJsonDownloadUrl(String apiUrl, Project project) {
		HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection()
		conn.setRequestMethod('GET')
		conn.setRequestProperty('Accept', 'application/json')
		conn.setInstanceFollowRedirects(true)
		conn.connect()

		int code = conn.responseCode
		if (code != 200) {
			String body = ''
			try {
				body = conn.errorStream?.text
			} catch (ignored) {}
			throw new RuntimeException("HTTP ${code} from ${apiUrl}: ${body}")
		}

		String body = conn.inputStream.text
		project.logger.lifecycle("[Modtale] download-url response: ${body}")

		def json = new JsonSlurper().parseText(body)
		String url = json.downloadUrl as String
		if (!url) throw new RuntimeException("No downloadUrl field in response: ${body}")
		return url
	}

	private static void streamToFile(String fileUrl, File dest) {
		HttpURLConnection conn = (HttpURLConnection) URI.create(fileUrl).toURL().openConnection()
		conn.setInstanceFollowRedirects(true)
		conn.connect()

		int code = conn.responseCode
		if (code != 200) {
			String body = ''
			try {
				body = conn.errorStream?.text
			} catch (ignored) {}
			throw new RuntimeException("HTTP ${code} from ${fileUrl}: ${body}")
		}

		conn.inputStream.withStream { is ->
			dest.withOutputStream { os -> os << is }
		}
	}

	private static String resolveActivePatchline(Provider<String> patchlineProvider) {
		if (patchlineProvider == null) {
			return 'release'
		}
		def value = patchlineProvider.getOrNull()
		if (value == null || value.trim().isEmpty()) {
			return 'release'
		}
		return value.trim()
	}

	private static void addHytaleServerRepos(Project project, Provider<String> patchlineProvider) {
		project.repositories.maven { MavenArtifactRepository repo ->
			repo.name = 'Hytale Server Release'
			repo.url = project.uri(RELEASE_REPO_URL)
		}
		project.repositories.maven { MavenArtifactRepository repo ->
			repo.name = 'Hytale Server Pre-Release'
			repo.url = project.uri(PRE_RELEASE_REPO_URL)
		}

		project.afterEvaluate {
			String active = resolveActivePatchline(patchlineProvider)
			boolean wantPreRelease = active.equalsIgnoreCase('pre-release') ||
					active.equalsIgnoreCase('prerelease')

			String inactiveRepoName = wantPreRelease ?
					'Hytale Server Release' : 'Hytale Server Pre-Release'

			project.repositories.named(inactiveRepoName).configure { repo ->
				repo.content { content ->
					content.excludeGroup(HYTALE_GROUP)
				}
			}
		}
	}

	private static void addMavenRepo(Project project, String repoName, String repoUrl) {
		project.repositories.maven { MavenArtifactRepository repo ->
			repo.name = repoName
			repo.url = project.uri(repoUrl)
		}
	}

	private static void addModifoldRepo(Project project) {
		project.repositories.exclusiveContent { spec ->
			spec.forRepository {
				project.repositories.ivy { IvyArtifactRepository repo ->
					repo.name = 'Modifold'
					repo.url = project.uri('https://api.modifold.com')
					repo.patternLayout { layout ->
						layout.artifact('projects/[module]/versions/[revision]/download')
					}
					repo.metadataSources { sources ->
						sources.artifact()
					}
				}
			}
			spec.filter { filter ->
				filter.includeGroup('modifold')
			}
		}
	}

	private static void applyAliasSubstitutions(Project project, String group) {
		project.configurations.configureEach { config ->
			config.resolutionStrategy.dependencySubstitution { substitutions ->
				substitutions.all { dependency ->
					def requested = dependency.requested
					if (!(requested instanceof ModuleComponentSelector)) return
						if (requested.group != group) return

						String module = requested.module
					int underscore = module.indexOf('_')
					if (underscore <= 0 || underscore >= module.length() - 1) return

						String resolvedModule = module.substring(underscore + 1)
					dependency.useTarget(
							substitutions.module("${group}:${resolvedModule}:${requested.version}")
							)
				}
			}
		}
	}
}