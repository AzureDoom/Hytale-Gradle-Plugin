package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class SyncAssetPackToRunTask extends DefaultTask {
    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getAssetPackSourceDirectory()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getManifestFile()

    @OutputDirectory
    abstract DirectoryProperty getAssetPackRunDirectory()

    @TaskAction
    void syncAssets() {
        def srcDir = assetPackSourceDirectory.get().asFile
        def manifest = manifestFile.get().asFile
        def dstDir = assetPackRunDirectory.get().asFile

        project.delete(dstDir)
        dstDir.mkdirs()

        if (srcDir.exists()) {
            project.copy {
                from(srcDir)
                into(dstDir)

                exclude('manifest.json')
                exclude('**/manifest.json')
            }
        }

        project.copy {
            from(manifest)
            into(dstDir)
            rename { 'manifest.json' }
        }

        logger.lifecycle("Staged editable asset pack at ${dstDir.absolutePath}")
    }
}