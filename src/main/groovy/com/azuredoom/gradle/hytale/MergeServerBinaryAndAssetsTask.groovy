package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Builds a merged IDE binary jar from the resolved server jar plus Assets.zip")
abstract class MergeServerBinaryAndAssetsTask extends DefaultTask {

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getServerJar()

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getAssetsZip()

	@OutputFile
	abstract RegularFileProperty getOutputJar()

	@TaskAction
	void merge() {
		def serverJarFile = serverJar.get().asFile
		def assetsZipFile = assetsZip.get().asFile
		def outFile = outputJar.get().asFile

		logger.lifecycle("Merging Hytale server jar with assets...")
		logger.lifecycle("Server jar: ${serverJarFile} (${formatBytes(serverJarFile.length())})")
		logger.lifecycle("Assets zip: ${assetsZipFile} (${formatBytes(assetsZipFile.length())})")
		logger.lifecycle("Output jar: ${outFile}")

		outFile.parentFile.mkdirs()
		project.delete(outFile)

		def seen = new LinkedHashSet<String>()
		int serverEntriesWritten = 0
		int assetEntriesWritten = 0
		int duplicateEntriesSkipped = 0

		new JarOutputStream(outFile.newOutputStream()).withCloseable { jos ->
			logger.lifecycle("Copying server jar entries...")

			new JarFile(serverJarFile).withCloseable { jar ->
				def entries = jar.entries()
				while (entries.hasMoreElements()) {
					def entry = entries.nextElement()
					if (entry.name == null || entry.name.trim().isEmpty()) {
						continue
					}
					if (!seen.add(entry.name)) {
						duplicateEntriesSkipped++
						continue
					}

					def newEntry = new ZipEntry(entry.name)
					newEntry.time = entry.time
					jos.putNextEntry(newEntry)
					if (!entry.isDirectory()) {
						jar.getInputStream(entry).withCloseable { input ->
							input.transferTo(jos)
						}
					}
					jos.closeEntry()
					serverEntriesWritten++
				}
			}

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

		logger.lifecycle(
				"Merged server jar + assets into ${outFile} " +
				"(${formatBytes(outFile.length())}; " +
				"${serverEntriesWritten} server entries, " +
				"${assetEntriesWritten} asset entries, " +
				"${duplicateEntriesSkipped} duplicates skipped)"
				)
	}

	protected static String formatBytes(long bytes) {
		if (bytes < 1024L) return "${bytes} B"
		def units = ['KiB', 'MiB', 'GiB', 'TiB']
		double value = bytes
		int unitIndex = -1
		while (value >= 1024D && unitIndex < units.size() - 1) {
			value /= 1024D
			unitIndex++
		}
		String.format(Locale.ROOT, '%.1f %s', value, units[unitIndex])
	}
}