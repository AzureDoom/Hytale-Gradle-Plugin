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

	void setJavaVersion(int v)                            { javaVersion.set(v) }
	void setHytaleVersion(String v)                       { hytaleVersion.set(v) }
	void setPatchline(String v)                           { patchline.set(v) }
	void setOauthBaseUrl(String v)                        { oauthBaseUrl.set(v) }
	void setAccountBaseUrl(String v)                      { accountBaseUrl.set(v) }
	void setManifestGroup(String v)                       { manifestGroup.set(v) }
	void setModId(String v)                               { modId.set(v) }
	void setModDescription(String v)                      { modDescription.set(v) }
	void setModUrl(String v)                              { modUrl.set(v) }
	void setMainClass(String v)                           { mainClass.set(v) }
	void setModCredits(String v)                          { modCredits.set(v) }
	void setManifestDependencies(String v)                { manifestDependencies.set(v) }
	void setManifestOptionalDependencies(String v)        { manifestOptionalDependencies.set(v) }
	void setCurseforgeId(String v)                        { curseforgeId.set(v) }
	void setDisabledByDefault(boolean v)                  { disabledByDefault.set(v) }
	void setIncludesPack(boolean v)                       { includesPack.set(v) }
	void setBundleAssetEditorRuntime(boolean v)           { bundleAssetEditorRuntime.set(v) }
	void setServerArgs(List<String> v)                    { serverArgs.set(v) }
	void setServerJvmArgs(List<String> v)                 { serverJvmArgs.set(v) }
	void setPreRunTask(String v)                          { preRunTask.set(v) }
	void setDebugEnabled(boolean v)                       { debugEnabled.set(v) }
	void setDebugPort(int v)                              { debugPort.set(v) }
	void setDebugSuspend(boolean v)                       { debugSuspend.set(v) }
	void setHotSwapEnabled(boolean v)                     { hotSwapEnabled.set(v) }
	void setRequireDcevm(boolean v)                       { requireDcevm.set(v) }
	void setUseHotswapAgent(boolean v)                    { useHotswapAgent.set(v) }
	void setJbrHome(String v)                             { jbrHome.set(v) }
}
