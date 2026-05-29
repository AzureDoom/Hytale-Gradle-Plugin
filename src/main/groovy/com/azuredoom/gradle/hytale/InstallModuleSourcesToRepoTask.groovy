package com.azuredoom.gradle.hytale

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Installs files into generated local Maven/Ivy repositories")
abstract class InstallModuleSourcesToRepoTask extends DefaultTask {
	@Input
	abstract Property<String> getGroupId()

	@Input
	abstract Property<String> getModuleId()

	@Input
	abstract Property<String> getModuleVersion()

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getBinaryJar()

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	abstract RegularFileProperty getSourcesJar()

	@Internal
	abstract DirectoryProperty getMavenRepoDir()

	@Internal
	abstract DirectoryProperty getIvyRepoDir()

	@OutputFiles
	Map<String, File> getOutputFiles() {
		String g = groupId.get()
		String m = moduleId.get()
		String v = moduleVersion.get()

		Map<String, File> out = new LinkedHashMap<>()
		out.putAll(HytaleDependencySupport.mavenRepoFiles(mavenRepoDir.get().asFile, g, m, v))
		out.putAll(HytaleDependencySupport.ivyRepoFiles(ivyRepoDir.get().asFile, g, m, v))
		return out
	}

	@TaskAction
	void install() {
		String g = groupId.get()
		String m = moduleId.get()
		String v = moduleVersion.get()

		HytaleDependencySupport.installModuleIntoMavenRepo(
				mavenRepoDir.get().asFile,
				g,
				m,
				v,
				binaryJar.get().asFile,
				sourcesJar.get().asFile
				)

		HytaleDependencySupport.installModuleIntoIvyRepo(
				ivyRepoDir.get().asFile,
				g,
				m,
				v,
				binaryJar.get().asFile,
				sourcesJar.get().asFile
				)
	}
}