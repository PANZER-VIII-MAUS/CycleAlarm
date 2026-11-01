import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 读取 keystore.properties（不存在时 release 回退到 debug 签名，方便先跑通）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

// 口令优先从环境变量读取（CI/发布机用），没有再回退到 keystore.properties。
// 这样可以把密码从工作区明文文件里挪走，properties 只留 storeFile / keyAlias。
fun signingSecret(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)

// 上架包（Play）默认关闭常驻 specialUse 前台服务：./gradlew assembleRelease -PplayBuild=true
val guardServiceDefaultOn = !project.hasProperty("playBuild")

android {
    namespace = "com.cyclealarm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyclealarm.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "0.6.5"
        // 常驻保活服务的默认开关（Play 包默认关，见上方 -PplayBuild）
        buildConfigField("boolean", "GUARD_SERVICE_DEFAULT_ON", guardServiceDefaultOn.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = signingSecret("CYCLEALARM_STORE_PASSWORD", "storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = signingSecret("CYCLEALARM_KEY_PASSWORD", "keyPassword")
                // 按需求同时启用 V1（JAR 签名）与 V2（APK 签名方案）
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        // 没有 keystore.properties 时**不再**回退 debug 签名：
        // release 会产出未签名 APK（无法安装），CI 只用它验证编译/R8；
        // 要发布必须在本地创建 keystore.properties（见 README）。
    }

    buildTypes {
        debug {
            // debug 包用独立 package name：真机上已装正式签名包时也能直接跑 instrumentation
            //（否则 debug 签名会因签名不一致装不上去）
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // 把导出的 schema JSON 放进 debug/test 的 assets（Robolectric 的 MigrationTestHelper 需要读它）；
    // release 不打包，省 6KB 也能避免把数据库结构暴露在正式包里
    sourceSets["debug"].assets.srcDir("$projectDir/schemas")

    // Kotlin 2.0 起 Compose 编译器随 kotlin("plugin.compose") 走，不再单独配置版本
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 仅测试用：解析层是纯 JVM，可在电脑上跑单测
    testImplementation("junit:junit:4.13.2")
    // Robolectric 上跑 Room 的真实迁移测试（不需要真机）
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")

    // instrumentation 冒烟（真机/模拟器）：启动、导航、R8 包行为
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
