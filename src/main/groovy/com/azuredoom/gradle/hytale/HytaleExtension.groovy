package com.azuredoom.gradle.hytale

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
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

    abstract RegularFileProperty getManifestFile()
    abstract DirectoryProperty getRunDirectory()
    abstract DirectoryProperty getAssetPackSourceDirectory()
    abstract DirectoryProperty getAssetPackRunDirectory()
}
