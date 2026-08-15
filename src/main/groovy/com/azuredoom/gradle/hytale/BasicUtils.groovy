package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class BasicUtils {

	static String formatBytes(long bytes) {
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

	static void recreateDirectories(File... directories) {
		directories.each { File directory ->
			deleteRecursively(directory)
			Files.createDirectories(directory.toPath())
		}
	}

	static String javaExecutableFor(JavaToolchainService javaToolchainService, int javaVersion) {
		def launcher = javaToolchainService.launcherFor { spec ->
			spec.languageVersion.set(JavaLanguageVersion.of(javaVersion))
		}

		return launcher.get().executablePath.asFile.absolutePath
	}

	static List<String> vineflowerExternalArgs(Collection<File> classpathFiles, File inputJar, File... generatedSourcesRepoDirs) {
		return classpathFiles
				.findAll { File file -> isExternalJar(file, inputJar, generatedSourcesRepoDirs as List<File>) }
				.collect { File file -> ('-e=' + file.absolutePath).toString() } as List<String>
	}

	static void runLoggedProcess(List<?> command, File workingDirectory, Logger logger, String failureMessagePrefix) {
		List<String> cmd = command.collect { it.toString() }

		logger.lifecycle("Running: ${cmd.join(' ')}")

		Process proc = new ProcessBuilder(cmd)
				.directory(workingDirectory)
				.redirectErrorStream(true)
				.start()

		proc.inputStream.withReader { reader ->
			reader.eachLine { line -> logger.lifecycle(line) }
		}

		int exit = proc.waitFor()
		if (exit != 0) {
			throw new GradleException("${failureMessagePrefix} (exit ${exit})")
		}
	}

	static String normalizeBaseUrl(String baseUrl) {
		return baseUrl.endsWith('/') ? baseUrl : baseUrl + '/'
	}

	static File cacheFileForClass(File cacheDir, String fqcn, String extension) {
		return new File(cacheDir, fqcn.replace('.', '/') + extension)
	}

	static String fqcnForJavaFile(File javaFile, File sourcesDir) {
		String rel = sourcesDir.toPath().relativize(javaFile.toPath()).toString()
		if (!rel.endsWith('.java')) {
			return null
		}

		return rel
				.substring(0, rel.length() - '.java'.length())
				.replace(File.separatorChar, '.' as char)
	}

	static void atomicWrite(File target, String content) {
		File tmp = new File(target.parentFile, target.name + '.tmp')
		tmp.setText(content, 'UTF-8')
		tmp.renameTo(target)
	}

	static String cleanHtml(String html) {
		return decodeHtml(html)
				.replaceAll(/(?i)<br\s*\/?>/, '\n')
				.replaceAll(/(?s)<[^>]+>/, '')
				.replaceAll(/[ \t\r\f]+/, ' ')
				.replaceAll(/\n\s+/, '\n')
				.trim()
	}

	static String decodeHtml(String value) {
		return value
				.replace('&nbsp;', ' ')
				.replace('&#160;', ' ')
				.replace('&lt;', '<')
				.replace('&gt;', '>')
				.replace('&amp;', '&')
				.replace('&quot;', '"')
				.replace('&#39;', "'")
				.trim()
	}

	static String toJavadoc(String text, String indent) {
		def lines = text.readLines()

		return indent + '/**\n' +
				indent + ' * <p><strong>Hytale API docs:</strong></p>\n' +
				lines.collect { indent + ' * ' + it }.join('\n') +
				'\n' + indent + ' */'
	}

	static boolean createSymlinkOrWindowsJunction(Path sourceDir, Path targetDir, String failureContext, Logger logger) {
		Path source = sourceDir.toAbsolutePath().normalize()
		Path target = targetDir.toAbsolutePath().normalize()

		Path targetParent = target.parent
		if (targetParent == null) {
			throw new GradleException("Target path must have a parent: ${target}")
		}

		try {
			Files.createDirectories(targetParent)

			Path relativeSource = relativizeOrAbsolute(targetParent, source)
			Files.createSymbolicLink(target, relativeSource)

			logger.lifecycle("Created symlink ${target} -> ${relativeSource}")
			return true
		} catch (FileAlreadyExistsException ignored) {
			logger.warn("Symlink creation failed because target already exists: ${target}")
		} catch (UnsupportedOperationException | SecurityException | IOException ex) {
			logger.warn("Symlink creation failed, attempting Windows junction fallback: ${ex.message}")
		}

		if (!isWindows()) {
			logger.warn("Cannot create Windows junction on non-Windows OS for ${failureContext}")
			return false
		}

		try {
			Process process = new ProcessBuilder(
					'cmd', '/c', 'mklink', '/J',
					target.toString(),
					source.toString()
					).redirectErrorStream(true).start()

			String output = process.inputStream.text
			int code = process.waitFor()

			if (code == 0 && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
				logger.lifecycle("Created junction ${target} -> ${source}")
				return true
			}

			logger.warn(
					"Junction creation failed for ${failureContext}.\n" +
					"Target: ${target}\n" +
					"Source: ${source}\n" +
					"Exit code: ${code}\n" +
					"Output:\n${output}"
					)
		} catch (IOException | InterruptedException ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt()
			}

			logger.warn(
					"Junction creation failed for ${failureContext}.\n" +
					"Target: ${target}\n" +
					"Source: ${source}\n" +
					"Reason: ${ex.message}"
					)
		}

		return false
	}

	private static void deleteRecursively(File file) {
		if (file == null || !file.exists()) {
			return
		}

		Path root = file.toPath()
		Files.walk(root).withCloseable { stream ->
			stream.sorted(Comparator.reverseOrder())
					.each { Path path -> Files.deleteIfExists(path) }
		}
	}

	private static boolean isExternalJar(File file, File inputJar, List<File> generatedSourcesRepoDirs) {
		return file.name.endsWith('.jar') &&
				file != inputJar &&
				!isGeneratedSourcesJar(file, generatedSourcesRepoDirs)
	}

	private static boolean isGeneratedSourcesJar(File file, List<File> generatedSourcesRepoDirs) {
		String filePath = file.absolutePath
		return generatedSourcesRepoDirs.any { File repoDir ->
			repoDir != null && filePath.startsWith(repoDir.absolutePath + File.separator)
		}
	}

	private static Path relativizeOrAbsolute(Path parent, Path source) {
		try {
			return parent.relativize(source)
		} catch (IllegalArgumentException ignored) {
			return source
		}
	}

	private static boolean isWindows() {
		return System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('win')
	}
}
