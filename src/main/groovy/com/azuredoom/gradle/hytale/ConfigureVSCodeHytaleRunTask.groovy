package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class ConfigureVSCodeHytaleRunTask extends DefaultTask {

	@OutputDirectory
	abstract DirectoryProperty getVscodeDirectory()

	@Input
	abstract ListProperty<String> getRecommendedExtensions()

	@Nested
	abstract VSCodeSettings getSettings()

	@Nested
	abstract VSCodeLaunchConfig getLaunchConfig()

	@TaskAction
	void generate() {
		File dir = vscodeDirectory.get().asFile
		dir.mkdirs()

		writeJson(
				new File(dir, 'extensions.json'),
				[
					recommendations: recommendedExtensions.get()
				]
				)

		writeJson(
				new File(dir, 'settings.json'),
				settings.asMap()
				)

		writeJson(
				new File(dir, 'launch.json'),
				[
					version       : '0.2.0',
					configurations: [
						launchConfig.asAttachConfiguration()
					]
				]
				)

		logger.lifecycle("Generated VS Code configuration in ${dir}")
	}

	private static void writeJson(File file, Object value) {
		file.parentFile.mkdirs()
		file.text = JsonOutput.prettyPrint(JsonOutput.toJson(value)) + System.lineSeparator()
	}

	static abstract class VSCodeSettings {
		@Input
		abstract Property<Boolean> getAutomaticGradleBuildConfigUpdate()

		@Input
		abstract Property<Boolean> getImportGradleEnabled()

		@Input
		abstract Property<Boolean> getUseGradleWrapper()

		Map<String, Object> asMap() {
			return [
				'java.configuration.updateBuildConfiguration': automaticGradleBuildConfigUpdate.get() ? 'automatic' : 'interactive',
				'java.import.gradle.enabled'                : importGradleEnabled.get(),
				'java.import.gradle.wrapper.enabled'        : useGradleWrapper.get()
			]
		}
	}

	static abstract class VSCodeLaunchConfig {
		@Input
		abstract Property<String> getName()

		@Input
		abstract Property<String> getHostName()

		@Input
		abstract Property<Integer> getPort()

		Map<String, Object> asAttachConfiguration() {
			return [
				type       : 'java',
				name       : name.get(),
				request    : 'attach',
				hostName   : hostName.get(),
				port       : port.get()
			]
		}
	}
}