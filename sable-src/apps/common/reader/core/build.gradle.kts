import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport


plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt.android)
    jacoco
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.ExperimentalMultiplatform")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.room.ktx)

            implementation(libs.hilt.android)

            implementation(libs.timber)

            implementation(project(":reader-model"))
            implementation(project(":reader-engine-pdf"))
            implementation(project(":reader-engine-comic"))
            implementation(project(":reader-engine-audio"))

            implementation(libs.readium.shared)
            implementation(libs.readium.streamer)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.core.ktx)
                implementation(libs.androidx.junit.ktx)
                implementation(libs.androidx.room.testing)
                implementation(libs.robolectric)

                implementation(libs.junit)
                // Legacy (v3) schema fixtures shared with the JVM migration test in :reader-model.
                implementation(project.dependencies.testFixtures(project(":reader-model")))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.mockk)
            }
        }
    }
}

android {
    namespace = "org.vaachak.reader.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.extensions.configure(JacocoTaskExtension::class.java) {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)

    add("kspAndroid", libs.androidx.room.compiler)

    add("kspAndroid", libs.hilt.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

room {
    schemaDirectory("$projectDir/schemas")
}
tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    group = "verification"
    description = "Writes the JaCoCo coverage report for the debug unit tests."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_Factory*.*",
        "**/*_HiltModules*.*",
        "**/*Hilt*.*",
        "**/*MembersInjector*.*",
        "**/*_Provide*Factory*.*",
        "**/*ComposableSingletons*.*"
    )

    val javaClasses = fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        exclude(fileFilter)
    }

    val kotlinClasses = fileTree("${layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin"
        )
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/testDebugUnitTest.exec"
            )
        }
    )
}