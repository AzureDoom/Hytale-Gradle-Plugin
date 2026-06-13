package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

import javax.inject.Inject

@DisableCachingByDefault(because = "Uses a global Gradle user home cache keyed by patchline+version instead of Gradle build cache")
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

	@Input
	abstract Property<String> getServerVersion()

	@Input
	abstract Property<String> getPatchline()

	@Internal
	abstract DirectoryProperty getGradleUserHomeDirectory()

	@Inject
	abstract JavaToolchainService getJavaToolchainService()

	@TaskAction
	void decompile() {
		def outDir  = outputDirectory.get().asFile
		def tempDir = tempDirectoryRoot.get().asFile

		File globalCacheDir = resolveGlobalCacheDir()
		File stampFile      = new File(globalCacheDir, '.complete')

		if (stampFile.exists()) {
			logger.lifecycle("DecompileServerJar: global cache hit for {}-{}, copying cached sources",
					patchline.get(), serverVersion.get())
			BasicUtils.recreateDirectories(outDir, tempDir)
			copyDirectory(globalCacheDir, outDir)
			return
		}

		def server = serverJar.get().asFile
		if (!server.exists()) {
			throw new GradleException('Could not resolve Hytale Server jar from vineServerJar')
		}

		BasicUtils.recreateDirectories(outDir, tempDir)

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

		populateGlobalCache(outDir, globalCacheDir, stampFile)
	}

	File resolveGlobalCacheDir() {
		return new File(
				gradleUserHomeDirectory.get().asFile,
				"caches/hytale-decompiled/${patchline.get()}-${serverVersion.get()}/server"
				)
	}

	private void populateGlobalCache(File sourceDir, File cacheDir, File stampFile) {
		try {
			cacheDir.mkdirs()
			copyDirectory(sourceDir, cacheDir)
			BasicUtils.atomicWrite(stampFile, "${patchline.get()}-${serverVersion.get()}")
			logger.lifecycle("DecompileServerJar: cached decompiled sources to {}", cacheDir)
		} catch (Exception e) {
			logger.warn("DecompileServerJar: failed to write global cache (non-fatal): {}", e.message)
		}
	}

	private static void copyDirectory(File src, File dst) {
		dst.mkdirs()
		src.eachFileRecurse { File srcFile ->
			if (srcFile.isFile() && !srcFile.name.startsWith('.')) {
				File dstFile = new File(dst, src.toPath().relativize(srcFile.toPath()).toString())
				dstFile.parentFile?.mkdirs()
				Files.copy(srcFile.toPath(), dstFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING)
			}
		}
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
