package com.azuredoom.gradle.hytale

import org.gradle.api.Project

final class HytaleConfigurationConfigurer {
	private HytaleConfigurationConfigurer() {}

	static void configure(Project project) {
		def implementation = project.configurations.named('implementation').get()
		def compileOnly = project.configurations.named('compileOnly').get()

		def vineImplementation = project.configurations.maybeCreate('vineImplementation')
		vineImplementation.canBeConsumed = false
		vineImplementation.canBeResolved = false

		def vineCompileOnly = project.configurations.maybeCreate('vineCompileOnly')
		vineCompileOnly.canBeConsumed = false
		vineCompileOnly.canBeResolved = false

		def vineDecompileTargets = project.configurations.maybeCreate('vineDecompileTargets')
		vineDecompileTargets.canBeConsumed = false
		vineDecompileTargets.canBeResolved = false

		def vineDecompileClasspath = project.configurations.maybeCreate('vineDecompileClasspath')
		vineDecompileClasspath.canBeConsumed = false
		vineDecompileClasspath.canBeResolved = true

		def vineServerJar = project.configurations.maybeCreate('vineServerJar')
		vineServerJar.canBeConsumed = false
		vineServerJar.canBeResolved = true

		def vineDependencyJars = project.configurations.maybeCreate('vineDependencyJars')
		vineDependencyJars.canBeConsumed = false
		vineDependencyJars.canBeResolved = true

		def vineMod = project.configurations.maybeCreate('vineMod')
		vineMod.canBeConsumed = false
		vineMod.canBeResolved = false

		def vineModJars = project.configurations.maybeCreate('vineModJars')
		vineModJars.canBeConsumed = false
		vineModJars.canBeResolved = true

		def vineImplementationJars = project.configurations.maybeCreate('vineImplementationJars')
		vineImplementationJars.canBeConsumed = false
		vineImplementationJars.canBeResolved = true

		def hytaleBundledRuntime = project.configurations.maybeCreate('hytaleBundledRuntime')
		hytaleBundledRuntime.canBeConsumed = false
		hytaleBundledRuntime.canBeResolved = true
		hytaleBundledRuntime.transitive = false

		def hytaleAssets = project.configurations.maybeCreate('hytaleAssets')
		hytaleAssets.canBeConsumed = false
		hytaleAssets.canBeResolved = false

		def requiredDependency = project.configurations.maybeCreate('requiredDependency')
		requiredDependency.canBeConsumed = false
		requiredDependency.canBeResolved = false

		def optionalDependency = project.configurations.maybeCreate('optionalDependency')
		optionalDependency.canBeConsumed = false
		optionalDependency.canBeResolved = false

		implementation.extendsFrom(vineImplementation, hytaleBundledRuntime, requiredDependency)
		compileOnly.extendsFrom(vineCompileOnly, vineServerJar, hytaleAssets, optionalDependency)
		vineDependencyJars.extendsFrom(vineDecompileTargets, vineCompileOnly, vineImplementation, requiredDependency, optionalDependency)
		vineDecompileClasspath.extendsFrom(vineCompileOnly, vineImplementation, vineServerJar, requiredDependency, optionalDependency)
		vineModJars.extendsFrom(vineMod)
		vineImplementationJars.extendsFrom(vineImplementation)
	}
}