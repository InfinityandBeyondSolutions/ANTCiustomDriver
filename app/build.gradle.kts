plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-kapt")
}

android {
    namespace = "com.ibs.ibs_antdrivers"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.ibs.ibs_antdrivers"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "EMPLOYEE", "\"E2M7P0L3O2Y0E2E5#\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    //implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    //implementation("com.google.firebase:firebase-analytics")
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.maps)
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("com.google.firebase:firebase-database-ktx:20.3.0")
    implementation ("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("androidx.vectordrawable:vectordrawable-animated:1.2.0") //was 1.1.0
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation ("com.github.bumptech.glide:glide:4.12.0")
    //annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")
    kapt("com.github.bumptech.glide:compiler:4.12.0")
}