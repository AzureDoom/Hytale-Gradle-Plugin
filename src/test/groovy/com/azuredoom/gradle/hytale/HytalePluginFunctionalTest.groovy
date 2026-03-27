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
}