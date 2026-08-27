package com.azuredoom.gradle.hytale

import org.gradle.api.Project

final class HytaleExtensionDefaults {
	private HytaleExtensionDefaults() {}

	static void apply(Project project, HytaleExtension ext) {
		ext.javaVersion.convention(project.providers.gradleProperty('java_version').map { (it ?: '25') as Integer }.orElse(25))
		ext.hytaleVersion.convention(project.providers.gradleProperty('hytale_version').orElse('0.+'))
		ext.manifestServerVersion.convention(
				project.providers.gradleProperty('manifest_server_version')
				)
		ext.patchline.convention(project.providers.gradleProperty('hytale_patchline').orElse('release'))
		ext.oauthBaseUrl.convention(project.providers.gradleProperty('hytools.hytale.oauth.base').orElse('https://oauth.accounts.hytale.com'))
		ext.accountBaseUrl.convention(project.providers.gradleProperty('hytools.hytale.accounts.base').orElse('https://account-data.hytale.com'))
		ext.hytaleHomeOverride.convention(
				project.providers.gradleProperty('hytale_home')
				.orElse(project.providers.gradleProperty('hytools.hytale.home'))
				.orElse('')
				)

		ext.manifestGroup.convention(project.providers.gradleProperty('manifest_group').orElse(project.group.toString()))
		ext.modId.convention(project.providers.gradleProperty('mod_id').orElse(project.name))
		ext.modDescription.convention(project.providers.gradleProperty('mod_description').orElse(''))
		ext.modUrl.convention(project.providers.gradleProperty('mod_url').orElse(''))
		ext.mainClass.convention(project.providers.gradleProperty('main_class').orElse(''))
		ext.modCredits.convention(project.providers.gradleProperty('mod_credits').orElse('replace_me'))
		ext.manifestDependencies.convention(project.providers.gradleProperty('manifest_dependencies').orElse('Hytale:AssetModule=*'))
		ext.manifestOptionalDependencies.convention(project.providers.gradleProperty('manifest_opt_dependencies').orElse(''))
		ext.curseforgeId.convention(project.providers.gradleProperty('curseforgeID').orElse(''))
		ext.disabledByDefault.convention(project.providers.gradleProperty('disabled_by_default').map { it.toBoolean() }.orElse(false))

		ext.disableHytaleModsInfoMaven.convention(
				project.providers.gradleProperty('hytools.repos.disableHytaleModsInfoMaven').map { it.toBoolean() }.orElse(false)
				)
		ext.disablePlaceholderApiMaven.convention(
				project.providers.gradleProperty('hytools.repos.disablePlaceholderApiMaven').map { it.toBoolean() }.orElse(false)
				)
		ext.disableCurseMaven.convention(
				project.providers.gradleProperty('hytools.repos.disableCurseMaven').map { it.toBoolean() }.orElse(false)
				)
		ext.disableAzureDoomMaven.convention(
				project.providers.gradleProperty('hytools.repos.disableAzureDoomMaven').map { it.toBoolean() }.orElse(false)
				)
		ext.disableHytaleModdingMaven.convention(
				project.providers.gradleProperty('hytools.repos.disableHytaleModdingMaven').map { it.toBoolean() }.orElse(false)
				)
		ext.disableModtaleResolver.convention(
				project.providers.gradleProperty('hytools.repos.disableModtaleResolver').map { it.toBoolean() }.orElse(false)
				)
		ext.disableModifoldRepo.convention(
				project.providers.gradleProperty('hytools.repos.disableModifoldRepo').map { it.toBoolean() }.orElse(false)
				)
		ext.includesPack.convention(project.providers.gradleProperty('includes_pack').map { it.toBoolean() }.orElse(false))
		ext.manifestFile.convention(project.layout.projectDirectory.file('src/main/resources/manifest.json'))
		ext.runDirectory.convention(project.layout.projectDirectory.dir('run'))
		ext.assetPackSourceDirectory.convention(project.layout.projectDirectory.dir('src/main/resources'))
		ext.assetPackRunDirectory.convention(
				project.provider {
					ext.runDirectory.get().dir("mods/${ext.manifestGroup.get().replace('.', '_')}_${ext.modId.get()}")
				}
				)
		ext.subPlugins.convention('[]')
		ext.bundleAssetEditorRuntime.convention(true)
		ext.generateAssetsBinary.convention(
				project.providers.gradleProperty('hytools.generate.assets.binary').map { it.toBoolean() }.orElse(true)
				)
		ext.serverArgs.convention([
			'--allow-op',
			'--disable-sentry'
		])
		ext.serverJvmArgs.convention(['-Xms4G', '-Xmx4G'])
		ext.preRunTask.convention('')

		ext.debugEnabled.convention(
				project.providers.systemProperty('debug').map { it.toBoolean() }.orElse(false)
				)
		ext.debugPort.convention(
				project.providers.gradleProperty('hytools.debug.port').map { it as Integer }.orElse(5005)
				)
		ext.debugSuspend.convention(
				project.providers.gradleProperty('hytools.debug.suspend').map { it.toBoolean() }.orElse(false)
				)

		ext.hotSwapEnabled.convention(
				project.providers.systemProperty('hotswap').map { it.toBoolean() }.orElse(false)
				)
		ext.hotswapAgentPath.convention(
				project.providers.gradleProperty('hytools.hotswap.agent.path').orElse('')
				)
		ext.requireDcevm.convention(false)
		ext.useHotswapAgent.convention(true)

		ext.jbrHome.convention(
				project.providers.gradleProperty('hytools.jbr.home')
				.orElse(project.providers.environmentVariable('JBR_HOME'))
				.orElse('')
				)

		ext.serverJavadocsUrl.convention(
				ext.patchline.map { String patchline ->
					hytaleServerDocsUrl(patchline) as String
				}
				)
		ext.injectServerJavadocsIntoSources.convention(true)

		ext.generatedSourcesRepoDirectory.convention(
				project.layout.dir(
				project.providers.gradleProperty('hytools.generatedSources.repoDirectory')
				.map {
					project.file(it)
				}
				)
				)
	}

	private static String hytaleServerDocsUrl(String patchline) {
		String value = patchline == null ? 'release' : patchline.trim()

		boolean prerelease =
				value.equalsIgnoreCase('pre-release') ||
				value.equalsIgnoreCase('prerelease')

		return prerelease
				? 'https://pre-release.docs.hytale.com/api/'
				: 'https://docs.hytale.com/api/'
	}
}