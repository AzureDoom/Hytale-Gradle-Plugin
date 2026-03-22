package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

abstract class RunServerTask extends JavaExec {
    @InputFile
    abstract RegularFileProperty getAssetsZip()

    @TaskAction
    @Override
    void exec() {
        def resolvedAssetsZip = assetsZip.get().asFile

        logger.lifecycle("Using extracted assets zip: ${resolvedAssetsZip.absolutePath}")
        logger.lifecycle("Assets exists: ${resolvedAssetsZip.exists()}, size: ${resolvedAssetsZip.exists() ? resolvedAssetsZip.length() : 0}")

        if (!resolvedAssetsZip.exists() || resolvedAssetsZip.length() == 0) {
            throw new GradleException("Assets zip not found or empty: ${resolvedAssetsZip}")
        }

        setArgs([
            "--assets=${resolvedAssetsZip.absolutePath}",
            '--allow-op',
            '--disable-sentry'
        ])

        super.exec()
    }
}
