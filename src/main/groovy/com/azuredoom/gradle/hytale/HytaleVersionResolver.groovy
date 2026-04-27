package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.provider.Provider

final class HytaleVersionResolver {
	static final String SERVER_GROUP = 'com.hypixel.hytale'
	static final String SERVER_MODULE = 'Server'

	private HytaleVersionResolver() {}

	static boolean isDynamic(String version) {
		if (version == null || version.isEmpty()) {
			return false
		}
		if (version.endsWith('+')) {
			return true
		}
		if (version.startsWith('latest.')) {
			return true
		}
		if (version.startsWith('[') || version.startsWith('(')) {
			return true
		}
		return false
	}

	static Provider<String> resolvedServerVersion(
			Project project,
			Provider<String> configuredVersion,
			Provider<Configuration> serverConfiguration
	) {
		project.providers.provider {
			def configured = configuredVersion.getOrNull()
			if (configured == null || configured.isEmpty()) {
				return null
			}
			if (!isDynamic(configured)) {
				return configured
			}

			def configuration = serverConfiguration.get()
			def root
			try {
				root = configuration.incoming.resolutionResult.root
			} catch (Exception e) {
				throw new GradleException(
				"Could not resolve dynamic Hytale server version '${configured}'. " +
				"Run with --refresh-dependencies, verify the configured patchline, " +
				"and check that the Hytale Maven repository is reachable.",
				e)
			}

			def resolved = findServerVersion(root)
			if (resolved == null) {
				throw new GradleException(
				"Resolved configuration 'vineServerJar' did not contain a " +
				"${SERVER_GROUP}:${SERVER_MODULE} component. " +
				"Configured version selector was '${configured}'.")
			}
			return resolved
		}
	}

	private static String findServerVersion(ResolvedComponentResult root) {
		for (def dependency : root.dependencies) {
			if (dependency instanceof UnresolvedDependencyResult) {
				def unresolved = dependency as UnresolvedDependencyResult
				throw new GradleException(
				"Could not resolve Hytale server dependency: ${unresolved.attempted}",
				unresolved.failure)
			}
			if (!(dependency instanceof ResolvedDependencyResult)) {
				continue
			}
			def selected = (dependency as ResolvedDependencyResult).selected
			def id = selected.moduleVersion
			if (id != null && id.group == SERVER_GROUP && id.name == SERVER_MODULE) {
				return id.version
			}
		}
		return null
	}
}
