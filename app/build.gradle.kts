import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
    id("com.chaquo.python")
}

android {
    val versionPropsFile = file("version.properties")
    var versionBuild: Int
    namespace = "eu.schnuff.bofilo"
    compileSdk = 34

    /*Setting default value for versionBuild which is the last incremented value stored in the file */
    if (versionPropsFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(versionPropsFile))
        versionBuild = (versionProps["VERSION_BUILD"] as String).toInt()
    } else {
        throw FileNotFoundException("Could not read version.properties!")
    }
    /*Wrapping inside a method avoids auto incrementing on every gradle task run. Now it runs only when we build apk*/
    val autoIncrementBuildNumber = fun() {

        if (versionPropsFile.canRead()) {
            val versionProps = Properties()
            versionProps.load(FileInputStream(versionPropsFile))
            versionBuild = (versionProps["VERSION_BUILD"] as String).toInt() + 1
            versionProps["VERSION_BUILD"] = versionBuild.toString()
            versionProps.store(versionPropsFile.writer(), null)
        } else {
            throw FileNotFoundException("Could not read version.properties!")
        }
    }
    // Hook to check if the release/debug task is among the tasks to be executed.
    //Let's make use of it
    gradle.taskGraph.whenReady(closureOf<TaskExecutionGraph> {
        if (this.hasTask("assembleDebug")) {  /* when run debug task */
            autoIncrementBuildNumber()
        } else if (this.hasTask("assembleRelease")) { /* when run release task */
            autoIncrementBuildNumber()
        }
    })

    if (hasProperty("releaseStoreFile")) {
        signingConfigs {
            create("release") {
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
            }
        }
        println("Signing file found. Singing config active.")
    } else
        println("No Release file found.")

    defaultConfig {
        applicationId = "eu.schnuff.bofilo"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "$versionCode.${"%04d".format(versionBuild)}"
        setProperty("archivesBaseName", "BoFiLo_v$versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Ref: https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split
        splits {
            // Configures multiple APKs based on ABI.
            abi {
                // Enables building multiple APKs per ABI.
                var isRelease = false
                gradle.startParameter.taskNames.find {
                    // Enable split for release builds in different build flavors
                    // (assemblePaidRelease, assembleFreeRelease, etc.).
                    if (it.matches(Regex(".*assemble.*Release.*"))) {
                        isRelease = true
                        return@find true // break
                    }

                    return@find false // continue
                }

                isEnable = isRelease
                // By default all ABIs are included, so use reset() and include to specify that we only
                // want APKs for armeabi-v7a, x86, arm64-v8a and x86_64.
                // Resets the list of ABIs that Gradle should create APKs for to none.
                reset()
                // Specifies a list of ABIs that Gradle should create APKs for.
                include("armeabi-v7a", "arm64-v8a") //, "x86", "x86_64")
                // Generate a universal APK that includes all ABIs, so user who installs from CI tool can use this one by default.
                isUniversalApk = true
            }
        }

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        chaquopy {
            defaultConfig {
                version = "3.10"
                if (file("../venv/bin/python").isFile)
                    buildPython = listOf("../venv/bin/python")
                pip {
                    install("-r", "requirements.txt")
                }
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasProperty("releaseStoreFile"))
                signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            ndk {
                isMinifyEnabled = false
                abiFilters += listOf("x86", "x86_64")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.commons.text)
    implementation(libs.sentry.android)
    // GeckoView
    implementation(libs.geckoview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.tedpermission.normal)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}