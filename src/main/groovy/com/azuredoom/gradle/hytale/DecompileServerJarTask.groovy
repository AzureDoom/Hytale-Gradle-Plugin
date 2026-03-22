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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import javax.inject.Inject

abstract class DecompileServerJarTask extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getServerJar()

    @Classpath
    abstract ConfigurableFileCollection getDecompileClasspath()

    @InputFile
    abstract RegularFileProperty getVineflowerJar()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @OutputDirectory
    abstract DirectoryProperty getTempDirectoryRoot()

    @Input
    abstract Property<Integer> getJavaVersion()

    @Inject
    abstract JavaToolchainService getJavaToolchainService()

    @TaskAction
    void decompile() {
        def outDir = outputDirectory.get().asFile
        def tempDir = tempDirectoryRoot.get().asFile
        project.delete(outDir, tempDir)
        outDir.mkdirs()
        tempDir.mkdirs()

        def launcher = javaToolchainService.launcherFor { spec ->
            spec.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(javaVersion.get()))
        }

        def javaExe = launcher.get().executablePath.asFile.absolutePath

        def server = serverJar.get().asFile
        if (!server.exists()) {
            throw new GradleException('Could not resolve Hytale Server jar from vineServerJar')
        }

        def filteredJar = new File(tempDir, 'Server-com-hytale-only.jar')
        project.ant.jar(destfile: filteredJar.absolutePath) {
            zipfileset(src: server.absolutePath) {
                include(name: 'com/hypixel/hytale/**')
            }
        }

        if (!filteredJar.exists() || filteredJar.length() == 0) {
            throw new GradleException("Filtered jar is empty. Nothing matched com/hypixel/hytale/** inside ${server.name}")
        }

        def externals = decompileClasspath.files
            .findAll { it.name.endsWith('.jar') && it != server }
            .collect { "-e=${it.absolutePath}" }

        def cmd = [
            javaExe,
            '-jar', vineflowerJar.get().asFile.absolutePath,
            *externals,
            filteredJar.absolutePath,
            outDir.absolutePath
        ].collect { it.toString() }

        logger.lifecycle("Running: ${cmd.join(' ')}")

        def proc = new ProcessBuilder(cmd)
            .directory(project.projectDir)
            .redirectErrorStream(true)
            .start()

        proc.inputStream.withReader { reader ->
            reader.eachLine { line -> logger.lifecycle(line) }
        }

        def exit = proc.waitFor()
        if (exit != 0) {
            throw new GradleException("Vineflower failed (exit ${exit})")
        }

        logger.lifecycle("Decompiled com/hypixel/hytale only from ${server.name} -> ${outDir}")
    }
}
