package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import org.gradle.api.logging.Logger

class BasicUtils {

    static String formatBytes(long bytes) {
        if (bytes < 1024L) return "${bytes} B"
        def units = ['KiB', 'MiB', 'GiB', 'TiB']
        double value = bytes
        int unitIndex = -1
        while (value >= 1024D && unitIndex < units.size() - 1) {
            value /= 1024D
            unitIndex++
        }
        String.format(Locale.ROOT, '%.1f %s', value, units[unitIndex])
    }

    static boolean createSymlinkOrWindowsJunction(Path sourceDir, Path targetDir, String failureContext, Logger logger) {
        Path source = sourceDir.toAbsolutePath().normalize()
        Path target = targetDir.toAbsolutePath().normalize()

        Path targetParent = target.parent
        if (targetParent == null) {
            throw new GradleException("Target path must have a parent: ${target}")
        }

        try {
            Files.createDirectories(targetParent)

            Path relativeSource = relativizeOrAbsolute(targetParent, source)
            Files.createSymbolicLink(target, relativeSource)

            logger.lifecycle("Created symlink ${target} -> ${relativeSource}")
            return true
        } catch (FileAlreadyExistsException ignored) {
            logger.warn("Symlink creation failed because target already exists: ${target}")
        } catch (UnsupportedOperationException | SecurityException | IOException ex) {
            logger.warn("Symlink creation failed, attempting Windows junction fallback: ${ex.message}")
        }

        if (!isWindows()) {
            logger.warn("Cannot create Windows junction on non-Windows OS for ${failureContext}")
            return false
        }

        try {
            Process process = new ProcessBuilder(
                    "cmd", "/c", "mklink", "/J",
                    target.toString(),
                    source.toString()
            ).redirectErrorStream(true).start()

            String output = process.inputStream.text
            int code = process.waitFor()

            if (code == 0 && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                logger.lifecycle("Created junction ${target} -> ${source}")
                return true
            }

            logger.warn(
                    "Junction creation failed for ${failureContext}.\n" +
                            "Target: ${target}\n" +
                            "Source: ${source}\n" +
                            "Exit code: ${code}\n" +
                            "Output:\n${output}"
            )
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt()
            }

            logger.warn(
                    "Junction creation failed for ${failureContext}.\n" +
                            "Target: ${target}\n" +
                            "Source: ${source}\n" +
                            "Reason: ${ex.message}"
            )
        }

        return false
    }

    private static Path relativizeOrAbsolute(Path parent, Path source) {
        try {
            return parent.relativize(source)
        } catch (IllegalArgumentException ignored) {
            return source
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
    }
}
