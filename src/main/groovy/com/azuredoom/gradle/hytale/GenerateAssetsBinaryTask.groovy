package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

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

	@TaskAction
	void generate() {
		def assetsZipFile = assetsZip.get().asFile
		def outFile = outputJar.get().asFile

		logger.lifecycle("Generating Hytale assets binary jar...")
		logger.lifecycle("Assets zip: ${assetsZipFile} (${BasicUtils.formatBytes(assetsZipFile.length())})")
		logger.lifecycle("Output jar: ${outFile}")

		outFile.parentFile.mkdirs()
		project.delete(outFile)

		def seen = new LinkedHashSet<String>()
		int assetEntriesWritten = 0
		int duplicateEntriesSkipped = 0

		new JarOutputStream(outFile.newOutputStream()).withCloseable { jos ->
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
				"Generated assets binary jar at ${outFile} " +
				"(${BasicUtils.formatBytes(outFile.length())}; " +
				"${assetEntriesWritten} asset entries written, " +
				"${duplicateEntriesSkipped} duplicates skipped)"
				)
	}
}