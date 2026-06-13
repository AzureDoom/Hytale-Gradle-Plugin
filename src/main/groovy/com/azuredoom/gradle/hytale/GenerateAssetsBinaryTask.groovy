package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Builds a dedicated IDE binary jar from Assets.zip")
abstract class GenerateAssetsBinaryTask extends DefaultTask {

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getAssetsZip()

	@OutputFile
	abstract RegularFileProperty getOutputJar()

	@Input
	abstract Property<String> getServerVersion()

	@Input
	abstract Property<String> getPatchline()

	@Internal
	abstract DirectoryProperty getGradleUserHomeDirectory()

	@TaskAction
	void generate() {
		def assetsZipFile = assetsZip.get().asFile
		def outFile = outputJar.get().asFile

		File globalCacheJar  = resolveGlobalCacheJar()
		File globalStampFile = new File(globalCacheJar.parentFile, "${globalCacheJar.name}.complete")

		if (globalStampFile.exists() && globalCacheJar.exists() && globalCacheJar.length() > 0) {
			logger.lifecycle(
					"GenerateAssetsBinary: global cache hit for {}-{}, copying cached jar",
					patchline.get(), serverVersion.get()
					)
			outFile.parentFile.mkdirs()
			Files.copy(globalCacheJar.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
			return
		}

		logger.lifecycle("Generating Hytale assets binary jar...")
		logger.lifecycle("Assets zip: ${assetsZipFile} (${BasicUtils.formatBytes(assetsZipFile.length())})")
		logger.lifecycle("Output jar: ${outFile}")

		outFile.parentFile.mkdirs()
		def tmpOutFile = new File(new File(temporaryDir, 'outputs'), outFile.name + '.part')
		deleteIfExistsQuietly(tmpOutFile)
		tmpOutFile.parentFile.mkdirs()

		def seen = new LinkedHashSet<String>()
		int assetEntriesWritten = 0
		int duplicateEntriesSkipped = 0

		try {
			new JarOutputStream(tmpOutFile.newOutputStream()).withCloseable { jos ->
				logger.lifecycle("Embedding asset entries under assets/...")

				new ZipFile(assetsZipFile).withCloseable { zip ->
					def entries = zip.entries()
					while (entries.hasMoreElements()) {
						def entry = entries.nextElement()
						def targetName = "assets/${entry.name}"

						if (targetName == null || targetName.trim().isEmpty()) {
							continue
						}
						if (!seen.add(targetName)) {
							duplicateEntriesSkipped++
							continue
						}

						def newEntry = new ZipEntry(targetName)
						newEntry.time = entry.time
						jos.putNextEntry(newEntry)
						if (!entry.isDirectory()) {
							zip.getInputStream(entry).withCloseable { input ->
								input.transferTo(jos)
							}
						}
						jos.closeEntry()
						assetEntriesWritten++
					}
				}
			}

			moveIntoPlace(tmpOutFile, outFile)
		} finally {
			deleteIfExistsQuietly(tmpOutFile)
		}

		logger.lifecycle(
				"Generated assets binary jar at ${outFile} " +
				"(${BasicUtils.formatBytes(outFile.length())}; " +
				"${assetEntriesWritten} asset entries written, " +
				"${duplicateEntriesSkipped} duplicates skipped)"
				)

		populateGlobalCache(outFile, globalCacheJar, globalStampFile)
	}

	File resolveGlobalCacheJar() {
		return new File(
				gradleUserHomeDirectory.get().asFile,
				"caches/hytale-assets/${patchline.get()}-${serverVersion.get()}-assets-binary.jar"
				)
	}

	private void populateGlobalCache(File builtJar, File cacheJar, File stampFile) {
		try {
			cacheJar.parentFile.mkdirs()
			Files.copy(builtJar.toPath(), cacheJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
			BasicUtils.atomicWrite(stampFile, "${patchline.get()}-${serverVersion.get()}")
			logger.lifecycle("GenerateAssetsBinary: cached assets jar to {}", cacheJar)
		} catch (Exception e) {
			logger.warn("GenerateAssetsBinary: failed to write global cache (non-fatal): {}", e.message)
		}
	}

	private static void deleteIfExistsQuietly(File file) {
		if (file == null) {
			return
		}
		try {
			Files.deleteIfExists(file.toPath())
		} catch (Exception ignored) {
		}
	}

	private static void moveIntoPlace(File from, File to) {
		Files.createDirectories(to.parentFile.toPath())
		try {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
		}
	}
}
