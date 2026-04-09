package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.util.jar.JarOutputStream

class ConfigurationCacheFunctionalTest extends Specification {

	@TempDir
	File testProjectDir

	@TempDir
	File gradleUserHomeDir

	def "runServer reuses configuration cache with preRunTask and local cached assets zip"() {
		given:
		writeFile('settings.gradle', """
            rootProject.name = 'runserver-cc-test'
        """)

		writeFile('build.gradle', """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }
        
            tasks.register('myPreTask') {
                doLast {
                    println 'pre task ran'
                }
            }
        
            hytaleTools {
                hytaleVersion = '1.0.0'
                patchline = 'release'
                manifestGroup = 'com.test'
                modId = 'testmod'
                mainClass = 'com.test.Main'
                preRunTask = 'myPreTask'
                debugEnabled = false
                hotSwapEnabled = false
            }
        """)

		createFakeAssetsZip('release', '1.0.0')
		createFakeServerArtifact(testProjectDir, '1.0.0')

		when:
		BuildResult first = gradleRunner(
				'runServer',
				'--configuration-cache',
				'--dry-run',
				'--stacktrace'
				).build()

		BuildResult second = gradleRunner(
				'runServer',
				'--configuration-cache',
				'--dry-run',
				'--stacktrace'
				).build()

		then:
		first.output.contains(':myPreTask')
		first.output.contains(':runServer')
		second.output.contains(':myPreTask')
		second.output.contains(':runServer')
		assertConfigurationCacheStored(first)
		assertConfigurationCacheReused(second)
	}

	def "hytaleJvmDoctor reuses configuration cache"() {
		given:
		writeFile('settings.gradle', """
            rootProject.name = 'jvm-doctor-cc-test'
        """)

		writeFile('build.gradle', """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                manifestGroup = 'com.test'
                modId = 'testmod'
                mainClass = 'com.test.Main'
            }
        """)

		when:
		BuildResult first = gradleRunner(
				'hytaleJvmDoctor',
				'--configuration-cache',
				'--stacktrace'
				).build()

		BuildResult second = gradleRunner(
				'hytaleJvmDoctor',
				'--configuration-cache',
				'--stacktrace'
				).build()

		then:
		first.output.contains('BUILD SUCCESSFUL')
		second.output.contains('BUILD SUCCESSFUL')
		first.output.contains('Java executable:')
		second.output.contains('Java executable:')
		assertConfigurationCacheStored(first)
		assertConfigurationCacheReused(second)
	}

	def "hytaleJvmDoctor reuses configuration cache with configured jbrHome"() {
		given:
		if (isWindows()) {
			return
		}

		File fakeJbr = new File(testProjectDir, 'fake-jbr')
		File binDir = new File(fakeJbr, 'bin')
		assert binDir.mkdirs()

		File fakeJava = new File(binDir, 'java')
		fakeJava.text = '''
#!/bin/sh
if [ "$1" = "-XshowSettings:properties" ]; then
  echo "java.vendor = JetBrains" 1>&2
  echo "java.vm.vendor = JetBrains" 1>&2
  exit 0
fi
if [ "$1" = "-XX:+AllowEnhancedClassRedefinition" ]; then
  exit 0
fi
if [ "$1" = "-XX:HotswapAgent=fatjar" ]; then
  exit 0
fi
exit 0
'''
		assert fakeJava.setExecutable(true)

		writeFile('settings.gradle', """
            rootProject.name = 'jvm-doctor-jbr-cc-test'
        """)

		writeFile('build.gradle', """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                manifestGroup = 'com.test'
                modId = 'testmod'
                mainClass = 'com.test.Main'
                jbrHome = '${escapeForGroovyString(fakeJbr.absolutePath)}'
            }
        """)

		when:
		BuildResult first = gradleRunner(
				'hytaleJvmDoctor',
				'--configuration-cache',
				'--stacktrace'
				).build()

		BuildResult second = gradleRunner(
				'hytaleJvmDoctor',
				'--configuration-cache',
				'--stacktrace'
				).build()

		then:
		first.output.contains('BUILD SUCCESSFUL')
		second.output.contains('BUILD SUCCESSFUL')
		first.output.contains('Resolution source: configured:jbrHome')
		second.output.contains('Resolution source: configured:jbrHome')
		assertConfigurationCacheStored(first)
		assertConfigurationCacheReused(second)
	}

