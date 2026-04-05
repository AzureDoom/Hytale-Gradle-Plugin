package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

final class HytaleIdeSourceConfigurer {
    private HytaleIdeSourceConfigurer() {}

    static TaskProvider<?> register(
            Project project,
            HytaleExtension ext,
            def generatedSourcesMavenRepoDir,
            def generatedSourcesIvyRepoDir,
            def vineflowerTool,
            def vineServerJar,
            def vineDependencyJars,
            def vineImplementation,
            def vineCompileOnly,
            def vineDecompileTargets
    ) {
        def vineflowerJarFile = project.layout.file(project.provider { vineflowerTool.singleFile })
        def serverJarFile = project.layout.file(project.provider { vineServerJar.singleFile })
        def decompiledServerDir = project.layout.buildDirectory.dir('vineflower/hytale-server')

        project.tasks.register('decompileServerJar', DecompileServerJarTask) {
            group = null
            description = 'Decompile only com/hypixel/hytale from the Hytale Server jar into build/vineflower/hytale-server/'

            serverJar.set(serverJarFile)
            vineflowerJar.set(vineflowerJarFile)
            decompileClasspath.from(vineServerJar)
            outputDirectory.set(decompiledServerDir)
            tempDirectoryRoot.set(project.layout.buildDirectory.dir('tmp/vineflower-server'))
            javaVersion.set(ext.javaVersion)
        }

        Provider<Set<ResolvedArtifactResult>> serverArtifactsProvider = project.providers.provider {
            HytaleDependencySupport.resolveArtifacts(project, 'vineServerJar')
        }

        def installDependencySourcesToRepo = project.tasks.register('installDependencyDecompiledSourcesToRepo') {
            group = null
            description = 'Installs generated dependency sources jars into local repos for IDE attachment.'
        }

        def serverSourcesJar = project.tasks.register('serverDecompiledSourcesJar', Jar) {
            group = null
            description = 'Packages decompiled Hytale server sources as a sources jar.'

            dependsOn('decompileServerJar')

            archiveBaseName.set('hytale-server-decompiled')
            archiveVersion.set(project.provider { project.version.toString() })
            archiveClassifier.set('sources')

            destinationDirectory.set(project.layout.buildDirectory.dir('generated-sources-jars/server'))
            from(decompiledServerDir)
        }

        def installServerSourcesToRepo = project.tasks.register('installServerDecompiledSourcesToRepo') {
            group = null
            description = 'Installs generated server sources jar into local repos for IDE attachment.'

            dependsOn(serverSourcesJar)
            inputs.file(serverSourcesJar.flatMap { it.archiveFile })

            outputs.files(project.provider {
                def artifacts = serverArtifactsProvider.get()
                if (!artifacts || artifacts.size() != 1) {
                    return []
                }

                def artifact = artifacts.iterator().next()
                def componentId = artifact.id.componentIdentifier
                if (!(componentId instanceof ModuleComponentIdentifier)) {
                    return []
                }

                def outputFiles = []
                outputFiles.addAll(HytaleDependencySupport.mavenRepoFiles(
                        generatedSourcesMavenRepoDir.get().asFile,
                        componentId.group,
                        componentId.module,
                        componentId.version
                ).values())
                outputFiles.addAll(HytaleDependencySupport.ivyRepoFiles(
                        generatedSourcesIvyRepoDir.get().asFile,
                        componentId.group,
                        componentId.module,
                        componentId.version
                ).values())
                outputFiles
            })

            doLast {
                def artifacts = serverArtifactsProvider.get()
                if (artifacts.isEmpty()) {
                    return
                }
                if (artifacts.size() != 1) {
                    throw new GradleException("Expected exactly one resolved artifact in vineServerJar, got ${artifacts.size()}")
                }

                def artifact = artifacts.iterator().next()
                def componentId = artifact.id.componentIdentifier
                if (!(componentId instanceof ModuleComponentIdentifier)) {
                    return
                }

                def binaryJarFile = artifact.file
                def sourcesJarFile = serverSourcesJar.get().archiveFile.get().asFile

                HytaleDependencySupport.installModuleIntoMavenRepo(
                        project,
                        generatedSourcesMavenRepoDir.get().asFile,
                        componentId.group,
                        componentId.module,
                        componentId.version,
                        binaryJarFile,
                        sourcesJarFile
                )

                HytaleDependencySupport.installModuleIntoIvyRepo(
                        project,
                        generatedSourcesIvyRepoDir.get().asFile,
                        componentId.group,
                        componentId.module,
                        componentId.version,
                        binaryJarFile,
                        sourcesJarFile
                )
            }
        }

        def seenDependencyTaskKeys = [] as Set<String>
        def registerDeclaredDependency = { ExternalModuleDependency declaredDep ->
            HytaleDependencySupport.validateDecompileDependency(declaredDep)

            def taskKey = "${declaredDep.group}:${declaredDep.name}:${declaredDep.version}"
            if (!seenDependencyTaskKeys.add(taskKey)) {
                return
            }

            registerDependencyInstallTasks(
                    project,
                    declaredDep,
                    vineflowerJarFile,
                    vineDependencyJars,
                    generatedSourcesMavenRepoDir,
                    generatedSourcesIvyRepoDir,
                    installDependencySourcesToRepo,
                    ext
            )
        }

        [vineDecompileTargets, vineCompileOnly, vineImplementation].each { cfg ->
            cfg.dependencies.all { dep ->
                if (dep instanceof ExternalModuleDependency) {
                    registerDeclaredDependency(dep as ExternalModuleDependency)
                }
            }
        }

        def prepareDecompiledSourcesForIde = project.tasks.register('prepareDecompiledSourcesForIde') {
            group = 'hytale'
            description = 'Builds and installs generated decompiled sources for IDE source attachment.'

            dependsOn(installServerSourcesToRepo)
            dependsOn(installDependencySourcesToRepo)
        }

        project.tasks.matching { it.name == 'idea' }.configureEach {
            dependsOn(prepareDecompiledSourcesForIde)
        }

        prepareDecompiledSourcesForIde
    }

