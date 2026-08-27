# Hytale Gradle Plugin

[![Gradle Plugin](https://img.shields.io/badge/Gradle-Plugin-blue)](https://plugins.gradle.org/plugin/com.azuredoom.hytale-tools)
[![Java](https://img.shields.io/badge/Java-25-orange)]()
[![Hytale](https://img.shields.io/badge/Hytale-Release%2FPre-green)]()

A Gradle plugin for Hytale mod development. It handles manifest generation and validation, local server runs, asset resolution, dependency staging, IDE source attachment, workspace orchestration, and optional Hytale Javadoc injection.

## Quickstart

Need a new project? Start with the [template generator](https://template.azuredoom.com/).

```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.50'
}

hytaleTools {
    hytaleVersion = '0.+'
    patchline = 'release'
    manifestGroup = 'com.example.mods'
    modId = 'examplemod'
    mainClass = 'com.example.mods.ExampleMod'
    modCredits = 'yourname'
}
```

Then run:

```bash
./gradlew setupHytaleDev
./gradlew runServer
```

For debugging and hot swap:

```bash
./gradlew runServer -Ddebug=true -Dhotswap=true
```

## What it provides

- Generated and validated `manifest.json`
- Authenticated Hytale asset resolution
- Local Hytale server launch tasks
- Runtime plugin and library staging
- Decompiled source attachment for IDEs
- Hosted Hytale Javadocs in generated sources
- Multi-project workspace support
- VS Code run, task, and debug configuration
- Diagnostics and cleanup tasks

## Common tasks

| Task | Purpose |
|---|---|
| `setupHytaleDev` | Prepare assets and IDE sources |
| `runServer` | Run one mod locally |
| `runAllMods` | Run all workspace mods together |
| `prepareDecompiledSourcesForIde` | Generate IDE source attachments |
| `updatePluginManifest` | Regenerate `manifest.json` |
| `hytaleDoctor` | Diagnose project, asset, and dependency issues |
| `hytaleJvmDoctor` | Diagnose debug and hot-swap support |

## Documentation

The full documentation lives in the [project wiki](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki).

### Getting started

- [Why use this plugin?](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Why-use-this-plugin)
- [Quickstart](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Quickstart)
- [Hot Swap Quickstart](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Hot-Swap-Quickstart)
- [Usage](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Usage)
- [Development Workflow](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Development-Workflow)
- [VS Code Setup](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/VS-Code-Setup)

### Workspace and configuration

- [Multi-Project Setup](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Multi-Project-Setup)
- [Workspace Tasks](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Workspace-Tasks)
- [Configuration](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Configuration)
- [Extension Reference](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Extension-Reference)
- [Configurations](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Configurations)
- [Dependencies](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Dependencies)
- [Manifest dependency fields](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Manifest-dependency-fields)
- [SubPlugins](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/SubPlugins)
- [AssetBridge](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/AssetBridge)

### Development and reference

- [Dependency Flow](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Dependency-Flow)
- [IDE Source Attachment](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/IDE-Source-Attachment)
- [Runtime Dependency Staging](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Runtime-Dependency-Staging)
- [Hytale Server Dependency](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Hytale-Server-Dependency)
- [Cleaning Generated Files and Caches](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Cleaning-Generated-Files-and-Caches)
- [Features](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Features)
- [Included Tasks](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Included-Tasks)
- [Task Reference](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Task-Reference)
- [Repositories](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Repositories)
- [Gradle Compatibility](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Gradle-Compatibility)
- [CI Usage](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/CI-Usage)

## Requirements

- Java 25+
- A supported Hytale release or pre-release patchline
- A modern Gradle version

## Support

- [Troubleshooting](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki/Troubleshooting)
- [GitHub issues](https://github.com/AzureDoom/Hytale-Gradle-Plugin/issues)
- [Discord](https://discord.gg/f2NJGA8ey8)
