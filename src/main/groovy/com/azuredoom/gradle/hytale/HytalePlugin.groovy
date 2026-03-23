package com.azuredoom.gradle.hytale

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.SourceSetContainer

class HytalePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
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
            group = 'hytale'
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
            finalizedBy('validateManifest')
        }


        def vineflowerJarFile = project.layout.file(project.provider { vineflowerTool.singleFile })
        def serverJarFile = project.layout.file(project.provider { vineServerJar.singleFile })

        project.tasks.register('decompileServerJar', DecompileServerJarTask) {
            serverJar.set(serverJarFile)
            vineflowerJar.set(vineflowerJarFile)

            group = 'hytale'
            description = 'Decompile only com/hypixel/hytale from the Hytale Server jar into build/vineflower/hytale-server/'

            serverJar.set(project.layout.file(project.provider { vineServerJar.singleFile }))
            decompileClasspath.from(vineDecompileClasspath)
            vineflowerJar.set(project.layout.file(project.provider { vineflowerTool.singleFile }))
            outputDirectory.set(project.layout.buildDirectory.dir('vineflower/hytale-server'))
            tempDirectoryRoot.set(project.layout.buildDirectory.dir('tmp/vineflower-server'))
            javaVersion.set(ext.javaVersion)
        }

        project.tasks.register('decompileVineDependencies', DecompileVineDependenciesTask) {
            vineflowerJar.set(vineflowerJarFile)
            group = 'hytale'
            description = 'Decompile selected dependency jars from vineDependencyJars into build/vineflower/dependencies/'

            dependencyJars.from(vineDependencyJars)
            decompileClasspath.from(vineDecompileClasspath)
            vineflowerJar.set(project.layout.file(project.provider { vineflowerTool.singleFile }))
            outputRootDirectory.set(project.layout.buildDirectory.dir('vineflower/dependencies'))
            javaVersion.set(ext.javaVersion)
        }

        project.tasks.register('prepareRunServer', PrepareRunServerTask) {
            group = 'hytale'
            description = 'Prepares the run directory for launching the Hytale server'

            dependsOn('classes', 'processResources')
            runDirectory.set(ext.runDirectory)
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
            project.tasks.named('processResources').configure {
                dependsOn('createModSkeleton', 'updatePluginManifest', 'validateManifest')
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
        }
    }

    private static void addHytaleRepositories(Project project) {
        project.repositories.mavenCentral()

        addMavenRepo(project, 'Hytale Server Release', 'https://maven.hytale.com/release')
        addMavenRepo(project, 'Hytale Server Pre-Release', 'https://maven.hytale.com/pre-release')
        addMavenRepo(project, 'hMReleases', 'https://maven.hytale-mods.dev/releases')
        addMavenRepo(project, "PlaceholderAPI", 'https://repo.helpch.at/releases/')
        addMavenRepo(project, "CurseMaven", 'https://cursemaven.com')
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
