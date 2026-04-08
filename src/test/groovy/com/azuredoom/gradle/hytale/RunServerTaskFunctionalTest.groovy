package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class RunServerTaskFunctionalTest extends Specification {

	@TempDir
	File projectDir

	def "runServer task is registered"() {
		given:
		setupBasicProject()

		when:
		def result = GradleRunner.create()
				.withProjectDir(projectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--all')
				.build()

		then:
		result.output.contains('runServer')
	}

	def "preRunTask is hooked into runServer"() {
		given:
		new File(projectDir, 'settings.gradle') << "rootProject.name='test'"

		new File(projectDir, 'build.gradle') << """
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools'
}

tasks.register('myPreTask') {
    doLast { println 'pre task ran' }
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.test'
    modId = 'testmod'
    mainClass = 'com.test.Main'
    preRunTask = 'myPreTask'
}
"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(projectDir)
				.withPluginClasspath()
				.withArguments('runServer', '--dry-run')
				.build()

		then:
		result.output.contains('myPreTask')
	}

	private void setupBasicProject() {
		new File(projectDir, 'settings.gradle') << "rootProject.name='test'"

		new File(projectDir, 'build.gradle') << """
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools'
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.test'
    modId = 'testmod'
    mainClass = 'com.test.Main'
}
"""
	}
}