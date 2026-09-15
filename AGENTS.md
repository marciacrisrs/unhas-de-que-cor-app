# AGENTS.md

## Cursor Cloud specific instructions

Single Gradle module Android app (`:app`) — "Unhas de Que Cor?" (Kotlin + Jetpack Compose, Hilt, Room, DataStore). There is no backend/service to run; it is a client app. Standard build/lint/test commands live in `README.md` and `build.gradle.kts` (`verifyCi` task); prefer those over duplicating them.

### Environment (already provisioned by the update script / VM snapshot)
- Android SDK lives at `~/android-sdk` (`cmdline-tools/latest`, `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`). `ANDROID_HOME` is exported in `~/.bashrc`.
- `local.properties` (git-ignored) holds `sdk.dir=$HOME/android-sdk`. The update script rewrites it on every startup, so you normally don't need to touch it.
- The build compiles/tests fine on the pre-installed JDK (targets JVM 17 bytecode). Do not assume JDK 17 specifically is present — just use `./gradlew`.

### Build / lint / test (all pass in this environment)
- Full CI parity: `./gradlew verifyCi` (detekt → lintDebug → testDebugUnitTest → jacocoDomainCoverageVerification (domain ≥80%) → jacocoAppCoverageVerification (app report ≥80%) → assembleDebug → assembleRelease).
- Unit tests only: `./gradlew :app:testDebugUnitTest` (JVM tests; Room DAO/migration tests run on JVM via the `sqlite-jdbc` dependency — no device needed).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release APK: `app/build/outputs/apk/release/app-release.apk` (debug-signed se não houver keystore).
- Especialistas / backlog: `docs/avaliacao-especialistas.md`.

### Non-obvious gotchas
- `org.gradle.configuration-cache=true` is set in `gradle.properties`. CI (and this setup) run `verifyCi` with `--no-configuration-cache` for reliability; do the same if you hit cache-related errors.
- The Android emulator does NOT boot in this VM: QEMU launches but the guest kernel never executes (nested-virtualization limitation), so `adb` stays `offline` and `sys.boot_completed` never becomes `1`. Do not rely on running an emulator for verification. Verify UI/logic changes with the JVM unit tests (the recommendation engine, use cases, ViewModels, and Room mappers are all covered there) and `assembleDebug`.
- Core app logic is pure Kotlin under `app/src/main/java/br/com/unhasdequecor/domain/` (recommendation engine) and `data/catalog/DefaultColorCatalog.kt` — these can be exercised directly from JVM unit tests without Android.
