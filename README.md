# Hytale Gradle Plugin

[![Gradle Plugin](https://img.shields.io/badge/Gradle-Plugin-blue)]()
[![Java](https://img.shields.io/badge/Java-25+-orange)]()
[![Hytale](https://img.shields.io/badge/Hytale-Release-green)]()

A Gradle plugin for Hytale mod development that standardizes project setup, manifest generation,
validation, local server runs, and IDE-friendly decompiled source attachment.

## Quickstart

```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.6'
}

dependencies {
    vineServerJar "com.hypixel.hytale:Server:$hytale_version"
    compileOnly "com.hypixel.hytale:Server:$hytale_version"
}
```

Then run:

```bash
./gradlew runServer
```

## Version Compatibility

| Plugin Version | Java Version | Hytale Support      |
|----------------|-------------:|---------------------|
| 1.0.x          |          25+ | Release/Pre-Release |

## Features

- Automatically adds required repositories
- Creates Hytale-specific and source `vine*` configurations
- Generates and validates `manifest.json`
- Bootstraps new mod projects
- Downloads authenticated Hytale assets
- Runs a local Hytale server for development
- Generates decompiled sources and attaches them in IDEs

## Included Tasks

### `updatePluginManifest`

Updates `src/main/resources/manifest.json` from Gradle properties
and `hytaleTools {}` values.

### `downloadAssetsZip`

Authenticates with Hytale device auth, downloads the asset wrapper, extracts
`Assets.zip`, and caches the result under Gradle user home. Has a fall back to use the users local installation and `Assets.zip` if unable to download.

### `runServer`

Launches a local Hytale server using:
- your project output
- your runtime classpath
- the resolved Hytale server jar

## `prepareDecompiledSourcesForIde`

Decompiles the server jar and all dependencies declared in `vineDecompileTargets` into `build/generated-sources-m2` and `build/generated-sources-ivy`.

This is useful for IDE source attachment.

## Development Workflow

A typical workflow looks like this:

```bash
./gradlew prepareDecompiledSourcesForIde
./gradlew runServer
```

What happens during normal development:

1. `updatePluginManifest` writes manifest values from Gradle configuration.
2. `validateManifest` runs before `processResources`, ensuring the manifest is generated and checked as part of the build.
3. `runServer` prepares the run directory, downloads assets if needed, and launches the server.

Because manifest generation and validation are wired into the build, most projects do not need to invoke those tasks manually.

## IDE Source Attachment

The plugin automatically prepares decompiled sources for IDE use.

It decompiles:
- the Hytale server jar
- every dependency declared in `vineDecompileTargets`

### How it works

Generated decompiled sources are packaged as `-sources.jar` files and installed into local generated repositories under:

```text
build/generated-sources-m2/
build/generated-sources-ivy/
```

The plugin now installs both:
- the original binary jar
- the generated sources jar

This matters because IDEs can resolve the binary artifact and attach matching generated sources from the same local repository layout, which makes source attachment more reliable.

### When it runs

Decompiled sources are prepared automatically when you run:

```bash
./gradlew idea
```

You can also run it directly:

```bash
./gradlew prepareDecompiledSourcesForIde
```

### Generated output

Decompiled source jars are written under:

```text
build/generated-sources-jars/
```

### Recommended dependency setup

Use `vineDecompileTargets` for any dependency whose decompiled sources you want available in your IDE:

```gradle
dependencies {
    vineDecompileTargets 'com.buuz135:MultipleHUD:1.0.6'
}
```

## Repositories

The plugin automatically adds these repositories:

- Maven Central
- Hytale Server Release
- Hytale Server Pre-Release
- Hytale Modding Maven
- Hytale-Mods.info Maven
- PlaceholderAPI
- CurseMaven
- AzureDoom Maven
- Modtale (exclusive Ivy content for group `modtale`)

You do not need to declare them manually.

## Configurations

The plugin automatically creates:

- `vineDecompileTargets`
- `vineDecompileClasspath`
- `vineServerJar`
- `vineDependencyJars`
- `vineflowerTool`

## Usage

### `settings.gradle`

```gradle
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

### `build.gradle`

```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.6'
}
```

## Dependencies

```gradle
dependencies {
    // Required
    vineServerJar "com.hypixel.hytale:Server:$hytale_version"
    compileOnly "com.hypixel.hytale:Server:$hytale_version"

    // Optional decompile targets for IDE source attachment
    vineDecompileTargets 'com.buuz135:MultipleHUD:1.0.6'
    vineDecompileTargets 'curse.maven:partyinfo-1429469:7526614'
}
```

## Configuration

Example extension usage:

```gradle
hytaleTools {
    javaVersion = project.java_version as Integer
    hytaleVersion = project.hytale_version.toString()
    patchline = project.hytale_patchline.toString()

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
}
```

### Backing Gradle properties

The plugin also reads these Gradle properties automatically:

- `java_version`
- `hytale_version`
- `hytale_patchline`
- `hygradle.hytale.oauth.base`
- `hygradle.hytale.accounts.base`
- `manifest_group`
- `mod_id`
- `mod_description`
- `mod_url`
- `main_class`
- `mod_credits`
- `manifest_dependencies`
- `manifest_opt_dependencies`
- `curseforgeID`
- `disabled_by_default`
- `includes_pack`

## Troubleshooting

### Sources are not attached in the IDE

Run:

```bash
./gradlew prepareDecompiledSourcesForIde
```

Then refresh or reimport the Gradle project in your IDE.

Also verify the dependency is listed in `vineDecompileTargets` if you expect decompiled dependency sources.

### `runServer` fails because assets are missing

Run:

```bash
./gradlew downloadAssetsZip
```

Also verify:
- `hytale_version` is set correctly
- `hytale_patchline` matches the artifact you expect
- authentication cache under Gradle user home is valid

### `manifest.json` is missing or out of date

Run:

```bash
./gradlew updatePluginManifest
```

The build also wires manifest generation and validation automatically, so this usually indicates missing configuration values.

### Manifest validation fails during build

Verify the configured values for:
- `manifestGroup`
- `modId`
- `mainClass`
- `hytaleVersion`

Also review:
- `manifest_dependencies`
- `manifest_opt_dependencies`
- `includes_pack`

### Dependency sources are not being generated

Make sure the dependency is declared in:

```gradle
vineDecompileTargets "group:module:version"
```

Only dependencies in that configuration are decompiled for IDE attachment.

## Notes

- Java 25 is the default
- `release` is the default patchline
- The plugin applies the `java` plugin automatically
- The plugin applies the `idea` plugin to support IDE source attachment
