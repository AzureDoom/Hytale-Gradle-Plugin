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

import javax.inject.Inject

@DisableCachingByDefault(because = "Uses a global Gradle user home cache keyed by Maven coordinates instead of Gradle build cache")
abstract class DecompileDependencyJarTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getInputJar()

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
	abstract Property<String> getGroupId()

	@Input
	abstract Property<String> getArtifactId()

	@Input
	abstract Property<String> getArtifactVersion()

	@Internal
	abstract DirectoryProperty getGradleUserHomeDirectory()

	@Internal
	abstract DirectoryProperty getGeneratedSourcesMavenRepoDir()

	@Internal
	abstract DirectoryProperty getGeneratedSourcesIvyRepoDir()

	@Inject
	abstract JavaToolchainService getJavaToolchainService()

	@TaskAction
	void decompile() {
		def outDir  = outputDirectory.get().asFile
		def tempDir = tempDirectoryRoot.get().asFile

		File globalCacheDir = resolveGlobalCacheDir()
		File stampFile      = new File(globalCacheDir, '.complete')

		if (stampFile.exists()) {
			logger.lifecycle("DecompileDependency: global cache hit for {}:{}:{}, copying cached sources",
					groupId.get(), artifactId.get(), artifactVersion.get())
			BasicUtils.recreateDirectories(outDir, tempDir)
			copyDirectory(globalCacheDir, outDir)
			return
		}

		def input = inputJar.get().asFile
		if (!input.exists()) {
			throw new GradleException("Could not resolve dependency jar: ${input}")
		}

		BasicUtils.recreateDirectories(outDir, tempDir)

		def cmd = [
			BasicUtils.javaExecutableFor(javaToolchainService, javaVersion.get()),
			'-jar',
			vineflowerJar.get().asFile.absolutePath,
			*BasicUtils.vineflowerExternalArgs(
			decompileClasspath.files,
			input,
			generatedSourcesMavenRepoDir.get().asFile,
			generatedSourcesIvyRepoDir.get().asFile
			),
			input.absolutePath,
			outDir.absolutePath
		]

		BasicUtils.runLoggedProcess(cmd, tempDir, logger, "Vineflower failed for ${input.name}")

		logger.lifecycle("Decompiled ${input.name} -> ${outDir}")

		populateGlobalCache(outDir, globalCacheDir, stampFile)
	}

	File resolveGlobalCacheDir() {
		return new File(
				gradleUserHomeDirectory.get().asFile,
				"caches/hytale-decompiled/dependencies/${groupId.get()}/${artifactId.get()}/${artifactVersion.get()}"
				)
	}

	private void populateGlobalCache(File sourceDir, File cacheDir, File stampFile) {
		try {
			cacheDir.mkdirs()
			copyDirectory(sourceDir, cacheDir)
			BasicUtils.atomicWrite(stampFile, "${groupId.get()}:${artifactId.get()}:${artifactVersion.get()}")
			logger.lifecycle("DecompileDependency: cached decompiled sources to {}", cacheDir)
		} catch (Exception e) {
			logger.warn("DecompileDependency: failed to write global cache (non-fatal): {}", e.message)
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
}
