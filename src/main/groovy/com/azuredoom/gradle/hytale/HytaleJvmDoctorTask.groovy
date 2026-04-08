package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Diagnostic task that prints JVM runtime inspection details")
abstract class HytaleJvmDoctorTask extends DefaultTask {

	@Input
	abstract Property<String> getJbrHome()

	@TaskAction
	void runDoctor() {
		def resolution = JvmDevRuntimeSupport.resolveJava(jbrHome.getOrElse(''))
		File javaExe = resolution.javaExecutable

		logger.lifecycle("Java executable: ${javaExe}")
		logger.lifecycle("Java home: ${resolution.javaHome}")
		logger.lifecycle("Resolution source: ${resolution.source}")
		logger.lifecycle("JetBrains Runtime detected: ${JvmDevRuntimeSupport.isJetBrainsRuntime(javaExe)}")
		logger.lifecycle("Enhanced class redefinition supported: ${JvmDevRuntimeSupport.supportsEnhancedRedefinition(javaExe)}")
		logger.lifecycle("HotswapAgent mode supported: ${JvmDevRuntimeSupport.supportsHotswapAgentMode(javaExe)}")

		File agent = JvmDevRuntimeSupport.resolveBundledHotswapAgent(javaExe)
		logger.lifecycle("Bundled HotswapAgent: ${agent != null ? agent.absolutePath : 'not found'}")
	}
}