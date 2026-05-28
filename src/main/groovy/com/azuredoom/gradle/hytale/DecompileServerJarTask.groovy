package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaToolchainService

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

import javax.inject.Inject

@CacheableTask
abstract class DecompileServerJarTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getServerJar()

	@Classpath
	abstract ConfigurableFileCollection getDecompileClasspath()

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getVineflowerJar()

	@OutputDirectory
	abstract DirectoryProperty getOutputDirectory()

	@OutputDirectory
	abstract DirectoryProperty getTempDirectoryRoot()

	@Input
	abstract Property<Integer> getJavaVersion()

	@Inject
	abstract JavaToolchainService getJavaToolchainService()

	@TaskAction
	void decompile() {
		def outDir = outputDirectory.get().asFile
		def tempDir = tempDirectoryRoot.get().asFile
		BasicUtils.recreateDirectories(outDir, tempDir)

		def server = serverJar.get().asFile
		if (!server.exists()) {
			throw new GradleException('Could not resolve Hytale Server jar from vineServerJar')
		}

		def filteredJar = new File(tempDir, 'Server-com-hytale-only.jar')
		writeFilteredJar(server, filteredJar, 'com/hypixel/hytale/')

		if (!filteredJar.exists() || filteredJar.length() == 0) {
			throw new GradleException("Filtered jar is empty. Nothing matched com/hypixel/hytale/** inside ${server.name}")
		}

		def cmd = [
			BasicUtils.javaExecutableFor(javaToolchainService, javaVersion.get()),
			'-jar',
			vineflowerJar.get().asFile.absolutePath,
			*BasicUtils.vineflowerExternalArgs(decompileClasspath.files, server),
			filteredJar.absolutePath,
			outDir.absolutePath
		]

		BasicUtils.runLoggedProcess(cmd, tempDir, logger, 'Vineflower failed')

		logger.lifecycle("Decompiled com/hypixel/hytale only from ${server.name} -> ${outDir}")
	}

	private static void writeFilteredJar(File sourceJar, File targetJar, String pathPrefix) {
		targetJar.parentFile.mkdirs()

		new JarFile(sourceJar).withCloseable { JarFile jarFile ->
			new JarOutputStream(new BufferedOutputStream(new FileOutputStream(targetJar))).withCloseable { JarOutputStream output ->
				jarFile.entries().each { JarEntry entry ->
					if (entry.name.startsWith(pathPrefix)) {
						def copiedEntry = new JarEntry(entry.name)
						copiedEntry.time = entry.time
						output.putNextEntry(copiedEntry)

						if (!entry.directory) {
							jarFile.getInputStream(entry).withCloseable { InputStream input ->
								input.transferTo(output)
							}
						}

						output.closeEntry()
					}
				}
			}
		}
	}
}
