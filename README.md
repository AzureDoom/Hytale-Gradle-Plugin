# Hytale Gradle Plugin

[![Gradle Plugin](https://img.shields.io/badge/Gradle-Plugin-blue)](https://plugins.gradle.org/plugin/com.azuredoom.hytale-tools)
[![Java](https://img.shields.io/badge/Java-25-orange)]()
[![Hytale](https://img.shields.io/badge/Hytale-Release/Pre-green)]()

A Gradle plugin for Hytale mod development that standardizes project setup, manifest generation,
validation, local server runs, and IDE-friendly decompiled source attachment.

---

## Why use this plugin?

This plugin replaces manual setup tasks such as:
- manual manifest management
- manual server setup
- manual dependency decompilation
- manual IDE source attachment setup

---

## Quickstart

```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.17'
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.example.mods'
    modId = 'examplemod'
    mainClass = 'com.example.mods.ExampleMod'
}
```

Then run:

```bash
./gradlew setupHytaleDev
./gradlew runServer
```

Having issues? See [Support & Issues](#support--issues).

---

## Multi-Project Setup

This plugin supports a standard Gradle multi-project layout for shared code and multiple mods.

### Quick Start

```text
root/
├── settings.gradle
├── build.gradle
├── common/
├── modA/
└── modB/
```

```gradle
// settings.gradle
rootProject.name = "my-hytale-workspace"
include("common", "modA", "modB")
```

```gradle
// root build.gradle
plugins {
    id 'com.azuredoom.hytale-tools' version '1.0.17'
}

subprojects {
    plugins.withId('com.azuredoom.hytale-tools') {
        group = 'com.example.mods'
    }
}
```

```gradle
// common/build.gradle
plugins {
    id 'java-library'
}
```

```gradle
// modA/build.gradle
plugins {
    id 'com.azuredoom.hytale-tools'
}

dependencies {
    implementation project(':common')
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.example.mods'
    modId = 'moda'
    mainClass = 'com.example.mods.moda.ModA'
}
```

Repeat for additional mod subprojects (e.g. `modB`).

---

### How it works

* `common` is a standard shared library and does not apply the plugin
* each mod (`modA`, `modB`, etc.) applies the plugin independently
* shared code is included via normal Gradle `project()` dependencies
* when the plugin is applied to the root project, it also exposes workspace tasks for multi-project builds
* workspace tasks (`runAllMods`, etc.) are only available when the plugin is applied to the root project
* `stageAllModAssets` stages each mod's asset pack into the root `run/mods` directory for the combined dev server
    * uses `assetPackSourceDirectory` (defaults to `src/main/resources`)
    * copies raw resource files and does not depend on compiled outputs
* workspace tasks operate across all subprojects that apply the plugin, ignoring non-Hytale projects (e.g. `common`)

---

### Running

Run a single module:

```bash
./gradlew :modA:runServer
./gradlew :modB:runServer
```

Or run a combined dev server for all Hytale mod projects:

```bash
./gradlew runAllMods
```

---

### Root tasks

When the plugin is applied to the root project, it also exposes these workspace tasks:

* `updateAllPluginManifests`
* `validateAllManifests`
* `stageAllModAssets`
* `runAllMods`

Example:

```bash
./gradlew updateAllPluginManifests
./gradlew validateAllManifests
./gradlew runAllMods
```

`runAllMods` launches a single dev server with all Hytale mod subprojects on the combined runtime classpath.

---

### Notes

* each mod must define its own `modId`, `mainClass`, and manifest
* `common` should not apply the plugin unless it is a real mod
* ordering is deterministic based on Gradle project path (e.g. `:modA`, `:modB`)
* all Hytale subprojects must use the same `hytaleVersion` and `patchline` for `runAllMods`
* `runAllMods` will fail early if any Hytale subproject has mismatched `hytaleVersion` or `patchline`
* asset packs are linked when possible and copied as a fallback on platforms where symlinks are unavailable

---

### Official support

This multi-project layout is the recommended approach for:

* shared code in `common`
* multiple mods in separate subprojects
* one combined dev runtime from the root project

The plugin is designed to work per-project, while the root project provides workspace-style orchestration for multi-project builds.

---

### VS Code Usage

If you are using VS Code, run:

```bash
./gradlew prepareDecompiledSourcesForIde
```

Then reload the workspace to enable source attachment.

## Dependency Flow

The following diagram shows how plugin configurations feed into compilation and decompilation:
```
vineServerJar ─┐
               ├──> compileOnly ───> compileClasspath
vineCompileOnly┘

vineImplementation ─────┐
vineCompileOnly   ──────┼──> vineDependencyJars ───> decompilation
vineDecompileTargets ───┘
```

At a high level:
- `vineServerJar` provides the Hytale server API
- `vineCompileOnly` and `vineImplementation` define your dependencies
- `vineDecompileTargets` controls which dependencies get source attachment

## Version Compatibility

| Plugin Version | Java Version | Hytale Support      |
|----------------|-------------:|---------------------|
| 1.0.x          |          25+ | Release/Pre-Release |

## Gradle Compatibility

This plugin is designed to work with modern Gradle features:

- ✅ Configuration cache compatible
- ✅ Passes Gradle 9.x plugin validation
- ⚠️ Selective build cache support

### Build Cache Behavior

Tasks are categorized as follows:

**Cacheable:**
- `DecompileDependencyJarTask`
- `DecompileServerJarTask`

**Not cacheable (by design):**
- `downloadAssetsZip` (network + auth dependent)
- `runServer` (executes an external process)
- `prepareRunServer` (filesystem links/junctions)
- manifest tasks (low-cost file operations)

This ensures correctness while still benefiting from caching where it matters.

## CI Usage

The plugin is compatible with:

- Gradle configuration cache
- Gradle plugin validation (Gradle 9+)

Recommended CI flags:

```bash
./gradlew build --configuration-cache
```

## Features

- Automatically adds required repositories
- Creates Hytale-specific and source `vine*` configurations
- Generates, updates, and validates `manifest.json`
- Downloads authenticated Hytale assets
- Runs a local Hytale server for development
- Generates decompiled sources and attaches them in IDEs

## Included Tasks

### `createManifestIfMissing`

Creates `src/main/resources/manifest.json` with a default structure if it does not already exist.

This task is safe to run repeatedly and will not overwrite an existing manifest.

### `updatePluginManifest`

Updates (or rewrites) `src/main/resources/manifest.json` from Gradle configuration.

### `downloadAssetsZip`

Authenticates with Hytale device auth, downloads the asset wrapper, extracts `Assets.zip`, and caches the result.

**Fallback behavior:**
If remote download fails, the task will attempt to locate a local Hytale installation
and reuse an existing `Assets.zip`.

You can override this location via:
```gradle
hytaleTools {
    hytaleHomeOverride = "/path/to/Hytale"
}
```

### `runServer`

Launches a local Hytale server using:
- your project output
- your runtime classpath
- the resolved Hytale server jar

## `prepareDecompiledSourcesForIde`

Decompiles the server jar and all dependencies declared in `vineImplementation`, `vineCompileOnly`, or `vineDecompileTargets` into `build/generated-sources-m2` and `build/generated-sources-ivy`.

This is useful for IDE source attachment.

## Development Workflow

For first-time setup, you can run:

```bash
./gradlew setupHytaleDev
```

During development:

```bash
./gradlew runServer
```

What happens during normal development:

1. `updatePluginManifest` writes manifest values from Gradle configuration, if missing `createManifestIfMissing` creates a default.
2. `validateManifest` runs before `processResources`, ensuring the manifest is generated and checked as part of the build.
3. `runServer` prepares the run directory, downloads assets if needed, and launches the server.

Because manifest generation and validation are wired into the build, most projects do not need to invoke those tasks manually.

## Extension Reference

| Property                       | Type          |                            Default | Required | Purpose                                                            |
|--------------------------------|---------------|-----------------------------------:|----------|--------------------------------------------------------------------|
| `javaVersion`                  | `Integer`     |                               `25` | No       | Java version used for decompilation/tooling                        |
| `hytaleVersion`                | `String`      |                               none | Usually  | Hytale server version to resolve                                   |
| `patchline`                    | `String`      |                          `release` | No       | Asset/server patchline                                             |
| `oauthBaseUrl`                 | `String`      |                   Hytale OAuth URL | No       | Override auth endpoint                                             |
| `accountBaseUrl`               | `String`      |            Hytale account-data URL | No       | Override account endpoint                                          |
| `manifestGroup`                | `String`      |                    `project.group` | Yes      | Manifest group / namespace                                         |
| `modId`                        | `String`      |                     `project.name` | Yes      | Manifest mod id                                                    |
| `modDescription`               | `String`      |                              empty | No       | Manifest description                                               |
| `modUrl`                       | `String`      |                              empty | No       | Manifest project URL                                               |
| `mainClass`                    | `String`      |                              empty | Usually  | Plugin entrypoint                                                  |
| `modCredits`                   | `String`      |                              empty | No       | Manifest credits                                                   |
| `manifestDependencies`         | `String`      |                              empty | No       | Required manifest deps                                             |
| `manifestOptionalDependencies` | `String`      |                              empty | No       | Optional manifest deps                                             |
| `curseforgeId`                 | `String`      |                              empty | No       | CurseForge project id                                              |
| `disabledByDefault`            | `Boolean`     |                            `false` | No       | Manifest flag                                                      |
| `includesPack`                 | `Boolean`     |                            `false` | No       | Manifest flag                                                      |
| `manifestFile`                 | `RegularFile` | `src/main/resources/manifest.json` | No       | Manifest location                                                  |
| `runDirectory`                 | `Directory`   |                             `run/` | No       | Local server run dir                                               |
| `assetPackSourceDirectory`     | `Directory`   |               `src/main/resources` | No       | Source asset directory used by `runServer` and `stageAllModAssets` |
| `assetPackRunDirectory`        | `Directory`   |      computed under `run/mods/...` | No       | Assets target dir                                                  |
| `bundleAssetEditorRuntime`     | `Boolean`     |                             `true` | No       | Controls whether AssetBridge is bundled into the final jar         |

## Task Reference

| Task                             | Group    | Purpose                                                         | Typical Use                        |
|----------------------------------|----------|-----------------------------------------------------------------|------------------------------------|
| `createManifestIfMissing`        | `hytale` | Creates a starter manifest if missing                           | First setup                        |
| `updatePluginManifest`           | `hytale` | Rewrites manifest from Gradle config                            | Normal dev/build                   |
| `updateAllPluginManifests`       | `hytale` | Rewrites manifests for all Hytale subprojects                   | Multi-project manifest sync        |
| `downloadAssetsZip`              | `hytale` | Authenticates and fetches assets                                | Before first run / troubleshooting |
| `hytaleDoctor`                   | `hytale` | Prints plugin, manifest, asset, and dependency diagnostics      | Troubleshooting                    |
| `runServer`                      | `hytale` | Launches local Hytale server                                    | Single-project dev loop            |
| `runAllMods`                     | `hytale` | Launches one shared server with all mod subprojects             | Multi-project dev loop             |
| `stageAllModAssets`              | `hytale` | Stages each mod's asset pack into the root `run/mods` directory | Multi-project run preparation      |
| `prepareDecompiledSourcesForIde` | `hytale` | Generates source jars for IDE attachment                        | IDE setup                          |
| `validateManifest`               | internal | Verifies generated manifest values                              | Runs automatically                 |
| `validateAllManifests`           | `hytale` | Verifies manifests for all Hytale subprojects                   | Multi-project validation           |
| `prepareRunServer`               | internal | Sets up run directory and mod assets                            | Runs automatically                 |
| `decompileServerJar`             | internal | Decompiles Hytale server sources                                | Internal source pipeline           |
| `setupHytaleDev`                 | `hytale` | Prepares IDE sources and downloads assets                       | First-time setup                   |

## IDE Source Attachment

The plugin can prepare decompiled sources for IDE use.

It decompiles:
- the Hytale server jar
- every dependency declared in `vineImplementation`, `vineCompileOnly`, or `vineDecompileTargets`

### How it works

Generated decompiled sources are packaged as `-sources.jar` files and installed into local generated repositories under:

```bash
build/generated-sources-m2/
build/generated-sources-ivy/
```

The plugin installs both:
- the original binary jar
- the generated sources jar

IDEs use these for source attachment after generation.

> Note:
> These generated repositories are not used for normal dependency resolution during the build.
> They exist only to support IDE source attachment after sources have been generated.
>
> This avoids Gradle task dependency conflicts and ensures consistent builds.

### When it runs

If the `idea` plugin is applied, decompiled sources are prepared automatically when you run:

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

### Configuration Overview

| Configuration        | Purpose                                                       |
|----------------------|---------------------------------------------------------------|
| vineServerJar        | Hytale server binary (auto-injected)                          |
| vineImplementation   | Runtime dependencies                                          |
| vineCompileOnly      | Compile-time only dependencies                                |
| vineDecompileTargets | Extra dependencies to decompile for IDE sources               |
| hytaleBundledRuntime | Runtime dependency automatically added and optionally bundled |

`compileOnly` automatically includes:
- `vineCompileOnly`
- `vineServerJar`

This lets you write mods against the Hytale server API without manually declaring the server dependency.

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
    id 'com.azuredoom.hytale-tools' version '1.0.17'
}
```

## Dependencies

```gradle
dependencies {
    // Dependencies declared in `vineImplementation`, `vineCompileOnly`, and `vineDecompileTargets` 
    // are included in the decompilation classpath (`vineDependencyJars`).
    vineImplementation 'com.buuz135:MultipleHUD:1.0.6'
    vineCompileOnly 'curse.maven:partyinfo-1429469:7526614'
    
    // Optional decompile targets for IDE source attachment
    vineDecompileTargets 'com.buuz135:MultipleHUD:1.0.6'
}
```

## Hytale Server Dependency

The plugin automatically adds the Hytale server dependency based on:

```groovy
hytaleTools {
    hytaleVersion = '1.0.0'
}
```

This injects:
```groovy
vineServerJar "com.hypixel.hytale:Server:${hytaleVersion}"
```

and makes it available on `compileOnly`. You **do not need to declare this manually**.

### Overriding the server dependency

If you want to use a different version of the Hytale server:

```groovy
dependencies {
    vineServerJar 'com.example:custom-server:1.0.0'
}
```

Auto-injection is skipped when a dependency is already declared.

## AssetBridge

The plugin automatically adds the [AssetBridge library](https://github.com/AzureDoom/AssetBridge) dependency:

```groovy
dependencies {
  implementation 'com.azuredoom.hytale:hytale-asset-editor-runtime:0.2.0'
}
```

This dependency is:
- added automatically via the `hytaleBundledRuntime` configuration
- available on `implementation`

### Bundling into the final jar

By default, the runtime is bundled into your mod jar (similar to a lightweight shading step without requiring an external plugin).

You can disable this behavior:
```groovy
hytaleTools {
    bundleAssetEditorRuntime = false
}
```

When disabled:
- the dependency is still available at `compile`/`runtime`
- it is not included inside the final `jar`

### Overriding the runtime version

You can override the default version:
```groovy
dependencies {
  hytaleBundledRuntime 'com.azuredoom.hytale:hytale-asset-editor-runtime:0.x.0'
}
```

Declaring a dependency manually will replace the plugin’s default.

---

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

## Support & Issues

If you encounter a bug, unexpected behavior, or have a feature request:

- 🐛 Open an issue: https://github.com/AzureDoom/Hytale-Gradle-Plugin/issues
- 💬 Join the Discord: https://discord.gg/f2NJGA8ey8

Issue templates are provided for bug reports and feature requests.

Please include:
- Gradle version
- Plugin version
- Full stacktrace (`--stacktrace`)
- Relevant build configuration

For general questions or help getting started, Discord is usually the fastest way to get support.

## Troubleshooting

Start here first:

```bash
./gradlew hytaleDoctor
```

hytaleDoctor prints a summary of:
- configured `hytaleVersion` and `patchline`
- manifest path and run directory
- resolved asset wrapper / `Assets.zip` cache paths
- auth token cache path
- resolved `vineServerJar` files
- declared `vineImplementation`, `vineCompileOnly`, and `vineDecompileTargets` dependencies

Use it when:
- runServer fails
- assets are missing
- manifest values look wrong
- expected dependency sources are not showing up

### Missing `hytaleVersion`

If `hytaleVersion` is not set and no `vineServerJar` is declared,
tasks that require the server jar may fail.

Always configure:

```groovy
hytaleTools {
    hytaleVersion = '...'
}
```

### Sources are not attached in the IDE

Run:

```bash
./gradlew prepareDecompiledSourcesForIde
```

This allows IDEs to attach readable generated source code instead of showing only compiled classes.

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

### `manifest.json` is out of date

Run:

```bash
./gradlew updatePluginManifest
```

The build also wires manifest validation automatically, so this usually indicates missing configuration values.

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

- Requires Java 25+
- `release` is the default patchline
- The plugin applies the `java` plugin automatically
- The plugin does not apply the `idea` plugin automatically
- If the `idea` plugin is present, IDEA integration is wired automatically
- You can always run `prepareDecompiledSourcesForIde` directly for source generation
