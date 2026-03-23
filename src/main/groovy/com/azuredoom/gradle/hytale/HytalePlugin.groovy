package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

class HytalePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('java')

        def ext = project.extensions.create('hytaleTools', HytaleExtension)

        ext.javaVersion.convention(project.providers.gradleProperty('java_version').map { (it ?: '21') as Integer }.orElse(21))
        ext.hytaleVersion.convention(project.providers.gradleProperty('hytale_version'))
        ext.patchline.convention(project.providers.gradleProperty('hytale_patchline').orElse('pre-release'))
        ext.oauthBaseUrl.convention(project.providers.gradleProperty('hygradle.hytale.oauth.base').orElse('https://oauth.accounts.hytale.com'))
        ext.accountBaseUrl.convention(project.providers.gradleProperty('hygradle.hytale.accounts.base').orElse('https://account-data.hytale.com'))

        ext.manifestGroup.convention(project.providers.gradleProperty('manifest_group').orElse(project.group.toString()))
        ext.modId.convention(project.providers.gradleProperty('mod_id').orElse(project.name))
        ext.modDescription.convention(project.providers.gradleProperty('mod_description').orElse(''))
        ext.modUrl.convention(project.providers.gradleProperty('mod_url').orElse(''))
        ext.mainClass.convention(project.providers.gradleProperty('main_class').orElse(''))
        ext.modCredits.convention(project.providers.gradleProperty('mod_credits').orElse(''))
        ext.manifestDependencies.convention(project.providers.gradleProperty('manifest_dependencies').orElse(''))
        ext.manifestOptionalDependencies.convention(project.providers.gradleProperty('manifest_opt_dependencies').orElse(''))
        ext.curseforgeId.convention(project.providers.gradleProperty('curseforgeID').orElse(''))
        ext.disabledByDefault.convention(project.providers.gradleProperty('disabled_by_default').map { it.toBoolean() }.orElse(false))
        ext.includesPack.convention(project.providers.gradleProperty('includes_pack').map { it.toBoolean() }.orElse(false))
        ext.manifestFile.convention(project.layout.projectDirectory.file('src/main/resources/manifest.json'))
        ext.runDirectory.convention(project.layout.projectDirectory.dir('run'))

        def generatedSourcesMavenRepoDir = project.layout.buildDirectory.dir('generated-sources-m2')
        def generatedSourcesIvyRepoDir = project.layout.buildDirectory.dir('generated-sources-ivy')

        project.repositories.maven { MavenArtifactRepository repo ->
            repo.name = 'Generated Decompiled Sources'
            repo.url = project.uri(generatedSourcesMavenRepoDir)
        }

        project.repositories.ivy { IvyArtifactRepository repo ->
            repo.name = 'Generated Decompiled Sources Ivy'
            repo.url = project.uri(generatedSourcesIvyRepoDir)
            repo.patternLayout { layout ->
                layout.ivy('[organisation]/[module]/[revision]/ivy-[revision].xml')
                layout.artifact('[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]')
            }
            repo.metadataSources { sources ->
                sources.ivyDescriptor()
                sources.artifact()
            }
        }

        addHytaleRepositories(project)
        createHytaleConfigurations(project)

        def vineflowerTool = project.configurations.maybeCreate('vineflowerTool')
        vineflowerTool.canBeConsumed = false
        vineflowerTool.canBeResolved = true

        def vineDecompileClasspath = project.configurations.maybeCreate('vineDecompileClasspath')
        def vineServerJar = project.configurations.maybeCreate('vineServerJar')
        def vineDependencyJars = project.configurations.maybeCreate('vineDependencyJars')

        project.dependencies.add('vineflowerTool', 'org.vineflower:vineflower:1.11.2')

        def assetsCacheDir = new File(project.gradle.gradleUserHomeDir, 'caches/hytale-assets')
        def authCacheDir = new File(project.gradle.gradleUserHomeDir, 'caches/hytale-auth')

        def wrapperFileProvider = project.providers.provider {
            new File(assetsCacheDir, "${ext.patchline.get()}-${ext.hytaleVersion.get()}.jar")
        }
        def assetsZipFileProvider = project.providers.provider {
            new File(assetsCacheDir, "${ext.patchline.get()}-${ext.hytaleVersion.get()}-Assets.zip")
        }
        def tokenFileProvider = project.providers.provider {
            new File(authCacheDir, 'tokens.json')
        }

        project.tasks.register('updatePluginManifest', UpdatePluginManifestTask) {
            group = 'hytale'
            description = 'Updates src/main/resources/manifest.json from Gradle properties and plugin extension values.'

            manifestFile.set(ext.manifestFile)
            manifestGroup.set(ext.manifestGroup)
            modId.set(ext.modId)
            versionString.set(project.provider { project.version.toString() })
            modDescription.set(ext.modDescription)
            modCredits.set(ext.modCredits)
            modUrl.set(ext.modUrl)
            hytaleVersion.set(ext.hytaleVersion)
            manifestDependencies.set(ext.manifestDependencies)
            manifestOptionalDependencies.set(ext.manifestOptionalDependencies)
            disabledByDefault.set(ext.disabledByDefault)
            mainClass.set(ext.mainClass)
            includesPack.set(ext.includesPack)
            curseforgeId.set(ext.curseforgeId)
        }

        project.tasks.register('createModSkeleton', CreateModSkeletonTask) {
            group = 'hytale'
            description = 'Creates a starter Hytale mod source layout and manifest.json if they do not exist.'

            javaSourceDirectory.set(project.layout.projectDirectory.dir('src/main/java'))
            resourcesDirectory.set(project.layout.projectDirectory.dir('src/main/resources'))
            manifestFile.set(ext.manifestFile)

            manifestGroup.set(ext.manifestGroup)
            modId.set(ext.modId)
            mainClass.set(ext.mainClass)
            modDescription.set(ext.modDescription)
            modUrl.set(ext.modUrl)
            modCredits.set(ext.modCredits)
            hytaleVersion.set(ext.hytaleVersion)
            includesPack.set(ext.includesPack)
            disabledByDefault.set(ext.disabledByDefault)
        }

        project.tasks.register('validateManifest', ValidateManifestTask) {
            group = null
            description = 'Validates src/main/resources/manifest.json against required fields and plugin configuration.'

            manifestFile.set(ext.manifestFile)
            manifestGroup.set(ext.manifestGroup)
            modId.set(ext.modId)
            mainClass.set(ext.mainClass)
            hytaleVersion.set(ext.hytaleVersion)
            manifestDependencies.set(ext.manifestDependencies)
            manifestOptionalDependencies.set(ext.manifestOptionalDependencies)
            includesPack.set(ext.includesPack)
        }

        project.tasks.named('updatePluginManifest').configure {
            dependsOn('createModSkeleton')
            finalizedBy('validateManifest')
        }

        project.tasks.named('validateManifest').configure {
            dependsOn('updatePluginManifest')
        }

        def vineflowerJarFile = project.layout.file(project.provider { vineflowerTool.singleFile })
        def serverJarFile = project.layout.file(project.provider { vineServerJar.singleFile })

        project.tasks.register('decompileServerJar', DecompileServerJarTask) {
            group = null
            description = 'Decompile only com/hypixel/hytale from the Hytale Server jar into build/vineflower/hytale-server/'

            serverJar.set(serverJarFile)
            vineflowerJar.set(vineflowerJarFile)
            decompileClasspath.from(vineDecompileClasspath)
            outputDirectory.set(project.layout.buildDirectory.dir('vineflower/hytale-server'))
            tempDirectoryRoot.set(project.layout.buildDirectory.dir('tmp/vineflower-server'))
            javaVersion.set(ext.javaVersion)
        }

        project.tasks.register('downloadAssetsZip', DownloadAssetsZipTask) {
            group = 'hytale'
            description = 'Downloads the authenticated Hytale asset wrapper and extracts the inner Assets.zip'

            hytaleVersion.set(ext.hytaleVersion)
            patchline.set(ext.patchline)
            oauthBaseUrl.set(ext.oauthBaseUrl)
            accountBaseUrl.set(ext.accountBaseUrl)
            resolvedAssetsWrapper.set(project.layout.file(wrapperFileProvider))
            resolvedAssetsZip.set(project.layout.file(assetsZipFileProvider))
            tokenCacheFile.set(project.layout.file(tokenFileProvider))
        }

        project.pluginManager.withPlugin('java') {
            def decompiledServerDir = project.layout.buildDirectory.dir('vineflower/hytale-server')

            project.tasks.named('compileJava').configure {
                dependsOn('createModSkeleton')
            }

            project.tasks.named('processResources').configure {
                dependsOn('validateManifest')
            }

            project.tasks.register('prepareRunServer', PrepareRunServerTask) {
                group = null
                description = 'Prepares the run directory for launching the Hytale server'

                dependsOn('classes', 'processResources')
                runDirectory.set(ext.runDirectory)
            }

            project.tasks.register('runServer', RunServerTask) {
                group = 'hytale'
                description = 'Launches a local Hytale server with this project and its dependencies'

                dependsOn('prepareRunServer', 'downloadAssetsZip')

                def sourceSets = project.extensions.getByType(SourceSetContainer)
                mainClass.set('com.hypixel.hytale.Main')
                classpath = project.files(
                        sourceSets.named('main').get().output,
                        sourceSets.named('main').get().runtimeClasspath,
                        vineServerJar
                )
                modularity.inferModulePath.set(true)
                workingDir = ext.runDirectory.get().asFile
                standardInput = System.in
                jvmArgs('--enable-native-access=ALL-UNNAMED')
                assetsZip.set(project.layout.file(assetsZipFileProvider))
            }

            project.pluginManager.apply('idea')

            Provider<Set<ResolvedArtifactResult>> serverArtifactsProvider = project.providers.provider {
                resolveArtifacts(project, 'vineServerJar')
            }

            Provider<Set<ResolvedArtifactResult>> dependencyArtifactsProvider = project.providers.provider {
                resolveArtifacts(project, 'vineDependencyJars')
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
                outputs.dirs(generatedSourcesMavenRepoDir, generatedSourcesIvyRepoDir)

                doLast {
                    def artifacts = serverArtifactsProvider.get()
                    if (artifacts.isEmpty()) {
                        logger.lifecycle('Skipping server source installation: no artifact resolved from vineServerJar')
                        return
                    }
                    if (artifacts.size() != 1) {
                        throw new GradleException("Expected exactly one resolved artifact in vineServerJar, got ${artifacts.size()}")
                    }

                    def artifact = artifacts.iterator().next()
                    def componentId = artifact.id.componentIdentifier

                    if (!(componentId instanceof ModuleComponentIdentifier)) {
                        logger.lifecycle("Skipping server source installation: unsupported component identifier ${componentId.class.name}")
                        return
                    }

                    def binaryJarFile = artifact.file
                    def sourcesJarFile = serverSourcesJar.get().archiveFile.get().asFile

                    installModuleIntoMavenRepo(
                            project,
                            generatedSourcesMavenRepoDir.get().asFile,
                            componentId.group,
                            componentId.module,
                            componentId.version,
                            binaryJarFile,
                            sourcesJarFile
                    )

                    installModuleIntoIvyRepo(
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

            project.afterEvaluate {
                def dependencyArtifacts = dependencyArtifactsProvider.get()
                if (!dependencyArtifacts || dependencyArtifacts.isEmpty()) {
                    return
                }

                dependencyArtifacts.each { ResolvedArtifactResult artifact ->
                    def componentId = artifact.id.componentIdentifier
                    def variantId = artifact.variant?.displayName ?: 'default'

                    if (!(componentId instanceof ModuleComponentIdentifier)) {
                        logger.lifecycle("Skipping dependency artifact ${artifact.file.name}: unsupported component identifier ${componentId.class.name}")
                        return
                    }

                    def artifactGroup = componentId.group
                    def artifactModule = componentId.module
                    def artifactVersion = componentId.version
                    def safeName = "${artifactGroup}__${artifactModule}__${artifactVersion}".replaceAll('[^A-Za-z0-9_.-]', '_')

                    def perArtifactDir = project.layout.buildDirectory.dir("vineflower/dependencies/${safeName}")

                    def decompileTask = project.tasks.register("decompile_${safeName}", DecompileDependencyJarTask) {
                        group = null
                        description = "Internal: Decompile ${artifactGroup}:${artifactModule}:${artifactVersion} (${variantId})"

                        inputJar.set(project.layout.file(project.provider { artifact.file }))
                        vineflowerJar.set(vineflowerJarFile)
                        decompileClasspath.from(vineDecompileClasspath)
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
                        outputs.dirs(generatedSourcesMavenRepoDir, generatedSourcesIvyRepoDir)

                        doLast {
                            def binaryJarFile = artifact.file
                            def sourcesJarFile = sourcesJarTask.get().archiveFile.get().asFile

                            installModuleIntoMavenRepo(
                                    project,
                                    generatedSourcesMavenRepoDir.get().asFile,
                                    artifactGroup,
                                    artifactModule,
                                    artifactVersion,
                                    binaryJarFile,
                                    sourcesJarFile
                            )

                            installModuleIntoIvyRepo(
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

            project.tasks.register('prepareDecompiledSourcesForIde') {
                group = 'hytale'
                description = 'Builds and installs generated decompiled sources for IDE source attachment.'

                dependsOn(installServerSourcesToRepo)
                dependsOn(installDependencySourcesToRepo)
            }

            project.tasks.matching { it.name == 'idea' }.configureEach {
                dependsOn('prepareDecompiledSourcesForIde')
            }

            project.tasks.named('assemble').configure {
                dependsOn('prepareDecompiledSourcesForIde')
            }
        }
    }

    private static Set<ResolvedArtifactResult> resolveArtifacts(Project project, String configurationName) {
        def configuration = project.configurations.getByName(configurationName)
        def artifactView = configuration.incoming.artifactView { view ->
            view.lenient(true)
        }
        return artifactView.artifacts.artifacts.findAll { it instanceof ResolvedArtifactResult } as Set<ResolvedArtifactResult>
    }

    private static void installModuleIntoMavenRepo(
            Project project,
            File repoRoot,
            String group,
            String module,
            String version,
            File binaryJarFile,
            File sourcesJarFile
    ) {
        def groupPath = group.replace('.', '/')
        def moduleDir = new File(repoRoot, "${groupPath}/${module}/${version}")
        moduleDir.mkdirs()

        project.copy {
            from binaryJarFile
            into moduleDir
            rename { "${module}-${version}.jar" }
        }

        project.copy {
            from sourcesJarFile
            into moduleDir
            rename { "${module}-${version}-sources.jar" }
        }

        def pomFile = new File(moduleDir, "${module}-${version}.pom")
        pomFile.text = """<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${group}</groupId>
  <artifactId>${module}</artifactId>
  <version>${version}</version>
  <packaging>jar</packaging>
</project>
"""
    }

    private static void installModuleIntoIvyRepo(
            Project project,
            File repoRoot,
            String group,
            String module,
            String version,
            File binaryJarFile,
            File sourcesJarFile
    ) {
        def groupPath = group.replace('.', '/')
        def moduleDir = new File(repoRoot, "${groupPath}/${module}/${version}")
        moduleDir.mkdirs()

        project.copy {
            from binaryJarFile
            into moduleDir
            rename { "${module}-${version}.jar" }
        }

        project.copy {
            from sourcesJarFile
            into moduleDir
            rename { "${module}-${version}-sources.jar" }
        }

        def ivyFile = new File(moduleDir, "ivy-${version}.xml")
        ivyFile.text = """<ivy-module version="2.0" xmlns:m="http://ant.apache.org/ivy/maven">
  <info organisation="${group}" module="${module}" revision="${version}"/>
  <configurations>
    <conf name="default"/>
    <conf name="sources"/>
  </configurations>
  <publications>
    <artifact name="${module}" type="jar" ext="jar" conf="default"/>
    <artifact name="${module}" type="jar" ext="jar" conf="sources" m:classifier="sources"/>
  </publications>
</ivy-module>
"""
    }

    private static void addHytaleRepositories(Project project) {
        project.repositories.mavenCentral()

        addMavenRepo(project, 'Hytale Server Release', 'https://maven.hytale.com/release')
        addMavenRepo(project, 'Hytale Server Pre-Release', 'https://maven.hytale.com/pre-release')
        addMavenRepo(project, 'hMReleases', 'https://maven.hytale-mods.dev/releases')
        addMavenRepo(project, 'PlaceholderAPI', 'https://repo.helpch.at/releases/')
        addMavenRepo(project, 'CurseMaven', 'https://cursemaven.com')
        addMavenRepo(project, 'AzureDoom Maven', 'https://maven.azuredoom.com/mods')

        project.repositories.exclusiveContent { spec ->
            spec.forRepository {
                project.repositories.ivy { IvyArtifactRepository repo ->
                    repo.name = 'Modtale'
                    repo.url = project.uri('https://api.modtale.net/api/v1')
                    repo.patternLayout { layout ->
                        layout.artifact('projects/[module]/versions/[revision]/download')
                    }
                    repo.metadataSources { sources ->
                        sources.artifact()
                    }
                }
            }
            spec.filter { filter ->
                filter.includeGroup('modtale')
            }
        }
    }

    private static void addMavenRepo(Project project, String repoName, String repoUrl) {
        def existing = project.repositories.find { repo ->
            repo.hasProperty('url') && repo.url?.toString() == repoUrl
        }
        if (existing != null) {
            return
        }

        project.repositories.maven { MavenArtifactRepository repo ->
            if (repoName) {
                repo.name = repoName
            }
            repo.url = project.uri(repoUrl)
        }
    }

    private static void createHytaleConfigurations(Project project) {
        def implementation = project.configurations.findByName('implementation')
        def compileOnly = project.configurations.findByName('compileOnly')

        def vineImplementation = project.configurations.maybeCreate('vineImplementation')
        vineImplementation.canBeResolved = false
        vineImplementation.canBeConsumed = false

        def vineCompileOnly = project.configurations.maybeCreate('vineCompileOnly')
        vineCompileOnly.canBeResolved = false
        vineCompileOnly.canBeConsumed = false

        def vineDecompileTargets = project.configurations.maybeCreate('vineDecompileTargets')
        vineDecompileTargets.canBeResolved = false
        vineDecompileTargets.canBeConsumed = false

        def vineDecompileClasspath = project.configurations.maybeCreate('vineDecompileClasspath')
        vineDecompileClasspath.canBeResolved = true
        vineDecompileClasspath.canBeConsumed = false
        vineDecompileClasspath.extendsFrom(vineImplementation, vineCompileOnly)

        def vineServerJar = project.configurations.maybeCreate('vineServerJar')
        vineServerJar.canBeResolved = true
        vineServerJar.canBeConsumed = false
        vineServerJar.transitive = false

        def vineDependencyJars = project.configurations.maybeCreate('vineDependencyJars')
        vineDependencyJars.canBeResolved = true
        vineDependencyJars.canBeConsumed = false
        vineDependencyJars.transitive = false
        vineDependencyJars.extendsFrom(vineDecompileTargets)

        if (implementation != null) {
            implementation.extendsFrom(vineImplementation)
        }

        if (compileOnly != null) {
            compileOnly.extendsFrom(vineCompileOnly)
        }
    }
}