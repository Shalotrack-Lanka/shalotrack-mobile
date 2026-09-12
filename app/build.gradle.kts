plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.letstracklanka"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.letstracklanka"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    // Official AndroidX SplashScreen API
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Lottie — lightweight vector animation renderer (used in MainActivity loading screen)
    // 15KB JSON animation, ~400KB AAR. Industry standard (Airbnb, Grab, Uber).
    implementation("com.airbnb.android:lottie:6.4.0")

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.messaging)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    // NEW -- SMS User Consent API, for real OTP box auto-fill. Chosen
    // over the full SMS Retriever API because that one requires a
    // specific app-signature hash embedded in the SMS message itself,
    // which Firebase's own SMS templates don't include since we don't
    // control that content. User Consent API works with any SMS format,
    // just requires a one-tap system dialog for the user's permission
    // to read the most recent message.
    implementation("com.google.android.gms:play-services-auth-api-phone:18.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.6.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.6.0")

    // App Check වලට අදාළ දේවල්
    implementation("com.google.firebase:firebase-appcheck-playintegrity:18.0.0")
    implementation("com.google.firebase:firebase-appcheck:18.0.0")
    implementation("com.google.firebase:firebase-appcheck-debug:18.0.0") // අලුතින් එකතු කළ පේළිය

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    //SignalR to get realtime location updates
    implementation("com.microsoft.signalr:signalr:7.0.20")

    //FCM to get the push notifications via FCM
    implementation("com.google.firebase:firebase-messaging")
}