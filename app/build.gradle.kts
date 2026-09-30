plugins {
    id("com.android.application")
}

android {
    namespace = "com.bitchord.rickroll"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bitchord.rickroll"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
}
