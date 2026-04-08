package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Launches a long-running external server process")
abstract class RunServerTask extends JavaExec {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getAssetsZip()

	@Input
	abstract ListProperty<String> getServerArgs()

	@Input
	abstract ListProperty<String> getServerJvmArgs()

	protected List<String> buildResolvedJvmArgs() {
		List<String> resolvedJvmArgs = []
		resolvedJvmArgs.addAll(serverJvmArgs.getOrElse([]))
		return resolvedJvmArgs
	}

	protected List<String> buildResolvedArgs(File resolvedAssetsZip) {
		List<String> resolvedArgs = [
			"--assets=${resolvedAssetsZip.absolutePath}"
		]
		resolvedArgs.addAll(serverArgs.getOrElse([]))
		return resolvedArgs
	}

	@TaskAction
	@Override
	void exec() {
		def resolvedAssetsZip = assetsZip.get().asFile

		logger.lifecycle("Using extracted assets zip: ${resolvedAssetsZip.absolutePath}")
		logger.lifecycle("Assets exists: ${resolvedAssetsZip.exists()}, size: ${resolvedAssetsZip.exists() ? resolvedAssetsZip.length() : 0}")

		if (!resolvedAssetsZip.exists() || resolvedAssetsZip.length() == 0) {
			throw new GradleException("Assets zip not found or empty: ${resolvedAssetsZip}")
		}

		jvmArgs(buildResolvedJvmArgs())
		setArgs(buildResolvedArgs(resolvedAssetsZip))

		super.exec()
	}
}