package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Mutates decompiled sources by injecting external Javadocs")
abstract class InjectServerJavadocsIntoDecompiledSourcesTask extends DefaultTask {
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	abstract DirectoryProperty getSourceDirectory()

	@Input
	abstract Property<String> getServerJavadocsUrl()

	@Input
	abstract Property<String> getPatchline()

	@Internal
	abstract DirectoryProperty getGradleUserHomeDirectory()

	@OutputDirectory
	abstract DirectoryProperty getOutputDirectory()

	@TaskAction
	void inject() {
		File sourceDir = sourceDirectory.get().asFile
		File cacheDir = new File(
				gradleUserHomeDirectory.get().asFile,
				"caches/hytale-javadocs/${patchline.get()}"
				)

		HytaleSourceJavadocInjector.inject(
				sourceDir,
				serverJavadocsUrl.get(),
				cacheDir,
				logger
				)
	}
}