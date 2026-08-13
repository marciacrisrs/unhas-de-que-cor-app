plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    jacoco
}

android {
    namespace = "br.com.unhasdequecor"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "br.com.unhasdequecor"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 10
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Overlay de landmarks/ROI/máscara no try-on. Ative com -PdebugNailOverlay=true
        buildConfigField(
            "boolean",
            "DEBUG_NAIL_OVERLAY",
            (project.findProperty("debugNailOverlay") as String? ?: "false"),
        )
    }

    signingConfigs {
        create("release") {
            val rawStorePath = (project.findProperty("RELEASE_STORE_FILE") as String?)
                ?: System.getenv("RELEASE_STORE_FILE")
            // Trim + tira aspas “inteligentes”/normais (comum no Windows ao colar path).
            val storeFilePath = rawStorePath
                ?.trim()
                ?.trim('"', '\'', '\u201C', '\u201D', '\u2018', '\u2019')
                ?.takeIf { it.isNotBlank() }
            if (storeFilePath != null) {
                // rootProject.file: absoluto fica absoluto; relativo à raiz do repo (não :app).
                storeFile = rootProject.file(storeFilePath)
                storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // MediaPipe (.so) → Play pede símbolos nativos p/ crashes/ANRs.
            // SYMBOL_TABLE: nomes de função (suficiente; FULL estoura tamanho fácil).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                // CI/local sem keystore: assina com debug (ver docs/release.md).
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    basePath.set(rootProject.projectDir)
    ignoredBuildTypes = listOf("release")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
    exclude("**/build/**")
    reports {
        html.required.set(true)
        sarif.required.set(true)
        // Checkstyle XML → sonar.kotlin.detekt.reportPaths
        checkstyle.required.set(true)
        markdown.required.set(true)
    }
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("17")
    exclude("**/build/**")
}

jacoco {
    toolVersion = "0.8.13"
}

val domainCoverageIncludes = listOf(
    "**/br/com/unhasdequecor/domain/**",
)

/** Pacotes/classes com testes unitários estáveis — relatório Sonar. */
val appCoverageIncludes = listOf(
    "**/br/com/unhasdequecor/domain/**",
    "**/br/com/unhasdequecor/data/catalog/**",
    "**/br/com/unhasdequecor/data/local/db/DatabaseMigrations*",
    "**/br/com/unhasdequecor/data/local/db/HistoryMapper*",
    "**/br/com/unhasdequecor/data/local/db/HistoryLimits*",
    "**/br/com/unhasdequecor/data/local/db/entity/**",
    "**/br/com/unhasdequecor/data/vision/nail/NailRoi*",
    "**/br/com/unhasdequecor/data/vision/nail/NailMask*",
    "**/br/com/unhasdequecor/data/vision/nail/Finger*",
    "**/br/com/unhasdequecor/data/vision/nail/DetectedNail*",
    "**/br/com/unhasdequecor/data/vision/nail/ImageCoordinates*",
    "**/br/com/unhasdequecor/data/vision/nail/NailColorApplier*",
    "**/br/com/unhasdequecor/data/vision/nail/PolishMaskRecolorer*",
    "**/br/com/unhasdequecor/data/vision/nail/NailOverlayAnchors*",
    "**/br/com/unhasdequecor/data/vision/nail/NailLandmarkMapper*",
    "**/br/com/unhasdequecor/data/vision/nail/NailPlateCalibration*",
    "**/br/com/unhasdequecor/data/vision/nail/NailTryOnPipeline*",
    "**/br/com/unhasdequecor/data/vision/nail/TryOnHandReliability*",
    "**/br/com/unhasdequecor/data/vision/nail/DetectionConfidenceFloor*",
    "**/br/com/unhasdequecor/data/vision/nail/DetectionFailureReason*",
    "**/br/com/unhasdequecor/data/vision/nail/DetectionFailureDiagnostics*",
    "**/br/com/unhasdequecor/data/vision/nail/TryOnPreviewLabels*",
    "**/br/com/unhasdequecor/data/vision/HandInferenceEnhancer*",
    "**/br/com/unhasdequecor/data/vision/HandLandmarkQuality*",
    "**/br/com/unhasdequecor/data/vision/HandPresenceScoring*",
    "**/br/com/unhasdequecor/data/vision/HandLandmarks*",
    "**/br/com/unhasdequecor/data/repository/HistoryRepositoryImpl*",
    "**/br/com/unhasdequecor/ui/history/HistoryViewModel*",
    "**/br/com/unhasdequecor/ui/result/ResultViewModel*",
    "**/br/com/unhasdequecor/ui/hand/HandReferenceViewModel*",
    "**/br/com/unhasdequecor/ui/home/HomeViewModel*",
    "**/br/com/unhasdequecor/ui/navigation/Routes*",
    "**/br/com/unhasdequecor/ui/navigation/ResultSources*",
)

val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*_Hilt*",
    "**/Hilt_*.*",
    "**/*_Factory*",
    "**/*_MembersInjector*",
    "**/*Module*",
    "**/*Module$*",
    "**/di/**",
    "**/MediaPipe*",
    "**/HandInferenceVariants*",
    "**/HandInferenceVariant*",
    "**/ImageLightingSampler*",
    "**/GeometricNailSegmenter*",
    "**/NailTryOnResult*",
    "**/NailTracker*",
    "**/DetectedNailPolishApplier*",
    "**/dao/**",
)

fun Project.jacocoClassDirectories(includes: List<String>): FileCollection {
    val buildDirPath = layout.buildDirectory.get().asFile
    // AGP 9 (built-in Kotlin compiler): classes live under intermediates/, not tmp/kotlin-classes.
    val kotlinTree = fileTree(
        buildDirPath.resolve("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
    ) {
        include(includes)
        exclude(jacocoExcludes)
    }
    val javaTree = fileTree(
        buildDirPath.resolve("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    ) {
        include(includes)
        exclude(jacocoExcludes)
    }
    return files(kotlinTree, javaTree)
}

fun Project.domainClassDirectories(): FileCollection = jacocoClassDirectories(domainCoverageIncludes)

fun Project.appClassDirectories(): FileCollection = jacocoClassDirectories(appCoverageIncludes)

fun Project.jacocoExecutionData(): FileCollection =
    fileTree(layout.buildDirectory.get().asFile) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/*.exec",
            "jacoco/testDebugUnitTest.exec",
        )
    }

tasks.register<JacocoReport>("jacocoDomainReport") {
    group = "verification"
    description = "Gera relatório JaCoCo focado no pacote domain."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(domainClassDirectories())
    executionData.setFrom(jacocoExecutionData())
}

tasks.register<JacocoReport>("jacocoAppReport") {
    group = "verification"
    description = "Gera relatório JaCoCo da lógica coberta por testes unitários (domain + data + VMs)."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(appClassDirectories())
    executionData.setFrom(jacocoExecutionData())
}

tasks.register<JacocoCoverageVerification>("jacocoDomainCoverageVerification") {
    group = "verification"
    description = "Exige ≥80% de cobertura de linhas no pacote domain."
    dependsOn("jacocoDomainReport")

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(domainClassDirectories())
    executionData.setFrom(jacocoExecutionData())

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.register<JacocoCoverageVerification>("jacocoAppCoverageVerification") {
    group = "verification"
    description = "Exige ≥80% de cobertura de linhas no escopo app (relatório Sonar)."
    dependsOn("jacocoAppReport")

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(appClassDirectories())
    executionData.setFrom(jacocoExecutionData())

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
