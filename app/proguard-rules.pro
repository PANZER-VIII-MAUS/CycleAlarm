# CycleAlarm R8 规则（收缩后）
#
# 原则：只保留“运行时靠反射/字符串查找”必需的东西，其余交给 R8 优化。
# Room / WorkManager / Compose / AndroidX 自带 consumer 规则已覆盖它们自身的需要，
# 这里不再对业务数据包做整体 keep（之前 `-keep class com.cyclealarm.app.data.** { *; }`
# 会把整个数据层连同字段/方法全保留，等于放弃优化）。

# --- 崩溃栈可读性（不影响体积） ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room：@Database 生成的实现类在运行时按类名反射查找，必须保留 ---
-keep class com.cyclealarm.app.data.AppDatabase { *; }
-keep class com.cyclealarm.app.data.AppDatabase_Impl { *; }

# --- 枚举：保留 values()/valueOf()（Room 类型转换与 Intent 序列化用） ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- 广播接收器 / 服务 / Activity 由 Manifest 声明，R8 自动保留，无需手写 ---

# --- 可选依赖告警静默 ---
-dontwarn androidx.work.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
