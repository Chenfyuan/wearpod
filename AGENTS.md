# Sisyphus/Agentic Coding Guidelines for wearpod

Welcome to the `wearpod` project. This guide outlines the build processes, testing commands, and coding standards for AI agents operating in this repository. 

`wearpod` is an Android application built for Wear OS using Kotlin and Jetpack Compose.

## 1. Build, Lint, and Test Commands

### General Build Commands
The project uses Gradle Wrapper. Always execute gradle tasks from the project root using `./gradlew` (or `gradlew.bat` on Windows).

- **Clean the project:**
  ```bash
  ./gradlew clean
  ```
- **Build debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Build release APK (requires signing configs):**
  ```bash
  ./gradlew assembleRelease
  ```

### Testing Commands
We prefer Test-Driven Development (TDD). Ensure tests are run before and after making changes.

- **Run all unit tests:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Run a single unit test class:**
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.sjtech.wearpod.domain.MyClassTest"
  ```
- **Run a single specific unit test method:**
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.sjtech.wearpod.domain.MyClassTest.testMethodName"
  ```
- **Run Android instrumented tests (requires emulator/device):**
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

### Linting and Code Formatting
- **Run Android Lint analysis:**
  ```bash
  ./gradlew lintDebug
  ```

*(Note: Run diagnostics/linting tests after each logical modification to avoid accumulating errors.)*

## 2. Code Style and Technical Guidelines

### Language & Modern Android
- **Kotlin First:** Write all new code in Kotlin. Leverage Coroutines and Flows for asynchronous operations.
- **Java Compatibility:** Ensure compatibility with Java 17 as defined in `compileOptions`.

### Architecture & UI
- **Jetpack Compose:** Use Jetpack Compose for UI construction. Avoid XML-based views unless integrating legacy components.
- **Wear OS Optimization:** Because this targets Wear OS (minSdk 30, targetSdk 36), use `androidx.wear.compose.foundation` and optimized Material/Material3 components suited for small, circular/square screens.
- **MVVM Pattern:** Follow the Model-View-ViewModel (MVVM) architecture. Keep logic and side effects in `ViewModel`s, and manage UI state explicitly using StateFlow.

### State Management & Persistence
- **Room Database:** Use `androidx.room` for structured relational data. Make sure DAOs return `Flow` or `suspend` functions.
- **DataStore:** Use `androidx.datastore.preferences` instead of SharedPreferences for key-value storage.
- **Media Playback:** The project utilizes `androidx.media3` (ExoPlayer & MediaSession). Follow Media3 best practices for background audio and media playback controls.

### Imports & Dependencies
- **Version Catalog:** Dependencies are managed via Version Catalogs (`libs.*`). Never hardcode versions in `build.gradle.kts`. Always use `libs.xxx` definitions from `gradle/libs.versions.toml` (if you add dependencies, add them there first).
- **Format:** Ensure alphabetical ordering of imports and remove unused ones.

### Error Handling & Safety
- **No Silent Failures:** Avoid empty catch blocks (`catch(e) {}`). Log the error appropriately, report to the UI state if needed, or rethrow.
- **Null Safety:** Leverage Kotlin's null safety. Avoid using `!!` (not-null assertion operator) unless absolute certainty exists; prefer safe calls (`?.`), Elvis operators (`?:`), or explicit null handling.
- **Type Safety:** Never suppress type errors (`as any` or `@Suppress("UNCHECKED_CAST")`) without a comprehensive inline comment explaining why it is unavoidable.

## 3. Agent Execution Protocol

- **Explore First:** If touching unfamiliar files, use background search tools (grep/explore agents) to understand the full context before modifying. Map dependencies across `ViewModel`, repositories, and UI files.
- **Single Responsibility:** Refactoring or feature additions should respect the single responsibility principle. If fixing a bug, do not do a massive refactor simultaneously layout components unprompted.
- **Verification:** After editing Android resources, database schemas, or complex ViewModels, run `lsp_diagnostics` and attempt `./gradlew assembleDebug` to verify compilation.

---
*Created by Sisyphus Orchestrator.*