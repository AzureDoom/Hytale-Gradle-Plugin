package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class HytalePluginIdeaIntegrationSpec extends Specification {

	private static final String PLUGIN_ID = 'com.azuredoom.hytale-tools'

	def "registers generated source repositories when plugin is applied"() {
		given:
		Project project = ProjectBuilder.builder().build()

		when:
		project.pluginManager.apply(PLUGIN_ID)

		then:
		project.repositories.any { it.name == 'Generated Decompiled Sources' }
		project.repositories.any { it.name == 'Generated Decompiled Sources Ivy' }
	}

	def "wires idea task to prepareDecompiledSourcesForIde when idea is applied before plugin"() {
		given:
		Project project = ProjectBuilder.builder().build()
		project.pluginManager.apply('idea')

		when:
		project.pluginManager.apply(PLUGIN_ID)

		then:
		hasTaskDependency(project, 'idea', 'prepareDecompiledSourcesForIde')
	}

	def "wires idea task to prepareDecompiledSourcesForIde when idea is applied after plugin"() {
		given:
		Project project = ProjectBuilder.builder().build()
		project.pluginManager.apply(PLUGIN_ID)

		when:
		project.pluginManager.apply('idea')

		then:
		hasTaskDependency(project, 'idea', 'prepareDecompiledSourcesForIde')
	}

	def "does not require idea plugin to be present"() {
		given:
		Project project = ProjectBuilder.builder().build()

		when:
		project.pluginManager.apply(PLUGIN_ID)

		then:
		!project.tasks.names.contains('idea')
		noExceptionThrown()
	}

	private static boolean hasTaskDependency(Project project, String taskName, String dependencyName) {
		def provider = project.tasks.named(taskName)

		def deps = provider.map { task ->
			task.taskDependencies.getDependencies(task)*.name
		}.get()

		return deps.contains(dependencyName)
	}
}