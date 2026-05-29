package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.result.ResolvedArtifactResult

import java.nio.file.Files
import java.nio.file.StandardCopyOption

final class HytaleDependencySupport {
	private HytaleDependencySupport() {}

	static String dependencyNotation(def dependency) {
		def group = dependency.hasProperty('group') ? dependency.group : null
		def name = dependency.hasProperty('name') ? dependency.name : null
		def version = dependency.hasProperty('version') ? dependency.version : null

		if (group && name && version) {
			return "${group}:${name}:${version}"
		}
		if (group && name) {
			return "${group}:${name}"
		}
		dependency.toString()
	}

	static void validateDecompileDependency(ExternalModuleDependency dependency) {
		if (!dependency.group || !dependency.name || !dependency.version) {
			throw new GradleException("Dependencies used for decompilation must use full GAV coordinates. Found: ${dependency}")
		}
	}

	static Configuration detachedNonTransitiveConfiguration(Project project, ModuleDependency dependency) {
		def detached = project.configurations.detachedConfiguration(dependency.copy())
		detached.canBeConsumed = false
		detached.canBeResolved = true
		detached.transitive = false
		return detached
	}

	static File singleFile(Configuration configuration, String displayName) {
		def files = configuration.files

		if (files.isEmpty()) {
			throw new GradleException("Could not resolve artifact for declared dependency ${displayName}")
		}

		if (files.size() > 1) {
			throw new GradleException("Expected exactly one artifact for declared dependency ${displayName}, got ${files.size()}")
		}

		return files.iterator().next()
	}

	static Set<ResolvedArtifactResult> resolveArtifacts(Project project, String configurationName) {
		def configuration = project.configurations.named(configurationName).get()
		def artifactView = configuration.incoming.artifactView { view ->
			view.lenient(true)
		}
		artifactView.artifacts.artifacts.findAll { it instanceof ResolvedArtifactResult } as Set<ResolvedArtifactResult>
	}

	static Map<String, File> mavenRepoFiles(File repoRoot, String group, String module, String version) {
		def groupPath = group.replace('.', '/')
		def moduleDir = new File(repoRoot, "${groupPath}/${module}/${version}")

		[
			binaryJar : new File(moduleDir, "${module}-${version}.jar"),
			sourcesJar: new File(moduleDir, "${module}-${version}-sources.jar"),
			descriptor: new File(moduleDir, "${module}-${version}.pom")
		]
	}

	static Map<String, File> ivyRepoFiles(File repoRoot, String group, String module, String version) {
		def groupPath = group.replace('.', '/')
		def moduleDir = new File(repoRoot, "${groupPath}/${module}/${version}")

		[
			binaryJar : new File(moduleDir, "${module}-${version}.jar"),
			sourcesJar: new File(moduleDir, "${module}-${version}-sources.jar"),
			descriptor: new File(moduleDir, "ivy-${version}.xml")
		]
	}

	private static File prepareRepoModuleDir(Map<String, File> files) {
		def moduleDir = files.binaryJar.parentFile
		moduleDir.mkdirs()
		moduleDir
	}

	private static void copyRepoArtifacts(
			File moduleDir,
			String module,
			String version,
			File binaryJarFile,
			File sourcesJarFile
	) {
		moduleDir.mkdirs()

		Files.copy(
				binaryJarFile.toPath(),
				new File(moduleDir, "${module}-${version}.jar").toPath(),
				StandardCopyOption.REPLACE_EXISTING
				)

		Files.copy(
				sourcesJarFile.toPath(),
				new File(moduleDir, "${module}-${version}-sources.jar").toPath(),
				StandardCopyOption.REPLACE_EXISTING
				)
	}

	static void installModuleIntoMavenRepo(
			File repoRoot,
			String group,
			String module,
			String version,
			File binaryJarFile,
			File sourcesJarFile
	) {
		def files = mavenRepoFiles(repoRoot, group, module, version)
		def moduleDir = prepareRepoModuleDir(files)
		copyRepoArtifacts(moduleDir, module, version, binaryJarFile, sourcesJarFile)

		files.descriptor.text = """<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>${group}</groupId>
  <artifactId>${module}</artifactId>
  <version>${version}</version>
  <packaging>jar</packaging>
</project>
"""
	}

	static void installModuleIntoIvyRepo(
			File repoRoot,
			String group,
			String module,
			String version,
			File binaryJarFile,
			File sourcesJarFile
	) {
		def files = ivyRepoFiles(repoRoot, group, module, version)
		def moduleDir = prepareRepoModuleDir(files)
		copyRepoArtifacts(moduleDir, module, version, binaryJarFile, sourcesJarFile)

		files.descriptor.text = """<ivy-module version="2.0">
  <info organisation="${group}" module="${module}" revision="${version}"/>
</ivy-module>
"""
	}
}