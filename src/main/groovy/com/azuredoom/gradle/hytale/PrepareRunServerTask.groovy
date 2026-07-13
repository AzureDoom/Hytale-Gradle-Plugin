package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Creates platform-specific symlinks or junctions in the run directory")
abstract class PrepareRunServerTask extends DefaultTask {
	@OutputDirectory
	abstract DirectoryProperty getRunDirectory()

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	abstract DirectoryProperty getAssetPackSourceDirectory()

	@OutputDirectory
	abstract DirectoryProperty getAssetPackRunDirectory()

	@Classpath
	abstract ConfigurableFileCollection getVineModJars()

	@Classpath
	abstract ConfigurableFileCollection getVineImplementationJars()

	@OutputDirectory
	abstract DirectoryProperty getVineRuntimeClasspathDir()

	private static final String STAGED_MOD_INDEX = '.vine-staged-mods'
	private static final String STAGED_IMPL_INDEX = '.vine-staged-impl'

	@TaskAction
	void prepare() {
		File runDir = runDirectory.get().asFile
		File srcDir = assetPackSourceDirectory.get().asFile
		File dstDir = assetPackRunDirectory.get().asFile

		if (!srcDir.exists()) {
			throw new GradleException("Asset pack source directory does not exist: ${srcDir}")
		}

		runDir.mkdirs()
		dstDir.parentFile.mkdirs()

		File modsDir = new File(runDir, 'mods')
		stageVineMods(modsDir)
		stageVineImplementation(modsDir, vineRuntimeClasspathDir.get().asFile)

		Path source = srcDir.toPath().toAbsolutePath().normalize()
		Path target = dstDir.toPath().toAbsolutePath().normalize()

		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			if (!isSameFile(target, source)) {
				logger.lifecycle("Replacing existing staged asset pack at ${target} with live link to ${source}")
				deleteRecursively(dstDir.toPath())
			} else {
				logger.info("Run asset pack already linked correctly: ${target} -> ${source}")
				return
			}
		}

		if (BasicUtils.createSymlinkOrWindowsJunction(source, target, "run asset pack", logger)) {
			return
		}

		throw new GradleException("Failed to create symlink for run asset pack: ${target} -> ${source}")
	}

	private void stageVineMods(File modsDir) {
		modsDir.mkdirs()
		File index = new File(modsDir, STAGED_MOD_INDEX)

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

	private void stageVineImplementation(File modsDir, File classpathRoot) {
		modsDir.mkdirs()

		File implIndex = new File(modsDir, STAGED_IMPL_INDEX)

		if (implIndex.exists()) {
			List<String> stagedNames = implIndex.readLines()
					.findAll { !it.trim().isEmpty() }

			for (String name : stagedNames) {
				File stale = new File(modsDir, name)

				if (stale.exists()) {
					deleteRecursively(stale.toPath())
				}
			}

			Files.deleteIfExists(implIndex.toPath())
		}

		if (classpathRoot.exists()) {
			deleteRecursively(classpathRoot.toPath())
		}

		classpathRoot.mkdirs()

		List<String> stagedEntries = []

		for (File jar : vineImplementationJars.files) {
			if (!jar.name.endsWith('.jar')) {
				continue
			}

			Map manifest = readPluginManifest(jar)
			String baseName = jar.name.replaceAll(/\.jar$/, '')
			File cpDir = new File(classpathRoot, baseName)

			if (manifest != null) {
				File destination = new File(modsDir, jar.name)

				Files.copy(
						jar.toPath(),
						destination.toPath(),
						StandardCopyOption.REPLACE_EXISTING
						)

				stagedEntries.add(jar.name)

				explodeJar(jar, cpDir, true, false)

				logger.lifecycle(
						"Staged vineImplementation plugin '${jar.name}' " +
						"as a full mod JAR and on the shared runtime classpath"
						)

				continue
			}

			explodeJar(jar, cpDir, false, false)

			logger.lifecycle(
					"Staged vineImplementation library on runtime classpath: ${jar.name}"
					)
		}

		if (!stagedEntries.isEmpty()) {
			implIndex.text = stagedEntries.join(System.lineSeparator())
		}
	}

	private static String manifestString(Map manifest, String... keys) {
		for (String key : keys) {
			def v = manifest.get(key)
			if (v != null && !v.toString().trim().isEmpty()) {
				return v.toString().trim()
			}
		}
		return ''
	}

	private static Map readPluginManifest(File jar) {
		new ZipFile(jar).withCloseable { ZipFile zf ->
			def entry = zf.getEntry('manifest.json')
			if (entry == null) {
				return null
			}
			zf.getInputStream(entry).withCloseable { input ->
				String text = input.getText('UTF-8')
				def parsed = new JsonSlurper().parseText(text)
				return (parsed instanceof Map) ? (Map) parsed : null
			}
		}
	}

	private static void explodeJar(File jar, File targetDir, boolean skipManifest, boolean skipClasses) {
		targetDir.mkdirs()
		Path targetRoot = targetDir.toPath().toAbsolutePath().normalize()

		new ZipFile(jar).withCloseable { ZipFile zf ->
			def entries = zf.entries()
			while (entries.hasMoreElements()) {
				def entry = entries.nextElement()
				if (entry.isDirectory()) {
					continue
				}
				String entryName = entry.name
				if (skipManifest && entryName == 'manifest.json') {
					continue
				}
				if (skipClasses && entryName.endsWith('.class')) {
					continue
				}

				Path outPath = targetRoot.resolve(entryName).normalize()
				if (!outPath.startsWith(targetRoot)) {
					throw new GradleException("Refusing to extract entry outside target directory: ${entryName}")
				}

				if (outPath.parent != null) {
					Files.createDirectories(outPath.parent)
				}
				zf.getInputStream(entry).withCloseable { input ->
					Files.copy(input, outPath, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}
	}

	private static boolean isSameFile(Path target, Path source) {
		try {
			return Files.isSameFile(target, source)
		} catch (IOException ignored) {
			return false
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
					.forEach { Path entry ->
						Files.deleteIfExists(entry)
					}
		}
	}
}