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

@DisableCachingByDefault(because = "Stages development plugins using links and mutable run-directory state")
abstract class StageAllModAssetsTask extends DefaultTask {

	private static final String WORKSPACE_INDEX = '.vine-staged-workspace'
	private static final String VINE_MOD_INDEX = '.vine-staged-mods'
	private static final String VINE_IMPLEMENTATION_INDEX = '.vine-staged-impl'

	@Input
	abstract ListProperty<String> getProjectPaths()

	@Input
	abstract ListProperty<String> getManifestGroups()

	@Input
	abstract ListProperty<String> getModIds()

	@Input
	abstract ListProperty<String> getAssetSourceDirectoryPaths()

	@Input
	abstract ListProperty<String> getClassOutputDirectoryPaths()

	@Input
	abstract ListProperty<Integer> getClassOutputDirectoryCounts()

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
		List<String> flattenedClassPaths = classOutputDirectoryPaths.get()
		List<Integer> classDirectoryCounts = classOutputDirectoryCounts.get()

		validateWorkspaceMetadata(
				sourceDirPaths,
				groups,
				ids,
				flattenedClassPaths,
				classDirectoryCounts
				)

		List<List<String>> classDirPathGroups = rebuildClassPathGroups(
				flattenedClassPaths,
				classDirectoryCounts
				)

		File runDirFile = runDirectory.get().asFile
		File modsDirFile = modsDirectory.get().asFile
		runDirFile.mkdirs()
		modsDirFile.mkdirs()

		cleanupIndexedEntries(modsDirFile, WORKSPACE_INDEX)
		List<String> stagedWorkspaceDirectories = []

		for (int i = 0; i < sourceDirPaths.size(); i++) {
			File resourcesDirectory = new File(sourceDirPaths[i])
			List<File> configuredClassDirectories = classDirPathGroups[i]
					.collect { String path -> new File(path) }

			if (!resourcesDirectory.isDirectory()) {
				throw new GradleException(
				"Workspace resource directory does not exist: ${resourcesDirectory}"
				)
			}

			List<File> existingClassDirectories = configuredClassDirectories
					.findAll { File directory -> directory.isDirectory() }

			if (existingClassDirectories.isEmpty()) {
				throw new GradleException(
				"Workspace project ${groups[i]}:${ids[i]} has no existing " +
				"class output directories. Expected one of: ${configuredClassDirectories}. " +
				"Ensure stageAllModAssets depends on the project's classes task."
				)
			}

			String targetName = "${groups[i].replace('.', '_')}_${ids[i]}"
			stagedWorkspaceDirectories.add(targetName)

			Path targetDirectory = new File(modsDirFile, targetName)
					.toPath()
					.toAbsolutePath()
					.normalize()

			List<Path> classDirectoryPaths = existingClassDirectories
					.collect { File directory ->
						directory.toPath().toAbsolutePath().normalize()
					}

			stageWorkspacePlugin(
					resourcesDirectory.toPath().toAbsolutePath().normalize(),
					classDirectoryPaths,
					targetDirectory
					)

			logger.lifecycle(
					"Staged workspace plugin ${groups[i]}:${ids[i]} with " +
					"${classDirectoryPaths.size()} class output(s) -> ${targetDirectory}"
					)
		}

		writeIndex(modsDirFile, WORKSPACE_INDEX, stagedWorkspaceDirectories)

		Map<String, String> stagedDependencyJars = [:]
		stageVineMods(modsDirFile, stagedDependencyJars)

