# 快速安装指南

## 方式一：从 GitHub Releases 安装（推荐）

1. 打开仓库的 Releases 页面
2. 下载最新版本 APK（文件名示例：`GlowSelfie-v1.1.0-debug.apk`）
3. 将 APK 传到手机后安装
4. 首次启动授予相机权限

## 方式二：从 Actions 构建产物安装

1. 打开仓库 Actions 页面
2. 进入最新的 Build/Release 工作流
3. 下载 Artifact 中的 APK
4. 传到手机安装

## 维护者发布流程（版本 + Release）

1. 修改 [app/build.gradle](app/build.gradle) 中 `versionCode` / `versionName`
2. 更新 [CHANGELOG.md](CHANGELOG.md)
3. 提交并推送到 `main`
4. 打标签并推送：`vX.Y.Z`（例如 `v1.1.0`）
5. GitHub Actions 自动构建并创建 Release，上传 APK 资产