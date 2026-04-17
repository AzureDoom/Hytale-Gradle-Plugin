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
		def outFile = outputJar.get().asFile
		outFile.parentFile.mkdirs()
		project.delete(outFile)

		def seen = new LinkedHashSet<String>()

		new JarOutputStream(outFile.newOutputStream()).withCloseable { jos ->
			new JarFile(serverJar.get().asFile).withCloseable { jar ->
				def entries = jar.entries()
				while (entries.hasMoreElements()) {
					def entry = entries.nextElement()
					if (entry.name == null || entry.name.trim().isEmpty()) {
						continue
					}
					if (!seen.add(entry.name)) {
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
				}
			}

			new ZipFile(assetsZip.get().asFile).withCloseable { zip ->
				def entries = zip.entries()
				while (entries.hasMoreElements()) {
					def entry = entries.nextElement()
					def targetName = "assets/${entry.name}"

					if (targetName == null || targetName.trim().isEmpty()) {
						continue
					}
					if (!seen.add(targetName)) {
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
				}
			}
		}

		logger.lifecycle("Merged server jar + assets into ${outFile}")
	}
}