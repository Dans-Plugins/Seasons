# CLAUDE.md

## Project overview

Seasons is a Spigot plugin that gives a server a year: a configurable four-season calendar that biases weather, scales crop growth and sets day length per season, for vanilla clients.

**Status: pre-implementation.** The repository holds a buildable, empty Spigot plugin skeleton. The scope of the first release, and its deliberate non-goals, are defined in [MVP.md](MVP.md). Read MVP.md before implementing anything; the non-goals are as binding as the goals.

## Technology stack

- Java, compiled with `options.release = 17` (see `build.gradle`: the oldest Minecraft version in `minecraft-versions.json`, 1.19.4, runs on Java 17). Build with JDK 21 (CI uses Temurin 21).
- Gradle 8.8 via the wrapper (`gradle/wrapper/gradle-wrapper.properties`).
- Spigot API `1.21.4-R0.1-SNAPSHOT` (`compileOnly`, repeated as `testImplementation`).
- JUnit Jupiter 5.10.2 for tests.
- GPL-3.0 licensed (`LICENSE`).

## Build and run commands

```bash
# Build the jar and run tests (the same command CI runs)
export JAVA_HOME=/path/to/jdk-21
./gradlew clean build
```

The plugin jar is written to `build/libs/Seasons-<version>.jar`. Install by dropping it in a Spigot server's `plugins/` directory.

## Test commands

```bash
# Full suite
./gradlew test

# Single test class
./gradlew test --tests com.dansplugins.seasons.SeasonsTest
```

Tests live in `src/test/java/com/dansplugins/seasons/` and mirror the main package. `SeasonsTest` is a smoke test: it checks the main class extends `JavaPlugin` and that the processed `plugin.yml` names it with the version placeholder expanded.

## CI

`.github/workflows/build.yml` runs on pushes and PRs to `main`/`develop`:

1. `./gradlew clean build` on JDK 21.
2. The release-gates Bukkit API compatibility check (`check_api_compat.py`, pinned at release-gates `v12`), which verifies every Bukkit class/field/method the jar uses exists on each version in `minecraft-versions.json` and that the bytecode level fits the Java each version runs on.

There is no lint or formatter configured.

## Repository layout

```
src/main/java/com/dansplugins/seasons/   Plugin source; Seasons.java is the JavaPlugin entry point
src/main/resources/plugin.yml         Plugin descriptor; version is expanded from build.gradle
src/test/java/com/dansplugins/seasons/   JUnit tests
minecraft-versions.json               Minecraft versions the plugin claims to support (read by the CI API gate)
gradle/, gradlew, gradlew.bat         Gradle wrapper
MVP.md                                Scope of the first release and its non-goals
README.md                             User-facing description
```

## Coding conventions

- Package root is `com.dansplugins.seasons`; group `com.dansplugins`.
- Version lives only in `build.gradle` (`version = '...'`); `plugin.yml` uses `'${version}'` and is filled by `processResources`.
- Standard Java naming (PascalCase classes, camelCase members), 4-space indentation, UTF-8 sources.
- The repository follows [dpc-conventions](https://github.com/Dans-Plugins/dpc-conventions), as stated in README.md.

## Project-specific notes

- Crop growth is scaled by intercepting growth events, not by changing `randomTickSpeed` (MVP.md).
- Foliage recolouring, temperature simulation and snow accumulation are explicit v1 non-goals.
- Seasons and Timescale must not both drive the world clock; see MVP.md.

## Do / Don't

**Do:**
- Keep `./gradlew clean build` green and add a unit test with every behaviour change.
- Check new Bukkit API use against every version in `minecraft-versions.json`; the CI gate fails on an API missing from any of them.
- Add `USER_GUIDE.md`, `COMMANDS.md`, `CONFIG.md` and `CHANGELOG.md` with the first release, as README.md promises.
- Register any new command or permission in `plugin.yml`.

**Don't:**
- Raise `targetJavaVersion` above 17 or bump the Spigot API dependency without also revisiting `minecraft-versions.json`; it would break the oldest supported server.
- Implement anything MVP.md lists under "v1 does not".
- Hand-edit the Gradle wrapper files (`gradle/wrapper/*`, `gradlew*`).
- Commit build output (`build/`, `.gradle/`, jars); `.gitignore` already excludes them.
