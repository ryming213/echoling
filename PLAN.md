# 英语学习APP（Echo Ling）开发计划

> 文档定位：Claude Code 安卓开发执行总纲 | 对标产品：听典英语 | 核心方向：原生安卓英语精听学习APP | 版本：V1.0

---

## 一、项目基础纲领

### 1.1 项目核心定位
以「可理解性输入」英语学习理论为核心，1:1复刻听典英语的核心架构，打造一款**无广告、轻量化、离线优先、高沉浸感**的安卓原生英语学习APP。

核心功能：「逐句精听+跟读模仿+生词闭环+进度追踪」

### 1.2 目标用户
- 有英语听力/口语提升需求的学生、职场人、英语爱好者
- 核心诉求：高效精听、沉浸式跟读、碎片化学习

### 1.3 核心开发原则
1. **MVP优先**：先完成核心学习闭环，再迭代进阶功能
2. **对标优先**：核心功能、UI交互优先对齐听典英语
3. **原生优先**：全程采用安卓官方推荐原生技术栈
4. **离线优先**：核心功能优先保障离线可用
5. **低耦合高内聚**：模块化开发，便于迭代与测试

---

## 二、技术栈与架构规范

### 2.1 基础开发约束
| 约束项 | 规范 |
|--------|------|
| 开发语言 | 100% Kotlin |
| 安卓兼容 | API 26 (Android 8.0) ~ API 34 (Android 14) |
| 构建工具 | Gradle Kotlin DSL |
| 包名 | com.echoling.app |

### 2.2 核心技术栈
| 技术层级 | 选型 | 用途 |
|----------|------|------|
| UI框架 | Jetpack Compose + Material Design 3 | 全页面UI |
| 架构模式 | MVVM + Clean Architecture | 代码分层 |
| 依赖注入 | Hilt | 依赖管理 |
| 音频播放 | ExoPlayer (Media3) | 音频播放、变速、后台 |
| 本地数据库 | Room | 数据持久化 |
| 异步处理 | Kotlin Coroutines + Flow | 异步逻辑 |
| 导航框架 | Jetpack Navigation Compose | 页面路由 |

### 2.3 核心架构分层
```
com.echoling.app
├── presentation/          # UI层
│   ├── ui/screens/       # 页面
│   ├── ui/components/    # 通用组件
│   ├── ui/navigation/    # 导航
│   ├── ui/theme/         # 主题
│   └── viewmodel/        # ViewModel
├── domain/               # 域层
│   ├── model/           # 数据模型
│   └── repository/      # 仓库接口
├── data/                 # 数据层
│   ├── repository/      # 仓库实现
│   ├── local/db/        # Room数据库
│   └── datasource/      # 数据源
├── player/               # 播放引擎模块
├── speech/               # 语音处理模块
├── di/                   # Hilt模块
└── utils/                # 工具类
```

---

## 三、核心功能模块（优先级分级）

### P0 - MVP核心必做
| 模块 | 功能 |
|------|------|
| 项目骨架 | 项目初始化、依赖配置、架构搭建、Hilt配置、主题导航 |
| 播放引擎 | ExoPlayer封装、0.5x-2.0x变速、AB复读、单句循环 |
| 精听主界面 | 沉浸式UI、逐句高亮、播放进度联动 |
| 字幕解析 | SRT/ASS字幕解析、与音频时间轴绑定 |
| 课程体系 | 课程列表、课程详情、示范课程 |
| 进度持久化 | Room存储学习进度、恢复上次位置 |

### P1 - 核心体验完善
| 模块 | 功能 |
|------|------|
| 点击查词 | 点击单词查词、一键收藏生词 |
| 生词本 | 生词管理、已掌握/未掌握标记 |
| 跟读录音 | 单句跟读、录音权限适配 |
| 离线缓存 | 课程下载/删除、缓存管理 |

