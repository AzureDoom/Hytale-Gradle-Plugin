package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class VSCodeGeneratorFunctionalTest extends Specification {

	@TempDir
	File testProjectDir

	private void setupBasicProject(String name = 'vscode-generator-test') {
		new File(testProjectDir, 'settings.gradle') << """
            rootProject.name = '${name}'
        """.stripIndent()

		new File(testProjectDir, 'build.gradle') << '''
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            group = 'com.example'
            version = '1.0.0'

            hytaleTools {
                hytaleVersion = '1.0.0'
                manifestGroup = 'com.example.mods'
                modId = 'examplemod'
                mainClass = 'com.example.mods.ExampleMod'
            }
        '''
	}

	private Object readJson(String path) {
		new JsonSlurper().parse(new File(testProjectDir, path))
	}

	def "generateVSCodeConfig creates expected vscode files"() {
		given:
		setupBasicProject()

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('configureVSCodeHytaleRun', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')
		new File(testProjectDir, '.vscode/extensions.json').exists()
		new File(testProjectDir, '.vscode/settings.json').exists()
		new File(testProjectDir, '.vscode/launch.json').exists()
	}

	def "configureVSCodeHytaleRun writes recommended Java and Gradle extensions"() {
		given:
		setupBasicProject()

		when:
		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('configureVSCodeHytaleRun', '--stacktrace')
				.build()

		then:
		def extensions = readJson('.vscode/extensions.json')
		extensions.recommendations.contains('redhat.java')
		extensions.recommendations.contains('vscjava.vscode-gradle')
		extensions.recommendations.contains('vscjava.vscode-java-debug')
	}

	def "configureVSCodeHytaleRun writes gradle-aware vscode settings"() {
		given:
		setupBasicProject()

		when:
		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('configureVSCodeHytaleRun', '--stacktrace')
				.build()

		then:
		def settings = readJson('.vscode/settings.json')
		settings.'java.configuration.updateBuildConfiguration' == 'automatic'
		settings.'java.import.gradle.enabled' == true
		settings.'java.import.gradle.wrapper.enabled' == true
	}

	def "configureVSCodeHytaleRun writes Java attach launch configuration"() {
		given:
		setupBasicProject()

		when:
		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('configureVSCodeHytaleRun', '--stacktrace')
				.build()

		then:
		def launch = readJson('.vscode/launch.json')
		launch.version == '0.2.0'
		launch.configurations.size() == 1

		def config = launch.configurations[0]
		config.type == 'java'
		config.request == 'attach'
		config.name == 'Attach to Hytale Server'
		config.hostName == 'localhost'
		config.port == 5005
	}

	def "generateHytaleDevContainer creates devcontainer json and dockerfile by default"() {
		given:
		setupBasicProject('devcontainer-default-test')

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('generateHytaleDevContainer', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')
		new File(testProjectDir, '.devcontainer/devcontainer.json').exists()
		new File(testProjectDir, '.devcontainer/Dockerfile').exists()
	}

	def "generateHytaleDevContainer writes default dockerfile-backed devcontainer config"() {
		given:
		setupBasicProject('devcontainer-content-test')

		when:
		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('generateHytaleDevContainer', '--stacktrace')
				.build()

		then:
		def devcontainer = readJson('.devcontainer/devcontainer.json')

		devcontainer.name == 'devcontainer-content-test Hytale Dev'
		devcontainer.dockerFile == 'Dockerfile'
		devcontainer.context == '..'
		devcontainer.remoteUser == 'vscode'
		devcontainer.customizations.vscode.extensions.contains('redhat.java')
		devcontainer.customizations.vscode.extensions.contains('vscjava.vscode-gradle')
		devcontainer.customizations.vscode.extensions.contains('vscjava.vscode-java-debug')
		devcontainer.mounts.any {
			it.contains('target=/home/vscode/.gradle') && it.contains('type=bind')
		}
		!devcontainer.containsKey('postCreateCommand')
		!devcontainer.containsKey('image')
	}

	def "generateHytaleDevContainer writes dockerfile with configured base image"() {
		given:
		setupBasicProject('devcontainer-dockerfile-test')

		when:
		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('generateHytaleDevContainer', '--stacktrace')
				.build()

		then:
		def dockerfile = new File(testProjectDir, '.devcontainer/Dockerfile').text
		dockerfile.contains('FROM mcr.microsoft.com/devcontainers/java:1-25-bookworm')
		dockerfile.contains('apt-get install -y --no-install-recommends')
		dockerfile.contains('git')
		dockerfile.contains('unzip')
		dockerfile.contains('zip')
		dockerfile.contains('USER vscode')
	}

	def "generateHytaleDevContainer can generate image-based config without dockerfile"() {
		given:
		setupBasicProject('devcontainer-image-test')

		new File(testProjectDir, 'build.gradle') << '''

            tasks.named('generateHytaleDevContainer', com.azuredoom.gradle.hytale.GenerateHytaleDevContainerTask) {
                generateDockerfile.set(false)
                baseImage.set('mcr.microsoft.com/devcontainers/java:1-25-bookworm')
            }
        '''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('generateHytaleDevContainer', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')

		def devcontainer = readJson('.devcontainer/devcontainer.json')
		devcontainer.image == 'mcr.microsoft.com/devcontainers/java:1-25-bookworm'
		!devcontainer.containsKey('dockerFile')
		!devcontainer.containsKey('context')
		!new File(testProjectDir, '.devcontainer/Dockerfile').exists()
	}

	def "generateHytaleDevContainer can include setup postCreateCommand when enabled"() {
		given:
		setupBasicProject('devcontainer-post-create-test')

		new File(testProjectDir, 'build.gradle') << '''

            tasks.named('generateHytaleDevContainer', com.azuredoom.gradle.hytale.GenerateHytaleDevContainerTask) {
                runSetupAfterCreate.set(true)
            }
        '''

		when:
		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('generateHytaleDevContainer', '--stacktrace')
				.build()

		then:
		def devcontainer = readJson('.devcontainer/devcontainer.json')
		devcontainer.postCreateCommand == './gradlew setupHytaleDev || true'
	}

	def "generator tasks are up-to-date on second run"() {
		given:
		setupBasicProject('generator-uptodate-test')

		when:
		def first = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('configureVSCodeHytaleRun', 'generateHytaleDevContainer', '--stacktrace')
				.build()

		def second = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('configureVSCodeHytaleRun', 'generateHytaleDevContainer', '--stacktrace')
				.build()

		then:
		first.output.contains('BUILD SUCCESSFUL')
		second.output.contains(':configureVSCodeHytaleRun UP-TO-DATE')
		second.output.contains(':generateHytaleDevContainer UP-TO-DATE')
	}
}