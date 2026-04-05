package com.azuredoom.gradle.hytale

import org.gradle.api.Project

final class HytaleConfigurationConfigurer {
    private HytaleConfigurationConfigurer() {}

    static void configure(Project project) {
        def implementation = project.configurations.getByName('implementation')
        def compileOnly = project.configurations.getByName('compileOnly')

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

        implementation.extendsFrom(vineImplementation)
        compileOnly.extendsFrom(vineCompileOnly, vineServerJar)
        vineDependencyJars.extendsFrom(vineDecompileTargets, vineCompileOnly, vineImplementation)
        vineDecompileClasspath.extendsFrom(vineCompileOnly, vineImplementation, vineServerJar)
    }
}
