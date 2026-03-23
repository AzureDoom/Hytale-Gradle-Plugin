package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
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
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('tasks', '--all')
                .build()

        then:
        result.output.contains('updatePluginManifest')
        result.output.contains('decompileServerJar')
        result.output.contains('prepareRunServer')
        result.output.contains('downloadAssetsZip')
        result.output.contains('runServer')
    }
}