package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Specification
import spock.lang.TempDir

class HytalePluginFunctionalTest extends Specification {

    @TempDir
    File testProjectDir

    def "plugin adds expected tasks to a real build"() {
        given:
        new File(testProjectDir, 'settings.gradle') << '''
            rootProject.name = 'functional-test'
        '''

        new File(testProjectDir, 'build.gradle') << '''
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            group = 'com.example'
            version = '1.0.0'

            hytaleTools {
                hytaleVersion = '1.0.0'
            }
        '''

        when:
        def runner = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('tasks', '--all', '--stacktrace')
                .forwardOutput()

        def result
        try {
            result = runner.build()
        } catch (UnexpectedBuildFailure e) {
            throw new AssertionError("Gradle build failed.\n\nOutput:\n${e.buildResult.output}", e)
        }

        then:
        result.output.contains('createManifestIfMissing')
        result.output.contains('updatePluginManifest')
        result.output.contains('validateManifest')
        result.output.contains('decompileServerJar')
        result.output.contains('prepareRunServer')
        result.output.contains('downloadAssetsZip')
        result.output.contains('runServer')
    }

    def "sourcesJar works when manifest must be created during the build"() {
        given:
        new File(testProjectDir, 'settings.gradle') << '''
        rootProject.name = 'sourcesjar-test'
    '''

        new File(testProjectDir, 'build.gradle') << '''
        plugins {
            id 'java'
            id 'com.azuredoom.hytale-tools'
        }

        group = 'com.example'
        version = '1.0.0'

        java {
            withSourcesJar()
        }

        hytaleTools {
            hytaleVersion = '1.0.0'
            manifestGroup = 'com.example.mods'
            modId = 'examplemod'
            mainClass = 'com.example.mods.ExampleMod'
        }
    '''

        new File(testProjectDir, 'src/main/java/com/example/mods').mkdirs()
        new File(testProjectDir, 'src/main/java/com/example/mods/ExampleMod.java') << '''
        package com.example.mods;
        public class ExampleMod {}
    '''

        new File(testProjectDir, 'src/main/resources').mkdirs()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('sourcesJar', '--stacktrace')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        new File(testProjectDir, 'src/main/resources/manifest.json').exists()
        new File(testProjectDir, 'build/libs').listFiles().any { it.name.endsWith('-sources.jar') }
    }

    def "javadocJar works when manifest must be created during the build"() {
        given:
        new File(testProjectDir, 'settings.gradle') << '''
        rootProject.name = 'javadocjar-test'
    '''

        new File(testProjectDir, 'build.gradle') << '''
        plugins {
            id 'java'
            id 'com.azuredoom.hytale-tools'
        }

        group = 'com.example'
        version = '1.0.0'

        java {
            withJavadocJar()
        }

        hytaleTools {
            hytaleVersion = '1.0.0'
            manifestGroup = 'com.example.mods'
            modId = 'examplemod'
            mainClass = 'com.example.mods.ExampleMod'
        }
    '''

        new File(testProjectDir, 'src/main/java/com/example/mods').mkdirs()
        new File(testProjectDir, 'src/main/java/com/example/mods/ExampleMod.java') << '''
        package com.example.mods;
        public class ExampleMod {}
    '''

        new File(testProjectDir, 'src/main/resources').mkdirs()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('javadocJar', '--stacktrace')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        new File(testProjectDir, 'src/main/resources/manifest.json').exists()
        new File(testProjectDir, 'build/libs').listFiles()?.any { it.name.endsWith('-javadoc.jar') }
    }
}