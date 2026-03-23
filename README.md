# Hytale Gradle Plugin

[![Gradle Plugin](https://img.shields.io/badge/Gradle-Plugin-blue)]()
[![Java](https://img.shields.io/badge/Java-21+-orange)]()
[![License](https://img.shields.io/badge/License-MIT-green)]()

A Gradle plugin that standardizes and simplifies Hytale mod development by providing
repositories, configurations, development tasks, and automatic source attachment.

---

## Quickstart

```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.4'
}

dependencies {
    vineServerJar "com.hypixel.hytale:Server:$hytale_version"
    vineCompileOnly "com.hypixel.hytale:Server:$hytale_version"
}
```

Then run:

```bash
./gradlew createModSkeleton
./gradlew runServer
```

---

## Version Compatibility

| Plugin Version | Java Version | Hytale Support      |
|----------------|--------------|---------------------|
| 1.0.x          | 25+          | Release/Pre-release |

---

## Features

- Automatic repository setup (Hytale, Modtale, CurseMaven, etc.)
- Preconfigured `vine*` dependency configurations
- Development tasks for mod setup, validation, and running
- Automatic decompilation and IDE source attachment

---

## Included Tasks

### `createModSkeleton`
Creates a basic mod structure if it does not exist.

### `updatePluginManifest`
Generates or updates `manifest.json` from Gradle configuration.

### `validateManifest`
Validates `manifest.json` and fails the build on errors.

### `downloadAssetsZip`
Downloads and caches Hytale assets.

### `runServer`
Runs a local Hytale server using your mod and dependencies.

---

## Development Workflow

Typical usage:

```bash
./gradlew createModSkeleton
./gradlew build
./gradlew runServer
```

---

## IDE Source Attachment

The plugin automatically decompiles:
- Hytale server
- Dependencies declared in `vineDecompileTargets`

Sources are:
- Generated with Vineflower
- Installed into local Maven/Ivy repos
- Automatically attached in IDEs (IntelliJ, etc.)

Triggers:
- `assemble`
- `idea`
- `prepareDecompiledSourcesForIde`

---

## Repositories

All required repositories are added automatically.

---

## Configurations

Created automatically:

- `vineImplementation`
- `vineCompileOnly`
- `vineDecompileTargets`
- `vineDecompileClasspath`
- `vineServerJar`
- `vineDependencyJars`

---

## Usage

### settings.gradle
```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.azuredoom.com/mods") }
    }
}
```

### build.gradle
```gradle
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.4'
}
```

---

## Dependencies

```gradle
dependencies {
    vineServerJar "com.hypixel.hytale:Server:$hytale_version"
    vineCompileOnly "com.hypixel.hytale:Server:$hytale_version"

    vineImplementation 'com.buuz135:MultipleHUD:1.0.6'
    vineCompileOnly 'curse.maven:partyinfo-1429469:7526614'

    vineDecompileTargets 'com.buuz135:MultipleHUD:1.0.6'
}
```

---

## Configuration

```gradle
hytaleTools {
    hytaleVersion = project.hytale_version.toString()
    modId = project.mod_id.toString()
    mainClass = project.main_class.toString()
}
```

---

## Troubleshooting

### Sources not attached in IDE
```bash
./gradlew prepareDecompiledSourcesForIde
```
Then refresh your Gradle project.

### Server fails to start
- Verify `hytale_version`
- Run:
```bash
./gradlew downloadAssetsZip
```

### Manifest validation fails
- Ensure required fields are set:
    - `modId`
    - `mainClass`
    - `hytaleVersion`

Run:
```bash
./gradlew updatePluginManifest
```

### Dependencies not decompiled
Ensure dependencies are added to:
```gradle
vineDecompileTargets
```

---

## Notes

- Requires Java 25+
- Designed for Hytale mod development workflows