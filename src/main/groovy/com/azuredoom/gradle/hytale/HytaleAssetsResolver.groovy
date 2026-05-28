package com.azuredoom.gradle.hytale

import org.gradle.api.Project

final class HytaleAssetsResolver {
	private HytaleAssetsResolver() {}

	static File resolveDirectOverrideAssetsZip(Project project, HytaleExtension ext) {
		def override = ext.hytaleHomeOverride.orNull?.trim()
		if (!override) {
			return null
		}

		def found = findAssetsZip(new File(override), ext.patchline.get())
		if (found != null) {
			return found
		}

		project.logger.lifecycle("hytaleHomeOverride is set but no Assets.zip was found at or under: ${override}")
		return null
	}

	static File findAssetsZip(File homeOrAssetsZip, String patchline) {
		def branch = (patchline ?: '').trim()

		def candidates = [
			homeOrAssetsZip,
			new File(homeOrAssetsZip, 'Assets.zip'),
			new File(homeOrAssetsZip, "install/${branch}/package/game/latest/Assets.zip"),
			new File(homeOrAssetsZip, "install/${branch}/game/latest/Assets.zip"),
			new File(homeOrAssetsZip, "install/${branch}/latest/Assets.zip"),
			new File(homeOrAssetsZip, "${branch}/package/game/latest/Assets.zip"),
			new File(homeOrAssetsZip, "${branch}/game/latest/Assets.zip")
		]

		return candidates.find { it.exists() && it.isFile() && it.length() > 0 }
	}
}