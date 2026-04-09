package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@DisableCachingByDefault(because = "Stages mod assets by creating symlinks, junctions, or copying files depending on platform and filesystem support")
abstract class StageAllModAssetsTask extends DefaultTask {

	@Input
	abstract ListProperty<String> getProjectPaths()

	@Input
	abstract ListProperty<String> getManifestGroups()

	@Input
	abstract ListProperty<String> getModIds()

	@Input
	abstract ListProperty<String> getAssetSourceDirectoryPaths()

	@Input
	abstract Property<String> getExpectedHytaleVersion()

	@Input
	abstract Property<String> getExpectedPatchline()

	@OutputDirectory
	abstract DirectoryProperty getRunDirectory()

	@OutputDirectory
	abstract DirectoryProperty getModsDirectory()

	@TaskAction
	void stageAssets() {
		List<String> sourceDirPaths = assetSourceDirectoryPaths.get()
		List<String> groups = manifestGroups.get()
		List<String> ids = modIds.get()

		if (sourceDirPaths.size() != groups.size() || sourceDirPaths.size() != ids.size()) {
			throw new GradleException("Workspace asset metadata is inconsistent.")
		}

		File runDirFile = runDirectory.get().asFile
		File modsDirFile = modsDirectory.get().asFile
		runDirFile.mkdirs()
		modsDirFile.mkdirs()

		for (int i = 0; i < sourceDirPaths.size(); i++) {
			File sourceDirFile = new File(sourceDirPaths[i])
			if (!sourceDirFile.exists()) {
				throw new GradleException("Asset pack source directory does not exist: ${sourceDirFile}")
			}

			Path sourceDir = sourceDirFile.toPath().toAbsolutePath().normalize()
			Path targetDir = new File(
					modsDirFile,
					"${groups[i].replace('.', '_')}_${ids[i]}"
					).toPath().toAbsolutePath().normalize()

			if (Files.exists(targetDir)) {
				deleteRecursively(targetDir)
			}

			targetDir.parent.toFile().mkdirs()
			createLinkJunctionOrCopy(sourceDir, targetDir)
		}
	}

	private void createLinkJunctionOrCopy(Path sourceDir, Path targetDir) {
		try {
			Path relativeSource = targetDir.parent.relativize(sourceDir)
			Files.createSymbolicLink(targetDir, relativeSource)
			logger.lifecycle("Created symlink ${targetDir} -> ${relativeSource}")
			return
		} catch (Exception ex) {
			logger.warn("Symlink creation failed, attempting fallback: ${ex.message}")
		}

		if (System.getProperty('os.name').toLowerCase().contains('win')) {
			def process = new ProcessBuilder(
					'cmd', '/c', 'mklink', '/J',
					targetDir.toString(),
					sourceDir.toString()
					).redirectErrorStream(true).start()

			def output = process.inputStream.text
			def code = process.waitFor()

			if (code == 0 && Files.exists(targetDir)) {
				logger.lifecycle("Created junction ${targetDir} -> ${sourceDir}")
				return
			}

			logger.warn(
					"Junction creation failed, falling back to copy.\n" +
					"Target: ${targetDir}\n" +
					"Source: ${sourceDir}\n" +
					"Output:\n${output}"
					)
		}

		copyDirectory(sourceDir, targetDir)
		logger.lifecycle("Copied asset pack ${sourceDir} -> ${targetDir}")
	}

	private static void copyDirectory(Path source, Path target) {
		Files.walk(source).forEach { path ->
			Path relative = source.relativize(path)
			Path destination = target.resolve(relative)

			if (Files.isDirectory(path)) {
				Files.createDirectories(destination)
			} else {
				if (destination.parent != null) {
					Files.createDirectories(destination.parent)
				}
				Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
			}
		}
	}

	private static void deleteRecursively(Path path) {
		if (!Files.exists(path)) {
			return
		}

		Files.walk(path)
				.sorted(Comparator.reverseOrder())
				.forEach { Files.deleteIfExists(it) }
	}
}