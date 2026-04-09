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

		def workspaceExt = project.extensions.create(
				'hytaleWorkspace',
				HytaleWorkspaceExtension
				)

		workspaceExt.patchline.convention('release')
		workspaceExt.modProjects.convention([])
		workspaceExt.hostProject.convention('')

		project.subprojects { subproject ->
			subproject.plugins.withId('com.azuredoom.hytale-tools') {
				def childExt = subproject.extensions.getByType(HytaleExtension)

				childExt.manifestGroup.convention(workspaceExt.manifestGroup)
				childExt.hytaleVersion.convention(workspaceExt.hytaleVersion)
				childExt.patchline.convention(workspaceExt.patchline)
			}
		}

		HytaleWorkspaceTaskRegistrar.register(project)
	}
}