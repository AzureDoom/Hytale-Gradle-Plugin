package com.azuredoom.gradle.hytale

import org.gradle.api.logging.Logger

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Stream

final class HytaleServerSources {
	private HytaleServerSources() {}

	static final String FIRST_OFFICIAL_SOURCES_VERSION = '0.8.0-pre.3'

	static final String SOURCE_KIND_MARKER = '.hytale-source-kind'
	static final String KIND_OFFICIAL = 'official'
	static final String KIND_DECOMPILED = 'decompiled'

	static final long MISSING_RECHECK_MILLIS = 24L * 60L * 60L * 1000L

	private static final int CONNECT_TIMEOUT_MILLIS = 15_000
	private static final int READ_TIMEOUT_MILLIS = 120_000

	static boolean officialSourcesExpected(String version) {
		if (version == null || version.trim().isEmpty() || HytaleVersionResolver.isDynamic(version.trim())) {
			return false
		}
		return compareVersions(version.trim(), FIRST_OFFICIAL_SOURCES_VERSION) >= 0
	}

	static int compareVersions(String a, String b) {
		int dashA = a.indexOf('-')
		int dashB = b.indexOf('-')
		String baseA = dashA >= 0 ? a.substring(0, dashA) : a
		String baseB = dashB >= 0 ? b.substring(0, dashB) : b
		String qualA = dashA >= 0 ? a.substring(dashA + 1) : null
		String qualB = dashB >= 0 ? b.substring(dashB + 1) : null

		int base = compareSegments(baseA.split('\\.'), baseB.split('\\.'), true)
		if (base != 0) {
			return base
		}
		if (qualA == null && qualB == null) {
			return 0
		}
		if (qualA == null) {
			return 1
		}
		if (qualB == null) {
			return -1
		}
		return compareSegments(qualA.split('\\.'), qualB.split('\\.'), false)
	}

	private static int compareSegments(String[] a, String[] b, boolean padWithZero) {
		int length = Math.max(a.length, b.length)
		for (int i = 0; i < length; i++) {
			boolean hasA = i < a.length
			boolean hasB = i < b.length
			if (!padWithZero && (!hasA || !hasB)) {
				return hasA ? 1 : -1
			}
			String sa = hasA ? a[i] : '0'
			String sb = hasB ? b[i] : '0'
			int cmp
			boolean numA = sa ==~ /\d+/
			boolean numB = sb ==~ /\d+/
			if (numA && numB) {
				cmp = new BigInteger(sa) <=> new BigInteger(sb)
			} else if (numA) {
				cmp = -1
			} else if (numB) {
				cmp = 1
			} else {
				cmp = sa <=> sb
			}
			if (cmp != 0) {
				return cmp
			}
		}
		return 0
	}

	static boolean isPreRelease(String patchline) {
		String value = patchline == null ? '' : patchline.trim()
		return value.equalsIgnoreCase('pre-release') || value.equalsIgnoreCase('prerelease')
	}

	static String repositoryUrlForPatchline(String patchline) {
		return isPreRelease(patchline)
				? HytaleRepositoryConfigurer.PRE_RELEASE_REPO_URL
				: HytaleRepositoryConfigurer.RELEASE_REPO_URL
	}

	static String sourcesJarUrl(String repositoryUrl, String version) {
		String base = repositoryUrl.endsWith('/') ? repositoryUrl : "${repositoryUrl}/"
		String groupPath = HytaleVersionResolver.SERVER_GROUP.replace('.', '/')
		String module = HytaleVersionResolver.SERVER_MODULE
		return "${base}${groupPath}/${module}/${version}/${module}-${version}-sources.jar"
	}

	static File downloadSourcesJar(String url, File target, Logger logger) throws IOException {
		target.parentFile?.mkdirs()
		File partial = new File(target.parentFile, "${target.name}.part")
		partial.delete()

		URLConnection connection = URI.create(url).toURL().openConnection()
		connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
		connection.readTimeout = READ_TIMEOUT_MILLIS

		if (connection instanceof HttpURLConnection) {
			HttpURLConnection http = connection as HttpURLConnection
			http.instanceFollowRedirects = true
			http.requestMethod = 'GET'
			int code = http.responseCode
			if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_GONE) {
				http.disconnect()
				return null
			}
			if (code != HttpURLConnection.HTTP_OK) {
				http.disconnect()
				throw new IOException("HTTP ${code} from ${url}")
			}
		}

		try {
			connection.inputStream.withCloseable { InputStream input ->
				Files.copy(input, partial.toPath(), StandardCopyOption.REPLACE_EXISTING)
			}
		} catch (FileNotFoundException ignored) {
			partial.delete()
			return null
		}

		Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
		logger?.info('HytaleServerSources: downloaded {} ({})', url, BasicUtils.formatBytes(target.length()))
		return target
	}

	static boolean isUsableSourcesJar(File jar) {
		if (jar == null || !jar.isFile() || jar.length() == 0) {
			return false
		}
		try {
			return new JarFile(jar).withCloseable { JarFile jarFile ->
				jarFile.entries().toList().any { JarEntry e -> !e.directory && e.name.endsWith('.java') }
			}
		} catch (IOException ignored) {
			return false
		}
	}

	static void extractSourcesJar(File jar, File outDir) {
		Path root = outDir.toPath().toAbsolutePath().normalize()
		Files.createDirectories(root)

		new JarFile(jar).withCloseable { JarFile jarFile ->
			jarFile.entries().each { JarEntry entry ->
				if (entry.directory || isSkippedMetaInf(entry.name)) {
					return
				}
				Path destination = root.resolve(entry.name).normalize()
				if (!destination.startsWith(root)) {
					throw new IOException("Refusing to extract ${entry.name} outside of ${outDir}")
				}
				Files.createDirectories(destination.parent)
				jarFile.getInputStream(entry).withCloseable { InputStream input ->
					Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}
	}

	private static boolean isSkippedMetaInf(String name) {
		String upper = name.toUpperCase(Locale.ROOT)
		if (!upper.startsWith('META-INF/')) {
			return false
		}
		return upper == 'META-INF/MANIFEST.MF' ||
				upper.endsWith('.SF') ||
				upper.endsWith('.RSA') ||
				upper.endsWith('.DSA') ||
				upper.endsWith('.EC')
	}

	static String readSourceKind(File dir) {
		File marker = new File(dir, SOURCE_KIND_MARKER)
		if (!marker.isFile()) {
			return null
		}
		String value = marker.getText('UTF-8').trim()
		return value.isEmpty() ? null : value
	}

	static void writeSourceKind(File dir, String kind) {
		dir.mkdirs()
		new File(dir, SOURCE_KIND_MARKER).setText(kind, 'UTF-8')
	}

	static boolean containsJavaSources(File dir) {
		if (dir == null || !dir.isDirectory()) {
			return false
		}
		Stream<Path> stream = Files.walk(dir.toPath())
		try {
			return stream.anyMatch { Path p -> p.fileName.toString().endsWith('.java') && Files.isRegularFile(p) }
		} finally {
			stream.close()
		}
	}

	static boolean isMissingMarkerFresh(File marker) {
		return marker.isFile() && (System.currentTimeMillis() - marker.lastModified()) < MISSING_RECHECK_MILLIS
	}
}