    private static void registerDependencyInstallTasks(
            Project project,
            ExternalModuleDependency declaredDep,
            Provider<?> vineflowerJarFile,
            def vineDependencyJars,
            def generatedSourcesMavenRepoDir,
            def generatedSourcesIvyRepoDir,
            def installDependencySourcesToRepo,
            HytaleExtension ext
    ) {
        def artifactGroup = declaredDep.group
        def artifactModule = declaredDep.name
        def artifactVersion = declaredDep.version

        def safeName = "${artifactGroup}__${artifactModule}__${artifactVersion}".replaceAll('[^A-Za-z0-9_.-]', '_')
        def perArtifactDir = project.layout.buildDirectory.dir("vineflower/dependencies/${safeName}")

        def resolvedBinaryJar = project.providers.provider {
            HytaleDependencySupport.resolveDeclaredDependencyArtifact(project, declaredDep)
        }

        def decompileTask = project.tasks.register("decompile_${safeName}", DecompileDependencyJarTask) {
            group = null
            description = "Internal: Decompile dependency ${artifactGroup}:${artifactModule}:${artifactVersion}"

            inputJar.set(project.layout.file(resolvedBinaryJar))
            vineflowerJar.set(vineflowerJarFile)
            decompileClasspath.from(vineDependencyJars)
            outputDirectory.set(perArtifactDir)
            tempDirectoryRoot.set(project.layout.buildDirectory.dir("tmp/vineflower-deps/${safeName}"))
            javaVersion.set(ext.javaVersion)
        }

        def sourcesJarTask = project.tasks.register("sourcesJar_${safeName}", Jar) {
            group = null
            description = "Internal: Package decompiled sources for ${artifactGroup}:${artifactModule}:${artifactVersion}"

            dependsOn(decompileTask)

            archiveBaseName.set(artifactModule)
            archiveVersion.set(artifactVersion)
            archiveClassifier.set('sources')

            destinationDirectory.set(project.layout.buildDirectory.dir("generated-sources-jars/dependencies/${safeName}"))
            from(perArtifactDir)
        }

        def installTask = project.tasks.register("installSources_${safeName}") {
            group = null
            description = "Internal: Install generated sources for ${artifactGroup}:${artifactModule}:${artifactVersion} into local repos"

            dependsOn(sourcesJarTask)
            inputs.file(sourcesJarTask.flatMap { it.archiveFile })

            outputs.files(project.provider {
                def outputFiles = []
                outputFiles.addAll(HytaleDependencySupport.mavenRepoFiles(
                        generatedSourcesMavenRepoDir.get().asFile,
                        artifactGroup,
                        artifactModule,
                        artifactVersion
                ).values())
                outputFiles.addAll(HytaleDependencySupport.ivyRepoFiles(
                        generatedSourcesIvyRepoDir.get().asFile,
                        artifactGroup,
                        artifactModule,
                        artifactVersion
                ).values())
                outputFiles
            })

            doLast {
                def binaryJarFile = resolvedBinaryJar.get()
                def sourcesJarFile = sourcesJarTask.get().archiveFile.get().asFile

                HytaleDependencySupport.installModuleIntoMavenRepo(
                        project,
                        generatedSourcesMavenRepoDir.get().asFile,
                        artifactGroup,
                        artifactModule,
                        artifactVersion,
                        binaryJarFile,
                        sourcesJarFile
                )

                HytaleDependencySupport.installModuleIntoIvyRepo(
                        project,
                        generatedSourcesIvyRepoDir.get().asFile,
                        artifactGroup,
                        artifactModule,
                        artifactVersion,
                        binaryJarFile,
                        sourcesJarFile
                )
            }
        }

        installDependencySourcesToRepo.configure {
            dependsOn(installTask)
        }
    }
}
