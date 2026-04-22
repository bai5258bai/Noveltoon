# NovelToon

个人使用版小说/漫画阅读器 Android 应用。

> **设计目标**：仅供个人使用，无联网商用行为，所有内容来自本地文件或手动输入的网络地址。

---

## 📦 安装说明 (Installation)

### 📱 系统要求

| 项目 | 要求 |
|------|------|
| Android 最低版本 | Android 8.0 (API 26) |
| Android 目标版本 | **Android 16 (API 36)** |
| 架构 | arm64-v8a / armeabi-v7a / x86 / x86_64 (通用 APK) |
| 存储空间 | 约 20 MB |

### 方式一：直接下载 APK 安装（推荐）

1. 打开手机浏览器，进入 [Releases 页面](https://github.com/bai5258bai/Noveltoon/releases)
2. 下载最新版本的 `NovelToon-v1.0-release.apk`
3. 打开下载好的 APK 文件开始安装

#### 首次安装可能需要的操作

**Android 16（以及 Android 8.0 及以上）首次安装未知来源 APK：**

1. 点击 APK 文件后，系统会弹出提示："出于安全考虑，你的手机不允许安装来自此来源的未知应用"
2. 点击 **设置** 按钮
3. 打开 **允许来自此来源** 的开关（安装完成后可再关闭）
4. 返回并点击 **安装**
5. 等待安装完成

**Android 16 特别说明：**
- Android 16 对第三方 APK 安装有更严格的扫描，首次安装若被 Play Protect 拦截，可点击"仍要安装"继续
- 在 "设置 → 安全 → 更多安全设置 → 安装未知应用" 中管理各浏览器/文件管理器的安装权限

### 方式二：通过 ADB 安装

适用于开发者或已开启 USB 调试的用户：

```bash
adb install -r NovelToon-v1.0-release.apk
```

如果提示签名冲突，使用：

```bash
adb install -r -d NovelToon-v1.0-release.apk
```

### 方式三：从源码自行构建

```bash
# 克隆仓库
git clone https://github.com/bai5258bai/Noveltoon.git
cd Noveltoon

# 构建 Debug APK（无需签名配置）
./gradlew assembleDebug
# 生成于：app/build/outputs/apk/debug/app-debug.apk

# 构建 Release APK（需配置 keystore.properties）
./gradlew assembleRelease
# 生成于：app/build/outputs/apk/release/app-release.apk
```

**构建环境要求：**
- JDK 17 或 21
- Android SDK API 36
- Android SDK Build-Tools 36.0.0
- Gradle 8.11.1（通过 wrapper 自动下载）
- Android Gradle Plugin 8.9.1

### 方式四：OEM/定制机型的特殊说明

**小米 / Redmi（HyperOS）：**
- 需要开启"设置 → 隐私保护 → 特殊权限 → 安装未知应用"
- 首次打开可能提示"应用未经审核"，点击"继续安装"

**华为 / 荣耀（HarmonyOS/EMUI）：**
- 需开启"设置 → 安全 → 更多设置 → 安装外部来源应用"
- 可能需要关闭"纯净模式"

**三星 One UI：**
- "设置 → 生物识别和安全 → 安装未知应用"

**OPPO / OnePlus / vivo：**
- 通常直接允许即可，若无法安装请查看"设置 → 系统 → 高级 → 安装未知应用"

### 🔑 权限说明

应用仅请求以下权限（符合"无冗余权限"原则）：

| 权限 | 用途 |
|------|------|
| INTERNET | 通过自定义书源/图源搜索和加载内容 |
| ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE | 判断"仅 Wi-Fi 加载原图"开关 |
| READ_EXTERNAL_STORAGE (Android ≤12) | 导入本地 txt 小说 / zip/cbz 漫画 |

**本应用不请求：** 位置、通讯录、相机、麦克风、后台自启、通知等权限。

### ⚠️ 卸载说明

由于应用不使用云端同步，卸载前建议先在 **设置 → 导出备份** 中导出书架和阅读进度，避免数据丢失。

---

## 功能特点

- **无登录、无注册、无数据上报**
- **无分享、无评论、无推荐算法**
- **无广告、无横幅、无积分、无会员**
- 所有内容来自本地文件或手动输入的网络地址（书源/图源）

## 模块说明

### 📖 小说模块
- 书架（网格/列表显示封面、书名、最近阅读章节）
- 搜索（通过书源规则引擎搜索）
- 阅读器（上下滚动/左右翻页、字体大小、行间距、页边距、夜间模式、背景色、进度条跳转、目录导航）
- 支持导入本地 txt 文件
- 自动保存阅读进度

### 🎨 漫画模块
- 书架（网格封面显示、未读标记）
- 搜索（通过图源规则引擎搜索）
- 阅读器：
  - 分页加载（当前页±2页预加载）
  - 内存缓存 + 磁盘缓存
  - 支持 WebP / JPG / PNG
  - 自适应屏幕宽度
  - 双指缩放
  - 超时重试（最多3次）
  - 阅读方向：从右到左（日漫）/ 竖向滚动 / 可切换
  - 自动记录当前页
- 支持导入本地漫画（zip / cbz）

### ⚙️ 设置模块
- 主题模式（浅色 / 深色 / 跟随系统）
- 图片缓存管理（显示大小 + 一键清除）
- 仅Wi-Fi加载原图开关
- 自动清理N天前缓存
- 备份/恢复（导出书架和阅读进度到本地文件）
- 关于页面

## 技术栈

- **Kotlin 2.0.21** + **Jetpack Compose** (Material 3)
- **Android Gradle Plugin 8.9.1** + **Gradle 8.11.1**
- **compileSdk / targetSdk = 36** (Android 16)
- **Room** 数据库
- **Coil** 图片加载（内存+磁盘缓存）
- **OkHttp** + **Jsoup** 网络请求与HTML解析
- **DataStore** 偏好存储
- **Navigation Compose** 页面导航

## 数据存储

- 所有数据存储在 `Android/data/com.noveltoon.app/files/`
- 仅请求存储权限 + 网络权限
- 可设置自动清理7天前的图片缓存

## 声明

仅供个人使用，无联网商用行为。
