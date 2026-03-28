package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Specification
import spock.lang.TempDir

class HytalePluginFunctionalTest extends Specification {

    @TempDir
    File testProjectDir

    def "auto injects default server dependency into vineServerJar and compileOnly"() {
            given:
            new File(testProjectDir, 'settings.gradle') << '''
                rootProject.name = 'auto-server-dep-test'
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

                tasks.register('printDeclaredDeps') {
                    doLast {
                        def vineServerJarDeps = configurations.vineServerJar.allDependencies
                            .collect { "${it.group}:${it.name}:${it.version}" }
                            .sort()
                        def compileOnlyDeps = configurations.compileOnly.allDependencies
                            .collect { "${it.group}:${it.name}:${it.version}" }
                            .sort()

                        println "VINE_SERVER_JAR=" + vineServerJarDeps.join(',')
                        println "COMPILE_ONLY=" + compileOnlyDeps.join(',')
                    }
                }
            '''

        when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('printDeclaredDeps', '-q', '--stacktrace')
                .build()

                    then:
            result.output.contains('VINE_SERVER_JAR=com.hypixel.hytale:Server:1.0.0')
            result.output.contains('COMPILE_ONLY=com.hypixel.hytale:Server:1.0.0')
        }

    def "does not auto inject when user explicitly declares vineServerJar"() {
            given:
            new File(testProjectDir, 'settings.gradle') << '''
                rootProject.name = 'explicit-server-dep-test'
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
        
                dependencies {
                    vineServerJar 'com.example:custom-server:9.9.9'
                }
        
                tasks.register('printDeclaredDeps') {
                    doLast {
                        def vineServerJarDeps = configurations.vineServerJar.allDependencies
                            .collect { "${it.group}:${it.name}:${it.version}" }
                            .sort()
                        def compileOnlyDeps = configurations.compileOnly.allDependencies
                            .collect { "${it.group}:${it.name}:${it.version}" }
                            .sort()
        
                        println "VINE_SERVER_JAR=" + vineServerJarDeps.join(',')
                        println "COMPILE_ONLY=" + compileOnlyDeps.join(',')
                    }
                }
            '''

        when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('printDeclaredDeps', '-q', '--stacktrace')
                .build()

        then:
            result.output.contains('VINE_SERVER_JAR=com.example:custom-server:9.9.9')
            result.output.contains('COMPILE_ONLY=com.example:custom-server:9.9.9')
            !result.output.contains('com.hypixel.hytale:Server:1.0.0')
        }

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

        def localRepo = new File(testProjectDir, 'test-m2')
        def artifactDir = new File(localRepo, 'com/hypixel/hytale/Server/1.0.0')
        artifactDir.mkdirs()

        new File(artifactDir, 'Server-1.0.0.pom').text = '''
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.hypixel.hytale</groupId>
              <artifactId>Server</artifactId>
              <version>1.0.0</version>
              <packaging>jar</packaging>
            </project>
        '''

        new java.util.zip.ZipOutputStream(
                new FileOutputStream(new File(artifactDir, 'Server-1.0.0.jar'))
        ).close()

        new File(testProjectDir, 'build.gradle') << '''
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }
        
            group = 'com.example'
            version = '1.0.0'
        
            repositories {
                maven { url = uri('test-m2') }
            }
        
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