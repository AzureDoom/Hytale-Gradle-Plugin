package com.azuredoom.gradle.hytale

class ManifestUtils {
	static Map<String, String> parseDepMap(String raw) {
		def out = [:]
		if (!raw) {
			return out as Map<String, String>
		}
		raw.split(/\s*,\s*/).findAll { it?.trim() }.each { entry ->
			def parts = entry.split(/\s*=\s*/, 2)
			if (parts.length == 2) {
				out[parts[0].trim()] = parts[1].trim()
			}
		}
		out as Map<String, String>
	}

	static List<Map<String, String>> parseAuthors(String value) {
		if (value == null || value.trim().isEmpty()) {
			return []
		}

		return value.split(',')
				.collect { it.trim() }
				.findAll { !it.isEmpty() }
				.collect { author ->
					def parts = author.split('\\|', -1)*.trim()

					def entry = [
						Name: parts[0]
					]

					if (parts.size() > 1 && parts[1]) {
						entry.Email = parts[1]
					}

					if (parts.size() > 2 && parts[2]) {
						entry.Url = parts[2]
					}

					return entry
				}
	}
}
