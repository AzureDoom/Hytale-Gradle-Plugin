package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.StandardCopyOption

@DisableCachingByDefault(because = "Copies a resolved server binary for IDE use")
abstract class CopyServerBinaryTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getInputJar()

	@OutputFile
	abstract RegularFileProperty getOutputJar()

	@TaskAction
	void copy() {
		File input = inputJar.get().asFile
		File output = outputJar.get().asFile

		output.parentFile.mkdirs()

		Files.copy(
				input.toPath(),
				output.toPath(),
				StandardCopyOption.REPLACE_EXISTING
				)

		logger.lifecycle("Copied server binary to ${output} (${BasicUtils.formatBytes(output.length())})")
	}
}