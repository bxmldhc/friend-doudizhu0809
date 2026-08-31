import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val asciiTestClassesDir = File(System.getProperty("java.io.tmpdir"), "wangyouge-test-classes")
val signingPropertiesFile = rootProject.file("app/signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseSigning = signingPropertiesFile.isFile

val prepareAsciiTestClasses by tasks.registering {
    dependsOn("compileDebugUnitTestKotlin", "bundleDebugClassesToRuntimeJar")
    doLast {
        asciiTestClassesDir.deleteRecursively()
        asciiTestClassesDir.mkdirs()
        copy {
            from(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"))
            into(asciiTestClassesDir)
        }
        copy {
            from(layout.buildDirectory.dir("intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar"))
            into(asciiTestClassesDir)
        }
    }
}

android {
    namespace = "com.loves.wangyouge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.loves.wangyouge"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    testOptions {
        unitTests.all { test ->
            if (test.name.contains("Debug")) {
                test.dependsOn("prepareAsciiTestClasses")
                test.classpath = files(
                    asciiTestClassesDir,
                    asciiTestClassesDir.resolve("classes.jar")
                ) + test.classpath
            }
        }
    }
}

tasks.register("verifyReleaseSigningConfigured") {
    doLast {
        check(hasReleaseSigning) {
            "Missing app/signing.properties. Release APK cannot be published without the permanent signing key."
        }
        val storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
        check(storeFile.isFile) {
            "Release keystore does not exist: ${storeFile.absolutePath}"
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("verifyReleaseSigningConfigured")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
