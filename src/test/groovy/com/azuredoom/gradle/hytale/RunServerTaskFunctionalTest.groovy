package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RunServerTaskFunctionalTest extends Specification {

	@TempDir
	File projectDir

	String localRepoUri

	def setup() {
		def repo = new File(projectDir, 'test-m2')
		createMavenRepoModule(repo, 'com.hypixel.hytale', 'Server', '1.0.0')
		localRepoUri = repo.toURI().toString()
	}

	def "runServer task is registered"() {
		given:
		setupBasicProject()

		when:
		def result = GradleRunner.create()
				.withProjectDir(projectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--all')
				.build()

		then:
		result.output.contains('runServer')
	}

	def "preRunTask is hooked into runServer"() {
		given:
		new File(projectDir, 'settings.gradle') << "rootProject.name='test'"

		new File(projectDir, 'build.gradle') << """
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools'
}

tasks.register('myPreTask') {
    doLast { println 'pre task ran' }
}

repositories {
    maven { url = uri("${localRepoUri}") }
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.test'
    modId = 'testmod'
    mainClass = 'com.test.Main'
    preRunTask = 'myPreTask'
}
"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(projectDir)
				.withPluginClasspath()
				.withArguments('runServer', '--dry-run')
				.build()

		then:
		result.output.contains('myPreTask')
	}

	private void setupBasicProject() {
		new File(projectDir, 'settings.gradle') << "rootProject.name='test'"

		new File(projectDir, 'build.gradle') << """
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools'
}

repositories {
    maven { url = uri("${localRepoUri}") }
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.test'
    modId = 'testmod'
    mainClass = 'com.test.Main'
}
"""
	}

	private static void createMavenRepoModule(File repoRoot, String group, String module, String version) {
		def groupPath = group.replace('.', '/')
		def artifactDir = new File(repoRoot, "${groupPath}/${module}/${version}")
		artifactDir.mkdirs()
		new File(artifactDir, "${module}-${version}.pom").text = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>${group}</groupId>
              <artifactId>${module}</artifactId>
              <version>${version}</version>
              <packaging>jar</packaging>
            </project>
        """
		new ZipOutputStream(new File(artifactDir, "${module}-${version}.jar").newOutputStream()).withCloseable { zos ->
			zos.putNextEntry(new ZipEntry('placeholder.txt'))
			zos.write('ok'.bytes)
			zos.closeEntry()
		}
	}
}