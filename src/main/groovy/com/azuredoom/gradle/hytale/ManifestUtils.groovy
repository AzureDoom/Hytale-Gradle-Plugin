package com.azuredoom.gradle.hytale

class ManifestUtils {
    static Map<String, String> parseDepMap(String raw) {
        def out = [:]
        if (!raw) {
            return out
        }
        raw.split(/\s*,\s*/).findAll { it?.trim() }.each { entry ->
            def parts = entry.split(/\s*=\s*/, 2)
            if (parts.length == 2) {
                out[parts[0].trim()] = parts[1].trim()
            }
        }
        out
    }

    static List<Map<String, String>> parseAuthors(String raw) {
        if (!raw) {
            return null
        }
        raw.split(/\s*,\s*/)
            .findAll { it?.trim() }
            .collect { [Name: it.trim()] }
    }
}
