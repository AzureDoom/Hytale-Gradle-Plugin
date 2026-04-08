package com.azuredoom.gradle.hytale

import org.gradle.api.Plugin
import org.gradle.api.Project

class HytaleWorkspacePlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		if (project != project.rootProject) {
			throw new IllegalStateException(
			"com.azuredoom.hytale-workspace must only be applied to the root project."
			)
		}

		def ext = project.extensions.create(
				'hytaleWorkspace',
				HytaleWorkspaceExtension
				)

		ext.patchline.convention('release')
		ext.modProjects.convention([])
		ext.hostProject.convention('')

		HytaleWorkspaceTaskRegistrar.register(project)
	}
}