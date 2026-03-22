# Hytale Gradle Plugin

A standalone Gradle plugin that extracts common Hytale development tasks
and shared build configuration into a reusable plugin.

This plugin automatically provides:

-   Required repositories
-   Custom `vine*` configurations
-   Task registrations for development workflows

## Included tasks

### `updatePluginManifest`

Updates `src/main/resources/manifest.json` using Gradle properties or
explicit `hytaleTools {}` configuration.

### `decompileServerJar`

Uses Vineflower to decompile only `com/hypixel/hytale/**` from the
resolved Hytale server jar into:

    build/vineflower/hytale-server/

### `decompileVineDependencies`

Uses Vineflower to decompile jars resolved from `vineDependencyJars`
into:

    build/vineflower/dependencies/

### `prepareRunServer`

Creates the local `run/` directory and depends on `classes` and
`processResources`.

### `downloadAssetsZip`

Authenticates with Hytale device auth, downloads the asset wrapper jar,
extracts `Assets.zip`, and caches the results under Gradle user home.

### `runServer`

Runs the Hytale server locally using your project output, runtime
classpath, and the resolved server jar.

------------------------------------------------------------------------

## What the plugin configures automatically

### Repositories

The plugin automatically adds required Hytale, Modtale, CurseForge, hMReleases, PlaceholderAPI, and AzureDoom
repositories.

You **do not need to declare these manually**.

------------------------------------------------------------------------

### Configurations

The plugin automatically creates:

-   `vineImplementation`
-   `vineCompileOnly`
-   `vineDecompileTargets`
-   `vineDecompileClasspath`
-   `vineServerJar`
-   `vineDependencyJars`

And wires:

    implementation <- vineImplementation
    compileOnly   <- vineCompileOnly

------------------------------------------------------------------------

## Using the plugin

### settings.gradle

``` groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}
```

### build.gradle

``` groovy
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.3'
}
```

------------------------------------------------------------------------

## Dependencies

``` groovy
dependencies {
    // Required
    vineServerJar "com.hypixel.hytale:Server:$hytale_version"
    vineCompileOnly "com.hypixel.hytale:Server:$hytale_version"

    // Optional
    vineImplementation 'com.buuz135:MultipleHUD:1.0.6'
    vineCompileOnly 'curse.maven:partyinfo-1429469:7526614'

    // Decompile targets
    vineDecompileTargets 'com.buuz135:MultipleHUD:1.0.6'
    vineDecompileTargets 'curse.maven:partyinfo-1429469:7526614'
}
```

------------------------------------------------------------------------

## Configuration

``` groovy
hytaleTools {
    javaVersion = project.java_version as Integer
    hytaleVersion = project.hytale_version.toString()
    manifestGroup = project.manifest_group.toString()
    modId = project.mod_id.toString()
    modDescription = project.mod_description.toString()
    modUrl = project.mod_url.toString()
    mainClass = project.main_class.toString()
    modCredits = project.mod_credits.toString()
    manifestDependencies = project.manifest_dependencies.toString()
    manifestOptionalDependencies = project.manifest_opt_dependencies.toString()
    curseforgeId = project.curseforgeID.toString()
    disabledByDefault = project.disabled_by_default.toString().toBoolean()
    includesPack = project.includes_pack.toString().toBoolean()
    patchline = project.patchline.toString()
}
```

------------------------------------------------------------------------