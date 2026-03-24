import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.3.2"
    id("com.chaquo.python") version "17.0.0"
}

val gVersionPropsFile = file("version.properties")
var gVersionBuild: Int = 0

if (gVersionPropsFile.canRead()) {
    val versionProps = Properties()
    versionProps.load(FileInputStream(gVersionPropsFile))
    gVersionBuild = (versionProps["VERSION_BUILD"] as String).toInt()
} else {
    throw FileNotFoundException("Could not read version.properties!")
}

val autoIncrementBuildNumber = {
    if (gVersionPropsFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(gVersionPropsFile))
        gVersionBuild = (versionProps["VERSION_BUILD"] as String).toInt() + 1
        versionProps["VERSION_BUILD"] = gVersionBuild.toString()
        versionProps.store(gVersionPropsFile.writer(), null)
    } else {
        throw FileNotFoundException("Could not read version.properties!")
    }
}

gradle.taskGraph.whenReady {
    if (this.hasTask(":app:assembleDebug") || this.hasTask(":app:assembleRelease")) {
        autoIncrementBuildNumber()
    }
}

val gVersionCode = 10
val gVersion = "$gVersionCode.${"%04d".format(gVersionBuild)}"
logger.info("Building Version {}", version)

base {
    archivesName = "BoFiLo_v$gVersion"
}

configure<ApplicationExtension> {
    namespace = "eu.schnuff.bofilo"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.schnuff.bofilo"
        minSdk = 26
        targetSdk = 36
        versionCode = gVersionCode
        versionName = gVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "x86_64")
                isUniversalApk = true
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    signingConfigs {
        create("release") {
            if (hasProperty("releaseStoreFile")) {
                        val releaseStoreFile: String by project
                        val RELEASE_STORE_PASSWORD: String by project
                        val RELEASE_KEY_ALIAS: String by project
                        val RELEASE_KEY_PASSWORD: String by project

                        if (!file(releaseStoreFile).exists())
                            logger.warn("Signing: Release store file does not exist.")
                        if (RELEASE_STORE_PASSWORD == "")
                            logger.warn("Signing: {} is empty.", "RELEASE_STORE_PASSWORD")
                        if (RELEASE_KEY_ALIAS == "")
                            logger.warn("Signing: {} is empty.", "RELEASE_KEY_ALIAS")
                        if (RELEASE_KEY_PASSWORD == "")
                            logger.warn("Signing: {} is empty.", "RELEASE_KEY_PASSWORD")
                        if (!file(releaseStoreFile).exists() || RELEASE_STORE_PASSWORD == "" || RELEASE_KEY_ALIAS == "" || RELEASE_KEY_PASSWORD == "")
                            throw GradleException("Signing not configured right.")

                        storeFile = file(releaseStoreFile)
                        storePassword = RELEASE_STORE_PASSWORD
                        keyAlias = RELEASE_KEY_ALIAS
                        keyPassword = RELEASE_KEY_PASSWORD

                        // Optional, specify signing versions used
                        enableV2Signing = true
                        enableV3Signing = true
                        enableV4Signing = true
                println("Signing file found. Singing config active.")
            } else
                println("No Release file found.")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            splits {
                abi {
                    isEnable = false
                }
            }
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        if (file("../venv/bin/python").isFile) {
            buildPython = listOf("../venv/bin/python")
        }
        pip {
            install("-r", "requirements.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.commons.text)
    implementation(libs.sentry.android)
    implementation(libs.geckoview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.tedpermission.normal)
    implementation(libs.material)
    implementation(libs.androidx.documentsfiles)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
