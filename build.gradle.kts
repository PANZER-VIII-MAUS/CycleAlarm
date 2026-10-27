// 根构建脚本：只声明插件版本，具体配置在 app/build.gradle.kts
plugins {
    // 与 Android Studio Hedgehog (2023.1.1) 匹配的稳定组合
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
