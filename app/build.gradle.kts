import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// local.properties 파일에서 API 인증키 읽기
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val trafficSignalApiKey = localProperties.getProperty("TRAFFIC_SIGNAL_API_KEY") ?: "\"YOUR_KEY_HERE\""

android {
    namespace = "com.example.ddanjitmode"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ddanjitmode"
        minSdk = 26 // Floating Window 및 Background Location을 고려한 최소 SDK
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig 클래스에 API 키 상수 주입
        buildConfigField("String", "TRAFFIC_SIGNAL_API_KEY", trafficSignalApiKey)
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig 클래스 생성 기능 활성화 (Gradle 8.0 이상 기본값 false 대응)
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // Kotlin 1.9.22 호환 버전
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1") // AlertDialog 지원을 위해 추가
    implementation("com.google.android.material:material:1.11.0") // Material3 테마 리소스 지원을 위해 추가

    // Jetpack Compose BoM 및 UI 컴포넌트
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Google Play Services Location (GPS 속도 추적용)
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Retrofit2 (교통 신호 API 통신용)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // API 로깅용 인터셉터 추가

    // 코루틴 비동기 프로그래밍 지원
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 테스트 라이브러리
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
