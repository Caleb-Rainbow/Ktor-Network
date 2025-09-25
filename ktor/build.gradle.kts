plugins {
    id("maven-publish")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.util.ktor"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

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
            withJavadocJar()
        }
    }
}


publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.Caleb-Rainbow"
            artifactId = "Ktor-Network"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {
    //serialization
    api(libs.serialization)
    //ktor
    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    //koin
    api(libs.koin.core)

    //test
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
}
