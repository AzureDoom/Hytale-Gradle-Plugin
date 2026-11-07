package com.azuredoom.gradle.hytale

import spock.lang.Specification
import spock.lang.TempDir

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class HytaleServerSourcesTest extends Specification {

	@TempDir
	File tempDir

	def "official sources are expected from 0.8.0-pre.3 onward"() {
		expect:
		HytaleServerSources.officialSourcesExpected(version) == expected

		where:
		version          || expected
		'0.5.0-pre.9.2'  || false
		'0.6.0-pre.13.1' || false
		'0.7.0-pre.5.2'  || false
		'0.8.0-pre.2.9'  || false
		'0.8.0-pre.3'    || true
		'0.8.0-pre.4'    || true
		'0.8.0-pre.10'   || true
		'0.5.10'         || false
		'0.7.6'          || false
		'0.8.0'          || true
		'0.9.0-pre.1'    || true
		'0.+'            || false
		'latest.release' || false
		null             || false
		''               || false
	}

	def "release sorts after its pre-releases"() {
		expect:
		HytaleServerSources.compareVersions('0.8.0', '0.8.0-pre.4') > 0
		HytaleServerSources.compareVersions('0.8.0-pre.4', '0.8.0-pre.3') > 0
		HytaleServerSources.compareVersions('0.8.0-pre.3', '0.8.0-pre.3') == 0
		HytaleServerSources.compareVersions('0.7.6', '0.8.0-pre.3') < 0
	}

	def "sources jar url follows the maven layout for the patchline repo"() {
		expect:
		HytaleServerSources.sourcesJarUrl(
				HytaleServerSources.repositoryUrlForPatchline('pre-release'), '0.8.0-pre.3') ==
				'https://maven.hytale.com/pre-release/com/hypixel/hytale/Server/0.8.0-pre.3/Server-0.8.0-pre.3-sources.jar'
		HytaleServerSources.repositoryUrlForPatchline('release') == HytaleRepositoryConfigurer.RELEASE_REPO_URL
		HytaleServerSources.repositoryUrlForPatchline(null) == HytaleRepositoryConfigurer.RELEASE_REPO_URL
	}

	def "missing sources jar downloads as null"() {
		given:
		def repo = new File(tempDir, 'repo')
		repo.mkdirs()
		def url = HytaleServerSources.sourcesJarUrl(repo.toURI().toString(), '0.8.0-pre.3')

		expect:
		HytaleServerSources.downloadSourcesJar(url, new File(tempDir, 'out/sources.jar'), null) == null
	}

	def "published sources jar is downloaded and extracted without its manifest"() {
		given:
		def repo = new File(tempDir, 'repo')
		def published = new File(repo, 'com/hypixel/hytale/Server/0.8.0-pre.3/Server-0.8.0-pre.3-sources.jar')
		writeJar(published, ['com/hypixel/hytale/Server.java': 'package com.hypixel.hytale; class Server {}'])
		def url = HytaleServerSources.sourcesJarUrl(repo.toURI().toString(), '0.8.0-pre.3')
		def target = new File(tempDir, 'cache/Server-0.8.0-pre.3-sources.jar')
		def outDir = new File(tempDir, 'extracted')

		when:
		def downloaded = HytaleServerSources.downloadSourcesJar(url, target, null)
		HytaleServerSources.extractSourcesJar(downloaded, outDir)

		then:
		downloaded == target
		HytaleServerSources.isUsableSourcesJar(downloaded)
		new File(outDir, 'com/hypixel/hytale/Server.java').text.contains('class Server')
		!new File(outDir, 'META-INF/MANIFEST.MF').exists()
		HytaleServerSources.containsJavaSources(outDir)
	}

	def "jar without java files is not usable"() {
		given:
		def jar = new File(tempDir, 'empty-sources.jar')
		writeJar(jar, ['readme.txt': 'nothing here'])

		expect:
		!HytaleServerSources.isUsableSourcesJar(jar)
	}

	def "source kind marker round trips"() {
		given:
		def dir = new File(tempDir, 'marker')

		when:
		HytaleServerSources.writeSourceKind(dir, HytaleServerSources.KIND_OFFICIAL)

		then:
		HytaleServerSources.readSourceKind(dir) == HytaleServerSources.KIND_OFFICIAL
		HytaleServerSources.readSourceKind(new File(tempDir, 'nothing')) == null
	}

	private static void writeJar(File jar, Map<String, String> entries) {
		jar.parentFile.mkdirs()
		def manifest = new Manifest()
		manifest.mainAttributes.putValue('Manifest-Version', '1.0')
		new JarOutputStream(new FileOutputStream(jar), manifest).withCloseable { out ->
			entries.each { name, content ->
				out.putNextEntry(new JarEntry(name))
				out.write(content.getBytes('UTF-8'))
				out.closeEntry()
			}
		}
	}
}
