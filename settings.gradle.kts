// settings.gradle.kts (最终修正版 v2)

pluginManagement {
    repositories {
        // --- 修改开始 ---
        // 优先级1: 阿里云的 Gradle 插件门户镜像
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // 优先级2: 阿里云的公共仓库 (包含 Maven Central，Kotlin插件在这里)
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        // 优先级3: 阿里云的谷歌镜像 (保留 content 过滤)
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }

        // 最后，将官方仓库作为备用（即使网络不通，写上也是一种保险）
        gradlePluginPortal()
        mavenCentral()
        google()
        // --- 修改结束 ---
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 这部分保持不变，已经很好了
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        google()
        mavenCentral()
    }
}

rootProject.name = "ZNC_App"
include(":app")