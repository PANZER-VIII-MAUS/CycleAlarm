pluginManagement {
    // 本地（国内）优先阿里云镜像；CI（GitHub Actions 等境外环境）只用官方源。
    // 原因：境外 runner 访问 maven.aliyun.com 可能被拒/超时，Gradle 会误判为"仓库里没有"，
    // 进而报 "Plugin was not found in any of the following sources"。
    // 注意：flag 必须声明在块内部——settings 脚本的顶层 val 在 pluginManagement 里不可见。
    val isCi = System.getenv("CI") != null
    repositories {
        if (isCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val isCi = System.getenv("CI") != null
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCi) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "CycleAlarm"
include(":app")
