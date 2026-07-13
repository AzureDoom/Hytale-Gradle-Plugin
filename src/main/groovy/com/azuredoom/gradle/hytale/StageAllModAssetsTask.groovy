package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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

	@Classpath
	abstract ConfigurableFileCollection getVineModJars()

	@Classpath
	abstract ConfigurableFileCollection getVineImplementationJars()

	@OutputDirectory
	abstract DirectoryProperty getVineRuntimeClasspathDirectory()

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

			if (Files.exists(targetDir, LinkOption.NOFOLLOW_LINKS)) {
				deleteRecursively(targetDir)
			}

			targetDir.parent.toFile().mkdirs()
			copyDirectory(sourceDir, targetDir)
			logger.lifecycle("Copied asset pack ${sourceDir} -> ${targetDir}")
		}

		stageVineMods(modsDirFile)

		File runtimeClasspathDir = vineRuntimeClasspathDirectory.get().asFile
		stageVineImplementation(modsDirFile, runtimeClasspathDir)
	}

	private void stageVineMods(File modsDir) {
		modsDir.mkdirs()
		File index = new File(modsDir, '.vine-staged-mods')

		if (index.exists()) {
			index.readLines().findAll { !it.trim().isEmpty() }.each { String name ->
				File stale = new File(modsDir, name)
				if (stale.isFile()) {
					Files.deleteIfExists(stale.toPath())
				}
			}
			Files.deleteIfExists(index.toPath())
		}

		List<String> staged = []
		vineModJars.files.findAll { it.name.endsWith('.jar') }.each { File jar ->
			File dest = new File(modsDir, jar.name)
			Files.copy(jar.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
			staged.add(jar.name)
			logger.lifecycle("Staged vine mod into run/mods: ${jar.name}")
		}

		if (!staged.isEmpty()) {
			index.text = staged.join(System.lineSeparator())
		}
	}

	private void stageVineImplementation(
			File modsDir,
			File classpathRoot
	) {
		modsDir.mkdirs()

		File index = new File(modsDir, '.vine-staged-impl')

		if (index.exists()) {
			List<String> staleNames = index.readLines()
					.findAll { !it.trim().isEmpty() }

			for (String name : staleNames) {
				File stale = new File(modsDir, name)

				if (stale.exists()) {
					deleteRecursively(stale.toPath())
				}
			}

			Files.deleteIfExists(index.toPath())
		}

		if (classpathRoot.exists()) {
			deleteRecursively(classpathRoot.toPath())
		}

		classpathRoot.mkdirs()

		List<String> stagedPluginJars = []
		Set<String> processedFiles = new HashSet<>()

		List<File> implementationFiles = vineImplementationJars.files
				.findAll { it.name.endsWith('.jar') }
				.sort { left, right ->
					left.absolutePath <=> right.absolutePath
				}

		for (File jar : implementationFiles) {
			String canonicalPath = jar.canonicalPath

			// The same dependency may be inherited by several workspace projects.
			if (!processedFiles.add(canonicalPath)) {
				continue
			}

			Map manifest = readPluginManifest(jar)

			String directoryName = safeDirectoryName(
					jar.name.replaceFirst(/\.jar$/, '')
			)

			File classpathDirectory = new File(
					classpathRoot,
					directoryName
			)

			if (manifest != null) {
				File destination = new File(modsDir, jar.name)

				Files.copy(
						jar.toPath(),
						destination.toPath(),
						StandardCopyOption.REPLACE_EXISTING
				)

				stagedPluginJars.add(jar.name)

				explodeJar(
						jar,
						classpathDirectory,
						true,
						false
				)

				logger.lifecycle(
						"Staged vineImplementation plugin '${jar.name}' " +
								"into run/mods and the shared runtime classpath"
				)
			} else {
				explodeJar(
						jar,
						classpathDirectory,
						false,
						false
				)

				logger.lifecycle(
						"Staged vineImplementation library '${jar.name}' " +
								"on the shared runtime classpath"
				)
			}
		}

		if (!stagedPluginJars.isEmpty()) {
			index.text = stagedPluginJars.join(System.lineSeparator())
		}
	}

	private static void copyDirectory(Path source, Path target) {
		Files.walk(source).withCloseable { paths ->
			paths.forEach { path ->
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
	}

	private static void deleteRecursively(Path path) {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return
		}

		Files.walk(path).withCloseable { paths ->
			paths.sorted(Comparator.reverseOrder())
					.forEach { Files.deleteIfExists(it) }
		}
	}

	private static Map readPluginManifest(File jar) {
		ZipFile zip = new ZipFile(jar)

		try {
			def entry = zip.getEntry('manifest.json')

			if (entry == null) {
				return null
			}

			zip.getInputStream(entry).withCloseable { stream ->
				return new JsonSlurper()
						.parse(stream) as Map
			}
		} finally {
			zip.close()
		}
	}

	private static void explodeJar(
			File jar,
			File destination,
			boolean skipPluginManifest,
			boolean skipClasses
	) {
		if (destination.exists()) {
			deleteRecursively(destination.toPath())
		}

		destination.mkdirs()

        ZipFile zip = new ZipFile(jar)

		try {
			Enumeration<? extends ZipEntry> entries = zip.entries()

			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement()
				String entryName = entry.name

				if (entry.directory) {
					continue
				}

				if (skipPluginManifest && entryName == 'manifest.json') {
					continue
				}

				if (skipClasses && entryName.endsWith('.class')) {
					continue
				}

				// Signature metadata is invalid after exploding a signed JAR.
				String upperName = entryName.toUpperCase(Locale.ROOT)
				if (upperName.startsWith('META-INF/') &&
						(upperName.endsWith('.SF') ||
								upperName.endsWith('.RSA') ||
								upperName.endsWith('.DSA'))) {
					continue
				}

				Path target = destination.toPath()
						.resolve(entryName)
						.normalize()

				if (!target.startsWith(destination.toPath().normalize())) {
					throw new GradleException(
							"Refusing to extract unsafe path '${entryName}' " +
									"from ${jar}"
					)
				}

				if (target.parent != null) {
					Files.createDirectories(target.parent)
				}

				zip.getInputStream(entry).withCloseable { input ->
					Files.copy(
							input,
							target,
							StandardCopyOption.REPLACE_EXISTING
					)
				}
			}
		} finally {
			zip.close()
		}
	}

	private static String safeDirectoryName(String value) {
		return value.replaceAll(/[^A-Za-z0-9._-]/, '_')
	}
}