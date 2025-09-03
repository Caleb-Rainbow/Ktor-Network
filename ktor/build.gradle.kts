

plugins {
    id("maven-publish")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.util.ktor"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.srcDirs("src/main/java")
    publishing {

        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.Caleb-Rainbow"
            artifactId = "Ktor-Network"
            version = "1.0.0-alpha1"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}


dependencies {
    //serialization
    implementation(libs.serialization)
    //ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}
