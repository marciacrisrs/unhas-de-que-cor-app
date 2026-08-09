plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register("verifyCi") {
    group = "verification"
    description = "Roda o mesmo conjunto de verificações do CI (detekt, lint, unit tests, assembleDebug)."
    dependsOn(
        ":app:detekt",
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
    )
}
