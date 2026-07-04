package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
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
	@Input abstract Property<String> getManifestServerVersion()
	@Input @Optional abstract Property<String> getResolvedServerVersion()
	@Input abstract Property<String> getManifestDependencies()
	@Input abstract Property<String> getManifestOptionalDependencies()
	@Input abstract Property<Boolean> getDisabledByDefault()
	@Input abstract Property<String> getMainClass()
	@Input abstract Property<Boolean> getIncludesPack()
	@Input abstract Property<String> getCurseforgeId()
	@Input @Optional abstract Property<String> getLoadBefore()
	@Input @Optional abstract Property<String> getSubPlugins()

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
		manifestJson.ServerVersion = manifestServerVersion.get()
		manifestJson.Dependencies = ManifestUtils.parseDepMap(manifestDependencies.orNull)
		manifestJson.OptionalDependencies = ManifestUtils.parseDepMap(manifestOptionalDependencies.orNull)
		manifestJson.DisabledByDefault = disabledByDefault.get()
		manifestJson.Main = mainClass.get()
		manifestJson.IncludesAssetPack = includesPack.get()
		manifestJson.UpdateChecker = [
			CurseForge: curseforgeId.orNull
		]

		def loadBeforeList = new JsonSlurper().parseText(loadBefore.getOrElse('[]')) as List
		if (loadBeforeList) {
			manifestJson.LoadBefore = loadBeforeList.collect { entry ->
				[ name: entry.name, version: entry.version ?: '*' ]
			}
		} else {
			manifestJson.remove('LoadBefore')
		}

		def resolvedVersion = manifestServerVersion.get()
		def subPluginList = new JsonSlurper().parseText(subPlugins.getOrElse('[]')) as List
		if (subPluginList) {
			manifestJson.SubPlugins = subPluginList.collect { sp ->
				def entry = new LinkedHashMap<String, Object>(sp as Map)
				if (!entry.containsKey('ServerVersion')) {
					entry.ServerVersion = resolvedVersion
				}
				if (!entry.containsKey('DisabledByDefault')) {
					entry.DisabledByDefault = false
				}
				if (!entry.containsKey('IncludesAssetPack')) {
					entry.IncludesAssetPack = false
				}
				if (entry.containsKey('LoadBefore') && entry.LoadBefore instanceof List) {
					entry.LoadBefore = (entry.LoadBefore as List).collect { lb ->
						[ name: lb.name, version: lb.version ?: '*' ]
					}
				}
				entry
			}
		} else {
			manifestJson.remove('SubPlugins')
		}

		file.text = JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson))
	}
}