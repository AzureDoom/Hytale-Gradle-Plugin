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

		def localBranches = branch == 'prerelease'
				? ['prerelease', 'pre-release']
				: [branch]

		def candidates = [
			homeOrAssetsZip,
			new File(homeOrAssetsZip, 'Assets.zip')
		]

		localBranches.each { localBranch ->
			candidates.addAll([
				new File(homeOrAssetsZip, "install/${localBranch}/package/game/latest/Assets.zip"),
				new File(homeOrAssetsZip, "install/${localBranch}/game/latest/Assets.zip"),
				new File(homeOrAssetsZip, "install/${localBranch}/latest/Assets.zip"),
				new File(homeOrAssetsZip, "${localBranch}/package/game/latest/Assets.zip"),
				new File(homeOrAssetsZip, "${localBranch}/game/latest/Assets.zip")
			])
		}

		def result = candidates.find {
			it.exists() && it.isFile() && it.length() > 0
		}

		return result
	}

	static File findAssetsZip(String homeOrAssetsZip, String patchline) {
		if (homeOrAssetsZip == null || homeOrAssetsZip.trim().isEmpty()) {
			return null
		}

		return findAssetsZip(new File(homeOrAssetsZip.trim()), patchline)
	}
}