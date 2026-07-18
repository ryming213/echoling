# Echo Ling · 听言英语

> 一款面向国内英语学习者的 Android 原生应用，**完全离线运行**：本地 Vosk 语音识别 + 离线词典，无需任何 API key 或网络连接。

[![Release v1.0](https://img.shields.io/badge/release-v1.0-7C3AED)](https://github.com/ryming213/EchoLing/releases/tag/v1.0)
[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84)](#-开发环境)
[![Kotlin](https://img.shields.io/badge/100%25-Kotlin-7F52FF)](#-技术栈)
[![License](https://img.shields.io/badge/license-MIT-blue)](#-license)

[📥 **下载 v1.0 APK (84.8 MB)**](https://github.com/ryming213/EchoLing/releases/download/v1.0/app-release.apk) · [🐛 反馈问题](https://github.com/ryming213/echoling/issues)

---

## ✨ 功能概览

### 📚 词汇 · 11 个分类，闪卡复习

- 11 个预置词库（小学 / 初中 / 高中 / CET-4 / CET-6 / 雅思 / 托福 / 考研 / GRE / BEC / TEM-8），共 **~26000+ 词**
- 闪卡复习：Y 轴 3D 翻转动画 + 正方形卡片，**逐张记录「认识 / 不认识」进度**
- **进程被杀 / 切 tab 后进度不丢**（Room v5 `recite_progress` 表持久化）
- 闪卡正面 / 反面 / 生词本每个单词配 **TTS 喇叭按钮**（内置 + 多引擎 fallback）
- 例句展示（英文 + 中文翻译对照）

### 🎬 自动字幕生成 · 完全离线

- 导入音视频（`.mp3` / `.mp4` / `.mkv`）时不传字幕，可点 **「立即转字幕」** 或 **「稍后转字幕」**
- 全本地管线：`ffmpeg-kit-min-gpl 6.0-2` 抽 16 kHz Mono WAV → `Vosk small` 离线 STT → `SrtSynthesizer` 纯 Kotlin 拼 `.srt` → 写入 `filesDir/courses/<id>.srt`
- 生成结果与手传字幕共用 `subtitleUri` 字段，**零下游代码改动** —— 直接进跟读练习
- 课程列表 chip 实时显示进度：`字幕识别中 N%` / `字幕识别失败，点击重试` / READY 后 chip 消失
- 进 Practice 时若字幕还在识别：显示 `字幕正在识别中… 请稍后回来`（48dp 沙漏 + 返回按钮），不报错
- **`setRequiresStorageNotLow`** —— worker 会在低存储时自动 defer

### 🎯 跟读练习 · 三个 Tab 全场景

| Tab | 功能 |
|---|---|
| **泛听** | 字幕逐句播放，点击句子自动播放该句，单击单词 reveal / 长按翻译 + 收藏 |
| **精听** | 按住录音键跟读，播放录音回放对比，单句循环 + 自动暂停 |
| **测试** | 词级 WordMatcher 容错匹配 STT 识别结果（n-best 投票 + 填充词过滤 + common alternates 表） |

- 全局字幕锁英文（不显示中文，避免用户偷看绕过听力训练）
- 单句播放状态隔离（`singleSubtitleIndex`/`isSinglePlayMode`，§12.36 race 修复完毕）
- 长按单词翻译走**离线词典**（本地 JSON 资产，<1s 响应）

### 🎬 我的练习 · 本地音视频 + 字幕导入

- 通过系统 SAF 选择本地 `.mp4` / `.mkv` / `.mp3` + `.srt` / `.ass` / `.lrc`
- 课程列表：4dp 难度 accent bar（A/B/C 难度色编码）+ 按下态浮起 + 缩放动画
- 删除课程 / 课程详情页 / 进度跟踪
- **HEVC / DTS 音频转码提示**（mkv DTS 需先 ffmpeg 转 AAC stereo）

### 📊 学习统计 · 今日 + 月度热力图

- 累计学习时长 / 已学句子数 / 词汇量统计
- 月度热力图，今日格用 `primaryContainer` 浅紫填充，高活跃格用 `primary` 深紫
- Continue Learning Card：基于上次进度 hero 区（深紫渐变 + 56dp Play 圆形按钮）

### 📂 生词本 · 长按收藏的单词

- 一键复制 / 听发音 / 删除
- 翻译灰色化（`Color(0xFF666666)`），主信号让位给英文文本
- 行高紧凑化（8dp vertical padding + 4dp/2dp spacer）

---

## 🏗️ 技术栈

| 维度 | 选型 | 理由 |
|---|---|---|
| 语言 | 100% Kotlin | 项目硬性约束（CLAUDE.md §一） |
| UI | Jetpack Compose + Material 3 | 单一 Activity + Composable 全覆盖 |
| 架构 | MVVM + Clean Architecture | UI → ViewModel → UseCase → Repository → Dao/Player/Speech |
| 依赖注入 | Hilt | `@Singleton + @Inject constructor` 自动注册 UseCase |
| 数据库 | Room v5 | 5 个 Entity（course / sentence / word / learning_progress / recite_progress） |
| 视频/音频 | Media3 ExoPlayer | `@Singleton AudioPlayer` 封装 |
| 本地 STT | Vosk Android (vosk-model-small-en-us-0.15) | 68 MB APK 内置，**完全离线** |
| 字幕解析 | 自研 SRT / ASS / LRC 解析器 | 端口号独立但格式统一 |
| TTS | Android `TextToSpeech` + 多引擎 fallback | 7 个硬编码引擎 + PackageManager 自动发现 |
| 持久化 | Room (主) + EncryptedSharedPreferences (历史，已删) | release keystore 签名 |
| 构建 | Gradle 8.x Kotlin DSL | AGP 8.5.x + 16KB page-size 对齐 hooks |

**无第三方翻译 API**（Baidu / Tencent / Youdao 全部移除），长按翻译走离线词典。

---

## 📐 架构

```
app/src/main/java/com/echoling/app/
├── EchoLingApplication.kt          # @HiltAndroidApp
├── presentation/                    # UI 层
│   ├── MainActivity.kt              # 单一 Activity
│   ├── viewmodel/                   # 8 个 ViewModel
│   └── ui/
│       ├── screens/                 # 14 个 screen
│       │   ├── practice/            # 跟读三 Tab（泛听 / 精听 / 测试）
│       │   ├── recite/              # 闪卡（Y 轴 3D 翻转）
│       │   ├── vocabulary/          # 生词本
│       │   └── statistics/          # 学习统计 + 月度热力图
│       ├── navigation/              # TabPagerHost + NavGraph + AppBottomBar
│       └── theme/                   # 深紫系品牌色板
├── domain/                          # 业务层
│   ├── model/                       # Course, Sentence, Word, DictEntry, ...
│   ├── repository/                  # 5 个 Repository 接口
│   └── usecase/                     # 21 个 UseCase（动词+名词）
├── data/                            # 数据层
│   ├── repository/                  # 5 个 Repository Impl
│   └── local/db/                    # Room v5（5 entities + 5 DAOs）
├── player/                          # 播放引擎
│   ├── AudioPlayer.kt              # ExoPlayer 封装
│   ├── TtsManager.kt               # 多引擎 fallback TTS
│   └── subtitle/                    # SRT/ASS/LRC 解析器
└── speech/                          # 语音处理
    ├── VoskSpeechRecognizer.kt     # 离线 STT + n-best alternatives
    ├── WordMatcher.kt              # 词级 fuzzy 容错匹配
    ├── WavRecorder.kt              # MediaRecorder 封装
    └── ModelManager.kt             # Vosk 模型从 assets 解压
```

**调用链**：`UI (Composable) → ViewModel → UseCase → Repository (interface) → RepositoryImpl → Dao → Room DB`

**设计原则**：
- 所有 UseCase `@Singleton + @Inject constructor`，无需 Module
- TTS / AudioPlayer / VoskRecognizer 等副作用服务**跳过 UseCase 层**（与 `AudioPlayer` 同类）
- `practice/*` 页面三 Tab 共享 `PracticeViewModel`，每个 Tab 独立 `PracticePage` 枚举隔离状态

---

## 🎨 品牌色板（CLAUDE.md §11.2 锁定）

| Token | 值 | 用途 |
|---|---|---|
| Primary | `#7C3AED` (violet-600) | 主强调色，CTA 按钮 / icon tint |
| OnPrimary | `#FFFFFF` | Primary 上文字 |
| PrimaryContainer | `#DDD6FE` (violet-200) | Continue Learning 卡片背景 / 今日热力格 |
| Secondary | `#8B5CF6` (violet-500) | 次级强调 |
| Tertiary | `#6D28D9` (violet-700) | accent bar / 深紫装饰 |
| InversePrimary | `#A78BFA` (violet-400) | 深色主题主色 / 装饰紫 |

**反例**：`A78BFA` 不适合做 CTA 按钮（太浅不像紫色），仅用于装饰 / 品牌栏 / 弱对比。

---

## 🔧 开发环境

```bash
# 必须
Android Studio Hedgehog 或更新
JDK 17
Android SDK 34（compileSdk = 34）
minSdk = 26（Android 8.0）
Gradle 8.2+

# 构建
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK（需 keystore，见 §发布）

# 测试
./gradlew testDebugUnitTest      # 单元测试（WordMatcherTest 等）
./gradlew installDebug           # 安装到连接设备
```

**16KB page-size 对齐**：Android 15+ 强制要求。`packageDebug` 通过 3 个 Gradle hook 自动处理：
- `doFirst`: `patchNativeLibsFor16KB` — 改 ELF PT_LOAD.p_align 16384
- `doLast`: `repackApk16kb` — zip entry 重排 + 4-byte 对齐 + 重新签名
- `createDebugApkListingFileRedirect.doLast`: 删 stale `.idsig`

**Root 检测 + 16KB 对齐的 Windows 坑**：`zipalign -p 16` 在 Windows 上是 broken 的（验证假阳性），必须用 `scripts/repack_apk_16kb.py`。

---

## 📦 发布

| 配置 | 值 |
|---|---|
| Release keystore | `keystore/echoling.keystore`（不在 git 内） |
| 别名 | `echoling` |
| 签名算法 | v1 + v2 + v3 |
| 版本号（v1.0） | versionCode = 1, versionName = "1.0" |

发布流程：
```bash
1. 准备 keystore：cp keystystore/keystore.properties.example keystore/keystore.properties
2. 填入 storePassword / keyPassword / keyAlias
3. ./gradlew assembleRelease
4. APK 输出到 app/build/outputs/apk/release/app-release.apk
```

CI / Play Console 上架（国内应用商店）详见 `docs/release/app-store-hardening.md`。

---

## 🔐 权限说明

| 权限 | 用途 | 必需性 |
|---|---|---|
| `READ_MEDIA_VIDEO` (API 33+) | 读取视频课程 | 可选（仅导入本地视频需要） |
| `READ_MEDIA_AUDIO` (API 33+) | 读取音频课程 | 可选 |
| `READ_EXTERNAL_STORAGE` (API ≤32) | 旧版本外部存储 | 可选 |
| `RECORD_AUDIO` | 跟读练习录音 | **必需** |
| `INTERNET` | 不需要 | 已删除（完全离线） |

**Android 11+ package visibility**：通过 `<queries>` 声明 `android.intent.action.TTS_SERVICE`，否则 PackageManager 返回空，**TTS 引擎不可见**。

---

## 📱 设备兼容性

| 设备 | 状态 |
|---|---|
| 小米 Mi 11 CN（主测设备） | ✅ 全部功能正常 |
| 华为 / OPPO / vivo | ✅ M3 标准 |
| Android 15+ 设备 | ✅ 16KB 对齐已修复 |
| 折叠屏 | ⚠️ 未测试 |

---

## 🤝 跨平台协作

iOS 对应项目：[EchoLing-iOS](https://github.com/ryming213/EchoLing-iOS) （SwiftUI + MVVM，架构对齐，独立维护）。

数据模型（Course / Sentence / Word / LearningProgress）和 Repository 接口设计**保持跨平台一致**。修改 Domain 层模型时需考虑 iOS 端兼容性。

---

## 📚 文档

- `CLAUDE.md` — 项目执行手册（架构 / 品牌色 / 12.x 改动历史）
- `PLAN.md` — 产品需求文档（功能优先级）
- `docs/superpowers/specs/` — 设计 spec
- `docs/superpowers/plans/` — 实现 plan
- `docs/release/app-store-hardening.md` — 国内应用商店上架指南
- `scripts/patch_native_libs_16kb.py` — 16KB 对齐 Layer 1
- `scripts/repack_apk_16kb.py` — 16KB 对齐 Layer 2 + 重新签名

---

## 🛠️ 已知限制

- **HEVC / DTS**：ExoPlayer 无内置 DTS 解码器，导入的 MKV + DTS 音频无声。解决：ffmpeg `-c:v copy -c:a aac -b:a 192k -ac 2 -c:s copy` 重封装。
- **Vosk small 模型 WER ~10%**：小模型固有上限。如需更高识别率可换 `vosk-model-en-us-0.22-lgraph`（APK +92 MB）。
- **Release 暂未启用 R8 混淆**（CLAUDE.md §9.3）：正式发布前需启用。
- **数据库 destructive migration**：DB v5，升级会清空学习进度（v6 之前用 `fallbackToDestructiveMigration()` 兜底）。
- **ffmpeg-kit-min-gpl（自动字幕生成）**: auto-subtitle pipeline uses the GPL flavor of ffmpeg-kit, which forces the entire APK to be GPL-licensed at distribution. To switch to LGPL, would lose DTS / FLAC / Opus support. AAR is vendored at `app/libs/ffmpeg-kit-min-gpl-6.0-2.aar` (~35 MB) because Arthenica withdrew Maven Central hosting in 2025.

---

## 🏷️ 版本历史

- **v1.0** (2026-07-15) — 首个 release APK 发布
  - 闪卡 per-category 进度持久化（Room v5）
  - 离线 STT + WordMatcher 容错匹配
  - 多 Tab 切换方向感知（Pager 架构，§12.27）
  - Edge-to-edge 状态栏修复
  - 11 个词库分类

---

## 📄 License

MIT License — 详见 [LICENSE](LICENSE)

---

## 🙋 维护者

- [@ryming213](https://github.com/ryming213)
- 项目设计文档 + 12.x 历史变更记录在 `CLAUDE.md`