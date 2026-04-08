package com.azuredoom.gradle.hytale

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class HytaleWorkspaceExtension {
	abstract Property<String> getManifestGroup()
	abstract Property<String> getHytaleVersion()
	abstract Property<String> getPatchline()
	abstract ListProperty<String> getModProjects()
	abstract Property<String> getHostProject()
}