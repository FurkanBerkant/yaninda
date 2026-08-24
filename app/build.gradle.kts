plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val releaseStoreFile = providers.environmentVariable("YANINDA_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable(
    "YANINDA_RELEASE_STORE_PASSWORD"
).orNull
val releaseKeyAlias = providers.environmentVariable("YANINDA_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("YANINDA_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
check(releaseSigningValues.none { it != null } || releaseSigningValues.all { !it.isNullOrBlank() }) {
    "Release signing requires all four YANINDA_RELEASE_* environment variables."
}

android {
    namespace = "com.berkant.yaninda"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.berkant.yaninda"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningValues.all { !it.isNullOrBlank() }) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "boolean",
                "USE_FIREBASE_EMULATORS",
                "true",
            )
        }

        release {
            buildConfigField(
                "boolean",
                "USE_FIREBASE_EMULATORS",
                "false",
            )

            signingConfig = signingConfigs.findByName("release")

            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add(
            layout.projectDirectory.dir("schemas").asFile.absolutePath
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.functions)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("com.google.firebase:firebase-functions")
}
