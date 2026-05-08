# EchoLing

一款支持字幕播放和跟读练习的英语学习 Android 应用。

## 功能特点

### 课程管理
- 导入本地音视频文件和字幕（SRT/ASS 格式）
- 课程列表展示，支持删除操作
- 显示课程难度、时长、音频/字幕状态

### 听力练习
- 字幕逐句播放，点击句子自动播放该句后停止
- 单词遮挡/显示模式：隐藏单词 → 点击显示 → 再次点击隐藏
- 字幕模式切换：双语 / 仅英语 / 仅中文
- 播放速度调节（0.5x - 2.0x）
- 句子循环播放
- 前后跳转 5 秒

### 跟读练习（Shadowing）
- 录音跟读：录下自己的发音并回放对比
- 播放录音、停止录音操作

### 词汇本
- 收藏陌生单词
- 查看已收藏的单词列表

### 学习统计
- 累计学习时长
- 已学句子数
- 词汇量统计

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **音频播放**: Media3 ExoPlayer
- **本地存储**: Room Database
- **字幕解析**: 自定义 SRT/ASS 解析器

## 项目结构

```
app/src/main/java/com/echoling/app/
├── data/                    # 数据层
│   ├── local/              # 本地存储（Room DB）
│   │   ├── db/             # Database, DAO, Entity
│   │   └── DatabaseSeeder.kt
│   └── repository/         # Repository 实现
├── domain/                  # 领域层
│   ├── model/              # 数据模型
│   └── repository/         # Repository 接口
├── di/                      # 依赖注入模块
├── player/                  # 音频播放与字幕处理
│   ├── AudioPlayer.kt
│   ├── PlaybackState.kt
│   └── subtitle/           # 字幕解析（SRT/ASS）
├── presentation/            # 展示层
│   ├── MainActivity.kt
│   ├── viewmodel/          # ViewModel
│   └── ui/
│       ├── screens/        # 各功能界面
│       ├── navigation/     # 导航
│       └── theme/          # 主题样式
└── speech/                  # 语音录制
    └── VoiceRecorder.kt
```

## 开发环境

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2

## 权限说明

| 权限 | 用途 |
|------|------|
| READ_MEDIA_VIDEO | 读取视频文件 |
| READ_MEDIA_AUDIO | 读取音频文件 |
| RECORD_AUDIO | 录音跟读 |

## License

MIT License
