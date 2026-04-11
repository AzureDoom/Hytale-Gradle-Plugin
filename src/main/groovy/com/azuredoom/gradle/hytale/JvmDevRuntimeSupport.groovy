package com.azuredoom.gradle.hytale

final class JvmDevRuntimeSupport {
	private JvmDevRuntimeSupport() {}

	static JavaResolution resolveJava(String configuredJbrHome) {
		if (configuredJbrHome != null && !configuredJbrHome.trim().isEmpty()) {
			File javaHome = new File(configuredJbrHome)
			File exe = javaExecutableFromHome(javaHome)
			if (exe.exists()) {
				return new JavaResolution(
						javaExecutable: exe,
						javaHome: javaHome,
						source: 'configured:jbrHome'
						)
			}
			System.err.println("Configured jbrHome does not contain a java executable: ${exe}")
		}

		Map<String, String> envHomes = [
			'JBR_HOME'          : System.getenv('JBR_HOME'),
			'JETBRAINS_RUNTIME' : System.getenv('JETBRAINS_RUNTIME'),
			'IDEA_JDK'          : System.getenv('IDEA_JDK'),
			'STUDIO_JDK'        : System.getenv('STUDIO_JDK')
		]

		for (Map.Entry<String, String> entry : envHomes.entrySet()) {
			String home = entry.value
			if (home != null && !home.trim().isEmpty()) {
				File javaHome = new File(home)
				File exe = javaExecutableFromHome(javaHome)
				if (exe.exists()) {
					return new JavaResolution(
							javaExecutable: exe,
							javaHome: javaHome,
							source: "env:${entry.key}"
							)
				}
			}
		}

		JavaResolution toolbox = resolveFromJetBrainsToolbox()
		if (toolbox != null) {
			return toolbox
		}

		File fallbackHome = new File(System.getProperty('java.home'))
		return new JavaResolution(
				javaExecutable: javaExecutableFromHome(fallbackHome),
				javaHome: fallbackHome,
				source: 'java.home'
				)
	}

	static JavaResolution resolveFromJetBrainsToolbox() {
		return resolveFromJetBrainsToolbox(jetBrainsToolboxRoots())
	}

	static JavaResolution resolveFromJetBrainsToolbox(List<File> roots) {
		List<File> candidates = []

		for (File root : roots) {
			if (!root.exists() || !root.isDirectory()) {
				continue
			}
			candidates.addAll(findJavaHomesUnder(root, 6))
		}

		if (candidates.isEmpty()) {
			return null
		}

		List<File> jbrCandidates = candidates.findAll { File home ->
			File exe = javaExecutableFromHome(home)
			exe.exists() && looksLikeJetBrainsRuntimeHome(home)
		}

		List<File> preferred = jbrCandidates.isEmpty() ? candidates : jbrCandidates
		File best = preferred.sort { a, b -> Long.compare(b.lastModified(), a.lastModified()) }.first()

		return new JavaResolution(
				javaExecutable: javaExecutableFromHome(best),
				javaHome: best,
				source: 'toolbox'
				)
	}

	static List<File> jetBrainsToolboxRoots() {
		String os = System.getProperty('os.name').toLowerCase()

		if (os.contains('win')) {
			String localAppData = System.getenv('LOCALAPPDATA')
			if (localAppData != null && !localAppData.trim().isEmpty()) {
				return [
					new File(localAppData, 'JetBrains/Toolbox/apps')
				]
			}
			return []
		}

		if (os.contains('mac')) {
			return [
				new File(System.getProperty('user.home'), 'Library/Application Support/JetBrains/Toolbox/apps')
			]
		}

		return [
			new File(System.getProperty('user.home'), '.local/share/JetBrains/Toolbox/apps')
		]
	}

	static List<File> findJavaHomesUnder(File root, int maxDepth) {
		List<File> matches = []
		visitDirectories(root, 0, maxDepth, matches)
		return matches.unique { it.absolutePath }
	}

	private static void visitDirectories(File dir, int depth, int maxDepth, List<File> matches) {
		if (dir == null || !dir.isDirectory() || depth > maxDepth) {
			return
		}

		File exe = javaExecutableFromHome(dir)
		if (exe.exists()) {
			matches.add(dir)
		}

		File[] children = dir.listFiles()
		if (children == null) {
			return
		}

		for (File child : children) {
			if (child.isDirectory()) {
				visitDirectories(child, depth + 1, maxDepth, matches)
			}
		}
	}

	static boolean looksLikeJetBrainsRuntimeHome(File javaHome) {
		String path = javaHome.absolutePath.toLowerCase()
		return path.contains('jetbrains') || path.contains('jbr')
	}

	static File javaExecutableFromHome(File javaHome) {
		boolean windows = System.getProperty('os.name').toLowerCase().contains('win')
		new File(javaHome, "bin/java" + (windows ? ".exe" : ""))
	}

	static boolean isJetBrainsRuntime(File javaExe) {
		ProbeResult probe = run(javaExe, [
			'-XshowSettings:properties',
			'-version'
		])
		String out = (probe.stdout + "\n" + probe.stderr).toLowerCase()

		return out.contains('jetbrains') ||
				out.contains('jbr') ||
				out.contains('java.vendor = jetbrains') ||
				out.contains('java.vm.vendor = jetbrains')
	}

	static boolean supportsEnhancedRedefinition(File javaExe) {
		ProbeResult probe = run(javaExe, [
			'-XX:+AllowEnhancedClassRedefinition',
			'-version'
		])
		return probe.exitCode == 0
	}

	static boolean supportsHotswapAgentMode(File javaExe) {
		ProbeResult probe = run(javaExe, [
			'-XX:HotswapAgent=fatjar',
			'-version'
		])
		return probe.exitCode == 0
	}

	static File resolveBundledHotswapAgent(File javaExe) {
		File javaHome = javaExe.parentFile?.parentFile
		if (javaHome == null) return null

		File candidate = new File(javaHome, 'lib/hotswap/hotswap-agent.jar')
		return candidate.exists() ? candidate : null
	}

	static ProbeResult run(File javaExe, List<String> args) {
		List<String> cmd = [javaExe.absolutePath]
		cmd.addAll(args)

		Process process = new ProcessBuilder(cmd)
				.redirectErrorStream(true)
				.start()

		String combined = process.inputStream.getText('UTF-8')
		int exit = process.waitFor()

		new ProbeResult(exitCode: exit, stdout: combined, stderr: '')
	}

	static class JavaResolution {
		File javaExecutable
		File javaHome
		String source
	}

	static class ProbeResult {
		int exitCode
		String stdout
		String stderr
	}
}