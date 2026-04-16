package com.azuredoom.gradle.hytale

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class HytaleExtension {
	abstract Property<Integer> getJavaVersion()
	abstract Property<String> getHytaleVersion()
	abstract Property<String> getPatchline()
	abstract Property<String> getOauthBaseUrl()
	abstract Property<String> getAccountBaseUrl()

	abstract Property<String> getManifestGroup()
	abstract Property<String> getModId()
	abstract Property<String> getModDescription()
	abstract Property<String> getModUrl()
	abstract Property<String> getMainClass()
	abstract Property<String> getModCredits()
	abstract Property<String> getManifestDependencies()
	abstract Property<String> getManifestOptionalDependencies()
	abstract Property<String> getCurseforgeId()
	abstract Property<Boolean> getDisabledByDefault()
	abstract Property<Boolean> getIncludesPack()
	abstract Property<Boolean> getBundleAssetEditorRuntime()

	abstract ListProperty<String> getServerArgs()
	abstract ListProperty<String> getServerJvmArgs()
	abstract Property<String> getPreRunTask()

	abstract Property<Boolean> getDebugEnabled()
	abstract Property<Integer> getDebugPort()
	abstract Property<Boolean> getDebugSuspend()

	abstract Property<Boolean> getHotSwapEnabled()
	abstract Property<Boolean> getRequireDcevm()
	abstract Property<Boolean> getUseHotswapAgent()
	abstract Property<String> getJbrHome()

	abstract RegularFileProperty getManifestFile()
	abstract DirectoryProperty getRunDirectory()
	abstract DirectoryProperty getAssetPackSourceDirectory()
	abstract DirectoryProperty getAssetPackRunDirectory()

	void javaVersion(int v) {
		getJavaVersion().set(v)
	}
	void hytaleVersion(String v) {
		getHytaleVersion().set(v)
	}
	void patchline(String v) {
		getPatchline().set(v)
	}
	void oauthBaseUrl(String v) {
		getOauthBaseUrl().set(v)
	}
	void accountBaseUrl(String v) {
		getAccountBaseUrl().set(v)
	}
	void manifestGroup(String v) {
		getManifestGroup().set(v)
	}
	void modId(String v) {
		getModId().set(v)
	}
	void modDescription(String v) {
		getModDescription().set(v)
	}
	void modUrl(String v) {
		getModUrl().set(v)
	}
	void mainClass(String v) {
		getMainClass().set(v)
	}
	void modCredits(String v) {
		getModCredits().set(v)
	}
	void manifestDependencies(String v) {
		getManifestDependencies().set(v)
	}
	void manifestOptionalDependencies(String v) {
		getManifestOptionalDependencies().set(v)
	}
	void curseforgeId(String v) {
		getCurseforgeId().set(v)
	}
	void disabledByDefault(boolean v) {
		getDisabledByDefault().set(v)
	}
	void includesPack(boolean v) {
		getIncludesPack().set(v)
	}
	void bundleAssetEditorRuntime(boolean v) {
		getBundleAssetEditorRuntime().set(v)
	}
	void preRunTask(String v) {
		getPreRunTask().set(v)
	}
	void debugEnabled(boolean v) {
		getDebugEnabled().set(v)
	}
	void debugPort(int v) {
		getDebugPort().set(v)
	}
	void debugSuspend(boolean v) {
		getDebugSuspend().set(v)
	}
	void hotSwapEnabled(boolean v) {
		getHotSwapEnabled().set(v)
	}
	void requireDcevm(boolean v) {
		getRequireDcevm().set(v)
	}
	void useHotswapAgent(boolean v) {
		getUseHotswapAgent().set(v)
	}
	void jbrHome(String v) {
		getJbrHome().set(v)
	}
	void serverArgs(List<String> v) {
		getServerArgs().set(v)
	}
	void serverArgs(String... v) {
		getServerArgs().addAll(v as List)
	}
	void serverArg(String v) {
		getServerArgs().add(v)
	}
	void serverJvmArgs(List<String> v) {
		getServerJvmArgs().set(v)
	}
	void serverJvmArgs(String... v) {
		getServerJvmArgs().addAll(v as List)
	}
	void serverJvmArg(String v) {
		getServerJvmArgs().add(v)
	}
	void manifestFile(Object v) {
		getManifestFile().set(v)
	}
	void runDirectory(Object v) {
		getRunDirectory().set(v)
	}
	void assetPackSourceDirectory(Object v) {
		getAssetPackSourceDirectory().set(v)
	}
	void assetPackRunDirectory(Object v) {
		getAssetPackRunDirectory().set(v)
	}
}
