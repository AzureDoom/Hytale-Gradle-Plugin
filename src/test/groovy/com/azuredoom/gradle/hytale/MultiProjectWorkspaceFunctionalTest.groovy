package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class MultiProjectWorkspaceFunctionalTest extends Specification {

	@TempDir
	File testProjectDir

	File writeFile(String path, String content) {
		File f = new File(testProjectDir, path)
		f.parentFile?.mkdirs()
		f.text = content.stripIndent()
		return f
	}

	def "runAllMods fails when hytaleVersion differs across mod subprojects"() {
		given:
		writeFile("settings.gradle", """
            rootProject.name = 'workspace-test'
            include 'common', 'modA', 'modB'
        """)

		writeFile("build.gradle", """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }
        """)

		writeFile("common/build.gradle", """
            plugins {
                id 'java-library'
            }
        """)

		writeFile("modA/build.gradle", """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                manifestGroup = 'com.example.mods'
                modId = 'moda'
                mainClass = 'com.example.mods.moda.ModA'
            }
        """)

		writeFile("modB/build.gradle", """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.1'
                manifestGroup = 'com.example.mods'
                modId = 'modb'
                mainClass = 'com.example.mods.modb.ModB'
            }
        """)

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments("runAllMods", "--stacktrace")
				.buildAndFail()

		then:
		result.output.contains("All Hytale subprojects must use the same hytaleVersion for runAllMods")
	}

	def "workspace tasks are only registered on the root project"() {
		given:
		writeFile("settings.gradle", """
            rootProject.name = 'workspace-test'
            include 'modA'
        """)

		writeFile("build.gradle", """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }
        """)

		writeFile("modA/build.gradle", """
            plugins {
                id 'com.azuredoom.hytale-tools'
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                manifestGroup = 'com.example.mods'
                modId = 'moda'
                mainClass = 'com.example.mods.moda.ModA'
            }
        """)

		when:
		def rootTasks = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments("tasks", "--all")
				.build()

		def subprojectTasks = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments(":modA:tasks", "--all")
				.build()

		then:
		rootTasks.output.contains("runAllMods")
		rootTasks.output.contains("updateAllPluginManifests")
		rootTasks.output.contains("validateAllManifests")
		rootTasks.output.contains("stageAllModAssets")

		subprojectTasks.output.contains("runServer")
		!subprojectTasks.output.contains("runAllMods")
		!subprojectTasks.output.contains("updateAllPluginManifests")
		!subprojectTasks.output.contains("validateAllManifests")
		!subprojectTasks.output.contains("stageAllModAssets")
	}

	def "stageAllModAssets creates the expected root mod asset path"() {
		given:
		writeFile("settings.gradle", """
        rootProject.name = 'workspace-test'
        include 'modA'
    """)

		writeFile("build.gradle", """
        plugins {
            id 'com.azuredoom.hytale-tools'
        }
    """)

		writeFile("modA/build.gradle", """
        plugins {
            id 'com.azuredoom.hytale-tools'
        }

        hytaleTools {
            hytaleVersion = '1.0.0'
            manifestGroup = 'com.example.mods'
            modId = 'moda'
            mainClass = 'com.example.mods.moda.ModA'
        }
    """)

		writeFile("modA/src/main/resources/test.txt", "hello")

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments("stageAllModAssets", "--stacktrace")
				.build()

		then:
		result.output.contains("BUILD SUCCESSFUL")

		def stagedPath = new File(testProjectDir, "run/mods/com_example_mods_moda")
		def stagedFile = new File(stagedPath, "test.txt")

		stagedPath.exists()
		stagedFile.exists()
		stagedFile.text == "hello"
	}
}