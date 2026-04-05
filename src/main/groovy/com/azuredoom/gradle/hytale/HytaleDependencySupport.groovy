package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

final class HytaleDependencySupport {
    private HytaleDependencySupport() {}

    static String dependencyNotation(def dependency) {
        def group = dependency.hasProperty('group') ? dependency.group : null
        def name = dependency.hasProperty('name') ? dependency.name : null
        def version = dependency.hasProperty('version') ? dependency.version : null

        if (group && name && version) {
            return "${group}:${name}:${version}"
        }
        if (group && name) {
            return "${group}:${name}"
        }
        dependency.toString()
    }

    static void validateDecompileDependency(ExternalModuleDependency dependency) {
        if (!dependency.group || !dependency.name || !dependency.version) {
            throw new GradleException("Dependencies used for decompilation must use full GAV coordinates. Found: ${dependency}")
        }
    }

    static File resolveDeclaredDependencyArtifact(Project project, ModuleDependency dependency) {
        def detached = project.configurations.detachedConfiguration(dependency.copy())
        detached.canBeConsumed = false
        detached.canBeResolved = true
        detached.transitive = false

        def artifacts = detached.incoming.artifactView { view ->
            view.lenient(false)
        }.artifacts.artifacts.findAll { it instanceof ResolvedArtifactResult } as List<ResolvedArtifactResult>

        if (artifacts.isEmpty()) {
            throw new GradleException("Could not resolve artifact for declared dependency ${dependency.group}:${dependency.name}:${dependency.version}")
        }

        if (artifacts.size() > 1) {
            throw new GradleException("Expected exactly one artifact for declared dependency ${dependency.group}:${dependency.name}:${dependency.version}, got ${artifacts.size()}")
        }

        artifacts.first().file
    }

    static Set<ResolvedArtifactResult> resolveArtifacts(Project project, String configurationName) {
        def configuration = project.configurations.getByName(configurationName)
        def artifactView = configuration.incoming.artifactView { view ->
            view.lenient(true)
        }
        artifactView.artifacts.artifacts.findAll { it instanceof ResolvedArtifactResult } as Set<ResolvedArtifactResult>
    }

    static Map<String, File> mavenRepoFiles(File repoRoot, String group, String module, String version) {
        def groupPath = group.replace('.', '/')
        def moduleDir = new File(repoRoot, "${groupPath}/${module}/${version}")

        [
                binaryJar : new File(moduleDir, "${module}-${version}.jar"),
                sourcesJar: new File(moduleDir, "${module}-${version}-sources.jar"),
                descriptor: new File(moduleDir, "${module}-${version}.pom")
        ]
    }

    static Map<String, File> ivyRepoFiles(File repoRoot, String group, String module, String version) {
        def groupPath = group.replace('.', '/')
        def moduleDir = new File(repoRoot, "${groupPath}/${module}/${version}")

        [
                binaryJar : new File(moduleDir, "${module}-${version}.jar"),
                sourcesJar: new File(moduleDir, "${module}-${version}-sources.jar"),
                descriptor: new File(moduleDir, "ivy-${version}.xml")
        ]
    }

    static void installModuleIntoMavenRepo(Project project, File repoRoot, String group, String module, String version, File binaryJarFile, File sourcesJarFile) {
        def files = mavenRepoFiles(repoRoot, group, module, version)
        def moduleDir = files.binaryJar.parentFile
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

        files.descriptor.text = """<project xmlns="http://maven.apache.org/POM/4.0.0"
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

    static void installModuleIntoIvyRepo(Project project, File repoRoot, String group, String module, String version, File binaryJarFile, File sourcesJarFile) {
        def files = ivyRepoFiles(repoRoot, group, module, version)
        def moduleDir = files.binaryJar.parentFile
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

        files.descriptor.text = """<ivy-module version="2.0" xmlns:m="http://ant.apache.org/ivy/maven">
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
}
