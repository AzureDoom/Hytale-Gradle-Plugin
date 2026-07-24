# Hytale Gradle Plugin

[![Gradle Plugin](https://img.shields.io/badge/Gradle-Plugin-blue)](https://plugins.gradle.org/plugin/com.azuredoom.hytale-tools)
[![Java](https://img.shields.io/badge/Java-25-orange)]()
[![Hytale](https://img.shields.io/badge/Hytale-Release/Pre-green)]()

A Gradle plugin for Hytale mod development that standardizes project setup, manifest generation,
validation, local server runs, IDE-friendly decompiled source attachment, and optional hosted Hytale Javadoc injection into generated server sources.

---

## Why use this plugin?

This plugin replaces manual setup tasks such as:
- manual manifest management
- manual server setup
- manual dependency decompilation
- manual IDE source attachment setup
- manually cross-referencing hosted Hytale server Javadocs while browsing decompiled sources

---

## Quickstart

Need a new mod project? Use the [template generator](https://template.azuredoom.com/).

This Gradle plugin assumes you already have a project and focuses on Hytale-specific Gradle tasks: manifests, assets, server runs, source attachment, diagnostics, and workspace orchestration.

```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.46'
}

hytaleTools {
    hytaleVersion = '0.+'
    // Optional: override the manifest ServerVersion field.
    // If unset, dynamic hytaleVersion selectors like '0.+'
    // resolve to the concrete server version in manifest.json.
    // manifestServerVersion = '>=0.5.0-pre.9 <0.6.0'
    manifestGroup = 'com.example.mods'
    // Author format: Name, or Name|Email, or Name|Email|Url.
    // Separate multiple authors with commas. Email and Url are optional.
    // Example: 'Alice|alice@example.com|https://example.com,Bob'
    modCredits = 'yourname'
    patchline = 'release'
    modId = 'examplemod'
    mainClass = 'com.example.mods.ExampleMod'
    
    // Optional: declare sub-plugins bundled with this mod (positional style)
    subPlugin('MyFeature', 'com.example.mods.feature.MyFeaturePlugin')
    // Or use the map style to set any manifest field (Description, Authors, Website, etc.)
    // subPlugin([ Name: 'MyFeature', Main: 'com.example.mods.feature.MyFeaturePlugin', Description: 'My feature' ])

    // Optional: declare load ordering
    // loadBefore 'otherpluginname'
    // loadBefore 'anotherplugin', '>=2.0.0'

    // Optional: enabled by default. Injects hosted Hytale API docs into
    // generated Vineflower server sources for easier IDE browsing.
    injectServerJavadocsIntoSources = true
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

- Use JetBrains Runtime (JBR) for enhanced class redefinition support.
- Let the plugin resolve the Java toolchain automatically, or set `jbrHome` if you need a specific JBR installation.
- Optionally configure `hotswapAgentPath` if you want to use an external HotswapAgent jar.

Example with an external HotswapAgent jar:

```gradle
hytaleTools {
    hotSwapEnabled = true
    useHotswapAgent = true
    hotswapAgentPath = '/path/to/hotswap-agent.jar'
}
```

Or from the command line:

```bash
./gradlew runServer -Ddebug=true -Dhotswap=true
```

Verify your JVM and hot swap setup:

```bash
./gradlew hytaleJvmDoctor
```

When launching from IntelliJ using Debug, IntelliJ may provide its own debugger agent. The plugin detects an existing JDWP debugger argument and avoids adding a duplicate one.

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
    id 'com.azuredoom.hytale-workspace' version '1.0.46'
}

// If you are getting an issue with Task 'prepareKotlinBuildScriptModel' not found in project ':modX'. 
// Add this
subprojects {
    tasks.register('prepareKotlinBuildScriptModel') {
        dependsOn(rootProject.tasks.named('prepareKotlinBuildScriptModel'))
    }
}

hytaleWorkspace {
    modProjects = [':modA', ':modB']

    // Optional (recommended)
    hostProject = ':modA'

    // Optional shared defaults propagated to child `hytaleTools` projects
    // when those projects apply the plugin, unless overridden locally
    manifestGroup = 'com.example.mods'
    hytaleVersion = '0.+'
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
    // hytaleVersion, patchline, and manifestGroup are inherited from the workspace
    // Override workspace default
    manifestGroup = 'com.example.custom'
    
    // Author format: Name, or Name|Email, or Name|Email|Url.
    // Separate multiple authors with commas. Email and Url are optional.
    // Example: 'Alice|alice@example.com|https://example.com,Bob'
    modCredits = 'yourname'
    modId = 'moda'
    mainClass = 'com.example.mods.moda.ModA'
}
```

Repeat for additional mod projects (e.g. `modB`).

**Note for Kotlin users, please ensure you are also adding:
```gradle
repositories {
    mavenCentral()
}
```
to any build.gradle that is calling the kotlin plugin.

---

## How It Works

- The **workspace plugin** (`hytale-workspace`) only applies to the root project.
- Each **mod project** applies `hytale-tools` independently.
- Non-mod projects (for example, `common`) remain standard Gradle modules.
- Workspace tasks operate only on projects listed in `modProjects`. When `modProjects` is omitted, the workspace discovers subprojects that apply `com.azuredoom.hytale-tools`.
- The workspace plugin does **not** automatically apply the mod plugin.
- `stageAllModAssets` depends on each mod project's `classes` and `validateManifest` tasks.
- Each workspace mod is staged as a directory under `run/mods/<manifest_group>_<mod_id>/`. Its resources and compiled class outputs are linked into that directory, with a file-copy fallback when links are unavailable.
- `runAllMods` launches one server using those staged plugin directories plus a shared runtime classpath for `vineImplementation` dependencies.
- Workspace plugin identifiers must be unique by `manifestGroup:modId`.

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

If `hytaleHomeOverride` is needed for `runAllMods`, configure it on the selected host
project's `hytaleTools` block. The workspace plugin uses the host project's resolved
assets path for the shared server launch.

```gradle
project(':modA') {
    hytaleTools {
        hytaleHomeOverride = "/path/to/Hytale/install/release/package/game/latest/Assets.zip"
    }
}
```

---

## Workspace Runtime Notes

- All workspace mod projects must use the same configured `hytaleVersion` and `patchline` after workspace defaults and local overrides are applied.
- `runAllMods` fails early when versions or patchlines differ, a workspace plugin identifier is duplicated, a resource directory is missing, or no compiled class output exists.
- Workspace mods are staged into `run/mods/` as development directories containing linked resources and compiled classes; they are not built and copied as project jars for the development run.
- Symbolic links are preferred for individual files. On Windows, the plugin also attempts hard links before falling back to copying.
- A copy fallback disables live updates for the copied file until the staging task runs again.
- `common` should not apply the plugin unless it is itself a loadable Hytale plugin.
- Stale entries created by previous staging runs are tracked with internal index files and removed automatically.

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

Generate recommended VS Code project files:

```bash
./gradlew configureVSCodeHytaleRun
```

This creates:

- `.vscode/extensions.json`
- `.vscode/settings.json`
- `.vscode/launch.json`
- `.vscode/tasks.json` with common Hytale Gradle tasks

For source attachment, run:

```bash
./gradlew prepareDecompiledSourcesForIde
```

To debug the local Hytale server use the generated launch configuration `Hytale: runServer`, which launches and sets the server to debug mode.

### Optional Dev Container

Generate an optional Dev Container:

```bash
./gradlew generateHytaleDevContainer
```

This creates `.devcontainer/devcontainer.json` and `.devcontainer/Dockerfile`.

The Dev Container is optional and mainly useful for contributors who want a reproducible Java/Gradle environment.

## Dependency Flow

The plugin separates compile-time visibility, runtime class loading, plugin discovery, and IDE source generation:

```text
vineServerJar ────────────────> compileOnly / compileClasspath
vineCompileOnly ──────────────> compileOnly / compileClasspath
vineImplementation ───────────> implementation / runtimeClasspath
requiredDependency ───────────> implementation / runtimeClasspath
optionalDependency ───────────> compileOnly / compileClasspath
vineDecompileTargets ─────────> IDE decompilation targets

vineMod ──────────────────────> run/mods as a complete plugin jar

vineImplementation plugin jar
  (contains manifest.json) ───> run/mods as a complete plugin jar
                            └─> exploded shared runtime classpath
                                (manifest.json omitted)

vineImplementation library jar
  (no manifest.json) ─────────> exploded shared runtime classpath only
```

At a high level:

- `vineServerJar` provides the Hytale server API and server runtime.
- `vineCompileOnly` is for dependencies needed to compile but not needed for the local runtime.
- `vineImplementation` is for dependencies needed at compile time and during local server runs.
- `requiredDependency` is a convenience alias for a required dependency: it is available through `implementation`, participates in dependency decompilation/source attachment, and should correspond to an entry in `manifestDependencies`.
- `optionalDependency` is a convenience alias for an optional integration: it is available through `compileOnly`, participates in dependency decompilation/source attachment, and should correspond to an entry in `manifestOptionalDependencies`.
- A `vineImplementation` jar containing a root-level `manifest.json` is treated as a Hytale plugin: the complete jar is copied into `run/mods`, and its contents are also expanded into an isolated runtime-classpath directory so its classes are visible to the shared JVM classloader.
- A `vineImplementation` jar without `manifest.json` is treated as a normal library and is expanded onto the shared runtime classpath without being placed in `run/mods`.
- `vineMod` is for a runtime plugin that should be copied into `run/mods` as a jar but should not be added to the shared application classpath.
- `vineDecompileTargets` controls additional dependencies that receive generated source attachment.
- Hytale `Assets.zip` can be exposed separately for IDE asset browsing.

### Assets in IDEs

The plugin packages the resolved Hytale `Assets.zip` into a dedicated `hytale-assets` binary jar and installs it into the local generated repos alongside the server library.

This means:

* assets appear as a **separate `hytale-assets` entry** under External Libraries in IntelliJ
* you can browse game assets independently of server classes in the library tree
* the server library remains clean — no asset entries embedded in it
* assets are not included in your final mod jar

If `generateAssetsBinary = false`, the plugin still resolves `Assets.zip` for local runs,
but skips generating the large `hytale-assets` IDE binary jar.

## Cleaning Generated Files and Caches

The plugin provides cleanup tasks for generated Hytale development outputs and caches.

```bash
./gradlew cleanHytaleGenerated
./gradlew cleanHytaleAssetsCache
./gradlew cleanHytaleGlobalCache
```

Use `cleanHytaleGenerated` when you want to remove project-local generated output, such as generated source jars and generated local source repositories.

Use `cleanHytaleAssetsCache` when you want to remove the cached Hytale `Assets.zip` / generated assets cache under the Gradle user home. This is useful when assets look stale, or you need the plugin to resolve them again.

Use `cleanHytaleGlobalCache` only when you want to clear global Hytale caches under the Gradle user home, such as decompiled source and injected Javadoc caches. Because this can affect other Hytale projects on the same machine, the task requires confirmation.

Interactive confirmation:

```bash
./gradlew cleanHytaleGlobalCache
```

When prompted, type:

```text
clean global hytale cache
```

For non-interactive environments, pass the confirmation property explicitly:

```bash
./gradlew cleanHytaleGlobalCache -PconfirmCleanHytaleGlobalCache=true
```

Generated VS Code tasks from `configureVSCodeHytaleRun` include these cleanup commands. The global cache cleanup task asks for confirmation before running.

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

**Globally cached (Gradle user home):**
- `DecompileServerJarTask` — keyed by patchline + server version under `~/.gradle/caches/hytale-decompiled/`
- `DecompileDependencyJarTask` — keyed by Maven coordinates under `~/.gradle/caches/hytale-decompiled/dependencies/`
- `GenerateAssetsBinaryTask` — keyed by patchline + server version under `~/.gradle/caches/hytale-assets/`
- `InjectServerJavadocsIntoDecompiledSourcesTask` — stamp keyed by patchline + server version under `~/.gradle/caches/hytale-javadocs/`

These tasks run once per version across all projects sharing the same Gradle user home. Subsequent projects skip the work entirely via `onlyIf` guards — no Vineflower run, no 3 GiB zip repack.

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
- Supports debug mode, IntelliJ-friendly JDWP handling, enhanced hot swap setup, and optional external HotswapAgent integration
- Generates decompiled sources and attaches them in IDEs
- Injects hosted Hytale Server API Javadocs into generated Vineflower server sources by default
- Adds external Hytale Server Javadoc links to generated Gradle Javadoc output and IDEA metadata when the `idea` plugin is present

## Included Tasks

### `createManifestIfMissing`

Creates `src/main/resources/manifest.json` with a default structure if it does not already exist.

This task is safe to run repeatedly and will not overwrite an existing manifest.

### `updatePluginManifest`

Updates (or rewrites) `src/main/resources/manifest.json` from Gradle configuration.

### `downloadAssetsZip`

Resolves the Hytale `Assets.zip` used by local development.

By default, this task authenticates with Hytale device auth, downloads the asset wrapper,
extracts `Assets.zip`, and caches the extracted zip under Gradle user home.

If `hytaleHomeOverride` is configured, the plugin resolves an existing local `Assets.zip`
from that path and uses it directly. In this mode, the assets zip is not copied into the
Gradle cache.

You can point `hytaleHomeOverride` at either:
- a direct `Assets.zip` file
- a directory containing `Assets.zip`
- a Hytale launcher/install directory that contains the configured patchline assets

```gradle
hytaleTools {
    hytaleHomeOverride = "/path/to/Hytale/install/release/package/game/latest/Assets.zip"
}
```
or
```gradle
hytaleTools {
    hytaleHomeOverride = "/path/to/Hytale/install/release/package/game/latest/"
}
```

If no override is configured and remote download fails, the task attempts to locate a local
Hytale installation as a fallback and caches the found `Assets.zip`.

### `runServer`

Launches a local Hytale server using:

- the current project's compiled class directories and resource source directories directly
- the project's runtime classpath, excluding the current project output and raw `vineImplementation` jars
- staged/exploded `vineImplementation` runtime-classpath directories
- complete `vineMod` jars in `run/mods`
- complete `vineImplementation` plugin jars in `run/mods`
- the resolved Hytale server jar

Before launch, `prepareRunServer` validates the manifest, stages dependencies, and links the configured asset-pack source directory into the run directory.

### Customizing `runServer`

You can customize the server launch arguments, JVM arguments, debug mode, and hot swap behavior through the extension:

```gradle
hytaleTools {
    hytaleVersion = '0.+'

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
    
    // Optional external HotswapAgent jar.
    // If unset, the plugin will try bundled JBR HotswapAgent support when available.
    hotswapAgentPath = ''
    
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

When `hotSwapEnabled` is true, the plugin checks the selected JVM for enhanced class redefinition support. If enhanced redefinition is available, it adds `-XX:+AllowEnhancedClassRedefinition`. If `useHotswapAgent` is also true, the plugin either uses the configured external `hotswapAgentPath` jar or falls back to bundled JBR HotswapAgent support when available.

For best results, use JetBrains Runtime with hot swap enabled.

Notes:
- `serverArgs` are appended to the default `--assets=...` argument automatically added by the plugin
- `serverJvmArgs` are added in addition to the plugin’s default JVM launch settings
- `preRunTask` lets you run a custom preparation task before `runServer`
- `debugEnabled` enables JDWP debugging for IDE attach
- `hotSwapEnabled` enables runtime probing for enhanced class redefinition support
- `requireDcevm` fails early if enhanced class redefinition is not available
- `useHotswapAgent` enables HotswapAgent integration when hot swap is enabled
- `hotswapAgentPath` points to an external HotswapAgent jar; when set, it is used as `-javaagent:<path>=autoHotswap=true`
- If `hotswapAgentPath` is empty, the plugin attempts to use bundled JBR HotswapAgent support when available
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

Decompiles the server jar and all dependencies declared in `vineImplementation`, `vineCompileOnly`, `requiredDependency`, `optionalDependency`, or `vineDecompileTargets` into `build/generated-sources-m2` and `build/generated-sources-ivy`.

This is useful for IDE source attachment. For the Hytale server jar, the plugin also injects hosted Hytale Server API Javadocs into the generated Vineflower sources by default, so documentation can appear directly in IDE source views instead of only through external Javadoc lookup.

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
3. `runServer` prepares the run directory, downloads assets if needed, stages plugin dependencies, expands runtime libraries/classes, links the current project's resources, and launches the server.

Because manifest generation and validation are wired into the build, most projects do not need to invoke those tasks manually.

## Extension Reference

| Property                          | Type           |                               Default | Required | Purpose                                                                                                                                                                                                               |
|-----------------------------------|----------------|--------------------------------------:|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `javaVersion`                     | `Integer`      |                                  `25` | No       | Java version used for decompilation/tooling                                                                                                                                                                           |
| `hytaleVersion`                   | `String`       |                                 '0.+' | Yes      | Hytale server version to resolve. Accepts dynamic selectors (e.g. `0.+`)                                                                                                                                              |
| `manifestServerVersion`           | `String`       | resolved `>=hytaleVersion` when unset | No       | Optional manifest `ServerVersion` override. Supports Hytale semver ranges such as `>=0.5.0-pre.9 <0.6.0`                                                                                                              |
| `patchline`                       | `String`       |                             `release` | No       | Asset/server patchline                                                                                                                                                                                                |
| `serverJavadocsUrl`               | `String`       |        computed from active patchline | No       | Hosted Hytale Server API Javadocs URL used for Gradle Javadocs, IDEA metadata, and source injection                                                                                                                   |
| `injectServerJavadocsIntoSources` | `Boolean`      |                                `true` | No       | Injects hosted Hytale API docs into generated Vineflower server sources for IDE browsing                                                                                                                              |
| `oauthBaseUrl`                    | `String`       |                      Hytale OAuth URL | No       | Override auth endpoint                                                                                                                                                                                                |
| `accountBaseUrl`                  | `String`       |               Hytale account-data URL | No       | Override account endpoint                                                                                                                                                                                             |
| `hytaleHomeOverride`              | `String`       |                                 empty | No       | Optional path to a local Hytale installation, a directory containing `Assets.zip`, or an `Assets.zip` file. When set, local dev tasks use this assets zip directly instead of requiring the Gradle cached assets zip. |
| `generateAssetsBinary`            | `Boolean`      |                                `true` | No       | Controls whether `prepareDecompiledSourcesForIde` / `idea` generate the large `hytale-assets.jar` IDE binary from `Assets.zip`. Disable to avoid creating the extra assets jar.                                       |
| `manifestGroup`                   | `String`       |                       `project.group` | Yes      | Manifest group / namespace                                                                                                                                                                                            |
| `modId`                           | `String`       |                        `project.name` | Yes      | Manifest mod id                                                                                                                                                                                                       |
| `modDescription`                  | `String`       |                                 empty | No       | Manifest description                                                                                                                                                                                                  |
| `modUrl`                          | `String`       |                                 empty | No       | Manifest project URL                                                                                                                                                                                                  |
| `mainClass`                       | `String`       |                                 empty | Yes      | Plugin entrypoint                                                                                                                                                                                                     |
| `modCredits`                      | `String`       |                        `'replace_me'` | No       | Manifest authors/credits. Entries use `Name`, `Name\|Email`, or `Name\|Email\|Url`; separate authors with commas.                                                                                                     |
| `manifestDependencies`            | `String`       |                `Hytale:AssetModule=*` | No       | Required manifest deps                                                                                                                                                                                                |
| `manifestOptionalDependencies`    | `String`       |                                 empty | No       | Optional manifest deps                                                                                                                                                                                                |
| `curseforgeId`                    | `String`       |                                 empty | No       | CurseForge project id                                                                                                                                                                                                 |
| `disabledByDefault`               | `Boolean`      |                               `false` | No       | Manifest flag                                                                                                                                                                                                         |
| `includesPack`                    | `Boolean`      |                               `false` | No       | Manifest flag                                                                                                                                                                                                         |
| `manifestFile`                    | `RegularFile`  |    `src/main/resources/manifest.json` | No       | Manifest location                                                                                                                                                                                                     |
| `runDirectory`                    | `Directory`    |                                `run/` | No       | Local server run dir                                                                                                                                                                                                  |
| `assetPackSourceDirectory`        | `Directory`    |                  `src/main/resources` | No       | Source asset directory used by `runServer` and `stageAllModAssets`                                                                                                                                                    |
| `assetPackRunDirectory`           | `Directory`    |         computed under `run/mods/...` | No       | Assets target dir                                                                                                                                                                                                     |
| `bundleAssetEditorRuntime`        | `Boolean`      |                                `true` | No       | Controls whether AssetBridge is bundled into the final jar                                                                                                                                                            |
| `serverArgs`                      | `List<String>` |  `['--allow-op', '--disable-sentry']` | No       | Additional Hytale server arguments appended after the required `--assets=...` argument                                                                                                                                |
| `serverJvmArgs`                   | `List<String>` |                                  `[]` | No       | Extra JVM arguments for `runServer`                                                                                                                                                                                   |
| `preRunTask`                      | `String`       |                                 empty | No       | Task name to run before `runServer`                                                                                                                                                                                   |
| `debugEnabled`                    | `Boolean`      |                               `false` | No       | Enables JDWP debugging for `runServer`                                                                                                                                                                                |
| `debugPort`                       | `Integer`      |                                `5005` | No       | Debug port used when `debugEnabled` is true                                                                                                                                                                           |
| `debugSuspend`                    | `Boolean`      |                               `false` | No       | Whether the JVM waits for a debugger before starting                                                                                                                                                                  |
| `hotSwapEnabled`                  | `Boolean`      |                               `false` | No       | Enables hot swap capability detection and runtime setup                                                                                                                                                               |
| `requireDcevm`                    | `Boolean`      |                               `false` | No       | Fails launch if enhanced class redefinition support is unavailable                                                                                                                                                    |
| `useHotswapAgent`                 | `Boolean`      |                                `true` | No       | Enables bundled HotswapAgent integration when available                                                                                                                                                               |
| `hotswapAgentPath`                | `String`       |                                 empty | No       | Optional path to an external HotswapAgent jar used when `hotSwapEnabled` and `useHotswapAgent` are true                                                                                                               |
| `jbrHome`                         | `String`       |                                 empty | No       | Optional path to a JetBrains Runtime installation                                                                                                                                                                     |
| `loadBefore`                      | `List<Map>`    |                                  `[]` | No       | Plugins this mod must be loaded before (see [Load Ordering](#load-ordering))                                                                                                                                          |
| `subPlugins`                      | `List<Map>`    |                                  `[]` | No       | Sub-plugins bundled with this mod (see [SubPlugins](#subplugins))                                                                                                                                                     |
| `disableHytaleModsInfoMaven`      | `Boolean`      |                               `false` | No       | Disables the `Hytale-Mods.info Maven` repository (see [Repositories](#repositories))                                                                                                                                  |
| `disablePlaceholderApiMaven`      | `Boolean`      |                               `false` | No       | Disables the `PlaceholderAPI` repository (see [Repositories](#repositories))                                                                                                                                          |
| `disableCurseMaven`               | `Boolean`      |                               `false` | No       | Disables the `CurseMaven` repository (see [Repositories](#repositories))                                                                                                                                              |
| `disableAzureDoomMaven`           | `Boolean`      |                               `false` | No       | Disables the `AzureDoom Maven` repository (see [Repositories](#repositories))                                                                                                                                         |
| `disableHytaleModdingMaven`       | `Boolean`      |                               `false` | No       | Disables the `Hytale Modding Maven` repository (see [Repositories](#repositories))                                                                                                                                    |
| `disableModtaleResolver`          | `Boolean`      |                               `false` | No       | Disables the Modtale API-backed resolver and its `modtale` alias substitution (see [Repositories](#repositories))                                                                                                     |
| `disableModifoldRepo`             | `Boolean`      |                               `false` | No       | Disables the Modifold repository and its `modifold` alias substitution (see [Repositories](#repositories))                                                                                                            |

### Author / Credits Formatting

`modCredits` is written to the manifest `Authors` array. Each author supports a required `Name` and optional `Email` and `Url` fields.

Use one of these formats for each author:

- `Name`
- `Name|Email`
- `Name|Email|Url`

Separate multiple authors with commas.

```gradle
hytaleTools {
    modCredits = 'Alice|alice@example.com|https://example.com,Bob,Charlie|charlie@example.com'
}
```

This writes:

```json
{
  "Authors": [
    {
      "Name": "Alice",
      "Email": "alice@example.com",
      "Url": "https://example.com"
    },
    {
      "Name": "Bob"
    },
    {
      "Name": "Charlie",
      "Email": "charlie@example.com"
    }
  ]
}
```

`Email` and `Url` are optional. `Authors` is also optional during validation for compatibility with handwritten or older manifests, but when it is present it must be an array of author objects. Each author object must have a non-blank `Name`.

Do not use commas or pipe characters inside an individual author value, because commas separate authors and pipes separate author fields.

## Load Ordering

The plugin supports declaring load-ordering constraints via `loadBefore`. When set, Hytale will ensure this plugin is loaded before the named plugin(s).

### Main manifest

Use the `loadBefore` DSL method in your `hytaleTools` block:

```gradle
hytaleTools {
    modId = 'mymod'
    mainClass = 'com.example.mods.MyMod'

    // Load before any version of another plugin
    loadBefore 'otherpluginname'

    // Load before a specific version range
    loadBefore 'anotherplugin', '>=2.0.0'
}
```

This writes the following into `manifest.json`:

```json
{
  "LoadBefore": [
    {
      "name": "otherpluginname",
      "version": "*"
    },
    {
      "name": "anotherplugin",
      "version": ">=2.0.0"
    }
  ]
}
```

If no `loadBefore` entries are declared, the `LoadBefore` key is omitted from the manifest entirely.

### `loadBefore` parameters

| Parameter | Type     | Default | Description                                                              |
|-----------|----------|---------|--------------------------------------------------------------------------|
| `name`    | `String` | —       | The name of the plugin this plugin must be loaded before                 |
| `version` | `String` | `*`     | Optional version constraint. Defaults to `*` (any version) when omitted  |

### Sub-plugins

Load ordering can also be declared per sub-plugin by including a `LoadBefore` list in the sub-plugin map:

```gradle
subPlugin([
    Name       : 'Economy',
    Main       : 'com.example.mods.economy.EconomyPlugin',
    LoadBefore : [
        [ name: 'otherpluginname', version: '*' ],
        [ name: 'anotherplugin',   version: '>=2.0.0' ]
    ]
])
```

Each entry follows the same `name` / `version` format as the main manifest. Version defaults to `*` when omitted.

---

## SubPlugins

The plugin supports declaring sub-plugins that are bundled within your main mod. Sub-plugins appear in `manifest.json` as a `SubPlugins` array and are loaded by the server alongside the parent mod.

Use the `subPlugin(...)` DSL method in your `hytaleTools` block. Two call styles are supported:

### Positional style

Quick shorthand for the most common fields:

```gradle
hytaleTools {
    modId = 'mymod'
    mainClass = 'com.example.mods.MyMod'

    subPlugin('Forestry', 'com.example.mods.forestry.ForestryPlugin')
    subPlugin('Economy', 'com.example.mods.economy.EconomyPlugin', true, false)
    // Pin a specific server version for this sub-plugin instead of inheriting the parent's:
    subPlugin('Legacy', 'com.example.mods.legacy.LegacyPlugin', false, false, '>=0.5.2')
}
```

#### Positional parameters

| Parameter           | Type      | Default | Description                                                                                       |
|---------------------|-----------|---------|---------------------------------------------------------------------------------------------------|
| `name`              | `String`  | —       | Sub-plugin name (maps to `Name` in the manifest)                                                  |
| `main`              | `String`  | —       | Fully-qualified entry point class (maps to `Main`)                                                |
| `disabledByDefault` | `boolean` | `false` | Whether this sub-plugin is disabled by default                                                    |
| `includesAssetPack` | `boolean` | `false` | Whether this sub-plugin includes an asset pack                                                    |
| `serverVersion`     | `String`  | `null`  | Optional server version override. When omitted, the parent mod's manifest `ServerVersion` is used |

### Map style

Pass a map of manifest fields directly. This supports all properties that Hytale accepts in a sub-plugin entry — `Name`, `Main`, `Description`, `Authors`, `Website`, `ServerVersion`, `DisabledByDefault`, `IncludesAssetPack`, `Dependencies`, `OptionalDependencies`, and any future fields the server may support. `Name` and `Main` are required; all others are optional.

```gradle
hytaleTools {
    modId = 'mymod'
    mainClass = 'com.example.mods.MyMod'

    subPlugin([
        Name              : 'Forestry',
        Main              : 'com.example.mods.forestry.ForestryPlugin',
        Description       : 'Adds forestry mechanics to the server',
        Authors           : [
            [ Name: 'Alice', Email: 'alice@example.com', Url: 'https://alice.example.com' ],
            [ Name: 'Bob',   Email: 'bob@example.com' ]
        ],
        Website           : 'https://example.com/forestry',
        DisabledByDefault : false
    ])
    
    subPlugin([
        Name              : 'Economy',
        Main              : 'com.example.mods.economy.EconomyPlugin',
        Description       : 'Optional economy system — enable per server as needed',
        Authors           : [
            [ Name: 'Charlie', Email: 'charlie@example.com', Url: 'https://charlie.example.com' ]
        ],
        DisabledByDefault : true
    ])
}
```

Any fields not supplied in the map are given safe defaults when written to the manifest (`ServerVersion` inherits from the parent, `DisabledByDefault` and `IncludesAssetPack` default to `false`).

### Manifest output

Each declared sub-plugin is written into `manifest.json`. The parent manifest `ServerVersion` is applied automatically to any sub-plugin that does not specify its own:

```json
{
  "SubPlugins": [
    {
      "Name": "Forestry",
      "Main": "com.example.mods.forestry.ForestryPlugin",
      "Description": "Adds forestry mechanics to the server",
      "Authors": [{ "Name": "Alice" }],
      "Website": "https://example.com/forestry",
      "ServerVersion": ">=0.5.2",
      "DisabledByDefault": false,
      "IncludesAssetPack": false
    },
    {
      "Name": "Economy",
      "Main": "com.example.mods.economy.EconomyPlugin",
      "Description": "Optional economy system — enable per server as needed",
      "ServerVersion": ">=0.5.2",
      "DisabledByDefault": true,
      "IncludesAssetPack": false
    }
  ]
}
```

If no sub-plugins are declared, the `SubPlugins` key is omitted from the manifest entirely.

## Task Reference

| Task                                        | Group    | Purpose                                                              | Typical Use                        |
|---------------------------------------------|----------|----------------------------------------------------------------------|------------------------------------|
| `createManifestIfMissing`                   | `hytale` | Creates a starter manifest if missing                                | First setup                        |
| `updatePluginManifest`                      | `hytale` | Rewrites manifest from Gradle config                                 | Normal dev/build                   |
| `updateAllPluginManifests`                  | `hytale` | Rewrites manifests for all Hytale subprojects                        | Multi-project manifest sync        |
| `downloadAssetsZip`                         | `hytale` | Authenticates and fetches assets                                     | Before first run / troubleshooting |
| `hytaleDoctor`                              | `hytale` | Prints plugin, manifest, asset, and dependency diagnostics           | Troubleshooting                    |
| `runServer`                                 | `hytale` | Launches local Hytale server                                         | Single-project dev loop            |
| `runAllMods`                                | `hytale` | Launches one shared server with all mod subprojects                  | Multi-project dev loop             |
| `stageAllModAssets`                         | `hytale` | Stages workspace resources/classes and external runtime dependencies | Multi-project run preparation      |
| `prepareDecompiledSourcesForIde`            | `hytale` | Generates source jars for IDE attachment                             | IDE setup                          |
| `validateManifest`                          | internal | Verifies generated manifest values                                   | Runs automatically                 |
| `validateAllManifests`                      | `hytale` | Verifies manifests for all Hytale subprojects                        | Multi-project validation           |
| `prepareRunServer`                          | internal | Stages dependencies and links the project into the run directory     | Runs automatically                 |
| `decompileServerJar`                        | internal | Decompiles Hytale server sources                                     | Internal source pipeline           |
| `injectServerJavadocsIntoDecompiledSources` | internal | Injects hosted Hytale API docs into decompiled server sources        | Internal source pipeline           |
| `setupHytaleDev`                            | `hytale` | Prepares IDE sources and downloads assets                            | First-time setup                   |
| `cleanHytaleGenerated`                      | `hytale` | Removes project-local Hytale generated outputs                       | Regenerate IDE/source artifacts    |
| `cleanHytaleAssetsCache`                    | `hytale` | Removes cached Hytale assets / generated assets cache                | Refresh stale assets               |
| `cleanHytaleGlobalCache`                    | `hytale` | Removes global Hytale decompile and Javadoc caches                   | Force full global regeneration     |
| `hytaleJvmDoctor`                           | `hytale` | Prints JVM debug / hot swap diagnostics                              | Debugging hot swap setup           |

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

### Hosted Hytale Javadocs in generated sources

The Hytale server jar does not contain source-level Javadocs, so Vineflower cannot recover comments from bytecode by itself. To make API documentation visible while browsing generated sources, the plugin fetches the hosted Hytale Server API Javadocs and injects matching class, constructor, and method documentation into the generated server source jar.

By default, the Javadocs URL is selected from the configured `patchline`:

| Patchline                    | Default Javadocs URL                         |
|------------------------------|----------------------------------------------|
| `release`                    | `https://release.server.docs.hytale.com/`    |
| `pre-release` / `prerelease` | `https://prerelease.server.docs.hytale.com/` |

You can override the URL or disable source injection:

```gradle
hytaleTools {
    // Optional override; normally computed from patchline
    serverJavadocsUrl = 'https://release.server.docs.hytale.com/'

    // Enabled by default
    injectServerJavadocsIntoSources = true
}
```

The injector caches downloaded pages and missing pages under Gradle user home:

```text
~/.gradle/caches/hytale-javadocs/<patchline>/
```

The first run may take longer while pages are fetched. Later runs are faster because successful pages and missing-page lookups are cached. If hosted docs change, and you need a fresh injection pass, delete the relevant cache directory and rerun:

```bash
rm -rf ~/.gradle/caches/hytale-javadocs/release
./gradlew prepareDecompiledSourcesForIde --rerun-tasks
```

The plugin also adds the hosted Javadocs URL to Gradle `javadoc` tasks. If the `idea` plugin is applied, the generated IDEA metadata also receives the external Javadocs URL, but the plugin does not apply `idea` automatically.

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
- Modtale local Maven cache/resolver (exclusive content for group `modtale`, populated through the Modtale API)
- Modifold (exclusive Ivy content for group `modifold`)

You do not need to declare them manually.

> **Note on patchlines:**
> Both Hytale server repos are registered, but `com.hypixel.hytale` artifacts are only served by the repo matching the configured `patchline`. The other repo is restricted with `excludeGroup('com.hypixel.hytale')` so dynamic selectors like `0.+` never cross patchlines. Other artifacts that happen to live in either repo are unaffected.

### Disabling a Repository

If one of the optional mavens is temporarily unreachable and is blocking dependency resolution, you can disable it without waiting for a plugin update:

```gradle
hytaleTools {
    disableCurseMaven = true
}
```

Each optional repository has a matching toggle:

| Repository               | Extension property           | Gradle property                            |
|--------------------------|------------------------------|--------------------------------------------|
| `Hytale-Mods.info Maven` | `disableHytaleModsInfoMaven` | `hytools.repos.disableHytaleModsInfoMaven` |
| `PlaceholderAPI`         | `disablePlaceholderApiMaven` | `hytools.repos.disablePlaceholderApiMaven` |
| `CurseMaven`             | `disableCurseMaven`          | `hytools.repos.disableCurseMaven`          |
| `AzureDoom Maven`        | `disableAzureDoomMaven`      | `hytools.repos.disableAzureDoomMaven`      |
| `Hytale Modding Maven`   | `disableHytaleModdingMaven`  | `hytools.repos.disableHytaleModdingMaven`  |
| Modtale resolver         | `disableModtaleResolver`     | `hytools.repos.disableModtaleResolver`     |
| Modifold                 | `disableModifoldRepo`        | `hytools.repos.disableModifoldRepo`        |

Disabling `disableModtaleResolver` or `disableModifoldRepo` also disables that group's alias substitution (e.g. `modtale:Name_ProjectID:Version` resolution), since the substitution has nothing to resolve against once its repository is removed.

The Gradle property form is useful for a global override (e.g. in `gradle.properties` or CI) without editing every mod project's `build.gradle`:

```properties
hytools.repos.disableCurseMaven=true
```

`Maven Central`, `Hytale Server Release`, and `Hytale Server Pre-Release` are always added and cannot be disabled, since core plugin functionality depends on them.

## Configurations

The plugin creates Hytale-facing dependency configurations together with internal resolvable configurations used for staging and source generation.

### Configuration Overview

| Configuration          | Purpose                                                                                                                               |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `vineServerJar`        | Hytale server binary; auto-injected unless you declare one explicitly                                                                 |
| `vineImplementation`   | Compile/runtime dependency. Plugins are staged into `run/mods` and the shared classpath; ordinary libraries are shared-classpath only |
| `vineCompileOnly`      | Compile-time dependency that is not staged for local runtime                                                                          |
| `requiredDependency`   | Alias for a required compile/runtime dependency; extends `implementation` and participates in decompilation                           |
| `optionalDependency`   | Alias for an optional compile-time integration; extends `compileOnly` and participates in decompilation                               |
| `vineMod`              | Runtime Hytale plugin copied into `run/mods` as a complete jar, without adding it to the shared classpath                             |
| `vineDecompileTargets` | Extra dependencies to decompile for IDE source attachment                                                                             |
| `hytaleBundledRuntime` | Runtime dependency automatically added and optionally bundled into the final mod jar                                                  |

Internal configurations such as `vineImplementationJars`, `vineModJars`, `vineDependencyJars`, `vineDecompileClasspath`, and `vineflowerTool` support resolution, staging, and decompilation. Projects normally declare dependencies using the public configurations above.

`requiredDependency` and `optionalDependency` are dependency-bucket aliases. They do not automatically edit `manifest.json`; you must still declare the matching plugin identifiers in `manifestDependencies` or `manifestOptionalDependencies`.

`compileOnly` automatically includes:

- `vineCompileOnly`
- `vineServerJar`
- `hytaleAssets` for IDE asset browsing

`implementation` includes `vineImplementation`, `requiredDependency`, and `hytaleBundledRuntime`.

`compileOnly` includes `vineCompileOnly`, `optionalDependency`, `vineServerJar`, and `hytaleAssets`.

Both aliases also feed the decompilation/source-attachment configurations.

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
    id 'com.azuredoom.hytale-tools' version '1.0.46'
}
```

## Dependencies

Use the Hytale-specific configurations for plugin and library dependencies:

```gradle
dependencies {
    // Required by your code and by the local development server.
    // If the jar contains manifest.json, it is treated as a Hytale plugin.
    // Otherwise, it is treated as a normal runtime library.
    vineImplementation 'com.example:some-runtime-plugin-or-library:1.0.0'

    // Runtime plugin only: copied to run/mods as a complete jar.
    // Its classes are not added to the shared launch classpath.
    vineMod 'com.example:some-isolated-plugin:1.0.0'

    // Required dependency alias: available to compile and run locally.
    // Also add its plugin identifier to manifestDependencies.
    requiredDependency 'com.example:required-plugin:1.0.0'

    // Optional dependency alias: compile-time only for optional integrations.
    // Also add its plugin identifier to manifestOptionalDependencies.
    optionalDependency 'com.example:optional-plugin:1.0.0'

    // Compile-time only: visible to your code, but not staged for a local run.
    vineCompileOnly 'curse.maven:hexcodes-1448311:8166165'

    // Optional IDE source-attachment target.
    vineDecompileTargets 'curse.maven:hexcodes-1448311:8166165'

    // Modtale: the text before `_` is an optional readable alias.
    // The resolver strips the alias and downloads ProjectID:Version through the Modtale API.
    vineImplementation 'modtale:LevelingCore_ProjectID:Version'

    // Modifold: the text before `_` is an optional readable alias.
    vineImplementation 'modifold:LevelingCore_projectSlug:versionID'
}
```

### Required and optional dependency aliases

The plugin provides two convenience configurations for expressing dependency intent in Gradle:

```gradle
dependencies {
    requiredDependency 'com.example:economy-api:1.2.0'
    optionalDependency 'com.example:placeholder-api:2.0.0'
}

hytaleTools {
    manifestDependencies = 'Example:Economy=*'
    manifestOptionalDependencies = 'Example:PlaceholderAPI=*'
}
```

`requiredDependency`:

- extends `implementation`
- is available while compiling and running the project
- participates in dependency decompilation and IDE source attachment
- represents a dependency your plugin cannot operate without

`optionalDependency`:

- extends `compileOnly`
- is available while compiling optional integration code
- is not automatically placed on the runtime classpath by this alias
- participates in dependency decompilation and IDE source attachment
- represents an integration your plugin can operate without

These aliases describe Gradle classpath behavior only. They do not infer Hytale manifest identifiers from Maven coordinates and do not automatically write `Dependencies` or `OptionalDependencies` into `manifest.json`.

### Choosing between `vineImplementation` and `vineMod`

Use `vineImplementation` when your own code directly references classes from the dependency or when the dependency is a normal library. During development, the plugin determines the jar type by checking for a root-level `manifest.json`:

- **Plugin jar:** copied intact to `run/mods` for Hytale plugin discovery and expanded into its own shared-runtime directory, excluding the plugin manifest from that expanded copy.
- **Library jar:** expanded into its own shared-runtime directory only.
- Signature files under `META-INF` (`.SF`, `.RSA`, and `.DSA`) are omitted from expanded copies.

Use `vineMod` when the dependency only needs to be discovered and loaded by Hytale as a plugin and your project does not need its classes on the shared JVM classpath.

Do not declare the same plugin through both configurations. The workspace staging task also rejects different dependencies that resolve to the same filename, because only one file can occupy that path in `run/mods`.

`vineCompileOnly`, `requiredDependency`, and `optionalDependency` are usually preferred over plain Gradle dependency buckets for Hytale plugin integrations because they participate in the plugin's decompilation and source-attachment pipeline.

## Runtime Dependency Loading

The local run tasks intentionally avoid placing the original `vineImplementation` jars directly on the Java launch classpath. Instead, each dependency is expanded into a separate directory under the generated Vine runtime-classpath area, and those directories are added to the server classpath.

This prevents a plugin dependency from being represented only as an ordinary JVM jar while still preserving the complete plugin jar in `run/mods` for manifest discovery. It also prevents the expanded runtime copy's `manifest.json` from being mistaken for another plugin.

For a single-project run, generated runtime entries are placed under:

```text
build/hytale-vine-runtime/classpath/
```

For a workspace run, they are placed under:

```text
build/hytale-vine-runtime/workspace-classpath/
```

These directories are recreated during staging. Do not store manual files in them.

## Manifest dependency fields

Use `manifestDependencies` when your mod requires another plugin or module to be present at runtime. The `requiredDependency` Gradle alias is the matching classpath-oriented convenience configuration, but it does not update this manifest field automatically.

```gradle
hytaleTools {
    manifestDependencies = 'Hytale:AssetModule=*,MoreMagicSpell:Rippod.Hexcode=*,Group:Name=Version'
}
```

Use `manifestOptionalDependencies` when your mod can integrate with another plugin if it is present,
but does not require that plugin in order to load. The `optionalDependency` Gradle alias provides compile-time visibility for that integration, but it does not update this manifest field automatically.

```gradle
hytaleTools {
    manifestOptionalDependencies = 'MoreMagicSpell:Rippod.Hexcode=*,Group:Name=Version'
}
```

Manifest dependency values use this format:

```text
Group:Name=Version
```

Where:

* `Group` is the dependency group or namespace.
* `Name` is the dependency/plugin/module name.
* `Version` is the required version or version range.
* `*` means any compatible version.

Multiple dependencies must be separated with commas and should not include spaces:

```gradle
hytaleTools {
    manifestDependencies = 'GroupOne:DependencyOne=*,GroupTwo:DependencyTwo=1.0.0,GroupThree:DependencyThree=>=2.0.0'
}
```

Do not write spaces after commas:

```gradle
// Correct
manifestDependencies = 'Hytale:AssetModule=*,MoreMagicSpell:Rippod.Hexcode=*'

// Incorrect
manifestDependencies = 'Hytale:AssetModule=*, MoreMagicSpell:Rippod.Hexcode=*'
```

A dependency should be placed in `manifestDependencies` if your mod cannot function without it.
A dependency should be placed in `manifestOptionalDependencies` if your mod only adds support for it when it is available.

For example, if your mod directly requires Hexcode to load:

```gradle
hytaleTools {
    manifestDependencies = 'Hytale:AssetModule=*,MoreMagicSpell:Rippod.Hexcode=*'
}
```

If your mod only enables extra Hexcode integration when Hexcode is installed:

```gradle
hytaleTools {
    manifestOptionalDependencies = 'MoreMagicSpell:Rippod.Hexcode=*'
}
```

## Hytale Server Dependency

The plugin automatically adds the Hytale server dependency based on:

```groovy
hytaleTools {
  hytaleVersion = '0.+'
}
```

This injects:
```groovy
vineServerJar "com.hypixel.hytale:Server:${hytaleVersion}"
```

and makes it available on `compileOnly`. You **do not need to declare this manually**.

### Dynamic versions

`hytaleVersion` accepts Gradle's dynamic version selectors, which is the easiest way to track the latest server build for a patchline:

```groovy
hytaleTools {
  hytaleVersion = '0.+'   // latest 0.x build on the configured patchline
  patchline     = 'release'
}
```

Supported selectors:

- `0.+` — latest version starting with `0.`
- `0.5.+` — latest version starting with `0.5.`
- `latest.release` — same as `+`

By default, the generated `manifest.json` contains the **resolved concrete version** (e.g. `0.5.0-pre.9` or `2026.1.22-6f8bdbdc4`), not the dynamic selector. For example, `hytaleVersion = '0.+'` resolves the server dependency normally, and the manifest `ServerVersion` is written as the resolved full version.

If you want the manifest to use a semver range instead, set `manifestServerVersion` explicitly:

```groovy
hytaleTools {
  hytaleVersion = '0.+'

  // Written directly to manifest.json as ServerVersion
  manifestServerVersion = '>=0.5.0-pre.9 <0.6.0'
}
```

When `manifestServerVersion` is set, it only affects the manifest `ServerVersion` field. `hytaleVersion` is still used to resolve the Hytale server dependency for development, compilation, and local runs.

### Manifest server version ranges

`hytaleVersion` controls which Hytale server artifact Gradle resolves for compilation, IDE setup, and local server runs.

`manifestServerVersion` controls the `ServerVersion` field written to `manifest.json`.

If `manifestServerVersion` is unset, the plugin resolves the configured Hytale version selector, such as `0.+` or `2026.+`, to the full concrete server version and writes it to the manifest as a minimum supported version range.

```groovy
hytaleTools {
  hytaleVersion = '0.+'
}
```

This writes a concrete resolved version, for example:

```json
{
  "ServerVersion": ">=0.5.0-pre.9"
}
```

Set `manifestServerVersion` when you want the manifest itself to accept a semver range:

```groovy
hytaleTools {
  hytaleVersion = '0.+'
  manifestServerVersion = '>=0.5.0-pre.9 <0.6.0'
}
```

This writes:
```json
{
  "ServerVersion": ">=0.5.0-pre.9 <0.6.0"
}
```

In this setup, hytaleVersion still resolves the development server dependency, while manifestServerVersion is written directly to the manifest.

#### Patchline scoping

When using a dynamic selector, the plugin scopes resolution to the configured `patchline`:

- `patchline = 'release'` resolves only against the Hytale Server Release repo
- `patchline = 'pre-release'` resolves only against the Hytale Server Pre-Release repo

This prevents `0.+` from accidentally selecting a pre-release build when you're targeting release, or vice versa. Static versions are unaffected and continue to resolve from whichever repo serves them.

#### Cache behavior

Gradle caches dynamic version resolutions. The plugin configures the `vineServerJar` configuration with a 10-minute cache window, so a freshly published server build is picked up within that interval. To force an immediate refetch:

```bash
./gradlew runServer --refresh-dependencies
```

The Hytale server dependency is also marked as `changing = true`, so the same flag will refetch the artifact bytes if Hytale ever republishes a coordinate.

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
    // Optional manifest ServerVersion override.
    // If unset, dynamic hytaleVersion selectors resolve to the full version in manifest.json.
    // manifestServerVersion = '>=0.5.0-pre.9 <0.6.0'

    // Optional override to an existing local Assets.zip if you don't want to download/cache it.
    // Accepts a direct Assets.zip path, a directory containing Assets.zip,
    // or a Hytale launcher/install directory.
    // Remember to update this path if changing patchlines/versions.
    // hytaleHomeOverride = "/path/to/Hytale/install/release/package/game/latest/Assets.zip"
    
    // Optional override to not generate an Assets Binary (won't show sources as binary in your IDE)
    // generateAssetsBinary = true
    manifestGroup = project.manifest_group.toString()
    modId = project.mod_id.toString()
    modDescription = project.mod_description.toString()
    modUrl = project.mod_url.toString()
    mainClass = project.main_class.toString()
    // Author format: Name, or Name|Email, or Name|Email|Url.
    // Separate multiple authors with commas. Email and Url are optional.
    modCredits = project.mod_credits.toString()

    manifestDependencies = project.manifest_dependencies.toString()
    manifestOptionalDependencies = project.manifest_opt_dependencies.toString()
    curseforgeId = project.curseforgeID.toString()

    disabledByDefault = project.disabled_by_default.toString().toBoolean()
    includesPack = project.includes_pack.toString().toBoolean()
    
    // Optional: declare sub-plugins (positional style)
    subPlugin('Forestry', 'com.example.mods.forestry.ForestryPlugin')
    // subPlugin(name, main, disabledByDefault, includesAssetPack, serverVersion)
    // Or use the map style for full manifest field control:
    // subPlugin([ Name: 'Forestry', Main: '...', Description: 'Adds forestry', DisabledByDefault: false ])

    // Optional: declare load ordering
    // loadBefore 'otherpluginname'
    // loadBefore 'anotherplugin', '>=2.0.0'
    
    serverArgs = ['--allow-op', '--disable-sentry']
    serverJvmArgs = ['-Xms1G', '-Xmx2G']
    preRunTask = 'generateDevResources'
    
    debugEnabled = false
    debugPort = 5005
    debugSuspend = false

    hotSwapEnabled = false
    requireDcevm = false
    useHotswapAgent = true
    
    // Optional external HotswapAgent jar
    hotswapAgentPath = ''
    
    // Optional JetBrains Runtime location
    // jbrHome = '/path/to/jbr'

    // Optional: defaults to the release/prerelease docs URL based on patchline
    // serverJavadocsUrl = 'https://release.server.docs.hytale.com/'

    // Optional: enabled by default
    injectServerJavadocsIntoSources = true
}
```

### Backing Gradle properties

The plugin also reads these Gradle properties automatically:

- `java_version`
- `hytale_version`
- `manifest_server_version`
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
- `hytools.hotswap.agent.path`

The plugin also recognizes these system properties for dev runtime features:

- `debug` → enables JDWP debug mode
- `hotswap` → enables hot swap runtime setup

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
- resolved asset wrapper path and effective `Assets.zip` path
  (local override path when `hytaleHomeOverride` is set, otherwise the Gradle cache path
- auth token cache path
- resolved `vineServerJar` files
- declared `vineImplementation`, `vineCompileOnly`, `requiredDependency`, `optionalDependency`, and `vineDecompileTargets` dependencies

Use it when:
- runServer fails
- assets are missing
- manifest values look wrong
- expected dependency sources are not showing up

### A required or optional alias does not update the manifest

`requiredDependency` and `optionalDependency` configure Gradle classpaths only. They cannot derive the Hytale manifest group, plugin name, or accepted version range from arbitrary Maven coordinates.

Declare both sides explicitly:

```gradle
dependencies {
    requiredDependency 'com.example:economy-plugin:1.2.0'
    optionalDependency 'com.example:placeholder-plugin:2.0.0'
}

hytaleTools {
    manifestDependencies = 'Example:Economy=*'
    manifestOptionalDependencies = 'Example:Placeholder=*'
}
```

### A dependency plugin cannot be found or its API is not loaded

Check both the Gradle configuration and the plugin manifest:

1. Use `vineImplementation` when your code references the dependency's classes and the dependency must run locally.
2. Use `vineMod` only when the plugin should be installed into `run/mods` without shared-classpath access.
3. Ensure the dependency jar contains `manifest.json` at the root. Without it, `vineImplementation` treats the jar as a normal library and does not copy it into `run/mods`.
4. Declare the runtime dependency in your own `manifestDependencies` when Hytale must load it before your plugin.
5. Run `./gradlew prepareRunServer --info` or `./gradlew stageAllModAssets --info` and look for whether the dependency was logged as a plugin or a library.
6. Remove stale manual copies from `run/mods`; plugin-managed staged entries are cleaned automatically, but unrelated files are intentionally preserved.

A compile dependency alone does not establish Hytale plugin load ordering. The Gradle dependency and the manifest dependency serve different purposes.

### Build fails because a maven repository is unreachable

If a build fails with a connection error or timeout pointing at one of the optional mavens (CurseMaven, AzureDoom Maven, PlaceholderAPI, Hytale Modding Maven, Hytale-Mods.info Maven), the Modtale resolver, or Modifold, that host is likely temporarily down. Disable it while it recovers:

```gradle
hytaleTools {
    disableCurseMaven = true
}
```

Or globally via `gradle.properties`, without touching individual mod build scripts:

```properties
hytools.repos.disableCurseMaven=true
```

See [Repositories](#repositories) for the full list of toggles. Note that disabling a repository does not remove any dependencies you've declared against it — if the build then fails to resolve, that dependency simply cannot resolve until the repository is re-enabled or the outage passes.

### Missing `hytaleVersion`

If `hytaleVersion` is not set and no `vineServerJar` is declared,
tasks that require the server jar may fail.

Always configure:

```groovy
hytaleTools {
  hytaleVersion = '...'
}
```

### Dynamic version not resolving the expected build

If you're using a dynamic selector like `0.+` and Gradle is picking the wrong version (or claims it can't find any matching version), check the following:

- **Use `+`, not `*`.** Gradle's dynamic version syntax is `0.+`, not `0.*`. The `*` form is treated as a literal version string and will fail to resolve.
- **Confirm `patchline` is set correctly.** Dynamic resolution is scoped to the active patchline. If you're tracking pre-release builds, set `patchline = 'pre-release'` (or `hytale_patchline=pre-release` in `gradle.properties`).
- **Force a refresh.** Dynamic versions are cached for 10 minutes. Run `./gradlew --refresh-dependencies` to bypass the cache and refetch the latest version listing.
- **Check `hytaleDoctor`.** The diagnostic task prints the configured patchline and the resolved server jar files so you can confirm what was actually picked.

### Sources are not attached in the IDE

Run:

```bash
./gradlew prepareDecompiledSourcesForIde
```

This allows IDEs to attach readable generated source code instead of showing only compiled classes.

Then refresh or reimport the Gradle project in your IDE.

Also verify the dependency is listed in `vineDecompileTargets` if you expect decompiled dependency sources.

### Hytale API comments are not showing in generated server sources

The plugin injects hosted Hytale API docs into generated server sources during `prepareDecompiledSourcesForIde`. If source comments are missing:

1. Confirm `injectServerJavadocsIntoSources` is not disabled.
2. Confirm `serverJavadocsUrl` points at the correct release or pre-release docs site.
3. Delete the generated source jars and Javadoc cache, then regenerate sources.

```bash
rm -rf build/vineflower/hytale-server
rm -rf build/generated-sources-jars/server
rm -rf build/generated-sources-m2
rm -rf build/generated-sources-ivy
rm -rf ~/.gradle/caches/hytale-javadocs/release
./gradlew prepareDecompiledSourcesForIde --rerun-tasks
```

The injector only adds comments where a matching hosted Javadoc page and member documentation can be found.

To force a full regeneration including the global caches:

```bash
rm -rf build/vineflower
rm -rf build/generated-sources-jars
rm -rf build/generated-sources-m2
rm -rf build/generated-sources-ivy
rm -rf ~/.gradle/caches/hytale-decompiled
rm -rf ~/.gradle/caches/hytale-javadocs/release
./gradlew prepareDecompiledSourcesForIde --rerun-tasks
```

Only clear `~/.gradle/caches/hytale-decompiled` if you want to force a full re-decompile across all projects. Deleting it will also affect other projects on the same machine until they regenerate the cache.

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
- `Authors`, if present, must be an array of objects with a non-blank `Name`; optional `Email` and `Url` values must not be blank

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

### External HotswapAgent jar is not found

If `hotswapAgentPath` or `hytools.hotswap.agent.path` is set, the file must exist and point directly to a HotswapAgent jar.

Example:

```gradle
hytaleTools {
    hotSwapEnabled = true
    useHotswapAgent = true
    hotswapAgentPath = '/absolute/path/to/hotswap-agent.jar'
}
```

Or:

```properties
hytools.hotswap.agent.path=/absolute/path/to/hotswap-agent.jar
```

If the file does not exist, `runServer` fails early with a clear error instead of launching without the agent.

### Duplicate JDWP debugger agent

If you launch `runServer` from IntelliJ using **Debug**, IntelliJ may inject its own JDWP debugger agent.

The plugin checks for an existing JDWP argument before adding its own debug agent. This prevents JVM startup errors such as:

```text
Cannot load this JVM TI agent twice, check your java command line for duplicate jdwp options.
```

For command-line debugging, use:

```bash
./gradlew runServer -Ddebug=true
```

For IntelliJ debugging, using the IDE's **Debug** action is usually enough. Avoid manually adding another `-agentlib:jdwp=...` entry in `serverJvmArgs` unless you know IntelliJ is not already providing one.

## Notes

- Requires Java 25+
- `release` is the default patchline
- The plugin applies the `java` plugin automatically
- The plugin does not apply the `idea` plugin automatically
- If the `idea` plugin is present, IDEA integration is wired automatically
- You can always run `prepareDecompiledSourcesForIde` directly for source generation
- Hosted Hytale Javadoc injection is enabled by default for generated server sources and uses a Gradle-user-home cache