	def "stageAllModAssets reuses configuration cache in a multi-project build"() {
		given:
		writeWorkspaceBuildForStageAllModAssets()

		writeFile('modA/src/main/resources/test-a.txt', 'hello-a')
		writeFile('modB/src/main/resources/test-b.txt', 'hello-b')

		when:
		BuildResult first = gradleRunner(
				'stageAllModAssets',
				'--configuration-cache',
				'--stacktrace'
				).build()

		BuildResult second = gradleRunner(
				'stageAllModAssets',
				'--configuration-cache',
				'--stacktrace'
				).build()

		then:
		first.output.contains('BUILD SUCCESSFUL')
		second.output.contains('BUILD SUCCESSFUL')

		new File(testProjectDir, 'run/mods/com_example_mods_moda/test-a.txt').text == 'hello-a'
		new File(testProjectDir, 'run/mods/com_example_mods_modb/test-b.txt').text == 'hello-b'

		assertConfigurationCacheStored(first)
		assertConfigurationCacheReused(second)
	}

	def "runAllMods dry-run reuses configuration cache with explicit hostProject"() {
		given:
		writeWorkspaceBuildForRunAllMods('1.0.0', '1.0.0')
		createFakeServerArtifact(new File(testProjectDir, 'modA'), '1.0.0')
		createFakeServerArtifact(new File(testProjectDir, 'modB'), '1.0.0')

		when:
		BuildResult first = gradleRunner(
				'runAllMods',
				'--configuration-cache',
				'--dry-run',
				'--stacktrace'
				).build()

		BuildResult second = gradleRunner(
				'runAllMods',
				'--configuration-cache',
				'--dry-run',
				'--stacktrace'
				).build()

		then:
		first.output.contains(':runAllMods SKIPPED') || first.output.contains(':runAllMods')
		second.output.contains(':runAllMods SKIPPED') || second.output.contains(':runAllMods')
		first.output.contains(':stageAllModAssets')
		second.output.contains(':stageAllModAssets')
		first.output.contains(':modA:classes')
		first.output.contains(':modB:classes')
		second.output.contains(':modA:classes')
		second.output.contains(':modB:classes')

		assertConfigurationCacheStored(first)
		assertConfigurationCacheReused(second)
	}

	def "runAllMods version mismatch fails consistently under configuration cache"() {
		given:
		writeWorkspaceBuildForRunAllMods('1.0.0', '1.0.1')

		when:
		BuildResult first = gradleRunner(
				'runAllMods',
				'--configuration-cache',
				'--stacktrace'
				).buildAndFail()

		BuildResult second = gradleRunner(
				'runAllMods',
				'--configuration-cache',
				'--stacktrace'
				).buildAndFail()

		then:
		first.output.contains('All Hytale subprojects must use the same hytaleVersion for runAllMods')
		second.output.contains('All Hytale subprojects must use the same hytaleVersion for runAllMods')
	}

	private void writeWorkspaceBuildForStageAllModAssets() {
		writeFile('settings.gradle', """
            rootProject.name = 'workspace-stage-assets-cc-test'
            include 'modA', 'modB'
        """)

		writeFile('build.gradle', """
            plugins {
                id 'com.azuredoom.hytale-workspace'
            }

            hytaleWorkspace {
                modProjects = [':modA', ':modB']
                hostProject = ':modA'
            }
        """)

		writeFile('modA/build.gradle', """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                patchline = 'release'
                manifestGroup = 'com.example.mods'
                modId = 'moda'
                mainClass = 'com.example.mods.moda.ModA'
            }
        """)

		writeFile('modB/build.gradle', """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                patchline = 'release'
                manifestGroup = 'com.example.mods'
                modId = 'modb'
                mainClass = 'com.example.mods.modb.ModB'
            }
        """)

		writeFile('modA/src/main/java/com/example/mods/moda/ModA.java', """
            package com.example.mods.moda;

            public class ModA {}
        """)

		writeFile('modB/src/main/java/com/example/mods/modb/ModB.java', """
            package com.example.mods.modb;

            public class ModB {}
        """)
	}

