package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HytaleWorkspacePluginTest extends Specification {

	@TempDir
	File testProjectDir

	File settingsFile
	File buildFile

	String localRepoUri

	def setup() {
		settingsFile = new File(testProjectDir, 'settings.gradle')
		buildFile = new File(testProjectDir, 'build.gradle')
		def repo = new File(testProjectDir, 'test-m2')
		createMavenRepoModule(repo, 'com.hypixel.hytale', 'Server', '1.0.0')
		localRepoUri = repo.toURI().toString()
	}

	def "runAllMods uses configured hostProject"() {
		given:
		settingsFile << """
            rootProject.name = 'test-workspace'
            include 'a', 'b'
        """

		buildFile << """
            plugins {
                id 'com.azuredoom.hytale-workspace'
            }

            hytaleWorkspace {
                modProjects = [':a', ':b']
                hostProject = ':b'
                manifestGroup = 'com.example'
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }

            tasks.register('printRunAllModsDeps') {
                doLast {
                    def runAllMods = tasks.named('runAllMods').get()
                    println 'RUN_ALL_MODS_DEPS=' + runAllMods.taskDependencies
                        .getDependencies(runAllMods)
                        .collect { it.path }
                        .sort()
                        .join(',')
                }
            }
        """

		def aBuild = new File(testProjectDir, 'a/build.gradle')
		aBuild.parentFile.mkdirs()
		aBuild << """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            repositories {
                maven { url = uri('${localRepoUri}') }
            }

            hytaleTools {
                modId = 'a'
                manifestGroup = 'com.example'
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }
        """

		def bBuild = new File(testProjectDir, 'b/build.gradle')
		bBuild.parentFile.mkdirs()
		bBuild << """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            repositories {
                maven { url = uri('${localRepoUri}') }
            }

            hytaleTools {
                modId = 'b'
                manifestGroup = 'com.example'
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }
        """

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printRunAllModsDeps')
				.build()

		then:
		result.output.contains(':b:downloadAssetsZip')
		!result.output.contains(':a:downloadAssetsZip')
	}

	def "fails when hostProject is not in modProjects"() {
		given:
		settingsFile << """
            rootProject.name = 'test-workspace'
            include 'a', 'b', 'c'
        """

		buildFile << """
            plugins {
                id 'com.azuredoom.hytale-workspace'
            }

            hytaleWorkspace {
                modProjects = [':a', ':b']
                hostProject = ':c'
                manifestGroup = 'com.example'
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }
        """

		def aBuild = new File(testProjectDir, 'a/build.gradle')
		aBuild.parentFile.mkdirs()
		aBuild << """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                modId = 'a'
                manifestGroup = 'com.example'
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }
        """

		def bBuild = new File(testProjectDir, 'b/build.gradle')
		bBuild.parentFile.mkdirs()
		bBuild << """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                modId = 'b'
                manifestGroup = 'com.example'
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }
        """

		def cBuild = new File(testProjectDir, 'c/build.gradle')
		cBuild.parentFile.mkdirs()
		cBuild << """
        """

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('runAllMods', '--dry-run')
				.buildAndFail()

		then:
		result.output.contains("hytaleWorkspace.hostProject ':c'")
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