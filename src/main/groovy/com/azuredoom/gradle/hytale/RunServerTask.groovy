package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.JavaExec
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Launches a long-running external server process")
abstract class RunServerTask extends JavaExec {

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getAssetsZip()

	@Input
	abstract ListProperty<String> getServerArgs()

	@Input
	abstract ListProperty<String> getServerJvmArgs()

	@Input
	abstract Property<Boolean> getDebugEnabled()

	@Input
	abstract Property<Integer> getDebugPort()

	@Input
	abstract Property<Boolean> getDebugSuspend()

	@Input
	abstract Property<Boolean> getHotSwapEnabled()

	@Input
	abstract Property<Boolean> getRequireDcevm()

	@Input
	abstract Property<Boolean> getUseHotswapAgent()

	@Input
	abstract Property<String> getJbrHome()

	RunServerTask() {
		standardInput = System.in
	}

	protected List<String> buildResolvedJvmArgs(File javaExe) {
		List<String> resolved = []
		resolved.addAll(serverJvmArgs.getOrElse([]))

		if (hotSwapEnabled.getOrElse(false)) {
			boolean isJbr = JvmDevRuntimeSupport.isJetBrainsRuntime(javaExe)
			boolean hasEnhanced = JvmDevRuntimeSupport.supportsEnhancedRedefinition(javaExe)

			logger.lifecycle("Dev JVM: ${javaExe}")
			logger.lifecycle("JetBrains Runtime detected: ${isJbr}")
			logger.lifecycle("Enhanced class redefinition supported: ${hasEnhanced}")

			if (requireDcevm.getOrElse(false) && !hasEnhanced) {
				throw new GradleException(
				"Hot swap was requested with requireDcevm=true, but the selected JVM does not support enhanced class redefinition."
				)
			}

			if (hasEnhanced) {
				resolved.add('-XX:+AllowEnhancedClassRedefinition')
			} else {
				logger.warn("Falling back to normal debugger hot swap only (method-body changes only).")
			}

			if (useHotswapAgent.getOrElse(true)) {
				boolean supportsHaMode = JvmDevRuntimeSupport.supportsHotswapAgentMode(javaExe)
				File bundledAgent = JvmDevRuntimeSupport.resolveBundledHotswapAgent(javaExe)

				if (supportsHaMode && bundledAgent != null) {
					resolved.add('-XX:HotswapAgent=fatjar')
					logger.lifecycle("Using bundled HotswapAgent: ${bundledAgent}")
				} else {
					logger.lifecycle("HotswapAgent not enabled: expected lib/hotswap/hotswap-agent.jar in the selected JBR.")
				}
			}
		}

		return resolved
	}

	protected List<String> buildResolvedArgs(File resolvedAssetsZip) {
		List<String> resolvedArgs = [
			"--assets=${resolvedAssetsZip.absolutePath}"
		]
		resolvedArgs.addAll(serverArgs.getOrElse([]))
		return resolvedArgs
	}

	@Override
	void exec() {
		File resolvedAssetsZip = assetsZip.get().asFile

		logger.lifecycle("Using extracted assets zip: ${resolvedAssetsZip.absolutePath}")
		logger.lifecycle("Assets exists: ${resolvedAssetsZip.exists()}, size: ${resolvedAssetsZip.exists() ? resolvedAssetsZip.length() : 0}")

		if (!resolvedAssetsZip.exists() || resolvedAssetsZip.length() == 0) {
			throw new GradleException("Assets zip not found or empty: ${resolvedAssetsZip}")
		}

		File javaExe = javaLauncher.get().executablePath.asFile
		def metadata = javaLauncher.get().metadata

		logger.lifecycle("Dev JVM: ${javaExe}")
		logger.lifecycle("Dev JVM home: ${metadata.installationPath.asFile}")
		logger.lifecycle("Dev JVM version: ${metadata.languageVersion}")

		jvmArgs('--enable-native-access=ALL-UNNAMED')
		jvmArgs(buildResolvedJvmArgs(javaExe))
		args(buildResolvedArgs(resolvedAssetsZip))

		if (!debug && debugEnabled.getOrElse(false)) {
			debug = true
			debugOptions {
				port = debugPort.getOrElse(5005)
				server = true
				suspend = debugSuspend.getOrElse(false)
			}
		}

		super.exec()
	}
}