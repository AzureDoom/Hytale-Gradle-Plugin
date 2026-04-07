package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class HytalePluginIdeaFunctionalSpec extends Specification {

	private static final String PLUGIN_ID = 'com.azuredoom.hytale-tools'

	@TempDir
	File testProjectDir

	def "configures generated source repositories and idea task dependency in a real build"() {
		given:
		writeFile('settings.gradle', """
            rootProject.name = 'idea-integration-test'
        """)

		writeFile('build.gradle', """
            plugins {
                id 'java'
                id 'idea'
                id '${PLUGIN_ID}'
            }

            tasks.register('verifyIdeaIntegration') {
                doLast {
                    def ideaTask = tasks.named('idea').get()
                    def dependencyNames = ideaTask.taskDependencies.getDependencies(ideaTask)*.name

                    if (!dependencyNames.contains('prepareDecompiledSourcesForIde')) {
                        throw new GradleException(
                            "Expected idea task to depend on prepareDecompiledSourcesForIde, " +
                            "but got: " + dependencyNames
                        )
                    }

                    if (repositories.findByName('Generated Decompiled Sources') == null) {
                        throw new GradleException("Missing repository: Generated Decompiled Sources")
                    }

                    if (repositories.findByName('Generated Decompiled Sources Ivy') == null) {
                        throw new GradleException("Missing repository: Generated Decompiled Sources Ivy")
                    }
                }
            }
        """)

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('verifyIdeaIntegration', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')
	}

	def "still wires idea task when plugin is applied before idea plugin"() {
		given:
		writeFile('settings.gradle', """
            rootProject.name = 'idea-order-test'
        """)

		writeFile('build.gradle', """
            plugins {
                id '${PLUGIN_ID}'
                id 'idea'
            }

            tasks.register('verifyIdeaOrderIntegration') {
                doLast {
                    def ideaTask = tasks.named('idea').get()
                    def dependencyNames = ideaTask.taskDependencies.getDependencies(ideaTask)*.name

                    if (!dependencyNames.contains('prepareDecompiledSourcesForIde')) {
                        throw new GradleException(
                            "Expected idea task to depend on prepareDecompiledSourcesForIde, " +
                            "but got: " + dependencyNames
                        )
                    }
                }
            }
        """)

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('verifyIdeaOrderIntegration', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')
	}

	private void writeFile(String relativePath, String content) {
		File file = new File(testProjectDir, relativePath)
		file.parentFile?.mkdirs()
		file.text = content.stripIndent().trim() + System.lineSeparator()
	}
}