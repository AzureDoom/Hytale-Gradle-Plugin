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

	@Input
	abstract Property<Boolean> getPreferOfficialSources()

	@Input
	abstract Property<String> getOfficialSourcesRepositoryUrl()

	@Internal
	abstract Property<Boolean> getOffline()

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

		if (shouldTryOfficialSources() && useOfficialSources(outDir, tempDir)) {
			return
		}

		decompileWithVineflower(outDir, tempDir)
	}

	boolean shouldTryOfficialSources() {
		return preferOfficialSources.getOrElse(true) &&
				HytaleServerSources.officialSourcesExpected(serverVersion.get())
	}

	boolean hasCurrentLocalOutput() {
		File outDir = outputDirectory.get().asFile
		if (!HytaleServerSources.containsJavaSources(outDir)) {
			return false
		}

		String kind = HytaleServerSources.readSourceKind(outDir) ?: HytaleServerSources.KIND_DECOMPILED
		boolean decompiledCached = new File(resolveGlobalCacheDir(), '.complete').exists()

		if (shouldTryOfficialSources()) {
			if (new File(resolveOfficialSourcesCacheDir(), '.complete').exists()) {
				return kind == HytaleServerSources.KIND_OFFICIAL
			}
			boolean wontRetry = offline.getOrElse(false) ||
					HytaleServerSources.isMissingMarkerFresh(resolveOfficialSourcesMissingMarker())
			return wontRetry && kind == HytaleServerSources.KIND_DECOMPILED && decompiledCached
		}

		return kind == HytaleServerSources.KIND_DECOMPILED && decompiledCached
	}

	private boolean useOfficialSources(File outDir, File tempDir) {
		String version = serverVersion.get()
		File cacheDir  = resolveOfficialSourcesCacheDir()
		File stampFile = new File(cacheDir, '.complete')

		if (stampFile.exists()) {
			logger.lifecycle("ServerSources: global cache hit for official {}-{} sources, copying cached sources",
					patchline.get(), version)
			BasicUtils.recreateDirectories(outDir, tempDir)
			copyDirectory(cacheDir, outDir)
			HytaleServerSources.writeSourceKind(outDir, HytaleServerSources.KIND_OFFICIAL)
			return true
		}

		if (offline.getOrElse(false)) {
			logger.lifecycle("ServerSources: offline and no cached official sources for {}-{}, decompiling instead",
					patchline.get(), version)
			return false
		}

		File missingMarker = resolveOfficialSourcesMissingMarker()
		if (HytaleServerSources.isMissingMarkerFresh(missingMarker)) {
			logger.info("ServerSources: official sources for {}-{} were missing recently, decompiling instead",
					patchline.get(), version)
			return false
		}

		String url = HytaleServerSources.sourcesJarUrl(officialSourcesRepositoryUrl.get(), version)
		File downloadDir = resolveOfficialSourcesDownloadDir()
		File jar = new File(downloadDir, "${HytaleVersionResolver.SERVER_MODULE}-${version}-sources.jar")

		File downloaded
		try {
			logger.lifecycle("ServerSources: fetching official sources {}", url)
			downloaded = HytaleServerSources.downloadSourcesJar(url, jar, logger)
		} catch (IOException e) {
			logger.warn("ServerSources: could not fetch official sources from {} ({}); decompiling instead",
					url, e.message)
			return false
		}

		if (downloaded == null || !HytaleServerSources.isUsableSourcesJar(downloaded)) {
			logger.lifecycle("ServerSources: no usable official sources jar at {}; decompiling instead", url)
			jar.delete()
			try {
				missingMarker.parentFile?.mkdirs()
				BasicUtils.atomicWrite(missingMarker, url)
			} catch (Exception e) {
				logger.warn("ServerSources: failed to record missing sources (non-fatal): {}", e.message)
			}
			return false
		}

		missingMarker.delete()
		BasicUtils.recreateDirectories(outDir, tempDir)
		HytaleServerSources.extractSourcesJar(downloaded, outDir)
		HytaleServerSources.writeSourceKind(outDir, HytaleServerSources.KIND_OFFICIAL)
		logger.lifecycle("Using official Hytale server sources ${downloaded.name} -> ${outDir}")

		populateGlobalCache(outDir, cacheDir, stampFile)
		return true
	}

	private void decompileWithVineflower(File outDir, File tempDir) {
		File globalCacheDir = resolveGlobalCacheDir()
		File stampFile      = new File(globalCacheDir, '.complete')

		if (stampFile.exists()) {
			logger.lifecycle("DecompileServerJar: global cache hit for {}-{}, copying cached sources",
					patchline.get(), serverVersion.get())
			BasicUtils.recreateDirectories(outDir, tempDir)
			copyDirectory(globalCacheDir, outDir)
			HytaleServerSources.writeSourceKind(outDir, HytaleServerSources.KIND_DECOMPILED)
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
			*BasicUtils.vineflowerExternalArgs(
			decompileClasspath.files,
			server,
			generatedSourcesMavenRepoDir.get().asFile,
			generatedSourcesIvyRepoDir.get().asFile
			),
			filteredJar.absolutePath,
			outDir.absolutePath
		]

		BasicUtils.runLoggedProcess(cmd, tempDir, logger, 'Vineflower failed')

		logger.lifecycle("Decompiled com/hypixel/hytale only from ${server.name} -> ${outDir}")

		populateGlobalCache(outDir, globalCacheDir, stampFile)
		HytaleServerSources.writeSourceKind(outDir, HytaleServerSources.KIND_DECOMPILED)
	}

	File resolveGlobalCacheDir() {
		return new File(
				gradleUserHomeDirectory.get().asFile,
				"caches/hytale-decompiled/${patchline.get()}-${serverVersion.get()}/server"
				)
	}

	File resolveOfficialSourcesDownloadDir() {
		return new File(
				gradleUserHomeDirectory.get().asFile,
				"caches/hytale-sources/${patchline.get()}-${serverVersion.get()}"
				)
	}

	File resolveOfficialSourcesCacheDir() {
		return new File(resolveOfficialSourcesDownloadDir(), 'server')
	}

	File resolveOfficialSourcesMissingMarker() {
		return new File(resolveOfficialSourcesDownloadDir(), '.missing')
	}

	private void populateGlobalCache(File sourceDir, File cacheDir, File stampFile) {
		try {
			cacheDir.mkdirs()
			copyDirectory(sourceDir, cacheDir)
			BasicUtils.atomicWrite(stampFile, "${patchline.get()}-${serverVersion.get()}")
			logger.lifecycle("ServerSources: cached server sources to {}", cacheDir)
		} catch (Exception e) {
			logger.warn("ServerSources: failed to write global cache (non-fatal): {}", e.message)
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
