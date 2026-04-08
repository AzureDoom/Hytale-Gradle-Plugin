package com.azuredoom.gradle.hytale

import spock.lang.Specification
import spock.lang.TempDir

class JvmDevRuntimeSupportTest extends Specification {

	@TempDir
	File tempDir

	def "findJavaHomesUnder finds nested Java homes"() {
		given:
		File javaHome = new File(tempDir, "apps/IDEA-U/ch-0/241.1/jbr")
		createFakeJavaHome(javaHome)

		when:
		def homes = JvmDevRuntimeSupport.findJavaHomesUnder(tempDir, 8)

		then:
		homes*.canonicalFile.contains(javaHome.canonicalFile)
	}

	def "looksLikeJetBrainsRuntimeHome detects common names"() {
		expect:
		JvmDevRuntimeSupport.looksLikeJetBrainsRuntimeHome(new File("/tmp/JetBrains/jbr"))
		JvmDevRuntimeSupport.looksLikeJetBrainsRuntimeHome(new File("/tmp/foo-jbrsdk"))
		!JvmDevRuntimeSupport.looksLikeJetBrainsRuntimeHome(new File("/tmp/plain-jdk"))
	}

	def "resolveJava returns configured jbr when valid"() {
		given:
		File javaHome = new File(tempDir, "jbr")
		createFakeJavaHome(javaHome)

		when:
		def resolution = JvmDevRuntimeSupport.resolveJava(javaHome.absolutePath)

		then:
		resolution.javaExecutable.canonicalFile == new File(javaHome, "bin/${javaName()}").canonicalFile
		resolution.source == "configured:jbrHome"
	}

	def "resolveFromJetBrainsToolbox prefers newest candidate"() {
		given:
		File older = new File(tempDir, "JetBrains/Toolbox/apps/IDEA-U/ch-0/old-jbr")
		File newer = new File(tempDir, "JetBrains/Toolbox/apps/IDEA-U/ch-0/new-jbr")

		createFakeJavaHome(older)
		createFakeJavaHome(newer)

		older.setLastModified(System.currentTimeMillis() - 100000)
		newer.setLastModified(System.currentTimeMillis())

		when:
		def result = JvmDevRuntimeSupport.resolveFromJetBrainsToolbox([tempDir])

		then:
		result != null
		result.javaHome.canonicalFile == newer.canonicalFile
		result.source == "toolbox"
	}

	private static void createFakeJavaHome(File home) {
		File bin = new File(home, "bin")
		assert bin.mkdirs()
		File exe = new File(bin, javaName())
		assert exe.createNewFile()
	}

	private static String javaName() {
		System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
	}
}