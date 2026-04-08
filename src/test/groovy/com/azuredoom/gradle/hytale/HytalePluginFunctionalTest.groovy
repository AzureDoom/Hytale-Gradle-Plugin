package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HytalePluginFunctionalTest extends Specification {

	@TempDir
	File testProjectDir

	def "auto injects default server dependency into vineServerJar and compileOnly when resolved"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
            rootProject.name = 'auto-server-dep-test'
        '''
		def localRepo = createMavenRepoModule('com.hypixel.hytale', 'Server', '1.0.0')

		new File(testProjectDir, 'build.gradle') << """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            group = 'com.example'
            version = '1.0.0'

            repositories {
                maven { url = uri('${localRepo.toURI()}') }
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
            }

            tasks.register('printResolvedDeps') {
                doLast {
                    def serverFiles = configurations.vineServerJar.files*.name.sort()
                    def compileFiles = configurations.compileClasspath.files*.name.sort()
            
                    println 'VINE_SERVER_FILES=' + serverFiles.join(',')
                    println 'COMPILE_CLASSPATH_FILES=' + compileFiles.join(',')
                }
            }
        """

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printResolvedDeps', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('VINE_SERVER_FILES=Server-1.0.0.jar')
		result.output.contains('COMPILE_CLASSPATH_FILES=Server-1.0.0.jar')
	}

	def "adds Assets.zip file dependency to hytaleAssets and exposes it through compileOnly only when present"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
	rootProject.name = 'assets-zip-classpath-test'
'''
		def localRepo = createMavenRepoModule('com.hypixel.hytale', 'Server', '1.0.0')

		new File(testProjectDir, 'build.gradle') << """
	plugins {
		id 'java'
		id 'com.azuredoom.hytale-tools'
	}

	group = 'com.example'
	version = '1.0.0'

	repositories {
		maven { url = uri('${localRepo.toURI()}') }
	}

	hytaleTools {
		hytaleVersion = '1.0.0'
		patchline = 'release'
	}

	tasks.register('seedAssetsZip') {
		doLast {
			def assetsFile = new File(gradle.gradleUserHomeDir, 'caches/hytale-assets/release-1.0.0-Assets.zip')
			assetsFile.parentFile.mkdirs()

			new java.util.zip.ZipOutputStream(new FileOutputStream(assetsFile)).withCloseable { zos ->
				zos.putNextEntry(new java.util.zip.ZipEntry('placeholder.txt'))
				zos.write('ok'.bytes)
				zos.closeEntry()
			}
		}
	}

	tasks.register('printAssetsClasspathInfo') {
		dependsOn 'seedAssetsZip'

		doLast {
			def hytaleAssets = configurations.hytaleAssets
			def compileOnly = configurations.compileOnly

			println "COMPILE_ONLY_EXTENDS_HYTALE_ASSETS=" + compileOnly.extendsFrom.contains(hytaleAssets)

			def fileDeps = hytaleAssets.dependencies.findAll {
				it instanceof org.gradle.api.artifacts.FileCollectionDependency
			}

			println "HYTALE_ASSETS_DEP_COUNT=" + fileDeps.size()
			println "COMPILE_CLASSPATH_FILES=" + configurations.compileClasspath.files*.name.sort().join(',')
		}
	}
	"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withTestKitDir(new File(testProjectDir, '.gradle-test-kit'))
				.withPluginClasspath()
				.withArguments('printAssetsClasspathInfo', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('COMPILE_ONLY_EXTENDS_HYTALE_ASSETS=true')
		result.output.contains('HYTALE_ASSETS_DEP_COUNT=1')

		def classpathLine = result.output.readLines().find { it.startsWith('COMPILE_CLASSPATH_FILES=') }
		assert classpathLine != null
		assert classpathLine.contains('release-1.0.0-Assets.zip')
	}

	def "does not auto inject when user explicitly declares vineServerJar"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
            rootProject.name = 'explicit-server-dep-test'
        '''
		def localRepo = new File(testProjectDir, 'test-m2')
		createMavenRepoModule(localRepo, 'com.hypixel.hytale', 'Server', '1.0.0')
		createMavenRepoModule(localRepo, 'com.example', 'custom-server', '9.9.9')

		new File(testProjectDir, 'build.gradle') << """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            group = 'com.example'
            version = '1.0.0'

            repositories {
                maven { url = uri('${localRepo.toURI()}') }
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
            }

            dependencies {
                vineServerJar 'com.example:custom-server:9.9.9'
            }

            tasks.register('printResolvedDeps') {
                doLast {
                    def serverFiles = configurations.vineServerJar.files*.name.sort()
                    def compileFiles = configurations.compileClasspath.files*.name.sort()
            
                    println 'VINE_SERVER_FILES=' + serverFiles.join(',')
                    println 'COMPILE_CLASSPATH_FILES=' + compileFiles.join(',')
                }
            }
        """

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printResolvedDeps', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('VINE_SERVER_FILES=custom-server-9.9.9.jar')
		result.output.contains('COMPILE_CLASSPATH_FILES=custom-server-9.9.9.jar')
		!result.output.contains('Server-1.0.0.jar')
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
		result.output.contains('prepareDecompiledSourcesForIde')
		result.output.contains('hytaleDoctor')
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

		def localRepo = createMavenRepoModule('com.hypixel.hytale', 'Server', '1.0.0')

		new File(testProjectDir, 'build.gradle') << """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            group = 'com.example'
            version = '1.0.0'

            repositories {
                maven { url = uri('${localRepo.toURI()}') }
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
        """

		new File(testProjectDir, 'src/main/java/com/example/mods').mkdirs()
		new File(testProjectDir, 'src/main/java/com/example/mods/ExampleMod.java') << '''
            package com.example.mods;
            public class ExampleMod {}
        '''

		new File(testProjectDir, 'src/main/resources').mkdirs()

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withTestKitDir(new File(testProjectDir, '.gradle-test-kit'))
				.withPluginClasspath()
				.withArguments('javadocJar', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')
		new File(testProjectDir, 'src/main/resources/manifest.json').exists()
		new File(testProjectDir, 'build/libs').listFiles()?.any { it.name.endsWith('-javadoc.jar') }
	}

	def "idea depends on prepareDecompiledSourcesForIde when idea plugin is applied"() {
		given:
		new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'functional-test'\n"
		new File(testProjectDir, 'build.gradle') << '''
        plugins {
            id 'java'
            id 'idea'
            id 'com.azuredoom.hytale-tools'
        }

        group = 'com.example'
        version = '1.0.0'

        hytaleTools {
            hytaleVersion = '1.0.0'
        }

        tasks.register('printIdeaDeps') {
            doLast {
                def ideaTask = tasks.named('idea').get()
                println 'IDEA_DEPS=' + ideaTask.taskDependencies.getDependencies(ideaTask)*.name.sort().join(',')
            }
        }
    '''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printIdeaDeps', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('prepareDecompiledSourcesForIde')
	}

	def "plugin does not auto apply idea plugin"() {
		given:
		new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'functional-test'\n"
		new File(testProjectDir, 'build.gradle') << '''
        plugins {
            id 'java'
            id 'com.azuredoom.hytale-tools'
        }

        tasks.register('checkIdeaTask') {
            doLast {
                println 'HAS_IDEA=' + tasks.names.contains('idea')
            }
        }
    '''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('checkIdeaTask', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('HAS_IDEA=false')
	}

	def "runServer uses default server args and empty custom jvm args by default"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
		rootProject.name = 'runserver-default-args-test'
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

		tasks.register('printRunServerConfig') {
			doLast {
				def runServer = tasks.named('runServer', com.azuredoom.gradle.hytale.RunServerTask).get()

				println 'SERVER_ARGS=' + runServer.serverArgs.get().join(',')
				println 'SERVER_JVM_ARGS=' + runServer.serverJvmArgs.get().join(',')
			}
		}
	'''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printRunServerConfig', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('SERVER_ARGS=--allow-op,--disable-sentry')
		result.output.contains('SERVER_JVM_ARGS=')
	}

	def "runServer uses custom server args and custom jvm args from the extension"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
		rootProject.name = 'runserver-custom-args-test'
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
			serverArgs = ['--allow-op', '--disable-sentry', '--disable-file-watcher']
			serverJvmArgs = ['-Xms1G', '-Xmx2G']
		}

		tasks.register('printRunServerConfig') {
			doLast {
				def runServer = tasks.named('runServer', com.azuredoom.gradle.hytale.RunServerTask).get()

				println 'SERVER_ARGS=' + runServer.serverArgs.get().join(',')
				println 'SERVER_JVM_ARGS=' + runServer.serverJvmArgs.get().join(',')
			}
		}
	'''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printRunServerConfig', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('SERVER_ARGS=--allow-op,--disable-sentry,--disable-file-watcher')
		result.output.contains('SERVER_JVM_ARGS=-Xms1G,-Xmx2G')
	}

	def "RunServerTask prepends the required assets argument before custom server args at execution time"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
		rootProject.name = 'runserver-assets-arg-test'
	'''

		new File(testProjectDir, 'build.gradle') << '''
		import com.azuredoom.gradle.hytale.RunServerTask
		import org.gradle.api.tasks.TaskAction

		plugins {
			id 'java'
			id 'com.azuredoom.hytale-tools'
		}

		group = 'com.example'
		version = '1.0.0'

		hytaleTools {
			hytaleVersion = '1.0.0'
			patchline = 'release'
			serverArgs = ['--allow-op', '--disable-sentry', '--disable-file-watcher']
			serverJvmArgs = ['-Xms1G', '-Xmx2G']
		}

		def assetsFile = new File(gradle.gradleUserHomeDir, 'caches/hytale-assets/release-1.0.0-Assets.zip')

		tasks.register('seedAssetsZip') {
			doLast {
				assetsFile.parentFile.mkdirs()

				new java.util.zip.ZipOutputStream(new FileOutputStream(assetsFile)).withCloseable { zos ->
					zos.putNextEntry(new java.util.zip.ZipEntry('placeholder.txt'))
					zos.write('ok'.bytes)
					zos.closeEntry()
				}
			}
		}

		abstract class InspectRunServerTask extends RunServerTask {
			@TaskAction
			@Override
			void exec() {
				def resolvedAssetsZip = assetsZip.get().asFile
				println 'FINAL_ARGS=' + buildResolvedArgs(resolvedAssetsZip).join(',')
				println 'FINAL_JVM_ARGS=' + buildResolvedJvmArgs().join(',')
			}
		}

		tasks.register('inspectRunServerArgs', InspectRunServerTask) {
			dependsOn 'seedAssetsZip'

			serverArgs.set(hytaleTools.serverArgs)
			serverJvmArgs.set(hytaleTools.serverJvmArgs)
			assetsZip.set(layout.file(provider { assetsFile }))

			mainClass.set('com.example.DoesNotMatter')
			classpath = files()
			jvmArgs('--enable-native-access=ALL-UNNAMED')
		}
	'''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withTestKitDir(new File(testProjectDir, '.gradle-test-kit'))
				.withPluginClasspath()
				.withArguments('inspectRunServerArgs', '-q', '--stacktrace')
				.build()

		then:
		def argsLine = result.output.readLines().find { it.startsWith('FINAL_ARGS=') }
		assert argsLine != null
		assert argsLine.contains('--assets=')
		assert argsLine.contains('release-1.0.0-Assets.zip')
		assert argsLine.contains('--allow-op,--disable-sentry,--disable-file-watcher')

		def jvmArgsLine = result.output.readLines().find { it.startsWith('FINAL_JVM_ARGS=') }
		assert jvmArgsLine != null
		assert jvmArgsLine.contains('-Xms1G')
		assert jvmArgsLine.contains('-Xmx2G')
	}

	def "runServer depends on configured preRunTask when set"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
		rootProject.name = 'runserver-prerun-test'
	'''

		new File(testProjectDir, 'build.gradle') << '''
		plugins {
			id 'java'
			id 'com.azuredoom.hytale-tools'
		}

		group = 'com.example'
		version = '1.0.0'

		tasks.register('generateDevResources') {
			doLast {
				println 'generated'
			}
		}

		hytaleTools {
			hytaleVersion = '1.0.0'
			preRunTask = 'generateDevResources'
		}

		tasks.register('printRunServerDeps') {
			doLast {
				def runServer = tasks.named('runServer').get()
				def deps = runServer.taskDependencies.getDependencies(runServer)*.name.sort()

				println 'RUN_SERVER_DEPS=' + deps.join(',')
			}
		}
	'''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printRunServerDeps', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('generateDevResources')
		result.output.contains('prepareRunServer')
		result.output.contains('downloadAssetsZip')
	}

	def "runServer does not add a preRunTask dependency when preRunTask is blank"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
		rootProject.name = 'runserver-no-prerun-test'
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
			preRunTask = ''
		}

		tasks.register('printRunServerDeps') {
			doLast {
				def runServer = tasks.named('runServer').get()
				def deps = runServer.taskDependencies.getDependencies(runServer)*.name.sort()

				println 'RUN_SERVER_DEPS=' + deps.join(',')
			}
		}
	'''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('printRunServerDeps', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('prepareRunServer')
		result.output.contains('downloadAssetsZip')
	}

	def "vineImplementation without version fails decompile target validation"() {
		given:
		new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'functional-test'\n"
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
                vineImplementation 'com.example:demo'
            }
        '''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--stacktrace')
				.buildAndFail()

		then:
		result.output.contains('Dependencies used for decompilation must use full GAV coordinates')
	}

	def "vineCompileOnly without version fails decompile target validation"() {
		given:
		new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'functional-test'\n"
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
                vineCompileOnly 'com.example:demo'
            }
        '''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--stacktrace')
				.buildAndFail()

		then:
		result.output.contains('Dependencies used for decompilation must use full GAV coordinates')
	}

	def "project dependency is ignored by decompile target validation"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
            rootProject.name = 'functional-test'
            include 'dep'
        '''
		new File(testProjectDir, 'dep').mkdirs()
		new File(testProjectDir, 'dep/build.gradle') << "plugins { id 'java' }\n"
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
                vineImplementation project(':dep')
            }
        '''

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--stacktrace')
				.build()

		then:
		result.output.contains('BUILD SUCCESSFUL')
	}

	def "hytaleDoctor prints a useful diagnostic summary"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
            rootProject.name = 'doctor-test'
        '''
		def localRepo = createMavenRepoModule('com.hypixel.hytale', 'Server', '1.0.0')

		new File(testProjectDir, 'build.gradle') << """
            plugins {
                id 'java'
                id 'com.azuredoom.hytale-tools'
            }

            group = 'com.example'
            version = '1.0.0'

            repositories {
                maven { url = uri('${localRepo.toURI()}') }
            }

            hytaleTools {
                hytaleVersion = '1.0.0'
                patchline = 'release'
            }
        """
		new File(testProjectDir, 'src/main/resources').mkdirs()
		new File(testProjectDir, 'src/main/resources/manifest.json') << '{}'

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('hytaleDoctor', '-q', '--stacktrace')
				.build()

		then:
		result.output.contains('HYTALE_VERSION=1.0.0')
		result.output.contains('PATCHLINE=release')
		result.output.contains('VINE_SERVER_FILES=Server-1.0.0.jar')
	}

	def "tasks is configuration cache compatible across two runs"() {
		given:
		new File(testProjectDir, 'settings.gradle') << '''
            rootProject.name = 'configuration-cache-test'
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
                vineImplementation 'com.example:demo:1.2.3'
            }
        '''

		when:
		def first = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--configuration-cache', '--stacktrace')
				.build()

		def second = GradleRunner.create()
				.withProjectDir(testProjectDir)
				.withPluginClasspath()
				.withArguments('tasks', '--configuration-cache', '--stacktrace')
				.build()

		then:
		first.output.contains('Configuration cache entry stored') || first.output.contains('Configuration cache entry reused')
		second.output.contains('Configuration cache entry reused')
	}

	private File createMavenRepoModule(String group, String module, String version) {
		def repo = new File(testProjectDir, 'test-m2')
		createMavenRepoModule(repo, group, module, version)
		repo
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

		createJar(new File(artifactDir, "${module}-${version}.jar"), 'placeholder.txt', 'ok'.bytes)
	}

	private static void createJar(File file, String entryName, byte[] content) {
		file.parentFile.mkdirs()
		new ZipOutputStream(file.newOutputStream()).withCloseable { zos ->
			zos.putNextEntry(new ZipEntry(entryName))
			zos.write(content)
			zos.closeEntry()
		}
	}
}
