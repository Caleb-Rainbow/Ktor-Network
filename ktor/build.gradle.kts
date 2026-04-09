import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("maven-publish")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<LibraryExtension>("android") {
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets["main"].java.directories.add("src/main/java")
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}


publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.Caleb-Rainbow"
            artifactId = "Ktor-Network"
            version = "2026.04.09.01"

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
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.client.auth)
    api(libs.ktor.client.logging)
    //koin
    api(libs.koin.core)

    //test
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
}
