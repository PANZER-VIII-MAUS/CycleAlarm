// 根构建脚本：只声明插件版本，具体配置在 app/build.gradle.kts
plugins {
    // targetSdk 36 需要 AGP 8.9+ / Gradle 8.11+ / Kotlin 2.0+（Compose 编译器随 Kotlin 插件走）
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
