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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    //serialization
    implementation(libs.serialization)
    //ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}

/*
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                // from(components["release"]) 告诉插件我们要发布 release 构建变体
                from(components["release"])

                // groupId, artifactId, 和 version 构成了库的唯一坐标
                groupId = "com.github.Caleb-Rainbow" // 替换为你的 GitHub 用户名
                artifactId = "Ktor-Network"          // 这个库的名称
                version = "V1.0.0-alpha3"             // 和你的 Tag 保持一致
            }
        }
    }
}*/
