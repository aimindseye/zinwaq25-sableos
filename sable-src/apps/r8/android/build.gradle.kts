plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

subprojects {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        ignoreFailures = true
        parallel = true
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
        ignoreFailures.set(true)
        outputToConsole.set(true)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
        }
    }
}

val rustRoot = layout.projectDirectory.dir("../rust")
val rustTargetTriple = "aarch64-linux-android"
val rustNdkVersion = "30.0.16248370"
val rustAndroidApi = 30
val rustTargetDir = layout.buildDirectory.dir("rust-jni-target")

val cargoExecutable = providers.environmentVariable("SABLE_CARGO")
    .orElse(providers.environmentVariable("CARGO"))
    .orElse(
        providers.environmentVariable("SABLE_CARGO_HOME")
            .orElse(providers.environmentVariable("CARGO_HOME"))
            .map { "$it/bin/cargo" }
    )
    .orElse("cargo")

val rustcExecutable = providers.environmentVariable("SABLE_RUSTC")
    .orElse(providers.environmentVariable("RUSTC"))
val cargoHome = providers.environmentVariable("SABLE_CARGO_HOME")
    .orElse(providers.environmentVariable("CARGO_HOME"))
val rustupHome = providers.environmentVariable("SABLE_RUSTUP_HOME")
    .orElse(providers.environmentVariable("RUSTUP_HOME"))

val androidSdkRoot = providers.environmentVariable("ANDROID_SDK_ROOT")
    .orElse(providers.environmentVariable("ANDROID_HOME"))

val buildRustJniArm64 = tasks.register<Exec>("buildRustJniArm64") {
    group = "build"
    description = "Build Sable R8 JNI libraries for Android arm64-v8a"

    workingDir(rustRoot.asFile)

    inputs.files(
        fileTree(rustRoot.asFile) {
            include("Cargo.toml")
            include("Cargo.lock")
            include("crates/**/*.toml")
            include("crates/**/*.rs")
        }
    )

    listOf(
        "libsable_calculator_jni.so",
        "libsable_convert_jni.so",
        "libsable_games_jni.so",
        "libsable_weather_jni.so",
    ).forEach { library ->
        outputs.file(
            rustTargetDir.map {
                it.file("$rustTargetTriple/release/$library")
            }
        )
    }

    doFirst {
        val sdkRoot = androidSdkRoot.orNull
            ?: throw GradleException(
                "ANDROID_SDK_ROOT or ANDROID_HOME must be set"
            )

        val prebuiltRoot = file(
            "$sdkRoot/ndk/$rustNdkVersion/toolchains/llvm/prebuilt"
        )
        val hostTags =
            when {
                System.getProperty("os.name").startsWith("Mac") ->
                    listOf("darwin-aarch64", "darwin-x86_64")
                System.getProperty("os.name").startsWith("Linux") ->
                    listOf("linux-x86_64")
                else ->
                    emptyList()
            }
        val hostTag =
            hostTags.firstOrNull { candidate ->
                prebuiltRoot.resolve(candidate).isDirectory
            } ?: throw GradleException(
                "Pinned Android NDK has no supported host toolchain under $prebuiltRoot"
            )
        val linker =
            prebuiltRoot.resolve(
                "$hostTag/bin/aarch64-linux-android${rustAndroidApi}-clang"
            )

        if (!linker.canExecute()) {
            throw GradleException(
                "Pinned Android NDK linker is unavailable: $linker"
            )
        }

        environment(
            "CARGO_TARGET_DIR",
            rustTargetDir.get().asFile.absolutePath,
        )

        environment(
            "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER",
            linker.absolutePath,
        )

        cargoHome.orNull?.let { environment("CARGO_HOME", it) }
        rustupHome.orNull?.let { environment("RUSTUP_HOME", it) }
        rustcExecutable.orNull?.let { environment("RUSTC", it) }

        executable = cargoExecutable.get()

        args(
            "build",
            "--locked",
            "--release",
            "--target",
            rustTargetTriple,
            "-p",
            "sable-calculator-jni",
            "-p",
            "sable-convert-jni",
            "-p",
            "sable-games-jni",
            "-p",
            "sable-weather-jni",
        )
    }
}

fun registerRustJniStage(
    taskName: String,
    projectPath: String,
    vararg libraryNames: String,
) {
    tasks.register<Copy>(taskName) {
        group = "build"
        description = "Stage Rust JNI libraries for $projectPath"

        dependsOn(buildRustJniArm64)

        libraryNames.forEach { libraryName ->
            from(
                rustTargetDir.map {
                    it.file("$rustTargetTriple/release/$libraryName")
                }
            )
        }

        into(
            project(projectPath)
                .layout
                .buildDirectory
                .dir("generated/rustJni/arm64-v8a")
        )
    }
}

registerRustJniStage(
    "stageCalculatorRustJni",
    ":calculator",
    "libsable_calculator_jni.so",
    "libsable_convert_jni.so",
)

registerRustJniStage(
    "stageGamesRustJni",
    ":games",
    "libsable_games_jni.so",
)

registerRustJniStage(
    "stageWeatherRustJni",
    ":weather",
    "libsable_weather_jni.so",
)
