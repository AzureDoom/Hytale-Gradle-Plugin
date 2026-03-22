package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import javax.inject.Inject

abstract class DecompileVineDependenciesTask extends DefaultTask {
    @InputFiles
    abstract ConfigurableFileCollection getDependencyJars()

    @Classpath
    abstract ConfigurableFileCollection getDecompileClasspath()

    @InputFile
    abstract RegularFileProperty getVineflowerJar()

    @OutputDirectory
    abstract DirectoryProperty getOutputRootDirectory()

    @Input
    abstract Property<Integer> getJavaVersion()

    @Inject
    abstract JavaToolchainService getJavaToolchainService()

    @TaskAction
    void decompile() {
        def outRootDir = outputRootDirectory.get().asFile
        project.delete(outRootDir)
        outRootDir.mkdirs()

        def jars = dependencyJars.files.findAll { it.name.endsWith('.jar') }
        if (jars.isEmpty()) {
            throw new GradleException('No jars resolved from vineDependencyJars')
        }

        def javaExe = javaToolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(javaVersion.get()))
        }.get().executablePath.asFile.absolutePath

        jars.each { jarFile ->
            def safeName = jarFile.name
                .replaceAll(/\.jar$/, '')
                .replaceAll(/[^A-Za-z0-9._-]/, '_')

            def outDir = new File(outRootDir, safeName)
            outDir.mkdirs()

            def externals = decompileClasspath.files
                .findAll { it.name.endsWith('.jar') && it != jarFile }
                .collect { "-e=${it.absolutePath}" }

            def cmd = [
                javaExe,
                '-jar', vineflowerJar.get().asFile.absolutePath,
                *externals,
                jarFile.absolutePath,
                outDir.absolutePath
            ].collect { it.toString() }

            logger.lifecycle("Running: ${cmd.join(' ')}")

            def proc = new ProcessBuilder(cmd)
                .directory(project.projectDir)
                .redirectErrorStream(true)
                .start()

            proc.inputStream.withReader { reader ->
                reader.eachLine { line -> logger.lifecycle("[${jarFile.name}] ${line}") }
            }

            def exit = proc.waitFor()
            if (exit != 0) {
                throw new GradleException("Vineflower failed for ${jarFile.name} (exit ${exit})")
            }

            logger.lifecycle("Decompiled ${jarFile.name} -> ${outDir}")
        }
    }
}
