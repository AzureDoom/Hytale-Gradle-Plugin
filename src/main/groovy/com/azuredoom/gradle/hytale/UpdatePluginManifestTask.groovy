package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Small manifest rewrite task with negligible build cache value")
abstract class UpdatePluginManifestTask extends DefaultTask {
	@OutputFile
	abstract RegularFileProperty getManifestFile()

	@Input abstract Property<String> getManifestGroup()
	@Input abstract Property<String> getModId()
	@Input abstract Property<String> getVersionString()
	@Input abstract Property<String> getModDescription()
	@Input abstract Property<String> getModCredits()
	@Input abstract Property<String> getModUrl()
	@Input abstract Property<String> getHytaleVersion()
	@Input abstract Property<String> getManifestDependencies()
	@Input abstract Property<String> getManifestOptionalDependencies()
	@Input abstract Property<Boolean> getDisabledByDefault()
	@Input abstract Property<String> getMainClass()
	@Input abstract Property<Boolean> getIncludesPack()
	@Input abstract Property<String> getCurseforgeId()

	@TaskAction
	void updateManifest() {
		def file = manifestFile.get().asFile
		def manifestJson = new JsonSlurper().parseText(file.text) as Map

		manifestJson.Group = manifestGroup.get()
		manifestJson.Name = modId.get()
		manifestJson.Version = versionString.get()
		manifestJson.Description = modDescription.get()
		manifestJson.Authors = ManifestUtils.parseAuthors(modCredits.orNull)
		manifestJson.Website = modUrl.get()
		manifestJson.ServerVersion = hytaleVersion.get()
		manifestJson.Dependencies = ManifestUtils.parseDepMap(manifestDependencies.orNull)
		manifestJson.OptionalDependencies = ManifestUtils.parseDepMap(manifestOptionalDependencies.orNull)
		manifestJson.DisabledByDefault = disabledByDefault.get()
		manifestJson.Main = mainClass.get()
		manifestJson.IncludesAssetPack = includesPack.get()
		manifestJson.UpdateChecker = [
			CurseForge: curseforgeId.orNull
		]

		file.text = JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson))
	}
}
