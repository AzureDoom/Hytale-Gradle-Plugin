package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaToolchainService

import javax.inject.Inject

@CacheableTask
abstract class DecompileDependencyJarTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getInputJar()

    @Classpath
    abstract ConfigurableFileCollection getDecompileClasspath()

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
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

        def input = inputJar.get().asFile
        if (!input.exists()) {
            throw new GradleException("Could not resolve dependency jar: ${input}")
        }

        def externals = decompileClasspath.files
                .findAll { it.name.endsWith('.jar') && it != input }
                .collect { "-e=${it.absolutePath}" }

        def cmd = [
                javaExe,
                '-jar', vineflowerJar.get().asFile.absolutePath,
                *externals,
                input.absolutePath,
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
            throw new GradleException("Vineflower failed for ${input.name} (exit ${exit})")
        }

        logger.lifecycle("Decompiled ${input.name} -> ${outDir}")
    }
}