package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class HytaleWorkspacePluginTest extends Specification {

	@TempDir
	File testProjectDir

	File settingsFile
	File buildFile

	def setup() {
		settingsFile = new File(testProjectDir, 'settings.gradle')
		buildFile = new File(testProjectDir, 'build.gradle')
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
}