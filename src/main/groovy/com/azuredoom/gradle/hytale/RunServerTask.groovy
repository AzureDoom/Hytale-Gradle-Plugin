package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = "Launches a long-running external server process")
abstract class RunServerTask extends DefaultTask {

	@Inject
	protected abstract ExecOperations getExecOperations()

	@Inject
	protected abstract ObjectFactory getObjectFactory()

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getAssetsZip()

	@Classpath
	abstract ConfigurableFileCollection getRuntimeClasspath()

	@Input
	abstract Property<String> getMainClassName()

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

	@Internal
	final Property<File> workingDirectory = getObjectFactory().property(File)

	protected List<String> buildResolvedJvmArgs(File javaExe) {
		List<String> resolved = []
		resolved.addAll(serverJvmArgs.getOrElse([]))

		if (debugEnabled.getOrElse(false)) {
			resolved.add(
					"-agentlib:jdwp=transport=dt_socket,server=y,suspend=${debugSuspend.getOrElse(false) ? 'y' : 'n'},address=*:${debugPort.getOrElse(5005)}"
					)
		}

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

	@TaskAction
	void runServer() {
		File resolvedAssetsZip = assetsZip.get().asFile

		logger.lifecycle("Using extracted assets zip: ${resolvedAssetsZip.absolutePath}")
		logger.lifecycle("Assets exists: ${resolvedAssetsZip.exists()}, size: ${resolvedAssetsZip.exists() ? resolvedAssetsZip.length() : 0}")

		if (!resolvedAssetsZip.exists() || resolvedAssetsZip.length() == 0) {
			throw new GradleException("Assets zip not found or empty: ${resolvedAssetsZip}")
		}

		def resolution = JvmDevRuntimeSupport.resolveJava(jbrHome.getOrElse(''))
		File javaExe = resolution.javaExecutable

		logger.lifecycle("Dev JVM: ${javaExe}")
		logger.lifecycle("Dev JVM source: ${resolution.source}")
		logger.lifecycle("Dev JVM home: ${resolution.javaHome}")

		execOperations.javaexec { spec ->
			spec.executable = javaExe.absolutePath
			spec.classpath = runtimeClasspath
			spec.mainClass.set(mainClassName)
			spec.workingDir = workingDirectory.get()
			spec.standardInput = System.in
			spec.jvmArgs('--enable-native-access=ALL-UNNAMED')
			spec.jvmArgs(buildResolvedJvmArgs(javaExe))
			spec.args(buildResolvedArgs(resolvedAssetsZip))
		}
	}
}