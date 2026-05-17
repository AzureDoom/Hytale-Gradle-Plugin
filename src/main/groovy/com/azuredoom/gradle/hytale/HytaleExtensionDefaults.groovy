package com.azuredoom.gradle.hytale

import org.gradle.api.Project

final class HytaleExtensionDefaults {
	private HytaleExtensionDefaults() {}

	static void apply(Project project, HytaleExtension ext) {
		ext.javaVersion.convention(project.providers.gradleProperty('java_version').map { (it ?: '25') as Integer }.orElse(25))
		// TODO: Make default '0.+' instead of '2026.+' after next update
		ext.hytaleVersion.convention(project.providers.gradleProperty('hytale_version').orElse('2026.+'))
		ext.patchline.convention(project.providers.gradleProperty('hytale_patchline').orElse('release'))
		ext.oauthBaseUrl.convention(project.providers.gradleProperty('hytools.hytale.oauth.base').orElse('https://oauth.accounts.hytale.com'))
		ext.accountBaseUrl.convention(project.providers.gradleProperty('hytools.hytale.accounts.base').orElse('https://account-data.hytale.com'))

		ext.manifestGroup.convention(project.providers.gradleProperty('manifest_group').orElse(project.group.toString()))
		ext.modId.convention(project.providers.gradleProperty('mod_id').orElse(project.name))
		ext.modDescription.convention(project.providers.gradleProperty('mod_description').orElse(''))
		ext.modUrl.convention(project.providers.gradleProperty('mod_url').orElse(''))
		ext.mainClass.convention(project.providers.gradleProperty('main_class').orElse(''))
		ext.modCredits.convention(project.providers.gradleProperty('mod_credits').orElse('replace_me'))
		ext.manifestDependencies.convention(project.providers.gradleProperty('manifest_dependencies').orElse(''))
		ext.manifestOptionalDependencies.convention(project.providers.gradleProperty('manifest_opt_dependencies').orElse(''))
		ext.curseforgeId.convention(project.providers.gradleProperty('curseforgeID').orElse(''))
		ext.disabledByDefault.convention(project.providers.gradleProperty('disabled_by_default').map { it.toBoolean() }.orElse(false))
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
		ext.serverArgs.convention([
			'--allow-op',
			'--disable-sentry'
		])
		ext.serverJvmArgs.convention([])
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
	}

	private static String hytaleServerDocsUrl(String patchline) {
		String value = patchline == null ? 'release' : patchline.trim()

		boolean prerelease =
				value.equalsIgnoreCase('pre-release') ||
				value.equalsIgnoreCase('prerelease')

		return prerelease
				? 'https://prerelease.server.docs.hytale.com/'
				: 'https://release.server.docs.hytale.com/'
	}
}