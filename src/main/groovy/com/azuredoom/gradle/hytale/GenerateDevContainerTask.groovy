package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateDevContainerTask extends DefaultTask {

	@OutputDirectory
	abstract DirectoryProperty getDevContainerDirectory()

	@Input
	abstract Property<String> getContainerName()

	@Input
	abstract Property<String> getBaseImage()

	@Input
	abstract ListProperty<String> getVscodeExtensions()

	@Input
	abstract Property<Boolean> getGenerateDockerfile()

	@Input
	abstract Property<Boolean> getRunSetupAfterCreate()

	@Input
	abstract Property<Boolean> getMountGradleCache()

	@TaskAction
	void generate() {
		File dir = devContainerDirectory.get().asFile
		dir.mkdirs()

		if (generateDockerfile.get()) {
			new File(dir, 'Dockerfile').text = dockerfileText()
		}

		Map<String, Object> json = [
			name      : containerName.get(),
			customizations: [
				vscode: [
					extensions: vscodeExtensions.get()
				]
			],
			remoteUser: 'vscode'
		]

		if (generateDockerfile.get()) {
			json.dockerFile = 'Dockerfile'
			json.context = '..'
		} else {
			json.image = baseImage.get()
		}

		if (mountGradleCache.get()) {
			json.mounts = [
				'source=${localEnv:HOME}/.gradle,target=/home/vscode/.gradle,type=bind,consistency=cached'
			]
		}

		if (runSetupAfterCreate.get()) {
			json.postCreateCommand = './gradlew setupHytaleDev || true'
		}

		writeJson(new File(dir, 'devcontainer.json'), json)

		logger.lifecycle("Generated Dev Container configuration in ${dir}")
	}

	private String dockerfileText() {
		return """\
FROM ${baseImage.get()}

USER root

RUN apt-get update \\
    && apt-get install -y --no-install-recommends \\
        git \\
        unzip \\
        zip \\
    && rm -rf /var/lib/apt/lists/*

USER vscode
""".stripIndent()
	}

	private static void writeJson(File file, Object value) {
		file.parentFile.mkdirs()
		file.text = JsonOutput.prettyPrint(JsonOutput.toJson(value)) + System.lineSeparator()
	}
}