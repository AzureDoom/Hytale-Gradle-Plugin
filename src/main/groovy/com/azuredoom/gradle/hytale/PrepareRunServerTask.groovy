package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path

abstract class PrepareRunServerTask extends DefaultTask {
    @OutputDirectory
    abstract DirectoryProperty getRunDirectory()

    @InputDirectory
    abstract DirectoryProperty getAssetPackSourceDirectory()

    @OutputDirectory
    abstract DirectoryProperty getAssetPackRunDirectory()

    @TaskAction
    void prepare() {
        File runDir = runDirectory.get().asFile
        File srcDir = assetPackSourceDirectory.get().asFile
        File dstDir = assetPackRunDirectory.get().asFile

        if (!srcDir.exists()) {
            throw new GradleException("Asset pack source directory does not exist: ${srcDir}")
        }

        runDir.mkdirs()
        dstDir.parentFile.mkdirs()

        Path source = srcDir.toPath().toAbsolutePath().normalize()
        Path target = dstDir.toPath().toAbsolutePath().normalize()

        if (Files.exists(target)) {
            boolean correctLink = false

            if (Files.isSymbolicLink(target)) {
                Path existing = Files.readSymbolicLink(target)
                Path resolved = target.parent.resolve(existing).normalize().toAbsolutePath()
                if (resolved == source) {
                    correctLink = true
                }
            }

            if (!correctLink) {
                logger.lifecycle("Replacing existing staged asset pack at ${target} with live link to ${source}")
                project.delete(dstDir)
            } else {
                logger.info("Run asset pack already linked correctly: ${target} -> ${source}")
                return
            }
        }

        try {
            Path relativeSource = target.parent.relativize(source)
            Files.createSymbolicLink(target, relativeSource)
            logger.lifecycle("Created symlink ${target} -> ${relativeSource}")
            return
        } catch (Exception ex) {
            logger.warn("Symlink creation failed, attempting Windows junction fallback: ${ex.message}")
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            def process = new ProcessBuilder(
                    "cmd", "/c", "mklink", "/J",
                    target.toString(),
                    source.toString()
            ).redirectErrorStream(true).start()

            String output = process.inputStream.text
            int code = process.waitFor()

            if (code == 0 && Files.exists(target)) {
                logger.lifecycle("Created junction ${target} -> ${source}")
                return
            }

            throw new GradleException(
                    "Failed to create junction for run asset pack.\n" +
                            "Target: ${target}\n" +
                            "Source: ${source}\n" +
                            "Output:\n${output}"
            )
        }

        throw new GradleException("Failed to create symlink for run asset pack: ${target} -> ${source}")
    }
}