		File runtimeClasspathDir = vineRuntimeClasspathDirectory.get().asFile
		stageVineImplementation(
				modsDirFile,
				runtimeClasspathDir,
				stagedDependencyJars
				)
	}

	private static void validateWorkspaceMetadata(
			List<String> sourceDirPaths,
			List<String> groups,
			List<String> ids,
			List<String> flattenedClassPaths,
			List<Integer> classDirectoryCounts
	) {
		if (sourceDirPaths.size() != groups.size() ||
				sourceDirPaths.size() != ids.size() ||
				sourceDirPaths.size() != classDirectoryCounts.size()) {
			throw new GradleException(
			"Workspace plugin metadata is inconsistent: " +
			"sources=${sourceDirPaths.size()}, " +
			"classCounts=${classDirectoryCounts.size()}, " +
			"groups=${groups.size()}, ids=${ids.size()}"
			)
		}

		if (classDirectoryCounts.any { Integer count -> count == null || count < 0 }) {
			throw new GradleException(
			"Workspace class-output counts may not contain null or negative values: " +
			"${classDirectoryCounts}"
			)
		}

		int expectedClassPathCount = classDirectoryCounts.sum(0) as int
		if (flattenedClassPaths.size() != expectedClassPathCount) {
			throw new GradleException(
			"Workspace class-output metadata is inconsistent: " +
			"paths=${flattenedClassPaths.size()}, expected=${expectedClassPathCount}"
			)
		}
	}

	private static List<List<String>> rebuildClassPathGroups(
			List<String> flattenedClassPaths,
			List<Integer> classDirectoryCounts
	) {
		List<List<String>> result = []
		int offset = 0

		for (Integer count : classDirectoryCounts) {
			result.add(
					new ArrayList<>(
					flattenedClassPaths.subList(offset, offset + count)
					)
					)
			offset += count
		}

		return result
	}

	private void stageWorkspacePlugin(
			Path resourcesDirectory,
			List<Path> classesDirectories,
			Path targetDirectory
	) {
		if (Files.exists(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
			deleteRecursively(targetDirectory)
		}

		Files.createDirectories(targetDirectory)
		linkDirectoryContents(resourcesDirectory, targetDirectory, 'resources')

		for (Path classesDirectory : classesDirectories) {
			linkDirectoryContents(classesDirectory, targetDirectory, 'classes')
		}

		Path manifest = targetDirectory.resolve('manifest.json')
		if (!Files.isRegularFile(manifest)) {
			throw new GradleException(
			"Workspace plugin is missing manifest.json after staging: ${targetDirectory}"
			)
		}
	}

	protected void linkDirectoryContents(
			Path sourceDirectory,
			Path targetDirectory,
			String contentType
	) {
		Files.createDirectories(targetDirectory)

		Files.list(sourceDirectory).withCloseable { stream ->
			Iterator<Path> entries = stream.iterator()

			while (entries.hasNext()) {
				Path sourceEntry = entries.next()
				Path targetEntry = targetDirectory.resolve(
						sourceEntry.fileName.toString()
						)

				if (Files.isDirectory(sourceEntry)) {
					if (Files.exists(targetEntry, LinkOption.NOFOLLOW_LINKS) &&
							!Files.isDirectory(
							targetEntry,
							LinkOption.NOFOLLOW_LINKS
							)) {
						throw new GradleException(
						"Workspace ${contentType} collision at '${targetEntry}'."
						)
					}

					Files.createDirectories(targetEntry)

					linkDirectoryContents(
							sourceEntry,
							targetEntry,
							contentType
							)

					continue
				}

				if (Files.exists(targetEntry, LinkOption.NOFOLLOW_LINKS)) {
					throw new GradleException(
					"Workspace ${contentType} file collision at '${targetEntry}'."
					)
				}

				createFileLinkOrCopy(
						sourceEntry,
						targetEntry,
						contentType
						)
			}
		}
	}

	protected void createFileLinkOrCopy(
			Path source,
			Path target,
			String contentType
	) {
		try {
			Files.createSymbolicLink(target, source)
			logger.lifecycle(
					"Linked workspace ${contentType}: ${target} -> ${source}"
					)
			return
		} catch (UnsupportedOperationException exception) {
			logLinkFailure(source, target, exception)
		} catch (IOException exception) {
			logLinkFailure(source, target, exception)
		} catch (SecurityException exception) {
			logLinkFailure(source, target, exception)
		}

		if (isWindows()) {
			try {
				Files.createLink(target, source)
				logger.lifecycle(
						"Hard-linked workspace ${contentType}: ${target} -> ${source}"
						)
				return
			} catch (Exception exception) {
				logger.info(
						"Could not create hard link ${target} -> ${source}: " +
						"${exception.message}"
						)
			}
		}

		Files.createDirectories(target.parent)
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)

		logger.warn(
				"Copied workspace ${contentType} because linking was unavailable: " +
				"${source} -> ${target}. Live reload is disabled for this file."
				)
	}

	private void logLinkFailure(Path source, Path target, Exception exception) {
		logger.info(
				"Could not create symbolic link ${target} -> ${source}: ${exception.message}"
				)
	}

	private void stageVineMods(
			File modsDir,
			Map<String, String> stagedDependencyJars
	) {
		modsDir.mkdirs()
		cleanupIndexedEntries(modsDir, VINE_MOD_INDEX)

		List<String> staged = []
		List<File> modFiles = vineModJars.files
				.findAll { it.name.endsWith('.jar') }
				.sort { left, right -> left.absolutePath <=> right.absolutePath }

		for (File jar : modFiles) {
			validateDependencyJarName(jar, stagedDependencyJars)

			File destination = new File(modsDir, jar.name)
			Files.copy(
					jar.toPath(),
					destination.toPath(),
					StandardCopyOption.REPLACE_EXISTING
					)

			staged.add(jar.name)
			logger.lifecycle("Staged vine mod into run/mods: ${jar.name}")
		}

		writeIndex(modsDir, VINE_MOD_INDEX, staged)
	}

	private void stageVineImplementation(
			File modsDir,
			File classpathRoot,
			Map<String, String> stagedDependencyJars
	) {
		modsDir.mkdirs()
		cleanupIndexedEntries(modsDir, VINE_IMPLEMENTATION_INDEX)

		if (classpathRoot.exists()) {
			deleteRecursively(classpathRoot.toPath())
		}
		classpathRoot.mkdirs()

		List<String> stagedPluginJars = []
		Set<String> processedFiles = new HashSet<>()
		Map<String, String> classpathDirectoryOwners = [:]

		List<File> implementationFiles = vineImplementationJars.files
				.findAll { it.name.endsWith('.jar') }
				.sort { left, right -> left.absolutePath <=> right.absolutePath }

		for (File jar : implementationFiles) {
			String canonicalPath = jar.canonicalPath
			if (!processedFiles.add(canonicalPath)) {
				continue
			}

			Map manifest = readPluginManifest(jar)
			String directoryName = safeDirectoryName(
					jar.name.replaceFirst(/\.jar$/, '')
					)

			String previousOwner = classpathDirectoryOwners.putIfAbsent(
					directoryName,
					canonicalPath
					)
			if (previousOwner != null && previousOwner != canonicalPath) {
				throw new GradleException(
				"Two vineImplementation dependencies resolve to the same runtime " +
				"classpath directory '${directoryName}': '${previousOwner}' and " +
				"'${canonicalPath}'."
				)
			}

			File classpathDirectory = new File(classpathRoot, directoryName)

			if (manifest != null) {
				validateDependencyJarName(jar, stagedDependencyJars)

				File destination = new File(modsDir, jar.name)
				Files.copy(
						jar.toPath(),
						destination.toPath(),
						StandardCopyOption.REPLACE_EXISTING
						)

				stagedPluginJars.add(jar.name)
				explodeJar(jar, classpathDirectory, true, false)

				logger.lifecycle(
						"Staged vineImplementation plugin '${jar.name}' into run/mods " +
						"and the shared runtime classpath"
						)
			} else {
				explodeJar(jar, classpathDirectory, false, false)
				logger.lifecycle(
						"Staged vineImplementation library '${jar.name}' on the shared " +
						"runtime classpath"
						)
			}
		}

		writeIndex(modsDir, VINE_IMPLEMENTATION_INDEX, stagedPluginJars)
	}

	private static void cleanupIndexedEntries(File parentDirectory, String indexName) {
		File index = new File(parentDirectory, indexName)
		if (!index.exists()) {
			return
		}

		List<String> entries = index.readLines()
				.findAll { !it.trim().isEmpty() }

		for (String name : entries) {
			File stale = new File(parentDirectory, name)
			if (stale.exists() || Files.exists(stale.toPath(), LinkOption.NOFOLLOW_LINKS)) {
				deleteRecursively(stale.toPath())
			}
		}

		Files.deleteIfExists(index.toPath())
	}

	private static void writeIndex(File parentDirectory, String indexName, List<String> entries) {
		File index = new File(parentDirectory, indexName)
		Files.deleteIfExists(index.toPath())

		if (!entries.isEmpty()) {
			index.text = entries.unique().sort().join(System.lineSeparator())
		}
	}

	private static void deleteRecursively(Path path) {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return
		}

		if (Files.isSymbolicLink(path) ||
				Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			Files.deleteIfExists(path)
			return
		}

		Files.walk(path).withCloseable { paths ->
			paths.sorted(Comparator.reverseOrder())
					.forEach { Path entry -> Files.deleteIfExists(entry) }
		}
	}

	private static Map readPluginManifest(File jar) {
		ZipFile zip = new ZipFile(jar)

		try {
			ZipEntry entry = zip.getEntry('manifest.json')
			if (entry == null) {
				return null
			}

			zip.getInputStream(entry).withCloseable { stream ->
				return new JsonSlurper().parse(stream) as Map
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

				String upperName = entryName.toUpperCase(Locale.ROOT)
				if (upperName.startsWith('META-INF/') &&
						(upperName.endsWith('.SF') ||
						upperName.endsWith('.RSA') ||
						upperName.endsWith('.DSA'))) {
					continue
				}

				Path target = destination.toPath().resolve(entryName).normalize()
				Path normalizedDestination = destination.toPath().normalize()

				if (!target.startsWith(normalizedDestination)) {
					throw new GradleException(
					"Refusing to extract unsafe path '${entryName}' from ${jar}"
					)
				}

				if (target.parent != null) {
					Files.createDirectories(target.parent)
				}

				zip.getInputStream(entry).withCloseable { input ->
					Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		} finally {
			zip.close()
		}
	}

	private static String safeDirectoryName(String value) {
		return value.replaceAll(/[^A-Za-z0-9._-]/, '_')
	}

	private static boolean isWindows() {
		return System.getProperty('os.name')
				.toLowerCase(Locale.ROOT)
				.contains('windows')
	}

	private static void validateDependencyJarName(
			File jar,
			Map<String, String> stagedDependencyJars
	) {
		String canonicalPath = jar.canonicalPath
		String existingPath = stagedDependencyJars.get(jar.name)

		if (existingPath == null) {
			stagedDependencyJars.put(jar.name, canonicalPath)
			return
		}

		if (existingPath != canonicalPath) {
			throw new GradleException(
			"Two dependency plugins resolve to the same staged filename " +
			"'${jar.name}': '${existingPath}' and '${canonicalPath}'. " +
			"Only one file can be placed at run/mods/${jar.name}."
			)
		}
	}
}