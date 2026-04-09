package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.JavaExec
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Launches a Java process to run Hytale with workspace mods and does not produce reusable cacheable outputs")
abstract class RunAllModsTask extends JavaExec {

	@Input
	abstract ListProperty<String> getProjectPaths()

	@Input
	abstract Property<String> getExpectedHytaleVersion()

	@Input
	abstract Property<String> getExpectedPatchline()

	@Input
	abstract Property<String> getHostProjectPath()

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getAssetsZip()

	@Internal
	abstract DirectoryProperty getRunDirectory()

	@Input
	String getAssetsZipPathForCache() {
		return assetsZip.present ? assetsZip.get().asFile.absolutePath : ""
	}

	@Override
	void exec() {
		if (!assetsZip.present) {
			throw new GradleException("Assets zip is not configured for runAllMods.")
		}

		File zip = assetsZip.get().asFile
		if (!zip.exists() || zip.length() == 0) {
			throw new GradleException("Assets zip not found or empty: ${zip}")
		}

		super.setWorkingDir(runDirectory.get().asFile)
		super.exec()
	}
}