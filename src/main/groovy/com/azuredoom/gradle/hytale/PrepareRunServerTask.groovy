package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

@DisableCachingByDefault(because = "Creates platform-specific symlinks or junctions in the run directory")
abstract class PrepareRunServerTask extends DefaultTask {
	@OutputDirectory
	abstract DirectoryProperty getRunDirectory()

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	abstract DirectoryProperty getAssetPackSourceDirectory()

	@OutputDirectory
	abstract DirectoryProperty getAssetPackRunDirectory()

	@TaskAction
	void prepare() {
		File runDir = runDirectory.get().asFile
		File srcDir = assetPackSourceDirectory.get().asFile
		File dstDir = assetPackRunDirectory.get().asFile

		if (!srcDir.exists()) {
			throw new GradleException("Asset pack source directory does not exist: ${srcDir}")
		}

		runDir.mkdirs()
		dstDir.parentFile.mkdirs()

		Path source = srcDir.toPath().toAbsolutePath().normalize()
		Path target = dstDir.toPath().toAbsolutePath().normalize()

		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			if (!isSameFile(target, source)) {
				logger.lifecycle("Replacing existing staged asset pack at ${target} with live link to ${source}")
				project.delete(dstDir)
			} else {
				logger.info("Run asset pack already linked correctly: ${target} -> ${source}")
				return
			}
		}

		if (BasicUtils.createSymlinkOrWindowsJunction(source, target, "run asset pack", logger)) {
			return
		}

		throw new GradleException("Failed to create symlink for run asset pack: ${target} -> ${source}")
	}

	private static boolean isSameFile(Path target, Path source) {
		try {
			return Files.isSameFile(target, source)
		} catch (IOException ignored) {
			return false
		}
	}
}