package com.azuredoom.gradle.hytale

final class HytaleAssetsResolver {
	private HytaleAssetsResolver() {}

	static File resolveDirectOverrideAssetsZip(String override, String patchline) {
		def value = override?.trim()
		if (!value) {
			return null
		}

		return findAssetsZip(new File(value), patchline)
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