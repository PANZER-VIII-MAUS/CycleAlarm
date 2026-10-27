# Room：保留实体与 DAO 生成的实现
-keep class com.cyclealarm.app.data.** { *; }

# WorkManager / 协程告警静默
-dontwarn androidx.work.**
-dontwarn kotlinx.coroutines.**

# Compose 与 AndroidX 的默认规则已由各库自带的 consumer 规则覆盖，无需额外配置
