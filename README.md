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
    id 'com.azuredoom.hytale-tools' version '1.0.19'
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

## Hot Swap Quickstart

To enable debugging and hot swap:

```bash
./gradlew runServer -Ddebug=true -Dhotswap=true
```

For best results:

- Install JetBrains Runtime (JBR)
- Let the plugin auto-detect it or set `jbrHome`

Verify your setup:

```bash
./gradlew hytaleJvmDoctor
```

---

## Multi-Project Setup

This plugin supports a clean separation between **workspace orchestration** and **individual mod projects**.

- `com.azuredoom.hytale-workspace` → applied to the **root project**
- `com.azuredoom.hytale-tools` → applied to each **mod project**

---

### Project Layout

```text
root/
├── settings.gradle
├── build.gradle
├── common/
├── modA/
└── modB/
```

---

### settings.gradle

```gradle
rootProject.name = "my-hytale-workspace"
include("common", "modA", "modB")
```

---

### Root Project (Workspace)

```gradle
// root build.gradle
plugins {
    id 'com.azuredoom.hytale-workspace' version '1.0.19'
}

hytaleWorkspace {
    modProjects = [':modA', ':modB']

    // Optional (recommended)
    hostProject = ':modA'

    // Optional shared defaults
    manifestGroup = 'com.example.mods'
    hytaleVersion = '1.0.0'
    patchline = 'release'
}
```

---

### Shared Module

```gradle
// common/build.gradle
plugins {
    id 'java-library'
}
```

---

### Mod Project

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

Repeat for additional mod projects (e.g. `modB`).

---

## How It Works

- The **workspace plugin** (`hytale-workspace`) only applies to the root project
- Each **mod project** applies `hytale-tools` independently
- Non-mod projects (e.g. `common`) remain standard Gradle modules
- Workspace tasks operate only on projects listed in `modProjects`
- The workspace plugin does **not** automatically apply the mod plugin

---

## Workspace Tasks

Available only on the root project:

- `updateAllPluginManifests`
- `validateAllManifests`
- `stageAllModAssets`
- `runAllMods`

Example:

```bash
./gradlew updateAllPluginManifests
./gradlew validateAllManifests
./gradlew runAllMods
```

---

## Running

Run a single mod:

```bash
./gradlew :modA:runServer
```

Run all mods together:

```bash
./gradlew runAllMods
```

---

## Host Project

The workspace uses a **host project** to resolve:

- Hytale assets
- Server runtime configuration

You can explicitly configure it:

```gradle
hytaleWorkspace {
    hostProject = ':modA'
}
```

If not set, the first project (by path) is used.

---

## Notes

- All mod projects must use the same `hytaleVersion` and `patchline`
- `runAllMods` fails early if versions mismatch
- Asset packs are staged into `run/mods/`
- Symlinks are used when possible, with copy fallback
- `common` should not apply the plugin unless it is a real mod

---

## Recommended Usage

This setup is ideal for:

- shared code (`common`)
- multiple independent mods
- a single combined development server

The design keeps:

- mod configuration **isolated per project**
- workspace behavior **centralized at the root**

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
- Hytale `Assets.zip` (for IDE asset browsing)

### Assets.zip in IDEs

The plugin automatically adds the resolved Hytale `Assets.zip` to the `compileOnly` classpath.

This means:
- it appears under **External Libraries** in IntelliJ
- you can browse game assets directly in your IDE
- it is **not included in your final jar**

This is provided via an internal `hytaleAssets` configuration, which is added to `compileOnly`.

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
- Supports debug mode and hot swap capable JVM runtime setup
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

### Customizing `runServer`

You can customize the server launch arguments, JVM arguments, debug mode, and hot swap behavior through the extension:

```gradle
hytaleTools {
    hytaleVersion = '1.0.0'

    serverArgs = [
        '--allow-op',
        '--disable-sentry',
        '--disable-file-watcher'
    ]

    serverJvmArgs = [
        '-Xms1G',
        '-Xmx2G'
    ]

    preRunTask = 'generateDevResources'

    // Optional debug / hot swap support
    debugEnabled = false
    debugPort = 5005
    debugSuspend = false

    hotSwapEnabled = false
    requireDcevm = false
    useHotswapAgent = true

    // Optional explicit JetBrains Runtime location
    // jbrHome = '/path/to/jbr'
}

tasks.register('generateDevResources') {
    doLast {
        println 'Preparing additional dev resources...'
    }
}
```

You can also enable debug and hot swap from the command line:

```bash
./gradlew runServer -Ddebug=true
./gradlew runServer -Ddebug=true -Dhotswap=true
```

Hot swap support depends on the selected JVM:

- Standard JVM: limited to method body changes
- JetBrains Runtime (JBR): supports enhanced class redefinition
- HotswapAgent (if available): improves runtime reload behavior

For best results, use JetBrains Runtime with hot swap enabled.

Notes:
- `serverArgs` are appended to the default `--assets=...` argument automatically added by the plugin
- `serverJvmArgs` are added in addition to the plugin’s default JVM launch settings
- `preRunTask` lets you run a custom preparation task before `runServer`
- `debugEnabled` enables JDWP debugging for IDE attach
- `hotSwapEnabled` enables runtime probing for enhanced class redefinition support
- `requireDcevm` fails early if enhanced class redefinition is not available
- `useHotswapAgent` enables bundled HotswapAgent support when available in the selected runtime
- `jbrHome` can be used to point the plugin at a specific JetBrains Runtime installation

### `hytaleJvmDoctor`

Prints JVM diagnostics relevant to debugging and hot swap, including:

- resolved Java executable
- JetBrains Runtime detection
- enhanced class redefinition support
- HotswapAgent mode support
- bundled HotswapAgent availability

This is useful when validating a local JetBrains Runtime setup before using hot swap. 

Runtime resolution uses `jbrHome` first, then known JetBrains Runtime environment variables (`JBR_HOME`, etc.), and finally falls back to the current JVM.

The plugin can automatically detect JetBrains Runtime installations from common locations, including JetBrains Toolbox installs, when `jbrHome` is not explicitly configured.

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

