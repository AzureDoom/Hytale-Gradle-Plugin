package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PrepareRunServerTask extends DefaultTask {
    @OutputDirectory
    abstract DirectoryProperty getRunDirectory()

    @TaskAction
    void prepare() {
        runDirectory.get().asFile.mkdirs()
    }
}
