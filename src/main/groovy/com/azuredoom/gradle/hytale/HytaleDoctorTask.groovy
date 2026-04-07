package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Diagnostic task that prints current plugin configuration and resolved state")
abstract class HytaleDoctorTask extends DefaultTask {
	@Input
	abstract Property<String> getHytaleVersion()

	@Input
	abstract Property<String> getPatchline()

	@Internal
	abstract RegularFileProperty getManifestFile()

	@Internal
	abstract DirectoryProperty getRunDirectory()

	@Internal
	abstract RegularFileProperty getAssetsZip()

	@Internal
	abstract RegularFileProperty getWrapperFile()

	@Internal
	abstract RegularFileProperty getTokenCacheFile()

	@Internal
	abstract ConfigurableFileCollection getVineServerJarFiles()

	@Input
	abstract ListProperty<String> getVineImplementationDependencies()

	@Input
	abstract ListProperty<String> getVineCompileOnlyDependencies()

	@Input
	abstract ListProperty<String> getVineDecompileTargetDependencies()

	@TaskAction
	void runDoctor() {
		println "HYTALE_VERSION=${hytaleVersion.orNull ?: ''}"
		println "PATCHLINE=${patchline.orNull ?: ''}"
		println "MANIFEST=${manifestFile.isPresent() ? manifestFile.get().asFile.absolutePath : ''}"
		println "RUN_DIR=${runDirectory.isPresent() ? runDirectory.get().asFile.absolutePath : ''}"
		println "ASSETS_ZIP=${assetsZip.isPresent() ? assetsZip.get().asFile.absolutePath : ''}"
		println "WRAPPER=${wrapperFile.isPresent() ? wrapperFile.get().asFile.absolutePath : ''}"
		println "TOKEN_CACHE=${tokenCacheFile.isPresent() ? tokenCacheFile.get().asFile.absolutePath : ''}"
		println "VINE_SERVER_FILES=" + vineServerJarFiles.files.collect { it.name }.sort().join(',')
		println "VINE_IMPLEMENTATION=" + new ArrayList<>(vineImplementationDependencies.get()).sort().join(',')
		println "VINE_COMPILE_ONLY=" + new ArrayList<>(vineCompileOnlyDependencies.get()).sort().join(',')
		println "VINE_DECOMPILE_TARGETS=" + new ArrayList<>(vineDecompileTargetDependencies.get()).sort().join(',')
	}
}