| Property                       | Type           |                              Default | Required | Purpose                                                                                |
|--------------------------------|----------------|-------------------------------------:|----------|----------------------------------------------------------------------------------------|
| `javaVersion`                  | `Integer`      |                                 `25` | No       | Java version used for decompilation/tooling                                            |
| `hytaleVersion`                | `String`       |                                 none | Usually  | Hytale server version to resolve                                                       |
| `patchline`                    | `String`       |                            `release` | No       | Asset/server patchline                                                                 |
| `oauthBaseUrl`                 | `String`       |                     Hytale OAuth URL | No       | Override auth endpoint                                                                 |
| `accountBaseUrl`               | `String`       |              Hytale account-data URL | No       | Override account endpoint                                                              |
| `manifestGroup`                | `String`       |                      `project.group` | Yes      | Manifest group / namespace                                                             |
| `modId`                        | `String`       |                       `project.name` | Yes      | Manifest mod id                                                                        |
| `modDescription`               | `String`       |                                empty | No       | Manifest description                                                                   |
| `modUrl`                       | `String`       |                                empty | No       | Manifest project URL                                                                   |
| `mainClass`                    | `String`       |                                empty | Usually  | Plugin entrypoint                                                                      |
| `modCredits`                   | `String`       |                                empty | No       | Manifest credits                                                                       |
| `manifestDependencies`         | `String`       |                                empty | No       | Required manifest deps                                                                 |
| `manifestOptionalDependencies` | `String`       |                                empty | No       | Optional manifest deps                                                                 |
| `curseforgeId`                 | `String`       |                                empty | No       | CurseForge project id                                                                  |
| `disabledByDefault`            | `Boolean`      |                              `false` | No       | Manifest flag                                                                          |
| `includesPack`                 | `Boolean`      |                              `false` | No       | Manifest flag                                                                          |
| `manifestFile`                 | `RegularFile`  |   `src/main/resources/manifest.json` | No       | Manifest location                                                                      |
| `runDirectory`                 | `Directory`    |                               `run/` | No       | Local server run dir                                                                   |
| `assetPackSourceDirectory`     | `Directory`    |                 `src/main/resources` | No       | Source asset directory used by `runServer` and `stageAllModAssets`                     |
| `assetPackRunDirectory`        | `Directory`    |        computed under `run/mods/...` | No       | Assets target dir                                                                      |
| `bundleAssetEditorRuntime`     | `Boolean`      |                               `true` | No       | Controls whether AssetBridge is bundled into the final jar                             |
| `serverArgs`                   | `List<String>` | `['--allow-op', '--disable-sentry']` | No       | Additional Hytale server arguments appended after the required `--assets=...` argument |
| `serverJvmArgs`                | `List<String>` |                                 `[]` | No       | Extra JVM arguments for `runServer`                                                    |
| `preRunTask`                   | `String`       |                                empty | No       | Task name to run before `runServer`                                                    |
| `debugEnabled`                 | `Boolean`      |                              `false` | No       | Enables JDWP debugging for `runServer`                                                 |
| `debugPort`                    | `Integer`      |                               `5005` | No       | Debug port used when `debugEnabled` is true                                            |
| `debugSuspend`                 | `Boolean`      |                              `false` | No       | Whether the JVM waits for a debugger before starting                                   |
| `hotSwapEnabled`               | `Boolean`      |                              `false` | No       | Enables hot swap capability detection and runtime setup                                |
| `requireDcevm`                 | `Boolean`      |                              `false` | No       | Fails launch if enhanced class redefinition support is unavailable                     |
| `useHotswapAgent`              | `Boolean`      |                               `true` | No       | Enables bundled HotswapAgent integration when available                                |
| `jbrHome`                      | `String`       |                                empty | No       | Optional path to a JetBrains Runtime installation                                      |

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
| `hytaleJvmDoctor`                | `hytale` | Prints JVM debug / hot swap diagnostics                         | Debugging hot swap setup           |

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
- `hytaleAssets` (Assets.zip for IDE browsing)

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
    id 'com.azuredoom.hytale-tools' version '1.0.19'
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
    
    serverArgs = ['--allow-op', '--disable-sentry']
    serverJvmArgs = ['-Xms1G', '-Xmx2G']
    preRunTask = 'generateDevResources'
    
    debugEnabled = false
    debugPort = 5005
    debugSuspend = false

    hotSwapEnabled = false
    requireDcevm = false
    useHotswapAgent = true

    // Optional
    // jbrHome = '/path/to/jbr'
}
```

### Backing Gradle properties

The plugin also reads these Gradle properties automatically:

- `java_version`
- `hytale_version`
- `hytale_patchline`
- `hytools.hytale.oauth.base`
- `hytools.hytale.accounts.base`
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
- `hytools.debug.port`
- `hytools.debug.suspend`
- `hytools.jbr.home`

The plugin also recognizes these system properties for dev runtime features:

- `debug` → enables debug mode
- `hotswap` → enables hot swap support

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

### `preRunTask` does not run

Verify that:
- the task name matches exactly
- the task is registered in the same project as `runServer`
- `preRunTask` is set to the task name as a string

### Validate your debug / hot swap runtime

Run:

```bash
./gradlew hytaleJvmDoctor
```

Use it to verify:
- which Java executable `runServer` will use
- whether JetBrains Runtime was detected
- whether enhanced class redefinition is supported
- whether bundled HotswapAgent support is available

If hot swap is not working as expected, this should be the first check.

## Notes

- Requires Java 25+
- `release` is the default patchline
- The plugin applies the `java` plugin automatically
- The plugin does not apply the `idea` plugin automatically
- If the `idea` plugin is present, IDEA integration is wired automatically
- You can always run `prepareDecompiledSourcesForIde` directly for source generation