### P2 - 进阶功能
| 模块 | 功能 |
|------|------|
| 自定义课程导入 | 导入本地音频+字幕文件 |
| 学习数据统计 | 学习日历、时长统计、完成率 |
| 锁屏播放 | 锁屏界面控制、线控适配 |
| 字幕模式切换 | 纯英文/双语/纯中文 |

---

## 四、数据模型

### 4.1 课程实体 CourseEntity
```kotlin
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val courseId: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val audioUri: String?,
    val videoUri: String?,
    val subtitleUri: String?,
    val durationMs: Long,
    val totalSentences: Int,
    val thumbnailUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

> **注意**：以上为设计参考模型。实际代码中的 Entity 定义是权威来源，详见 `data/local/db/entity/CourseEntity.kt`。

### 4.2 句子实体 SentenceEntity
```kotlin
@Entity(
    tableName = "sentences",
    primaryKeys = ["courseId", "sentenceId"]
)
data class SentenceEntity(
    val courseId: String,
    val sentenceId: Int,
    val contentEn: String,
    val contentCn: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isCompleted: Boolean = false,
    val isTested: Boolean = false
)
```

> **注意**：以上为设计参考模型。实际代码中的 Entity 定义是权威来源，详见 `data/local/db/entity/SentenceEntity.kt`。字段名以代码为准（`isCompleted`/`isTested`）。

### 4.3 学习进度实体 LearningProgressEntity
```kotlin
@Entity(tableName = "learning_progress")
data class LearningProgressEntity(
    @PrimaryKey val courseId: String,
    val currentPositionMs: Long,
    val currentSentenceId: Int,
    val learnedSentences: Int,
    val totalLearnTimeMs: Long,
    val lastLearnTime: Long,
    val finishRate: Float
)
```

### 4.4 生词实体 WordEntity
```kotlin
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val word: String,
    val phonetic: String,
    val translation: String,
    val exampleSentence: String,
    val sourceCourseId: String,
    val sourceSentenceId: Int,
    val isMastered: Boolean = false,
    val collectedAt: Long,
    val reviewCount: Int = 0,
    val nextReviewTime: Long
)
```

---

## 五、分阶段开发里程碑

| 阶段 | 名称 | 核心交付物 |
|------|------|------------|
| 阶段1 | 项目骨架搭建 | 可编译运行的项目、架构分层、Hilt配置、主题导航 |
| 阶段2 | 播放引擎与主界面 | ExoPlayer封装、精听UI、字幕解析、播放控制 |
| 阶段3 | MVP闭环 | 课程列表/详情、进度持久化、全流程闭环 |
| 阶段4 | P1模块开发 | 查词、生词本、跟读录音、离线缓存 |
| 阶段5 | 性能优化与迭代 | P2模块、兼容性优化、测试覆盖 |

---

## 六、开发规范

### 分层调用规则
- UI层 仅访问 ViewModel
- ViewModel 仅访问 UseCase
- UseCase 仅访问 Repository（接口）
- Repository 实现 仅访问数据源（Dao）

> **当前状态**：UseCase 层已实施（14个 UseCase，位于 `domain/usecase/`）。所有 ViewModel 通过 UseCase 间接调用 Repository。UseCase 使用 `@Singleton` + `@Inject constructor` 由 Hilt 自动注入。

### 异步处理规则
- 所有异步操作通过 Coroutines+Flow 实现
- IO 操作必须指定 IO 调度器
- UI 更新必须在 Main 调度器

### 权限规范
- 所有权限申请必须先判断是否授予
- 禁止强制申请非必要权限

---

## 七、风险预案

| 风险类型 | 应对预案 |
|----------|----------|
| ExoPlayer 精度不足 | 封装时间轴校准工具，优化字幕解析逻辑 |
| 机型兼容性问题 | 做好异常捕获，提供降级方案 |
| 内存占用过高 | LazyColumn懒加载，优化缓存策略 |