	private void writeWorkspaceBuildForRunAllMods(String modAVersion, String modBVersion) {
		writeFile('settings.gradle', """
            rootProject.name = 'workspace-run-all-mods-cc-test'
            include 'modA', 'modB'
        """)

		writeFile('build.gradle', """
            plugins {
                id 'com.azuredoom.hytale-workspace'
            }

            hytaleWorkspace {
                modProjects = [':modA', ':modB']
                hostProject = ':modA'
            }
        """)

		writeFile('modA/build.gradle', """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '${modAVersion}'
                patchline = 'release'
                manifestGroup = 'com.example.mods'
                modId = 'moda'
                mainClass = 'com.example.mods.moda.ModA'
            }
        """)

		writeFile('modB/build.gradle', """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '${modBVersion}'
                patchline = 'release'
                manifestGroup = 'com.example.mods'
                modId = 'modb'
                mainClass = 'com.example.mods.modb.ModB'
            }
        """)

		writeFile('modA/src/main/java/com/example/mods/moda/ModA.java', """
            package com.example.mods.moda;

            public class ModA {}
        """)

		writeFile('modB/src/main/java/com/example/mods/modb/ModB.java', """
            package com.example.mods.modb;

            public class ModB {}
        """)
	}

	private void createFakeAssetsZip(String patchline, String hytaleVersion) {
		File zip = new File(
				gradleUserHomeDir,
				"caches/hytale-assets/${patchline}-${hytaleVersion}-Assets.zip"
				)
		zip.parentFile.mkdirs()
		zip.bytes = [0x50, 0x4B, 0x03, 0x04] as byte[]
	}

	private File writeFile(String path, String content) {
		File file = new File(testProjectDir, path)
		file.parentFile?.mkdirs()
		file.text = content.stripIndent()
		file
	}

	private GradleRunner gradleRunner(String... arguments) {
		List<String> allArgs = []
		allArgs.addAll(arguments.toList())
		allArgs.addAll([
			'-g',
			gradleUserHomeDir.absolutePath
		])

		GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments(allArgs)
				.forwardOutput()
	}

	private static void assertConfigurationCacheStored(BuildResult result) {
		String output = normalizedOutput(result.output)
		assert output.contains('configuration cache')
		assert output.contains('stored') ||
		output.contains('entry stored')
	}

	private static void assertConfigurationCacheReused(BuildResult result) {
		String output = normalizedOutput(result.output)
		assert output.contains('configuration cache')
		assert output.contains('reusing configuration cache') ||
		output.contains('configuration cache entry reused')
		assert !output.contains('configuration cache problems found')
	}

	private static String normalizedOutput(String output) {
		output.toLowerCase(Locale.ROOT)
	}

	private static String escapeForGroovyString(String value) {
		value.replace("\\", "\\\\")
	}

	private static boolean isWindows() {
		System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('win')
	}

	private static void createFakeServerArtifact(File projectDir, String version = '1.0.0') {
		File moduleDir = new File(
				projectDir,
				"build/generated-sources-m2/com/hypixel/hytale/Server/${version}"
				)
		moduleDir.mkdirs()

		new File(moduleDir, "Server-${version}.pom").text = """
		<project xmlns="http://maven.apache.org/POM/4.0.0"
		         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
		  <modelVersion>4.0.0</modelVersion>
		  <groupId>com.hypixel.hytale</groupId>
		  <artifactId>Server</artifactId>
		  <version>${version}</version>
		  <packaging>jar</packaging>
		</project>
	""".stripIndent().trim()

		def jarFile = new File(moduleDir, "Server-${version}.jar")
		new JarOutputStream(new FileOutputStream(jarFile)).close()
	}
}