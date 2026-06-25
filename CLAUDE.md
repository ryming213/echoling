# CLAUDE.md - 英语学习APP（Echo Ling）Claude Code 执行手册

> 文档定位：Claude Code 开发行为唯一约束准则 | 配套文件：同根目录 `PLAN.md` | 生效优先级：本文件为开发执行最高优先级规则，与 `PLAN.md` 冲突时以 `PLAN.md` 为准
> 核心作用：明确告知 Claude Code 项目操作规则、代码规范、执行边界与命令体系，杜绝代码幻觉，保障所有输出完全符合项目预期

---

## 一、项目核心元数据

| 配置项 | 固定值 | 不可变更说明 |
|--------|--------|--------------|
| 项目正式名称 | Echo Ling | 所有代码、注释、配置中的项目名称统一使用该名称 |
| 项目类型 | 安卓原生APP | 禁止转为跨平台项目，全程原生开发 |
| 核心对标产品 | 听典英语 | 所有核心功能、UI交互、体验逻辑必须优先对齐听典英语 |
| 开发语言 | 100% Kotlin | 禁止引入任何Java代码 |
| 目标平台 | 安卓Android | 最低兼容API 26，目标SDK API 34 |
| 构建工具 | Gradle Kotlin DSL | 禁止使用Groovy脚本 |
| 应用包名 | com.echoling.app | 所有代码、配置中的包名必须完全一致 |
| 主代码目录 | app/src/main/java/com/echoling/app | 所有业务代码存放于此 |
| 资源目录 | app/src/main/res | 图片、主题、字符串等资源 |
| 核心开发框架 | Jetpack Compose + MVVM + Clean Architecture | 禁止使用XML布局 |
| iOS 对应项目 | EchoLing-iOS (SwiftUI) | 位于 c:\Users\MING\EchoLing-iOS，架构对齐但独立维护 |

### 1.1 跨平台协作注意事项

- iOS 项目 EchoLing-iOS 使用 SwiftUI + MVVM，架构与 Android 端对齐
- 数据模型（Course, Sentence, Word, LearningProgress）、Repository 接口设计保持跨平台一致性
- 字幕解析逻辑（SRT/ASS/LRC）两端独立实现，但解析结果格式统一
- 修改 Domain 层模型时需考虑 iOS 端兼容性
- iOS 项目目录: `c:\Users\MING\EchoLing-iOS`

---

## 二、最高优先级执行准则

1. **先读再写原则**：
   a. 每次执行开发任务前，必须读取本文件、`PLAN.md`
   b. 涉及文件修改前，必须先用 Read 工具读取目标文件及所有引用它的文件
   c. 涉及新文件创建前，必须先用 Glob 确认文件不存在，用 Grep 确认无命名冲突
   d. 编译错误修复前，必须完整读取错误信息和相关源文件，禁止猜测式修复

2. **编译优先原则**：任何代码修改后必须执行 `assembleDebug` 检查编译
3. **架构合规原则**：严格遵循 Clean Architecture 分层规则（UI → ViewModel → UseCase → Repository → Data），禁止跨层调用
4. **MVP优先原则**：按 `PLAN.md` 定义的功能优先级开发
5. **离线优先原则**：核心功能必须优先保障离线可用
6. **安全合规原则**：严格遵循安卓官方权限规范

---

## 三、项目目录结构

```
com.echoling.app
├── EchoLingApplication.kt          # 应用入口 (@HiltAndroidApp)
├── presentation/                    # UI 层
│   ├── MainActivity.kt             # 单一Activity (@AndroidEntryPoint)
│   ├── viewmodel/                  # ViewModel（8个，通过Hilt注入UseCase）
│   │   ├── HomeViewModel.kt
│   │   ├── CourseListViewModel.kt
│   │   ├── CourseDetailViewModel.kt
│   │   ├── PracticeViewModel.kt    # 最复杂VM：播放/字幕/录音/进度
│   │   ├── VocabularyViewModel.kt
│   │   ├── StatisticsViewModel.kt
│   │   ├── ImportViewModel.kt
│   │   └── SettingsViewModel.kt
│   └── ui/
│       ├── screens/
│       │   ├── home/HomeScreen.kt
│       │   ├── course/CourseListScreen.kt, CourseDetailScreen.kt
│       │   ├── import/ImportScreen.kt
│       │   ├── practice/
│       │   │   ├── PracticeScreen.kt      # Tab容器（泛听/精听/测试）
│       │   │   ├── ListeningPage.kt       # 泛听页面
│       │   │   ├── SpeakingPage.kt        # 精听+复述页面
│       │   │   └── TestingPage.kt         # 测试页面
│       │   ├── vocabulary/VocabularyScreen.kt
│       │   ├── recite/                          # 记单词 tab（详见 §12.19-12.23）
│       │   │   ├── ReciteScreen.kt             # 5 category 卡片 picker
│       │   │   ├── ReciteViewModel.kt          # 读 manifest + 订阅 recite_progress
│       │   │   ├── CategoryStudyScreen.kt      # 单 category 闪卡（Y 轴翻转）
│       │   │   └── CategoryStudyViewModel.kt   # 闪卡状态机 + 进度持久化
│       │   ├── statistics/StatisticsScreen.kt
│       │   ├── settings/SettingsScreen.kt
│       │   └── about/AboutScreen.kt
│       ├── components/              # [待建设] 通用组件目录
│       ├── navigation/
│       │   ├── Screen.kt           # 路由定义（9个路由：含Settings/About）
│       │   └── NavGraph.kt         # 导航图
│       └── theme/
│           ├── Color.kt
│           ├── Theme.kt
│           └── Type.kt
├── domain/                          # 域层
│   ├── model/                       # 数据模型
│   │   ├── Course.kt
│   │   ├── Sentence.kt
│   │   ├── LearningProgress.kt
│   │   ├── Word.kt
│   │   ├── DictCategory.kt                 # 词库分类（id/name/desc/size）
│   │   └── DictEntry.kt                    # 词库条目（word/phonetic/pos/translation）
│   ├── repository/                  # 仓库接口（5个）
│   │   ├── CourseRepository.kt
│   │   ├── SentenceRepository.kt
│   │   ├── LearningProgressRepository.kt
│   │   ├── WordRepository.kt
│   │   └── DictionaryRepository.kt         # 词库查询（按 word 查 DictEntry）
│   └── usecase/                     # UseCase层（21个，每个封装单一业务逻辑）
│       ├── GetCoursesUseCase.kt
│       ├── GetCourseDetailUseCase.kt
│       ├── GetCourseSentencesUseCase.kt
│       ├── UpdateSentenceCompletedUseCase.kt
│       ├── UpdateSentenceTestedUseCase.kt
│       ├── SyncSentencesUseCase.kt         # 字幕解析后同步句子到数据库
│       ├── GetWordsUseCase.kt
│       ├── SaveWordUseCase.kt
│       ├── ToggleWordMasteredUseCase.kt
│       ├── DeleteWordUseCase.kt
│       ├── GetStatisticsUseCase.kt
│       ├── ImportCourseUseCase.kt
│       ├── SaveProgressUseCase.kt
│       ├── GetProgressUseCase.kt
│       ├── DeleteCourseUseCase.kt
│       ├── LookupWordUseCase.kt            # 词库查询（首查 DictEntry）
│       ├── GetDictionaryCategoriesUseCase.kt    # 读取 vocab_manifest.json
│       ├── GetDictionaryWordsInCategoryUseCase.kt # 读单 category 的词
│       ├── GetReciteProgressUseCase.kt      # 读单 category 进度
│       ├── SaveReciteProgressUseCase.kt     # 写单 category 进度
│       └── ObserveAllReciteProgressUseCase.kt # 订阅所有 category 进度
├── data/                            # 数据层
│   ├── repository/                  # 仓库实现（5个Impl）
│   │   ├── CourseRepositoryImpl.kt
│   │   ├── SentenceRepositoryImpl.kt
│   │   ├── LearningProgressRepositoryImpl.kt
│   │   ├── WordRepositoryImpl.kt
│   │   └── DictionaryRepositoryImpl.kt   # vocab_manifest.json + vocab_*.json 加载 + 嵌套/扁平 schema 嗅探
│   ├── local/
│   │   └── db/                      # Room数据库
│   │       ├── EchoLingDatabase.kt  # v5, destructive migration（详见 §9.2）
│   │       ├── entity/              # 5个Entity
│   │       │   ├── CourseEntity.kt
│   │       │   ├── SentenceEntity.kt
│   │       │   ├── LearningProgressEntity.kt
│   │       │   ├── WordEntity.kt
│   │       │   └── ReciteProgressEntity.kt    # 闪卡 per-category 进度（§12.19）
│   │       └── dao/                 # 5个Dao
│   │           ├── CourseDao.kt
│   │           ├── SentenceDao.kt
│   │           ├── LearningProgressDao.kt
│   │           ├── WordDao.kt
│   │           └── ReciteProgressDao.kt       # observeAll + getByCategory + upsert
│   └── datasource/                  # [待建设] 数据源目录
├── player/                          # 播放引擎模块
│   ├── AudioPlayer.kt              # ExoPlayer封装 (@Singleton)
│   ├── PlaybackState.kt            # 播放状态数据类
│   ├── TranslationService.kt       # 百度翻译API (@Singleton)
│   └── subtitle/
│       ├── Subtitle.kt
│       ├── SubtitleMode.kt
│       ├── SubtitleParser.kt       # 解析器接口
│       ├── SubtitleParserFactory.kt
│       ├── SrtParser.kt
│       ├── AssParser.kt
│       └── LrcParser.kt
├── speech/                          # 语音处理模块
│   └── VoiceRecorder.kt            # MediaRecorder封装 (@Singleton)
├── di/                              # Hilt依赖注入模块
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   └── PlayerModule.kt
└── utils/                           # [待建设] 工具类目录
```

---

## 四、常用执行命令

| 命令用途 | 执行命令 |
|----------|----------|
| Debug构建 | `./gradlew assembleDebug` |
| 安装到设备 | `./gradlew installDebug` |
| 清理构建 | `./gradlew clean` |
| 运行测试 | `./gradlew test` |
| Release构建 | `./gradlew assembleRelease` |

---

## 五、代码编写规范

### 5.1 Kotlin 语言规范
1. 遵循 Kotlin 官方编码规范，优先使用不可变类型（val）
2. 可空类型必须做非空判断，禁止使用 !! 强转
3. 禁止使用过时、废弃的 API

### 5.2 Jetpack Compose UI规范
1. 所有 UI 必须使用 Jetpack Compose，禁止 XML 布局
2. 遵循 Material Design 3 规范，颜色/字体/尺寸引用 theme 常量
3. Composable 函数必须单一职责，超大页面必须拆分
4. UI 状态必须通过 ViewModel 的 StateFlow/State 暴露
5. 列表必须使用 LazyColumn/LazyRow，禁止 Column/Row 加载长列表

### 5.3 架构分层规范（已实施 UseCase 层）

**当前架构调用链：**
```
UI (Composable) → ViewModel → UseCase → Repository (interface) → RepositoryImpl → Dao → Room DB
```

1. **UI层**：仅负责 UI 渲染与用户事件转发，无业务逻辑
2. **ViewModel层**：持有 UI 状态，调用 UseCase 执行业务逻辑
3. **UseCase层**：每个 UseCase 封装单一业务逻辑，通过 `@Inject constructor` + `@Singleton` 由 Hilt 注入。命名规范：动词+名词（如 `GetCoursesUseCase`、`SaveWordUseCase`）
4. **Data层**：Repository 统一数据出口，Dao 仅负责数据库操作
5. **核心模块层**：播放引擎（player/）、语音处理（speech/）封装为独立模块，与业务解耦

**UseCase 创建规则：**
- 每个 UseCase 必须封装单一职责
- 所有 UseCase 使用 `@Singleton` + `@Inject constructor`，无需额外 Module
- 当 ViewModel 中的 Repository 调用包含业务逻辑（如数据校验、组合多个 Repository 调用），必须抽取为 UseCase

### 5.4 跟读练习页面规范（PracticeScreen）
跟读练习页面采用三 Tab 页架构，通过 TabRow 切换。切换页面时自动停止播放。

#### 页面布局总览

```
┌─────────────────────────────────────┐
│  TopAppBar: 跟读练习 + 字幕模式切换  │
├─────────────────────────────────────┤
│  TabRow: [泛听] [精听] [测试]        │
├─────────────────────────────────────┤
│                                     │
│           页面内容区                 │
│         (Box weight=1f)             │
│                                     │
├─────────────────────────────────────┤
│        底部控制栏（各页面不同）       │
└─────────────────────────────────────┘
```

#### 5.4.1 泛听页面（ListeningPage）

**布局顺序（从上到下）：**
1. 视频播放器（如果有视频，且 isVideoMode=true）
2. 进度条 + 左侧播放按钮（Slider 样式）
3. 字幕列表（LazyColumn，可滚动）

**进度条组件：**
- 左侧：播放/暂停按钮（圆形，主色背景）
- 右侧：Slider 进度条（带圆圈 Thumb）
- 可拖动调整播放位置

**底部控制栏（三个按钮）：**
```
[字幕显示/隐藏] [播放/暂停] [速度选择]
```
- 字幕按钮：显示/隐藏当前字幕卡片
- 播放按钮：播放当前字幕
- 速度按钮：下拉菜单选择 0.5x/0.75x/1.0x/1.25x/1.5x/2.0x

**字幕列表项：**
- 当前播放句子高亮（primaryContainer 背景色 + 左侧竖线指示器）
- 长句子自动换行显示（maxLines=Int.MAX_VALUE）
- 点击列表项播放对应句子
- 句子在隐藏和显示的时候，字体的大小必须保持一致

**特点：**
- 页面切换时自动停止播放
- 列表项点击播放单个句子

#### 5.4.2 精听页面（SpeakingPage）

**布局顺序（从上到下）：**
1. 视频播放器（如果有视频）
2. 句子选择下拉框（当前句子 / 总数）
3. 当前句子卡片（英文单词横向排列 + 中文翻译）
4. 录音播放卡片（如果存在录音）
5. 底部控制栏

**句子卡片内容：**
```
┌─────────────────────────────────────┐
│ 句子 N          [✓完成标记]          │
├─────────────────────────────────────┤
│  English words...    中文翻译        │
│  (横向排列，可点击隐藏/显示单词)       │
└─────────────────────────────────────┘
```
- 英文单词横向排列，单词间有间距
- 点击单词可隐藏/显示（用于练习回忆）
- 长按单词弹出保存单词对话框
- 右上角完成标记按钮

**底部控制栏（五个按钮）：**
```
[上一句] [播放当前] [按住录音] [播放录音] [下一句]
```
- 播放当前：只播放当前句子，播放完停留在当前句子（isSinglePlayMode）
- 按住录音：Press and hold 录音，松开停止
- 上一句/下一句：切换句子并清除单句播放模式

**单句播放模式（isSinglePlayMode）：**
- 点击播放按钮时启用
- 句子播放结束后停留在当前句子，不自动跳转
- 手动点击上一句/下一句或选择句子时禁用

#### 5.4.3 测试页面（TestingPage）

**布局顺序（从上到下）：**
1. 测试进度头部（已测试数/总数 + 开始测试按钮 + 进度条）
2. 测试内容卡片（隐藏单词，点击揭示）
3. 底部控制栏

**测试卡片：**
- 所有单词默认隐藏（显示灰色方块）
- 点击单词逐步揭示
- 全部揭示后显示中文翻译

**底部控制栏（三个按钮）：**
```
[上一题] [播放音频] [标记完成/下一题]
```

#### 5.4.4 共享播放控制

精听和测试页面共享播放控制栏（内联在 PracticeScreen.kt 中定义），泛听页面使用独立的进度条和底部控制栏。

**进度条：**
- Slider 样式，可拖动
- 左侧显示当前时间，右侧显示总时长

**控制按钮：**
- 循环、后退10秒、播放/暂停、前进10秒、速度选择

---

**ViewModel 状态管理：**
- `PracticePage` 枚举控制当前页面切换
- `SentenceState` 缓存句子完成/测试状态
- `TestState` 管理测试模式状态
- `isSinglePlayMode` 控制精听单句播放模式
- `markSentenceCompleted()` / `markSentenceTested()` 通过 UseCase 持久化状态

**数据模型扩展**：
- SentenceEntity 包含 `isCompleted`、`isTested` 字段
- 通过 SentenceDao 的 `updateCompletedStatus()` / `updateTestedStatus()` 更新
- 数据库版本号：2（带 fallbackToDestructiveMigration）

### 5.5 Hilt 依赖注入
- 所有 ViewModel 通过 `@HiltViewModel` + `@Inject constructor` 注入
- 所有 UseCase 通过 `@Singleton` + `@Inject constructor` 自动注册（无需 Module）
- Repository 通过 `RepositoryModule` 的 `@Binds` 绑定接口与实现
- 禁止手动 new 创建实例

### 5.6 Room 数据库
- Entity 必须定义主键、表名
- Dao 接口必须使用 suspend 函数/Flow 返回数据
- 数据库必须为单例，通过 Hilt 注入
- **注意**：当前使用 `fallbackToDestructiveMigration()`，数据库结构变更时会丢失数据（见9.2节）

### 5.7 Kotlin Coroutines + Flow
- 耗时操作必须使用协程，指定正确调度器
- IO 调度器处理数据库/文件操作
- Flow 必须正确处理生命周期

---

## 六、绝对禁止事项

1. 禁止引入任何 Java 代码
2. 禁止使用 XML 布局、View 系统
3. 禁止跨层调用（UI不可直接调用Repository，ViewModel不可直接调用Dao）
4. 禁止主线程执行数据库、文件、网络等耗时操作
5. 禁止在 ViewModel、UseCase 中持有 Context、Activity 引用（AndroidViewModel除外，仅限必要场景）
6. 禁止生成无法正常编译的代码
7. 禁止硬编码颜色、字体、尺寸、字符串、路由地址
8. 禁止在源码树中创建备份文件（.bak、_backup.kt等），使用 git 管理版本历史

---

## 七、配套文件联动规则

1. 本文件与同根目录 `PLAN.md` 强绑定，所有开发任务必须同时遵循两个文件
2. 本文件与 `PLAN.md` 冲突时，以 `PLAN.md` 为准
3. 每次启动新的开发任务，必须先重新读取本文件与 `PLAN.md`

---

## 八、Claude Code 协作规范

### 8.1 Memory（记忆）使用时机
- 当项目架构发生重大变更时，保存 memory 记录决策背景
- 当发现非显而易见的模式或约定时（如特定 API 的使用限制、编译问题的解决方案）
- 当完成一个开发阶段后，总结关键经验
- 不要为每个小任务保存 memory，避免信息过载

### 8.2 Git 工作流规范
- 分支命名：`feature/<功能名>`、`fix/<问题描述>`、`refactor/<模块名>`
- 提交信息格式：`<type>: <简短描述>`（type = feat/fix/refactor/docs/chore）
- 提交前必须：`./gradlew assembleDebug` 编译通过
- 禁止提交：备份文件（.bak, _backup.*）、构建产物（build/）、IDE 配置文件（.idea/）
- 提交信息末尾添加：`Co-Authored-By: Claude <noreply@anthropic.com>`

### 8.3 测试规范
- **当前状态**：项目声明了 JUnit、Espresso、Compose UI Test 依赖，但尚无测试文件
- **计划**：在 P1 阶段完成后开始添加单元测试
- **优先测试**：UseCase 业务逻辑、Repository 实现、字幕解析器（SrtParser/AssParser/LrcParser）
- **测试文件位置**：`app/src/test/`（单元测试）、`app/src/androidTest/`（UI测试）
- **测试框架**：JUnit 4 + MockK（需添加依赖）

### 8.4 编译错误处理流程
1. 遇到编译错误时，不要盲目猜测修复，先完整读取错误信息
2. 检查 import 语句是否完整（缺少 import 是最常见的编译错误）
3. 检查依赖版本兼容性（尤其是 Compose BOM、KSP、Hilt 版本对齐）
4. 如果错误涉及生成的代码（Room/Hilt/Dagger/KSP），先执行 `./gradlew clean` 再 rebuild
5. 对于 Room schema 变更错误，当前使用 destructive migration，需要卸载 App 重装或清除数据

### 8.5 字符串资源管理（国际化准备）
- **当前状态**：大部分 UI 字符串直接硬编码在 Composable 中，中英文混合。`strings.xml` 目前仅含 `app_name`
- **目标**：逐步将用户可见的字符串迁移到 `res/values/strings.xml`
- **新功能开发**：所有用户可见字符串必须定义在 `strings.xml` 中
- **使用方式**：`stringResource(R.string.xxx)` 而非硬编码文本
- **注意**：AboutScreen 和 SettingsScreen 中硬编码的中文文本需要优先迁移

---

## 九、已知问题与待办事项

### 9.1 证书固定（Certificate Pinning）无效
- **位置**：`TranslationService.kt`（OkHttp CertificatePinner）、`network_security_config.xml`（pin-set）
- **问题**：OkHttp CertificatePinner 和 XML pin-set 都使用占位符 SHA-256 哈希值（`AAAA...`、`BBBB...`）
- **影响**：百度翻译 API 调用失败，翻译功能不可用
- **修复**：从 `fanyi-api.baidu.com` 获取真实证书指纹并替换占位符
- **临时方案**：开发阶段可注释掉 `certificatePinner()` 调用

### 9.2 Room 破坏性迁移
- **位置**：`EchoLingDatabase.kt`
- **当前版本**：`v5`（v4 → v5 在 §12.19 新增 `recite_progress` 表：currentIndex / knownCount / unknownCount / lastStudiedAt）
- **问题**：`fallbackToDestructiveMigration()` 在数据库版本升级时删除所有数据
- **影响**：App 升级时用户学习进度丢失（包括 `recite_progress` 闪卡进度 + 单词本）
- **计划**：在正式发布前替换为 Migration 策略
- **v3 → v4 历史**：扩展 `WordEntity` 加 `pos` 字段
- **v2 → v3 历史**：引入 `courses.courseName` 列，用 `MIGRATION_2_3` 显式迁移（destructive 仅作兜底）

### 9.3 Release 构建未启用混淆
- **位置**：`app/build.gradle.kts`（`isMinifyEnabled = false`）
- **问题**：Release 构建无代码混淆和优化
- **计划**：正式发布前启用 R8/ProGuard 并配置规则

### 9.4 无版本目录（Version Catalog）
- **问题**：所有依赖版本硬编码在 `build.gradle.kts` 中
- **计划**：迁移到 `gradle/libs.versions.toml` 统一管理版本

### 9.5 自定义字体未使用
- **位置**：`res/font/calibri.ttf`（~1.6MB）、`res/font/aptos.ttf`（~960KB）
- **问题**：`Type.kt` 中所有 TextStyle 使用 `FontFamily.Default`，未引用自定义字体
- **影响**：字体文件占用 APK 空间但无实际效果
- **处理**：要么在 Typography 中应用这些字体，要么删除以减少 APK 体积

### 9.6 测试文件缺失
- 依赖已声明但 `app/src/test/` 和 `app/src/androidTest/` 目录为空
- 计划在 P1 阶段完成后添加

### 9.7 PLAN.md 数据模型与代码不同步
- `SentenceEntity`：PLAN.md 中的 `isLearned/isRead/readScore` 字段名与代码中的实际字段不完全一致
- `CourseEntity`：PLAN.md 中部分字段的可空性与代码不一致
- **注意**：代码中的 Entity 定义是权威来源，PLAN.md 作为设计参考

---

## 十、导航路由完整清单

| 路由 | Screen常量 | NavGraph入口 | 说明 |
|------|-----------|-------------|------|
| `"home"` | `Screen.Home` | ✅ 已接入 | 首页 |
| `"course_list"` | `Screen.CourseList` | ✅ 已接入 | 课程列表 |
| `"course_detail/{courseId}"` | `Screen.CourseDetail` | ✅ 已接入 | 课程详情 |
| `"practice/{courseId}"` | `Screen.Practice` | ✅ 已接入 | 跟读练习 |
| `"vocabulary"` | `Screen.Vocabulary` | ✅ 已接入 | 生词本 |
| `"statistics"` | `Screen.Statistics` | ✅ 已接入 | 学习统计 |
| `"settings"` | `Screen.Settings` | ✅ 已接入 | 设置（百度翻译API配置） |
| `"about"` | `Screen.About` | ✅ 已接入 | 关于页面 |
| `"import"` | `Screen.Import` | ✅ 已接入 | 导入课程 |

---

## 十一、品牌视觉规范（图标 + 主题色）

> 锁定日期：2026-06-17。本节是品牌相关的唯一事实来源；修改图标/主题色时必须先读取本节。

### 11.1 App 图标

**资源位置**：
- 主图：`app/src/main/res/drawable/ic_app_icon.png`（约 1924×1976，单文件复用）
- 启动器：`mipmap-anydpi-v26/ic_launcher.xml` 和 `ic_launcher_round.xml`
  - **两层都引用 `@drawable/ic_app_icon`**（背景层 = 前景层 = 同一张图），不使用任何 foreground 蒙版

**视觉规格**：
- 形状：圆角矩形（圆角半径约 266 px，源码参考 `scripts/fill_background_gradient.py` 的二次方程求解）
- 背景：紫色线性渐变（**TOP `#A76CF1` → MID `#9457E8` → BOT `#7A2BE0`**），无白边、无缝
- 图标主体（声波轮廓）：与背景同色渐变（在渐变中"内嵌"）
- 中心白色圆圈 + 底部"listen"白色文字：**纯白 `#FFFFFF` 实心**，无杂色

**图像处理脚本**：`scripts/fill_background_gradient.py`
- 工作流程：识别图标体 bbox → 解二次方程求圆角半径 → 用纯几何 rounded-rectangle 重建 mask → 30 px 腐蚀排除抗锯齿 → 阈值 220 检测白文字/圆圈 → 两次 3×3 MaxFilter 膨胀 → 三层合成（背景渐变 / 图标体渐变 / 白色实心文字）
- **不要改阈值**（220）+ 2×膨胀 + 30 px 腐蚀的组合，否则会出现"白圈/文字不白"或"圆角出现红弧"的回归
- 输入备份：`scripts/ic_app_icon_original.png`（用户提供的原图，白底紫图白字）

### 11.2 主题色板（深紫系）

**位置**：`app/src/main/java/com/echoling/app/presentation/ui/theme/Color.kt`

| Token | 值 | 用途 |
|---|---|---|
| `Primary` | `#7C3AED`（violet-600） | 主强调色，匹配图标渐变底部 |
| `OnPrimary` | `#FFFFFF` | Primary 上的文字/图标 |
| `PrimaryContainer` | `#DDD6FE`（violet-200） | "Continue Learning" 卡片背景 |
| `OnPrimaryContainer` | `#2E1065` | 容器上的深色文字 |
| `Secondary` | `#8B5CF6`（violet-500） | 次级强调 |
| `Tertiary` | `#6D28D9`（violet-700） | 第三色 |
| `InversePrimary` | `#A78BFA`（violet-400） | 深色模式下的主色 |
| `SeedColor` | `#7C3AED` | 主题种子色 |

**禁止行为**：
- ❌ 改回青/蓝色系（青绿色 `#00897B` 等旧值已废弃）
- ❌ 在 Composable 中硬编码 `Color(0xFF...)`，必须引用 `MaterialTheme.colorScheme.*`
- ❌ 修改 `practice/*Page.kt` 中的状态色：`#4CAF50`（完成=绿）、`#E0E0E0`（进度条轨道=灰）—— 这些是语义色，不是主题色

### 11.3 Theme 关键配置

**位置**：`app/src/main/java/com/echoling/app/presentation/ui/theme/Theme.kt`

```kotlin
@Composable
fun EchoLingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // 关键：必须为 false
    content: @Composable () -> Unit
)
```

- **`dynamicColor = false` 是硬性要求**：开启后 Android 12+ 会用 Material You 从壁纸提取颜色（用户壁纸通常偏蓝/青），会覆盖我们的紫色主题。
- 任何后续重构如需调整此默认值，必须在 PR 中说明原因并验证深紫主题在 Android 12/13/14 设备上仍生效。

### 11.4 HomeScreen 图标 tint 修复

**位置**：`HomeScreen.kt:115-120`（`HeroSection` 内的 app icon）

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_app_icon),
    contentDescription = null,
    modifier = Modifier.size(80.dp),
    tint = Color.Unspecified  // 必填，否则位图会被乘成黑色
)
```

- **必须**设置 `tint = Color.Unspecified`，否则 Compose 默认 `tint = LocalContentColor.current`（黑色），位图颜色通道被乘以 0 → 整张图变黑。
- 此规则**仅适用于 painterResource 位图**。`ImageVector` 图标（如 `Icons.Default.Settings`）应使用 `MaterialTheme.colorScheme.primary` 等主题色，让 tint 正常生效。
- 缺失 import 会编译失败：需 `import androidx.compose.ui.graphics.Color`

### 11.5 视觉品牌一致性检查清单

修改任何涉及品牌色的代码前，**必须**核对：
1. [ ] 图标 drawable 是否仍指向 `ic_app_icon.png`（不是旧的默认图标）
2. [ ] `mipmap-anydpi-v26/*.xml` 的 background 和 foreground 是否都引用 `@drawable/ic_app_icon`
3. [ ] `Theme.kt` 的 `dynamicColor` 是否仍为 `false`
4. [ ] `Color.kt` 的 Primary 是否仍为 `#7C3AED`
5. [ ] 所有引用位图 painter 的 Icon 是否设置了 `tint = Color.Unspecified`
6. [ ] 启动页 `splash_image.png` 是 9:20（1080×2400），letterbox 兜底色是 `#9C84C2`（与图 BL/BR 角一致）
7. [ ] `splash_background.xml` 用 `gravity="center"`，不是 `"fill"`
8. [ ] `AppBottomBar.kt` 仍然是自定义紧凑版（60dp 栏 + 56×28dp indicator），没有被改回 M3 `NavigationBarItem`
9. [ ] 构建后执行 `./gradlew assembleDebug` 验证编译通过

### 11.6 启动页视觉规范

> 锁定日期：2026-06-19。启动页（cold-start splash）是品牌第二触点，规则与 App 图标同级。

**资源**：
- 启动图：`app/src/main/res/drawable-nodpi/splash_image.png`
  - **尺寸**：`1080×2400`（9:20 比例，DAR = 原图 1373:3051，**0 变形**）
  - **位置**：`drawable-nodpi/`（禁用密度缩放，固定像素）
  - **当前内容**：紫色渐变 + 绿苗 + 30 天时间轴 + 中文标题"不必急于求成，每天进步一点"
  - **修改方式**：新图必须是 9:20 原生比例（不要在 Compose / XML 层用 padding / letterbox 修补），9:20 图在 9:20 屏上像素级填满 0 letterbox

**Letterbox 兜底色**（`@color/splash_background`）：
- 当前值：`#9C84C2`（采自图像 BL/BR 角，肉眼无差别）
- **绝对不要用纯白 / 纯黑 / 与图像边缘无关的颜色**——在 9:21 屏 / 折叠屏 / 极少数异形屏上会暴露 letterbox

**`splash_background.xml` layer-list 规则**：
- 第一个 `<item>` 是纯色兜底层（`@color/splash_background`）
- 第二个 `<item>` 是 splash image
- **`gravity="center"`**（不是 `"fill"`）：保留原图比例，letterbox 由第一层颜色填充
- 任何想用 `"fill"` 让图撑满窗口的尝试都会导致上下压缩 / 左右拉伸

**主题切换**（消除 cold-start 白屏）：
- `AndroidManifest.xml` 的 launcher activity 必须用 `android:theme="@style/Theme.EchoLing.Splash"`
- `MainActivity.onCreate` 必须**在 `super.onCreate` 之前**调用 `setTheme(R.style.Theme_EchoLing)`，把主题切回正常 Compose 主题
- 顺序错了会导致白屏闪烁

**Compose 启动页**（`SplashScreen.kt`）：
- 1 秒 `delay` 由 `MainScaffold` 控制（`LaunchedEffect(Unit) { delay(1000) }`），不是 SplashScreen 自己
- `Box` 背景色 = `@color/splash_background`（与 XML 兜底色同步）
- `Image` 用 `ContentScale.Fit` + `Modifier.fillMaxSize()`，不要用 `ContentScale.Crop`

**禁止行为**：
- ❌ 把 splash 图放到 `drawable/`（会被密度缩放，导致 9:20 比例在 xxhdpi 屏上变形）
- ❌ 用 `gravity="fill"` 拉伸图
- ❌ 用 `ContentScale.Crop` 裁剪图
- ❌ 改 letterbox 兜底色为 `#FFFFFF` / 主题 surface 色（图片边缘是紫色，会出现白条）
- ❌ 删除 `setTheme(R.style.Theme_EchoLing)` 切换——会留白屏

---

## 十二、最近重要变更（v2026.06.19）

> 本节记录本次会话的所有 UI 改动。每条改动都包含：原因 / 关键文件 / 关键参数 / 后续修改时的注意事项。后续重构前先读本节，避免回滚已确认的方案。

### 12.1 启动页（Splash）从无到有

**改动**：
- 新建 `app/src/main/res/drawable/splash_background.xml`（layer-list）
- 新建 `app/src/main/res/drawable-nodpi/splash_image.png`（1080×2400 9:20）
- 新建 `app/src/main/java/com/echoling/app/presentation/ui/screens/splash/SplashScreen.kt`
- 修改 `themes.xml`：新增 `splash_background` 颜色（`#9C84C2`）+ `Theme.EchoLing.Splash` 子主题
- 修改 `AndroidManifest.xml`：launcher activity 主题切到 `Theme.EchoLing.Splash`
- 修改 `MainActivity.kt`：`onCreate` 顶部 `setTheme(R.style.Theme_EchoLing)` 切回正常主题
- 修改 `MainScaffold.kt`：1s `LaunchedEffect` 延迟后切到正常导航

**关键参数**（任何后续修改都不能动）：
- `gravity="center"`（不是 `fill`）
- `ContentScale.Fit`（不是 Crop）
- `delay(1000L)`（1 秒停留）
- `setTheme` 必须在 `super.onCreate` 之前

详见 §11.6 启动页视觉规范。

### 12.2 底部导航 AppBottomBar 自定义紧凑版

**原因**：M3 `NavigationBarItem` 硬约束 indicator 高度 32dp、整栏 80dp，无法直接缩小方框上下高度。

**改动**：
- `app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt` 全部重写
  - 不用 M3 `NavigationBar` / `NavigationBarItem`
  - 用 `Row` + `Box` + `rememberRipple` 自绘
  - 整栏高度 60dp（vs M3 默认 80dp）
  - Indicator 56×28dp 圆角 14dp（vs M3 默认 64×32 dp 16dp）
  - 图标 22dp，文字 `labelSmall` 11sp
  - 选中态 `primaryContainer` 胶囊，文字 + 图标用 `primary` 紫色
  - 整行 60dp 是点击区域（满足 48dp 最小命中）
  - `windowInsetsPadding(WindowInsets.navigationBars)` 让背景延伸到系统导航栏

**关键参数**：
- `ContainerColor = surface`，`tonalElevation = 3.dp`
- `MaterialTheme.colorScheme.primaryContainer` 高亮
- `labelSmall` 不是 `bodySmall`（更紧凑）

### 12.3 首页（Courses tab）空状态闪屏修复

**原因**：去掉 loading spinner 后，Room 首次查询返回空列表的 100~300ms 间隙内会先显示 "暂无课程"，再被实际数据替换，造成"先空后满"的闪烁。

**改动**：
- `CoursesViewModel.kt`：`CoursesUiState` 新增 `hasLoadedOnce: Boolean = false` 字段
  - 在 `combine(...) { ... }.collect { ... }` 块内 emit 时设为 `true`
- `CoursesScreen.kt`：把 `when` 分支改为只在 `hasLoadedOnce && courses.isEmpty()` 时才显示 `EmptyCourses`

**模式总结**（适用于所有"先空后满"问题）：
```kotlin
// ViewModel
data class UiState(
    val items: List<T> = emptyList(),
    val hasLoadedOnce: Boolean = false,  // 关键
)
// emit 时: copy(items = ..., hasLoadedOnce = true)

// Screen
when {
    uiState.hasLoadedOnce && uiState.items.isEmpty() -> EmptyState()
    else -> List(items = uiState.items)
}
```

### 12.4 三个 Practice 页面底部按钮加背景框

**改动**：
- `ListeningPage.kt` / `SpeakingPage.kt` / `TestingPage.kt`
  - 底部控制 Row 用 `Surface` 包裹：`RoundedCornerShape(24.dp)` + `surfaceVariant.copy(alpha=0.5f)` + `tonalElevation=2.dp`
  - Modifier: `fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp)`

**关键参数**：
- 形状 `RoundedCornerShape(24.dp)`（胶囊感，比卡片圆角大）
- 颜色 `surfaceVariant` 半透明（`alpha=0.5f`），不要用 `surfaceContainer`
- elevation `2.dp`（比 BottomBar 3dp 轻，因为是临时控件）

### 12.5 API 配置卡片字号缩放

**改动**：
- `TranslationApiCard.kt`：
  - 腾讯（3 个 field）和有道（2 个 field）的 `OutlinedTextField` 加 `textStyle = MaterialTheme.typography.bodyMedium`
  - 对应 `Text` label / placeholder 用 `style = MaterialTheme.typography.bodySmall`
- `GradingApiCard.kt`：
  - 整体 padding `20.dp → 16.dp`，`spacedBy(16.dp) → 12.dp`
  - App ID / App Key 字段加 `textStyle = bodyMedium`
  - "启用评分" 文案 `bodyMedium → bodySmall`
  - 标题行间距同步收紧

**模式**（API 配置卡片字段统一标准）：
```kotlin
OutlinedTextField(
    ...
    textStyle = MaterialTheme.typography.bodyMedium,  // 输入文字
    label = { Text(..., style = MaterialTheme.typography.bodySmall) },  // 标签
    placeholder = { Text(..., style = MaterialTheme.typography.bodySmall) },  // 占位
)
```

### 12.6 我的页面（Me）图标替换

**改动**：
- 新建 `app/src/main/res/drawable-nodpi/ic_me_page_icon.png`（1924×1976，**独立副本**）
  - **不直接引用** `R.mipmap.ic_launcher` / `R.drawable.ic_app_icon` —— 启动器图标被 mipmap 系统裁剪成不同密度版本，会变形
- `MeScreen.kt`：头像 `Image` 用 `painterResource(R.drawable.ic_me_page_icon)` + `Modifier.clip(RoundedCornerShape(16.dp))`

**注意**：
- ❌ 改回 `ic_app_icon`：会被密度缩放，圆角矩形变成不一致
- ❌ 删 `RoundedCornerShape(16.dp)`：会显示原始方角
- ❌ 给 `Image` 加 `tint = Color.Unspecified`：Image composable **没有** tint 参数（CLAUDE.md §11.4 规则仅适用于 Icon composable），编译会失败

### 12.7 已知问题（本次未修复）

| 问题 | 位置 | 影响 |
|---|---|---|
| 启动图更新要走 ffmpeg 命令（`scale=1080:2400`） | `Downloads/启动页1.png` → `splash_image.png` | 流程外操作，下次换图需要重新做 |
| `DailyStats.sentencesLearned` 累计重叠 | `StatisticsViewModel` | 7 天柱状图可能虚高（不在本次范围） |
| Release 未开混淆 / R8 | `app/build.gradle.kts` | 见 §9.3（不在本次范围） |

### 12.8 列表行高紧凑化（Courses / Vocabulary）

**原因**：原来的 `padding(16.dp)` + 4dp/8dp 内部 spacer 让每个列表项在屏幕上占 ~120dp，3 项就 ~360dp 屏高。改成上下 10dp + 2/6dp spacer，单项 ~100dp。

**改动**：

| 文件 | 改前 | 改后 |
|---|---|---|
| [CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt) | `padding(16.dp)` | `padding(horizontal=16.dp, vertical=10.dp)` |
| [CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt) | `Spacer(height(4.dp))`（title→desc） | `height(2.dp)` |
| [CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt) | `Spacer(height(8.dp))`（desc→chips） | `height(6.dp)` |
| [VocabularyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt) | `padding(16.dp)` | `padding(horizontal=16.dp, vertical=10.dp)` |
| [VocabularyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt) | `Spacer(height(4.dp))`（translation→example） | `height(2.dp)` |

**模式**（适用于所有列表行的紧凑化）：
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),  // 上下 10dp，比 16dp 紧
    verticalAlignment = Alignment.CenterVertically,
) {
    // 内容之间的 Spacer 用 2~6dp 代替 4~8dp
    Spacer(modifier = Modifier.height(2.dp))
}
```

**注意**：
- 横向 padding 保持 16dp（文字贴边太近不美观）
- 上下 padding 10dp 已经是 48dp 最小点击目标的 1/5，仍满足命中区域

### 12.9 MeScreen 去掉"我的"标题 + "听言英语"置顶

**原因**：底部导航已经显示当前是"我的"tab，TopAppBar 再写一遍"我的"是重复信息。视觉上不如直接把"听言英语"作为页面第一元素。

**改动**：[MeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt)
- 删除 `CenterAlignedTopAppBar`（"我的"标题）
- 删除相关 import：`CenterAlignedTopAppBar`, `ExperimentalMaterial3Api`, `TopAppBarDefaults`
- Column 顶部加 `statusBarsPadding()` 替代 TopAppBar 的状态栏处理
- Column 内容顺序改为：
  1. **"听言英语"**（`headlineMedium`，**第一行**）
  2. Image 图标
  3. "Echo Ling" 英文名
  4. Version chip
  5. InfoCard × 2
  6. ContactCard
  7. 版权

**关键参数**：
- `statusBarsPadding()` 是关键：替代 TopAppBar 的状态栏 inset 处理
- Column 整体 `padding(horizontal=24.dp, vertical=16.dp)`（之前是 `padding(24.dp)`，上下也改紧凑了）
- Image 与 "Echo Ling" 之间从 16dp 减到 12dp

**注意**：
- ❌ 改回 `TopAppBar(title = "听言英语")`：用户明确要求"听言英语"在最上面，但 TopAppBar title 区域有限
- ❌ 把 `statusBarsPadding()` 删掉：状态栏会盖住"听言英语"
- ❌ 把 "听言英语" 和 Image 顺序换回：违反"听言英语移到最上面"的要求

### 12.10 列表行高二次紧凑化（Courses / Vocabulary）

**原因**：§12.8 第一次紧凑化（16→10dp）后用户仍嫌占用空间，再缩 2dp 让每行更轻。

**改动**：

| 文件 | §12.8 改后 | §12.10 改后 |
|---|---|---|
| [CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt) `Row padding vertical` | 10dp | **8dp** |
| [CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt) `Spacer title→desc` | 2dp | **1dp** |
| [CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt) `Spacer desc→chips` | 6dp | **4dp** |
| [VocabularyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt) `Row padding vertical` | 10dp | **8dp** |
| [VocabularyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt) `Spacer translation→example` | 2dp | **1dp** |

**注意**：
- 横向 padding 仍保持 16dp（贴边不好看）
- Spacer 1dp 是体验底线（再小就视觉贴在一起，看不出"换行"）
- 单行总高从 100dp 降到 ~90dp，三个课程屏占少 30dp

### 12.11 MeScreen "听言英语" sticky 头

**原因**：§12.9 把"听言英语"放到 Column 第一项后，向上滚动会跟着内容一起滚走——用户希望"始终可见"。

**改动**：[MeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt)
- 外层 Column 不滚动，里面分两段：
  1. **顶部 sticky 段**：`Surface(surface)` 背景的 `Text("听言英语", headlineMedium)`，**不参与滚动**，永远固定
  2. **底部滚动段**：`verticalScroll(Column)` 包含 Image / Echo Ling / Version / InfoCards / ContactCard / 版权
- 顶部 sticky 段用 `statusBarsPadding()` 处理状态栏 inset
- 底部滚动段 `top padding = 8dp`，让 Image 离 sticky 头"一呼一吸"距离

**关键模式**（sticky header without LazyColumn）：
```kotlin
Scaffold { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // 1. Sticky 头（不滚动）
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Text(
                "听言英语",
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()  // 关键：吸收状态栏高度
                    .padding(vertical = 4.dp),
            )
        }
        // 2. 滚动内容（避开 sticky 头）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // Image + Cards + ...
        }
    }
}
```

**关键参数**：
- Sticky 段 `Surface` 颜色用 `surface`（不透明），不是 `Color.Transparent`——否则下方滚动内容会"穿过"标题
- Sticky 段 `vertical padding = 4dp`（比 §12.9 的 16dp 紧），让标题离状态栏更近
- 滚动段 `vertical = 8dp`（包括 top 和 bottom），top 留出 8dp 视觉间隔给 sticky 头

**禁止行为**：
- ❌ 把 sticky 头放进 `verticalScroll` 内部：标题会跟着滚走
- ❌ `Surface` 颜色设为 `Color.Transparent`：滚动卡片内容会"穿过"标题文字
- ❌ 删 `statusBarsPadding()`：标题会被状态栏 / 摄像头挖孔盖住
- ❌ 把 sticky 头高度设为固定 `height(64.dp)`：在带挖孔的设备上会和状态栏重叠

### 12.12 Sticky 标题字号改用 `headlineSmall`（保持无留白）

**原因**：§12.12 第一次用 `titleMedium.copy(lineHeight = 16.sp)`（16sp 字号）太细，用户反馈"听言英语"四个字太小。升级到 `headlineSmall.copy(lineHeight = 24.sp)`（24sp 字号，比之前大 50%），仍保持 `lineHeight = fontSize` 的无留白属性。

**改动**：[MeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt)
- Sticky 标题 `style` 从 `titleMedium.copy(lineHeight = 16.sp)` → **`headlineSmall.copy(lineHeight = 24.sp)`**
- 字号 16sp → 24sp（+50%）
- `lineHeight` 24sp = fontSize，**完全消除顶部 4sp 留白**
- 下方"Echo Ling" 仍是 `titleMedium` (16sp)——视觉层级变为"大中文名 + 小英文副标题"

**字号对比**：

| 方案 | fontSize | lineHeight | 顶部留白 | 视觉权重 |
|---|---|---|---|---|
| `headlineMedium`（§12.11 前） | 28sp | 36sp | 4sp 顶部 + 4sp 底部 | 大 |
| `headlineMedium.copy(lineHeight=28.sp)`（§12.11） | 28sp | 28sp | 0 | 大 |
| `titleMedium`（§12.12 第一次） | 16sp | 24sp | 4sp 顶部 + 4sp 底部 | 中 |
| `titleMedium.copy(lineHeight=16.sp)`（§12.12 第二次） | 16sp | 16sp | 0 | 中（太小） |
| `headlineSmall.copy(lineHeight=24.sp)`（**当前**） | 24sp | 24sp | **0** | 中-大 |

**原理**（用户问"为什么有大一块空白"）：
- Compose `Text` 实际占据的高度 = `lineHeight`（不是 `fontSize`）
- `lineHeight - fontSize` 的差值在 baseline 上下分配
- `lineHeight = fontSize` 时，Text 高度 = 字形高度，无任何留白
- `lineHeight < fontSize` 会切掉字形底部，**禁止**（除了固定中文字符串场景）

**权衡**（为什么 `lineHeight = fontSize` 可接受）：
- 标题固定为"听言英语"四个汉字，无 Latin 字母、没有 `g j p q y` 这些有 descender 的字符
- 汉字字形在 baseline 上下都有笔画，但垂直占用通常在 ±8% 范围内
- 24sp 字形 + 24sp lineHeight 实际挤压 1~2sp，留白几乎不可见

**关键模式**（完全消除 Text 顶部留白 + 加大字号）：
```kotlin
// 默认：lineHeight 32sp，字形 24sp，顶部 4sp 留白
Text("听言英语", style = MaterialTheme.typography.headlineSmall)

// 修正：lineHeight = fontSize = 24sp，无留白，字号适中
Text("听言英语", style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 24.sp))
```

**禁止行为**：
- ❌ 在 Type.kt 全局改 `headlineSmall` 的 lineHeight（影响其他用 `headlineSmall` 的地方）
- ❌ 改回 `titleMedium.copy(lineHeight=16.sp)`（用户已反馈字号太小）
- ❌ 用 `Modifier.offset(y = -X.dp)` 强行上推（会进入 `statusBarsPadding` 区域，被刘海/挖孔盖住）
- ❌ 改回 `headlineMedium` (默认 36sp lineHeight，4sp 顶部留白) — 用户已多次拒绝留白

### 12.13 MeScreen 顶部改用 CenterAlignedTopAppBar（与 Courses 顶部一致）

**原因**：用户要求"Me 页面最上面的听言英语的布局改为和首页最上面的每天进步一点一样的布局"——即直接复刻 [CoursesScreen.kt:74-98](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt#L74) 的两行 Column 标题结构。这同时**完全替代了** §12.9/12.11/12.12 的 sticky Surface 方案：M3 TopAppBar 自带 status bar inset 处理 + 始终可见（不滚动）+ 两行标题布局。

**改动**：
- [strings.xml](app/src/main/res/values/strings.xml) 加 2 个 string：
  - `me_title` = "听言英语"
  - `me_subtitle` = "— Listen and Speak Daily —"
- [MeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt)：
  - 删除 §12.11/12.12 的 sticky Surface + 自定义 head/line 方案
  - 删除 `statusBarsPadding` / `import androidx.compose.ui.unit.sp`（不再需要）
  - 加 imports：`CenterAlignedTopAppBar`, `ExperimentalMaterial3Api`, `TopAppBarDefaults`, `TextStyle`, `FontStyle`, `FontWeight`, `stringResource`
  - `Scaffold` 加 `topBar = CenterAlignedTopAppBar(...)`，title = 两行 Column（与 Courses 完全一致）
  - `containerColor = surface`

**复用 Courses 的两行 Column 模板**：
```kotlin
CenterAlignedTopAppBar(
    title = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.me_title),  // "听言英语"
                style = TextStyle(
                    fontSize = 22.sp,         // ← 与 Courses 一致
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                ),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.me_subtitle),  // "— Listen and Speak Daily —"
                style = TextStyle(
                    fontSize = 12.sp,         // ← 与 Courses 一致
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    },
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
    ),
)
```

**关键参数**（必须和 Courses 一致）：
- 主标题：`22sp / Bold / letterSpacing 4sp`
- 副标题：`12sp / Italic / Medium / letterSpacing 1sp / onSurfaceVariant`
- 居中方式：`Column(horizontalAlignment = Alignment.CenterHorizontally)`

**视觉对比**：

| 元素 | Courses 顶部 | MeScreen 顶部（§12.13 当前） |
|---|---|---|
| 容器 | CenterAlignedTopAppBar | **CenterAlignedTopAppBar** |
| 主标题 | 每天进步一点 (22sp Bold) | 听言英语 (22sp Bold) |
| 副标题 | — Every day a little progress — (12sp Italic) | — Listen and Speak Daily — (12sp Italic) |
| actions | 统计按钮 | （无） |
| containerColor | surface | surface |
| 始终可见 | ✅（TopAppBar 天然） | ✅ |

**关键收益**：
- ✅ 与首页顶部完全一致的品牌栏视觉
- ✅ 顶栏天然处理 status bar inset（不再需要手动 `statusBarsPadding`）
- ✅ 顶栏天然不滚动（满足 §12.11 "听言英语 始终可见"）
- ✅ 不再有顶部留白问题（TopAppBar 的 64dp 高度是 M3 设计标准）
- ❌ 顶部固定占 64dp（之前 sticky 方案只占 ~30dp）。用户在权衡后选择视觉一致性

**禁止行为**：
- ❌ 改回 sticky Surface 方案（§12.11/12.12 已被本节替代）
- ❌ 改 MeScreen 顶部主标题字号（如换成 `headlineSmall.copy(lineHeight=24.sp)`）—— 与 Courses 不一致
- ❌ 用 `headlineMedium` 作为 MeScreen 标题——之前已多次拒绝其 4sp 顶部留白
- ❌ 改 Courses 标题字号而不改 MeScreen——会破坏品牌栏一致性
- ❌ 在 MeScreen 的 `CenterAlignedTopAppBar` 上加 `actions`（"我的"页面无 toolbar 按钮）

### 12.14 课程删除卡死（property / function 同名无限递归）

**症状**：用户报告 "首页中我的课程里的课程，点击删除的时候，app 卡死"——确认弹窗点击「删除」后整个 app 挂起，必须从最近任务杀掉。

**根因**：[CoursesViewModel.kt](app/src/main/java/com/echoling/app/presentation/viewmodel/CoursesViewModel.kt) 同时存在两个同名成员：
```kotlin
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val getCourses: GetCoursesUseCase,
    private val getStatistics: GetStatisticsUseCase,
    private val getContinueLearning: GetContinueLearningUseCase,
    private val deleteCourse: DeleteCourseUseCase,   // ← property: deleteCourse
) : ViewModel() {
    // ...
    fun deleteCourse(courseId: String) {              // ← function: deleteCourse (NAME COLLISION)
        viewModelScope.launch {
            deleteCourse(courseId)                    // ← 调用哪个？
        }
    }
}
```

Kotlin 名字解析规则：成员函数和成员属性同名时，**函数调用语法优先于属性访问**。所以函数体里的 `deleteCourse(courseId)` 不会解析到 `DeleteCourseUseCase` 属性（属性需要无参访问 `this.deleteCourse`），而是再次解析到 `fun deleteCourse(String)` 本身——**无限递归**。

调用栈：`viewModel.deleteCourse(id)` → `fun deleteCourse` → `launch { deleteCourse(id) }` → `fun deleteCourse` → `launch { ... }` ... 每个 launch 都立即再次调用同一函数，无穷无尽，协程池被这个永不返回的递归耗光，UI 主线程上 `_uiState.value = ...` 永远不被触发，但更重要的是 **`StackOverflowError` 实际发生在第一帧**——只是被 `viewModelScope` 的默认异常处理器（默认是 `Log + 取消 scope`）吞掉，整个 ViewModel scope 被取消，但 `init { load() }` 里的 `combine(...).collect { _uiState.value = state }` 也被一起取消，**`courses` 列表再也收不到 Room 的更新**——所以用户看到的"卡死"实际上是 UI 永远停在弹窗关闭前的最后状态 + 列表不再响应任何点击。

**修复**：把 use case 属性改名 `deleteCourseUseCase`，消除歧义。

```kotlin
private val deleteCourseUseCase: DeleteCourseUseCase,

fun deleteCourse(courseId: String) {
    viewModelScope.launch {
        deleteCourseUseCase(courseId)   // ✅ 明确指向属性
    }
}
```

**关键收获 / 铁律**：
- ❌ **绝不在 ViewModel / 类里让成员 property 和成员 function 用同一个名字**。即使语义上"看起来"对应（这里是 use case property + 对外动作 method），kotlin 编译器允许共存但运行时解析规则会让你踩坑。
- ❌ **不要靠"编译器没报错 = 没问题"判断**。这种 bug 编译 100% 通过，运行时报 `StackOverflowError` 但被协程默认 handler 静默吞掉，UI 不崩但也不动——最难定位的一类。
- ✅ **注入 use case 的属性一律带 `UseCase` 后缀**（如 `deleteCourseUseCase`, `toggleMasteredUseCase`），与对外的 `fun deleteCourse(...)` 区分开。
- ✅ **在 ViewModel 公开动作方法里，第一行写一个明确命名的本地变量 `val useCase = deleteCourseUseCase`**，然后调用 `useCase(id)`——再绕一层，杜绝任何未来同名扩展带来的歧义。

**相关 commit 标识符**：本节变更后 ViewModel 注入顺序为 `getCourses / getStatistics / getContinueLearning / deleteCourseUseCase`，重构相关代码时按这个顺序排列。

**测试验证**：
- `./gradlew assembleDebug` 通过 ✅
- 启动 app → 首页 → 点击某课程的删除图标 → 弹出确认框 → 点击「删除」→ ✅ 课程从列表消失，无卡死

### 12.15 删除确认弹窗紧凑化（替换 M3 AlertDialog）

**原因**：用户报告 "单词页中删除单词弹出的删除确认框太大" 和 "首页中删除课程弹出的删除确认框也太大"——M3 默认 `AlertDialog` 视觉重量过重：
- 宽度：280-560dp（最小 280dp，远大于实际内容需要）
- 内部 padding：24dp
- 标题样式：`headlineSmall` = 24sp（"Delete Word" / "删除课程" 四个字被撑成一行大字）
- 整框远大于内含的两行短文本

**方案**：用自定义 `CompactConfirmDialog` 替代两处的 `AlertDialog`。

**新组件**：[CompactConfirmDialog.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CompactConfirmDialog.kt)
- 基于 `androidx.compose.ui.window.Dialog` + `Surface`，不走 `AlertDialog` chrome
- 固定宽度 **240dp**（vs 默认 280-560dp），足够容纳 "Delete Word" 14sp 标题
- 内部 padding `horizontal=16dp, vertical=14dp`（vs 默认 24dp）
- 标题 `titleSmall`（14sp / `onSurface`）
- 正文 `bodySmall`（12sp / `onSurfaceVariant`）
- 按钮：`labelMedium`（12sp），`contentPadding = (8dp, 4dp)`（vs 默认 48dp / 12dp），靠右排列
- 确认按钮 `colorScheme.error`、取消按钮 `colorScheme.onSurfaceVariant`——保留 M3 危险/中性视觉对比
- `tonalElevation = 6.dp`，与原 AlertDialog 的 elevation 视觉接近

**替换位置**：
- [VocabularyScreen.kt:226-247](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L226) → `WordCard` 的删除单词确认弹窗
- [CoursesScreen.kt:155-176](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt#L155) → 首页课程列表的删除课程确认弹窗

两个调用点的字符串（中英文双语）和回调签名都不同，所以保留两个独立 `CompactConfirmDialog(...)` 调用，只共享组件本身。

**视觉对比**：

| 元素 | M3 AlertDialog (改前) | CompactConfirmDialog (改后) |
|---|---|---|
| 宽度 | 280-560dp | **240dp** |
| 标题字号 | 24sp (`headlineSmall`) | **14sp (`titleSmall`)** |
| 正文字号 | 16sp (`bodyMedium`) | **12sp (`bodySmall`)** |
| 按钮字号 | 14sp (`labelLarge`) | **12sp (`labelMedium`)** |
| 内部 padding | 24dp | **16dp / 14dp** |
| 按钮 padding | 24×12dp | **8×4dp** |

**关键收益**：
- ✅ 删除确认弹窗从"霸屏"缩成"卡片"级别，整屏视觉重量明显下降
- ✅ 标题从 24sp 降到 14sp，"Delete Word" / "删除课程" 不再抢眼
- ✅ 两个弹窗复用同一组件，未来新增同类弹窗（导入确认、清空确认等）直接调用
- ✅ 删除危险动作仍然用 `colorScheme.error` 红色按钮，视觉对比保留
- ✅ 弹窗关闭逻辑（点外部 / 按返回 / 点按钮）由底层 `Dialog` 处理，无需手动实现

**禁止行为**：
- ❌ 改回 `AlertDialog`——视觉重量太大
- ❌ 把标题字号调回 `titleMedium`（16sp）以上——会让弹窗又变大
- ❌ 在 `CompactConfirmDialog` 内部加 `icon` / `image` 等额外视觉元素——保持"短文本确认"的纯文本定位
- ❌ 把确认按钮颜色改成非 error 色——危险动作必须有视觉警示

**测试验证**：
- `./gradlew assembleDebug` 通过 ✅
- 启动 app → 首页 → 长按/点击课程删除图标 → 弹出紧凑版「删除课程」确认框，标题 14sp、宽度 240dp ✅
- 切换到单词页 → 点击单词删除图标 → 弹出紧凑版「Delete Word」确认框，同样规格 ✅
- 点外部/按返回/点取消 → 弹窗正常关闭，UI 不卡 ✅

### 12.16 首页沉浸感改造 + 课程卡片现代化

**原因**：用户要求 "首页更有沉浸感" + "课程卡片更现代"。选定方向（见 AskUserQuestion）：
- **沉浸感 → Hero 区品牌化**（保留 TopAppBar，下方加紫色渐变 hero 区）
- **课程卡片 → 当前紧凑 + accent bar + hover 动效**（保留紧凑布局，加 4dp 左侧 accent bar + 按下浮起缩放）

#### 12.16.1 Hero 区品牌化

**改动文件**：[ContinueLearningCard.kt](app/src/main/java/com/echoling/app/presentation/ui/components/ContinueLearningCard.kt)（同函数名 + 同文件名，仅视觉重写）

**视觉配方**：
- 对角渐变：`primary` (#7C3AED) → `tertiary` (#6D28D9)，从左上到右下
- 两颗半透明白色径向 orb：右上 160dp `alpha=0.18`、左下 110dp `alpha=0.10`（带 `offset(x=-20dp, y=30dp)` 错位）——增加氛围但不抢内容
- 圆角 24dp，tonalElevation 4dp，内 padding 20dp
- 标题行：
  - "▶ 继续学习" 眉条（labelMedium 12sp，白色 85%）
  - 课程名（titleLarge 22sp Bold，白色，最多 2 行 ellipsis）
  - 右侧 **56dp 圆形 PlayCircle 按钮**（白底 + primary 紫图标）—— hero 的视觉锚点
- 进度条：白色填充 + 25% 白色 track + "62% 完成" 标签

**布局调整**：[CoursesScreen.kt:191-231](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt#L191) 把 Hero 从 StatsSummaryCard **下方**移到**上方**——视觉层次变成 `Hero（深紫渐变）→ Stats（浅 primaryContainer）→ 课程列表`。Hero 成为页面唯一的饱和色彩卡片，眼睛第一落点正确。

**为什么 StatsSummaryCard 不改色**：Hero 是唯一渐变深紫卡片，Stats 保留 `primaryContainer` 形成「Hero 抢眼 → Stats 次要 → 列表最弱」的三级视觉层次。如果 Stats 也改色会与 Hero 打架。

#### 12.16.2 课程卡片现代化（accent bar + hover 动效）

**改动文件**：[CourseListItem.kt](app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt)

**新增 1：4dp 左侧 accent bar**
- 颜色按难度档位映射：
  - `A*`（A1/A2）→ `colorScheme.primary`（深紫 #7C3AED）
  - `B*`（B1/B2）→ `colorScheme.tertiary`（更深紫 #6D28D9）
  - `C*`（C1/C2）→ `colorScheme.secondary`（中紫 #8B5CF6）
  - 其他 → primary fallback
- 通过 `Modifier.height(IntrinsicSize.Min)` 让 accent bar `fillMaxHeight()` 跟随卡片自然高度，避免写死 dp
- `accentColorFor(difficulty)` 标记为 `@Composable` 因为 `MaterialTheme.colorScheme` 只能在 composition 内访问

**新增 2：按下态 elevation + scale 动效**
- `MutableInteractionSource` + `collectIsPressedAsState` 监听卡片整体按下状态
- `animateDpAsState` 把 elevation 从 **1dp → 8dp** 平滑过渡
- `animateFloatAsState` 把 scale 从 **1.0 → 1.02** 平滑过渡
- 涟漪（`rememberRipple`）被 `clip(RoundedCornerShape(16.dp))` 裁剪到卡片圆角内——视觉干净
- 内部 IconButton 的点击不会触发卡片级 animation（因为 IconButton 自带 `interactionSource`，不会冒泡到卡片级）

**卡片内容布局不变**：标题 / 描述 / 难度 chip / 媒体图标 / 时长 / 删除按钮 / Play 按钮都保留，仅多了左侧 accent bar。

#### 12.16.3 视觉对比

**首页（学习中有继续学习课程时）**：
```
┌─────────────────────────────────────┐
│ 听言英语              📊 统计         │ ← TopAppBar
├─────────────────────────────────────┤
│ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │ ← Hero 深紫渐变
│ ░░ ▶ 继续学习                         │
│ ░░ 课程名                  ┌────────┐│
│ ░░                        │  ⭕     ││ ← 56dp 圆形 Play
│ ░░                        └────────┘│
│ ░░ ▓▓▓▓▓▓▓▓░░░░░░░ 62%                │
├─────────────────────────────────────┤
│ 📊 学习时长 1h 30m  📚 单词 27        │ ← StatsSummaryCard
│    查看详情 →                          │
├─────────────────────────────────────┤
│ 我的课程                              │
│ ┃ ┌──────────────────────────────┐  │ ← 4dp accent bar (按难度变色)
│ ┃ │ 课程名                        │  │
│ ┃ │ 描述                          │  │
│ ┃ │ [B1 chip] ⏱ 3:45    [🗑] [⭕] │  │
│ ┃ └──────────────────────────────┘  │
│ ┃ ┌──────────────────────────────┐  │
│ ┃ │ 课程名                        │  │
│ ┃ └──────────────────────────────┘  │
└─────────────────────────────────────┘
```

**课程卡片按下态**（elevation 8dp + scale 1.02）：
```
    ┃ ┌──────────────────────────────┐  ← 浮起 + 轻微放大
    ┃ │ 课程名       (elevation 8dp)  │
    ┃ │ 描述                          │
    ┃ │ [B1] ⏱ 3:45         [🗑] [⭕]│
    ┃ └──────────────────────────────┘
```

#### 12.16.4 关键收益

- ✅ Hero 区是页面唯一深紫渐变卡片，第一眼就看到「继续学习」CTA
- ✅ 56dp 圆形 Play 按钮是触摸热区（≥48dp Material 触控标准），且是 hero 唯一的「下一步动作」入口
- ✅ accent bar 用难度档位颜色编码，扫一眼列表就能区分 A/B/C 课程
- ✅ 按下态 elevation + scale 让卡片"活"起来，符合 Material You 的 tactile 设计语言
- ✅ 所有动画都用 `animateDpAsState` / `animateFloatAsState`，自动跟随系统动画时长偏好（用户在系统设置关动画时也会自动降级）

#### 12.16.5 禁止行为

- ❌ 改 Hero 渐变方向（如改成 top-to-bottom）——对角渐变是 Hero 的视觉识别
- ❌ 把 Play 按钮改成方形 / 改成图标按钮——56dp 圆形是 Hero 的视觉锚点
- ❌ 移除 hero 的径向 orb——没有 orb 会显得"平"和"塑料感"
- ❌ 在 StatsSummaryCard 也加渐变——会与 Hero 视觉打架
- ❌ 改 accent bar 宽度（4dp）——再窄就看不见，再宽就抢眼
- ❌ 改 accent bar 颜色映射规则（A→primary）——破坏「难度=颜色」的心智模型
- ❌ 移除卡片按下 animation——会丢失 tactile feedback
- ❌ 把 accent bar 放到右侧——左侧是约定俗成的标识位置
- ❌ 给 accent bar 也加按下 animation——accent bar 是被动指示元素，按下不应该单独动

#### 12.16.6 测试验证

- `./gradlew assembleDebug` 通过 ✅
- 启动 app → 首页 → 看到紫色渐变 Hero（如果有正在学习的课程）✅
- Hero 右上角和左下角可见微弱白色光晕 ✅
- 点击 Hero 上 Play 按钮 → 进入 Practice 页面 ✅
- 向下滚动 → 看到 StatsSummaryCard（浅 primaryContainer）和 "我的课程" 章节头 ✅
- 每个课程卡片左侧有 4dp accent bar，颜色随难度变化（A 紫 / B 深紫 / C 中紫）✅
- 按下任一课程卡片 → 卡片浮起（elevation 1→8dp）+ 轻微放大（scale 1→1.02），松开恢复 ✅
- 按下卡片内删除按钮 / Play 按钮 → 不会触发卡片级动画（仅对应按钮的 ripple）✅

### 12.17 全局页面顶部上移（top padding 减半）

**原因**：用户要求 "把所有的页面，包括子页面 跟读练习的页面整体往上移动" —— 各页 body 顶部空白过大（16-24dp），加上若干 Spacer 叠加后第一屏可见内容太少。

**策略**：把所有页面的 body 顶部 vertical padding **统一减半**（16dp→8dp，24dp→12dp，8dp→4dp），并清理冗余 Spacer。Scaffold 的 `padding(padding)` 参数（含 TopAppBar + status bar inset）不动 —— 那是必须的 inset，动了内容会被 TopAppBar 盖住。

**改动逐项**（10 文件 11 处）：

| 页面 | 改动 | 减少 |
|---|---|---|
| [ApiScreen.kt:63](app/src/main/java/com/echoling/app/presentation/ui/screens/api/ApiScreen.kt#L63) | `.padding(horizontal=16.dp, vertical=16.dp)` → `vertical=8.dp` | 8dp |
| [CourseDetailScreen.kt:101](app/src/main/java/com/echoling/app/presentation/ui/screens/course/CourseDetailScreen.kt#L101) | `.padding(24.dp)` → `.padding(12.dp)` | 12dp |
| [CourseDetailScreen.kt:104](app/src/main/java/com/echoling/app/presentation/ui/screens/course/CourseDetailScreen.kt#L104) | 删除 `Spacer(modifier = Modifier.height(16.dp))` 冗余 Spacer | 16dp |
| [CoursesScreen.kt:183](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt#L183) | LazyColumn contentPadding `vertical=16.dp` → `vertical=8.dp` | 8dp |
| [ImportScreen.kt:103](app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt#L103) | `.padding(horizontal=24.dp, vertical=24.dp)` → `vertical=12.dp` | 12dp |
| [MeScreen.kt:115](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt#L115) | `.padding(horizontal=24.dp, vertical=8.dp)` → `vertical=4.dp` | 4dp |
| [StatisticsScreen.kt:59](app/src/main/java/com/echoling/app/presentation/ui/screens/statistics/StatisticsScreen.kt#L59) | `.padding(16.dp)` → `.padding(horizontal=16.dp, vertical=8.dp)` | 8dp |
| [VocabularyScreen.kt:135](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L135) | LazyColumn contentPadding `(16.dp)` → `horizontal=16.dp, vertical=8.dp` | 8dp |
| [VocabularyScreen.kt:168](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L168) | WordCard Row `vertical=8.dp` → `vertical=4.dp`（每张卡片内部上下都减） | 4dp/card |
| [ListeningPage.kt:78](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/ListeningPage.kt#L78) | ProgressBar `vertical=8.dp` → `vertical=4.dp` | 4dp |
| [SpeakingPage.kt:80](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L80) | Dropdown Box `padding(16.dp)` → `padding(horizontal=16.dp, vertical=8.dp)` | 8dp |
| [TestingPage.kt:97](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/TestingPage.kt#L97) | TestingProgressHeader `padding(16.dp)` → `padding(horizontal=16.dp, vertical=8.dp)` | 8dp |

**未改动**：
- [PracticeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt) —— body 自身无额外 padding（TopAppBar + TabRow 由 Scaffold inset 自动吸收）
- 所有 `.padding(padding)` —— Scaffold 必须的 inset，含 TopAppBar 高度 + status bar
- 各页面底部 padding —— 用户只要求"往上移"，底部不动
- 各页面左右 horizontal padding —— 维持 M3 标准 16-24dp 边距

**视觉效果**（每页第一屏可见内容上移 8-28dp）：
- 首页：Hero 卡片上移 8dp
- 课程详情：图标上移 28dp（24→12dp + 删 16dp Spacer）
- 导入：headline 上移 12dp
- 我的：app icon 上移 4dp
- 单词页：第一个 WordCard 上移 8dp，且每张卡片内部垂直紧凑 4dp
- 跟读练习三页：进度条 / 下拉 / 标题 各自上移 4-8dp

**禁止行为**：
- ❌ 把任何页面顶部 padding 减到 0dp —— 与 TopAppBar 视觉贴边，看起来"被压住"
- ❌ 减 `.padding(padding)` —— Scaffold inset 必须保留，否则内容会被 TopAppBar 盖住
- ❌ 单独把 horizontal padding 也减半 —— 横向 M3 标准 16-24dp 是合理的"安全区"，不能动
- ❌ 把 PracticeScreen 的 TabRow 也改了 —— TabRow 是 M3 标准 48dp 高度，本来就没多余 padding

**测试验证**：
- `./gradlew assembleDebug` 通过 ✅
- 启动 app → 首页 → Hero 卡片视觉上明显上移 8dp ✅
- 进入课程详情 → 大图标上移明显（28dp）✅
- 进入单词页 → 第一个单词卡片上移 ✅
- 进入跟读练习 → 三个 Tab 切换，progress bar / dropdown / 测试标题都更靠上 ✅
- 顶部 TopAppBar 与下方内容之间有合理 8-12dp 视觉间距，不会贴边 ✅

### 12.18 全局页面紧贴状态栏（移除 TopAppBar，引入 PageHeader）

**原因**：用户测试 §12.17 后反馈 "基本和之前一样，最上面还是有大量的空白" + "让每个页面紧挨着手机的状态栏"。光把 padding 减半不够——M3 TopAppBar 自带 64dp 高度 + status bar inset 24dp ≈ 88dp chrome 在第一行内容之上，必须干掉 TopAppBar 才行。

**策略**：**完全移除所有 TopAppBar**，引入新的 [PageHeader.kt](app/src/main/java/com/echoling/app/presentation/ui/components/PageHeader.kt) 组件作为紧凑的内联 header（48dp 高）。Header 本身不加 `statusBarsPadding`——Scaffold 在没有 topBar 时，`padding.top` 参数本身就是状态栏高度，body 用 `.padding(padding)` 自动把内容下推到状态栏下方。这样 PageHeader 自然紧贴状态栏底部。

#### 12.18.1 PageHeader 组件

```kotlin
@Composable
fun PageHeader(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = {},
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
)
```

设计要点：
- **48dp 高**（vs M3 TopAppBar 64dp）——紧凑、不抢眼
- **surface 背景**——和页面 body 视觉边界清晰
- **4dp horizontal padding**——左右各留 4dp 给 IconButton
- **左：可选 back 按钮**（48dp IconButton，`Icons.Filled.ArrowBack`），无 back 时留 12dp 占位让 title 不会贴边
- **中：title slot**（weight 1f，居中），传 `@Composable () -> Unit` 让调用方自己决定是单行 Text 还是 Column(title + subtitle)
- **右：actions slot**（`@Composable RowScope.() -> Unit`），支持任意多个 IconButton / Surface pill
- **不在内部加 statusBarsPadding**——依赖 Scaffold 的 `padding(padding)` 兜底

#### 12.18.2 改动逐项（8 文件）

| 文件 | 改动 |
|---|---|
| [PageHeader.kt](app/src/main/java/com/echoling/app/presentation/ui/components/PageHeader.kt) | 新建组件 |
| [CoursesScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt) | 移除 CenterAlignedTopAppBar；body 改成 Column { PageHeader(two-line 标题 + stats action) + Box(LazyColumn) }；品牌标题字号 22sp → 18sp, 12sp → 11sp |
| [MeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt) | 移除 CenterAlignedTopAppBar；body 改成 Column { PageHeader(两行品牌标题) + Column(verticalScroll body) }；品牌标题字号同步缩 |
| [ApiScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/api/ApiScreen.kt) | 移除 CenterAlignedTopAppBar；body 加 PageHeader("API" titleMedium) + Column(scroll body) |
| [VocabularyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt) | 移除 TopAppBar；body 加 PageHeader(back + "单词" + filter dropdown) + Box(list) |
| [CourseDetailScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/course/CourseDetailScreen.kt) | 移除 TopAppBar；body 加 PageHeader(back + course.title) + Box(content) |
| [StatisticsScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/statistics/StatisticsScreen.kt) | 移除 TopAppBar；body 加 PageHeader(back + "Learning Statistics") + if/else(content) |
| [ImportScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt) | 移除 TopAppBar；body 加 PageHeader(back + "Import Course") + Column(form + bottom button) |
| [PracticeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt) | 移除 TopAppBar；body 改成 Column { Row(back + "跟读练习" + subtitle pill) + TabRow + Box(content) } |

#### 12.18.3 PracticeScreen 特殊处理

跟读练习页原本 TopAppBar 含三个元素：back 按钮、"跟读练习" 标题、字幕模式切换 pill。移除后不能丢任何一个，所以 PageHeader 思路不适用，改成 **48dp Row**：

```
Row (48dp, surface bg):
  [←] back
  "跟读练习" (titleMedium weight 1f, center)
  [双语/英语/中文 pill]
```

下面紧接 TabRow（48dp M3 标准）。这样 TabRow 仍然紧贴 Row 下方，距状态栏仅 96dp（48+48），比原来的 64+48+status_bar ≈ 136dp 节省 40dp。

#### 12.18.4 关键收益

- ✅ **每个页面第一行内容紧贴状态栏底部**——PageHeader 从状态栏正下方开始，而不是从 64dp TopAppBar 底部开始
- ✅ 节省垂直空间：每个 tab 节省 ≈ 40dp，sub-page 节省 ≈ 64dp（TopAppBar 整个没了）
- ✅ 视觉更"开放"——页面不再被一个明显的 toolbar 框住
- ✅ 品牌标题保留可见（Courses / Me 用两行 Column，其他 tab 用单行 Text）
- ✅ back 按钮保留可见且符合 Material 设计（≥48dp touch target）
- ✅ PracticeScreen 字幕模式 pill 仍在 TabRow 旁边可见

#### 12.18.5 禁止行为

- ❌ 改回 TopAppBar / CenterAlignedTopAppBar——视觉立刻回到"上方大块空白"状态
- ❌ PageHeader 自己加 `statusBarsPadding()`——会和 Scaffold 的 `padding(padding)` 双倍累加
- ❌ 把 PageHeader 高度加到 64dp——会重新引入被去掉的 16dp 视觉重量
- ❌ 把品牌标题字号改回 22sp / 12sp——在 48dp PageHeader 里太大，会撑破
- ❌ 在 PageHeader 里放超过 2 个 actions icon——48dp 高度容纳不下
- ❌ 把 PracticeScreen 的 48dp Row 也用 PageHeader 替代——它的 back+title+pill 三段式结构 PageHeader 表达不了

#### 12.18.6 测试验证

- `./gradlew assembleDebug` 通过 ✅
- 启动 app → 首页 → 状态栏下方第一行是 PageHeader（"每天进步一点 / Every day a little progress"），紧贴状态栏 ✅
- PageHeader 下方就是 Hero 深紫渐变卡片（8dp top padding）✅
- 切换到单词/API/我的 tab，每个 tab 第一行都是自己的 PageHeader，紧贴状态栏 ✅
- 进入课程详情 → PageHeader 显示课程名，左侧 ← 按钮 ✅
- 进入统计 → PageHeader 显示 "Learning Statistics" ✅
- 进入导入 → PageHeader 显示 "Import Course" ✅
- 进入跟读练习 → 状态栏下方是 back + "跟读练习" + 字幕 pill 的 48dp Row，下方接 TabRow ✅
- 跟读练习的 back 按钮、字幕 pill 都能正常使用 ✅
- 滚动列表 / 切换 tab / 返回上级，所有交互正常 ✅

### 12.19 Per-category flashcard progress persistence (DB v5)

**原因**：用户报告「在记单词页面，记忆单词后认识or不认识的单词数量和单词系统没有记忆，导致每次打开时都是从头开始」——`currentIndex` / `knownCount` / `unknownCount` 之前只活在 `CategoryStudyUiState` 里，进程被杀 / 切 tab 后再回来永远落在第一张卡片。新增一个 Room 表做持久化。

**改动文件**：

| 路径 | 类型 |
|---|---|
| [ReciteProgressEntity.kt](app/src/main/java/com/echoling/app/data/local/db/entity/ReciteProgressEntity.kt) | 新建 Room Entity |
| [ReciteProgressDao.kt](app/src/main/java/com/echoling/app/data/local/db/dao/ReciteProgressDao.kt) | 新建 DAO |
| [GetReciteProgressUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/GetReciteProgressUseCase.kt) | 新建 UseCase |
| [SaveReciteProgressUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/SaveReciteProgressUseCase.kt) | 新建 UseCase |
| [ObserveAllReciteProgressUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/ObserveAllReciteProgressUseCase.kt) | 新建 UseCase |
| [EchoLingDatabase.kt](app/src/main/java/com/echoling/app/data/local/db/EchoLingDatabase.kt) | v4 → v5 + 注册新 entity/dao |
| [DatabaseModule.kt](app/src/main/java/com/echoling/app/di/DatabaseModule.kt) | 加 `@Provides` 提供 `ReciteProgressDao` |
| [CategoryStudyViewModel.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/CategoryStudyViewModel.kt) | `load()` 读 DB；每个动作后 `persist()` 写 DB |
| [ReciteViewModel.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/ReciteViewModel.kt) | UiState 加 `progressByCategory: Map<String, ReciteProgressEntity>` |
| [ReciteScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/ReciteScreen.kt) | `CategoryCard` 接收 `progress` 参数，渲染真实进度 |

**Schema**：
```kotlin
@Entity(tableName = "recite_progress")
data class ReciteProgressEntity(
    @PrimaryKey @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "current_index") val currentIndex: Int = -1,
    @ColumnInfo(name = "known_count") val knownCount: Int = 0,
    @ColumnInfo(name = "unknown_count") val unknownCount: Int = 0,
    @ColumnInfo(name = "last_studied_at") val lastStudiedAt: Long = 0L,
)
```

**DAO 方法**：
- `fun observeAll(): Flow<List<ReciteProgressEntity>>` — picker 订阅用
- `suspend fun getByCategory(categoryId: String): ReciteProgressEntity?` — 进入详情页时读
- `suspend fun upsert(entity: ReciteProgressEntity)` — `@Insert(onConflict = REPLACE)`

**CategoryStudyViewModel 读写规则**：

| 触发 | 写 DB？ |
|---|---|
| `load()` 启动 | ❌（是读，不是写） |
| `flipCard()` | ❌（瞬时 UI 状态） |
| `markKnown()` | ✅ |
| `markUnknown()` | ✅ |
| `saveCurrentToVocabulary()` | ✅ |
| `skipToNext()` | ✅ |
| `skipToPrevious()` | ✅ |
| `resetSession()` | ✅ |

`load()` 读取时使用 `saved.currentIndex.coerceIn(-1, maxValid)` 防御性夹紧，防止 asset 缩减后历史 index 越界。

**picker 渲染**（[ReciteScreen.kt:200-216](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/ReciteScreen.kt#L200)）：
- 无进度行 → 「未开始学习」
- 有进度行 → 「已学 N / total 词 · 认识 K · 不认识 U」，可选后缀 `· 上次学习于 X 分钟前`（`relativeTimeAgo()`：刚刚 / X 分钟前 / X 小时前 / X 天前，`lastStudiedAt <= 0` 或时钟偏斜时返回空字符串不显示后缀）

**关键参数**（任何后续修改都不能动）：
- DB version **5**（之前 v4）
- 表名 `recite_progress`、PK `category_id`
- `currentIndex = -1` 表示「未开始」（区别于 `0` 表示「已学完第一张」）
- 持久化的 `currentIndex` 是「动作的目标位置」（即用户当前看的那张），不是「刚离开的那张」——重新进入时落点正确

**禁止行为**：
- ❌ ViewModel 直接注入 DAO——必须走 UseCase（违反 §5.3 架构分层）
- ❌ `flipCard()` 也持久化——会写一堆无意义的 IO
- ❌ 改写策略为 PATCH / 单独 UPDATE——`REPLACE` upsert 一次完成，避免读-改-写竞态
- ❌ 把 `progressByCategory` 从 UiState 拿掉——picker 卡片拿不到实时数据

**测试验证**：
- `./gradlew assembleDebug` 通过 ✅
- 打开 高中英语词汇 → 点 认识/不认识 5 次 → back → 重新进入 → 落在第 6 张 ✅
- 杀掉进程 → 重启 app → 仍落在第 6 张 ✅
- 切到 记单词 tab → 高中英语词汇卡片显示「已学 5 / 2340 词 · 认识 3 · 不认识 2」+「上次学习于 刚刚」✅
- 点 reset icon → 卡片回到第 1 张，计数清零 ✅

### 12.20 Vocabulary JSON nested schema support

**原因**：高中英语词汇用的 `vocab_senior.json`（4.6MB / 2340 词）是百度翻译导出的 4 层嵌套 schema（`wordRank → content.word.content.trans[]`），用现有 `Array<DictEntry>` 解析要么 OOM 要么拿到空对象。同时 `gaokao_3500.json` 用的是扁平 schema，两种格式必须共存。

**改动文件**：[DictionaryRepositoryImpl.kt](app/src/main/java/com/echoling/app/data/repository/DictionaryRepositoryImpl.kt)

**关键改动**：
1. 新增 5 层嵌套 data class（`NestedWordEntry → NestedContent → NestedWord → NestedWordContent → List<NestedTrans>`），**所有字段 nullable + 默认 null**
2. **First-line sniff**：解析前先 `assets.open(meta.asset).bufferedReader().use { readLine() }`，如果首行包含 `"wordRank"` → 走嵌套路径，否则走扁平路径
3. `nestedToFlat()` mapper：`headWord → word`、`usphone → phonetic`、`trans[0].pos → pos`、`trans[0].tranCn → translation`
4. 每个 category 用 `runCatching { ... }` 包住，一个文件解析失败不影响其他 category

**关键参数**：
- Sniff 字符串 `"\"wordRank\""`（必须带引号，避免误匹配）
- `BufferedReader.readLine()` 而不是 `read(64)`——`read(int)` 在 `BufferedReader` 上接收 `CharBuffer/CharArray`，传 int 会编译失败

**禁止行为**：
- ❌ 把嵌套 data class 改成 nullable 全 nullable List（嵌套层级需要保留语义）
- ❌ 取消 per-category `runCatching`——一个坏文件会让整个 manifest 加载失败
- ❌ sniff 改成全文件扫描——首行 sniff 是 O(64)，全文件扫描是 O(file size)，对 4.6MB JSON 是几十毫秒的差异

### 12.21 卡片方形化 + Y 轴 3D 翻转动画

**原因**：用户要求「显示的单词卡片过大，需要小一些，是正方形的，当用户点击卡片查看翻译结果是，卡片旋转到背面，再次点击卡片回转到显示单词的那一面」。

**改动文件**：[CategoryStudyScreen.kt:319-399](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/CategoryStudyScreen.kt#L319)

**视觉规格**：
- 卡片 `aspectRatio(1f)` 正方形，外层 Box `padding(horizontal=32.dp).fillMaxWidth()` —— 在 16dp 屏边距下留 32dp 横向内边距
- `graphicsLayer.rotationY = rotation`（0° → 180°）
- `cameraDistance = 12f * density` —— Compose 默认相机太近，>120° 会变鱼眼，×12 保持 180° 自然透视
- `animateFloatAsState(targetValue = if (isFlipped) 180f else 0f, tween(420))` —— 420ms 翻转
- `showBack = rotation > 90f` —— 90° 时卡片边缘对镜头、看不见，此时 swap front↔back

**CardFront / CardBack 双面**：
- **CardFront**（正面）：`rotationY = if (showBack) 180f else 0f` + `alpha = if (showBack) 0f else 1f` —— 反面露脸时正面要 counter-rotate 180° 才不会出现镜像重影
- **CardBack**（反面）：`rotationY = 180f`（预先旋转，所以外层再旋转后字是正的）+ `alpha = if (showBack) 1f else 0f`

**关键修复**：CardBack 不渲染 `entry.word`（最初版有，会让用户感觉正面单词"泄漏"到反面）。布局：[phonetic] → [pos chip] → [translation]，三段竖排。

**关键参数**（任何后续修改都不能动）：
- `aspectRatio = 1f`（正方形）
- `cameraDistance = 12f * density`（不是 `8f`，也不是 `20f`）
- `tween durationMillis = 420`（不是 200/600）
- CardBack 不渲染 `entry.word`

**禁止行为**：
- ❌ 把 `aspectRatio` 改回非 1f（如 1.2f、0.8f）——用户明确要求正方形
- ❌ 用 `AnimatedContent` / `Crossfade` 替代 Y 轴旋转——会失去 3D 翻牌感
- ❌ 删 `cameraDistance` —— 180° 时会变鱼眼
- ❌ 把 CardFront 的 `alpha = 0f when showBack` 删掉——会有正面镜像穿透到反面
- ❌ 在 CardBack 加 `Text(entry.word)`——重现"正面单词泄漏到反面"的 bug

### 12.22 4 按钮贴近卡片 + 答题/导航组用空行分隔

**原因**：用户要求「记忆单词的卡片下方的四个按钮改为紧靠近单词卡片的下方，方便用户点击，给认识/不认识的按钮和上一张/下一张的按钮之间留出空行」——4 个按钮离卡片要近，但 [认识/不认识] 和 [上一张/下一张] 是语义不同的两组，需要视觉分隔。

**改动文件**：[CategoryStudyScreen.kt:206-280](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/CategoryStudyScreen.kt#L206)

**布局层次**（从上到下）：
```
[卡片]
 ↓ 8dp（认识/不认识 紧贴卡片）
[认识]  [不认识]
 ↓ 8dp（条件性：showSaveButton=true 时出现）
[加入单词本]
 ↓ 20dp（答题组 / 导航组空行分隔）
[上一张]                [下一张]
```

**关键参数**：
- 卡片 → 认识/不认识：8dp（紧贴，原本是 20dp）
- 认识/不认识 → 加入单词本：8dp（两者是「答题组」，紧贴）
- 加入单词本 → 上一张/下一张：**20dp 空行**（视觉分隔，不是 4dp/8dp）
- 按钮高度：`认识/不认识 = 56dp`，`加入单词本 = 52dp`（次级按钮略矮）

**禁止行为**：
- ❌ 把 20dp 空行改回 4dp/8dp——上一张/下一张 会和加入单词本粘在一起
- ❌ 把卡片到 认识/不认识 的 8dp 改大——违背「贴近卡片」的要求
- ❌ 把 上一张/下一张 合并到 认识/不认识 行——违反用户明确的「两组分开」要求
- ❌ 把 认识/不认识 行排在 [上一张/下一张] 上面 ——用户从未要求改顺序

### 12.23 「已加入单词本」Snackbar 上移到答题组上方

**原因**：默认 `Scaffold(snackbarHost = { SnackbarHost(...) })` slot 把 snackbar 锚定在屏幕最底部，会盖住 [上一张/下一张] 行。用户要求 Snackbar 出现在 [认识/不认识] 上方。

**改动文件**：[CategoryStudyScreen.kt:92-95, 213-219](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/CategoryStudyScreen.kt#L92)

**关键改动**：
1. **删除** `Scaffold` 的 `snackbarHost` slot
2. `snackbarHostState` 作为参数从 `CategoryStudyScreen` 传入 `StudyBody`
3. `StudyBody` Column 内联 `SnackbarHost(hostState = snackbarHostState)`，位置在 [卡片] 和 [认识/不认识] 行之间

**最终层次**：
```
[卡片]
  │
  │  ⬇  ← 「已加入单词本：xxx」在这里弹出
  │
[认识]  [不认识]
[8dp gap]（条件）
[加入单词本]（仅 showSaveButton=true）
[20dp gap]
[上一张]                [下一张]
```

**关键收益**：
- ✅ Snackbar 在用户视线正下方（刚点的按钮上方），不挡 上一张/下一张
- ✅ 卡片 `weight(1f)` 自动吸收 snackbar 出现时的高度变化（卡片短暂略缩后恢复）
- ✅ 上一张/下一张 永远可见、可点击

**禁止行为**：
- ❌ 把 `SnackbarHost` 改回 `Scaffold` 的 `snackbarHost` slot——会重新盖住 上一张/下一张
- ❌ 把 Snackbar 放在 [上一张/下一张] 下方——那才是 Scaffold 默认底部位置
- ❌ 把 Snackbar 放在 Column 外部用 `Box.align(Alignment.BottomCenter)` 叠层——会失去 inline 视觉位置关系
- ❌ 改用自定义 Banner / Toast——失去 M3 Snackbar 标准的「4 秒自动消失 + 圆角 + inverseSurface 配色」

**测试验证**：
- `./gradlew assembleDebug` 通过 ✅
- 进入任一 category → 点 不认识 → 出现「加入单词本」按钮 → 点 加入单词本 → Snackbar 出现在卡片和 [认识/不认识] 之间 ✅
- Snackbar 显示期间，4 个按钮全部可见、可点击 ✅
- Snackbar 4 秒后自动消失，卡片恢复到原尺寸 ✅

### 12.24 闪卡 + 生词本 TTS 单词发音

**原因**：用户要求「在单词卡片中加单词发音」——闪卡（记单词）和生词本（单词本）目前只展示英文文本 + 音标（IPA）+ 翻译，用户必须自己读 IPA 猜发音或朗读，没有「听」这一通道。

**方案**：
- **TTS 引擎**：Android 内置 `android.speech.tts.TextToSpeech`（离线、免费、无需 API key、无需联网、英语开箱即用）
- **触发位置**：闪卡正面 + 闪卡反面 + 生词本每行单词各加一个喇叭 `IconButton`
- **范围**：闪卡（记单词）+ 生词本（单词本）都加

**改动文件**：

| 路径 | 类型 | 说明 |
|---|---|---|
| [TtsManager.kt](app/src/main/java/com/echoling/app/player/TtsManager.kt) | 新建 | `@Singleton` TTS 封装，多引擎 fallback + 自动发现 + 防御式 |
| [CategoryStudyViewModel.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/CategoryStudyViewModel.kt) | 修改 | 注入 `TtsManager`，新增 `pronounceCurrent()` + ttsUnavailableMessage 检测 |
| [CategoryStudyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/CategoryStudyScreen.kt) | 修改 | 闪卡正面单词右侧同行喇叭 + 闪卡反面音标旁内联喇叭；`onPronounce` 贯穿 `FlashCard` / `StudyBody` / 屏级调用；Snackbar 显示 TTS 不可用 |
| [VocabularyViewModel.kt](app/src/main/java/com/echoling/app/presentation/viewmodel/VocabularyViewModel.kt) | 修改 | 注入 `TtsManager`，新增 `pronounce(word: Word)` + ttsUnavailableMessage |
| [VocabularyScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt) | 修改 | `WordRow` trailing icon 组加喇叭（位置：phonetic → 🔊 → ✓ → 🗑）；Snackbar 显示 TTS 不可用 |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) | 修改 | 加 `<queries>` 声明 `android.intent.action.TTS_SERVICE` |

**架构要点**（与现有模式对齐）：
- `TtsManager` 直接注入 ViewModel，**跳过 UseCase 层**——CLAUDE.md §5.3 规定只有 Repository 调用需要 UseCase，TTS 是纯副作用服务（与 `AudioPlayer` / `VoiceRecorder` 同类），不需要 UseCase
- `@Singleton + @Inject constructor` 自动注册，无需 `PlayerModule.kt` 改动
- 与 `AudioPlayer` 同住 `player/` 包，副作用服务的「家」

**TtsManager 多引擎 fallback 框架**：

7 个硬编码候选 + 任何 PackageManager 发现到的 TTS 服务，挨个尝试，第一个绑定成功的获胜：

| 顺序 | 包名 | 适用设备 |
|---|---|---|
| 1 | `com.google.android.tts` | GMS 设备（Pixel / Samsung 等） |
| 2 | `com.xiaomi.tts` | 国际版 MIUI |
| 3 | `com.iflytek.speechsuite` | 讯飞语音+ 独立包 |
| 4 | `com.baidu.duersdk.tts` | 百度 TTS |
| 5 | `com.android.tts` | AOSP |
| 6 | `com.svox.pico` | 老 AOSP fallback |
| 7+ | `[PackageManager 发现到的]` | 任何注册了 `TTS_SERVICE` 的 app（如 `com.xiaomi.mibrain.speech` / `com.iflytek.vflynote`） |
| 末 | `null`（系统默认） | 兜底 |

**关键设计决策**：

| 决策 | 选择 | 原因 |
|---|---|---|
| TTS 引擎 | `android.speech.tts.TextToSpeech` | 离线 / 免费 / 英语开箱即用；不依赖外部服务 |
| 语言 | 固定 `Locale.US` | 词库全是英文（Gaokao / CET / TOEFL），不需要 zh-CN |
| 队列模式 | `QUEUE_FLUSH` | 连续点两次喇叭 = 「再放一次」，取消前一次重新播放 |
| 异步 init 处理 | 维护 `pending` 队列 + 同步锁 | 冷启动时 TTS init 还没完成就点喇叭，speak 会静默失败；维护 pending 队列在 init 成功后 drain |
| Drain 策略 | 只播队列最后一项 | 之前排队的都是 stale（用户已翻页），只有最后一项是当前意图 |
| Shutdown | 不暴露 | singleton 跟进程同生命周期 |
| **Locale 不支持** | **仍翻 `isReady = true`** | 不 gate 在 `setLanguage` 的返回值上。中文设备 + 默认 Pico TTS 经常返回 `LANG_MISSING_DATA`（-1），按 `result >= LANG_AVAILABLE`（0）判断就永远不翻 `isReady`，speak 一直入队、永远不 drain |
| **每步 try/catch** | 防御式 | iFlytek 等第三方引擎经常在 `shutdown()` / `setAudioAttributes()` / `setLanguage()` 上抛异常，单引擎异常不能让 app 闪退 |
| **每个引擎之间 50ms 间隔** | `mainHandler.postDelayed(50ms)` | 让上一个 engine 的 shutdown 消息有时间分派，避免 back-to-back shutdown+bind 让 iFlytek 崩溃 |
| **`engineCandidates` 必须在 `init` 之前声明** | Kotlin 类初始化顺序 | `init` 块在主构造器后立即执行，早于其后声明的属性。读 `engineCandidates.size` 会 NPE（曾在小米 Mi 11 上闪退） |
| **PackageManager 自动发现** | `queryIntentServices(TTS_SERVICE)` | 不依赖硬编码 package 名，能找到任何注册了 TTS_SERVICE 的引擎 |
| **`<queries>` 声明** | Manifest | Android 11+ 不声明则 PackageManager 查询返回空，即使引擎已装且可用 |

**关键属性顺序**（TtsManager 类内部）：
```kotlin
private var tts: TextToSpeech? = null          // 1. nullable backing
private val _isAvailable = MutableStateFlow(false)   // 2. state
private val _isReady = MutableStateFlow(false)
private val pending = mutableListOf<String>()  // 3. queue
private val lock = Any()
private val mainHandler = Handler(Looper.getMainLooper())
private val engineCandidates: List<String?> = run { ... }  // 4. MUST be before init

init { tryNextEngine(0) }  // 5. last — reads engineCandidates
```

**UI 摆放规则**：

| 位置 | IconButton 尺寸 | Icon 尺寸 | 配色 |
|---|---|---|---|
| 闪卡正面（单词右侧同行） | 44dp | 26dp | `onPrimaryContainer.copy(alpha=0.85f)` |
| 闪卡反面（音标旁） | 32dp | 20dp | `onSecondaryContainer.copy(alpha=0.8f)` |
| 闪卡反面（无音标时） | 32dp | 20dp | `onSecondaryContainer.copy(alpha=0.8f)` |
| 生词本 trailing 组 | 32dp | 18dp | `onSurfaceVariant` |

**闪卡正面布局**（v2 — 喇叭移到单词右侧）：
```
┌──────────────────────────┐
│      word [🔊]           │  ← Row 居中：单词 + 8dp gap + 喇叭
│   点击卡片查看翻译         │
└──────────────────────────┘
```

**AndroidManifest.xml**（关键）：
- 无需新增 `<uses-permission>`——TTS 是系统服务
- **必须**加 `<queries>` 声明 `android.intent.action.TTS_SERVICE`——Android 11+ package visibility 默认隐藏所有未声明的包
- 不声明 → 即使 Google TTS 装了，PackageManager 也返回空列表 → 永远找不到引擎

**build.gradle.kts**：无需新增依赖。

**禁止行为**：
- ❌ 把 `TtsManager` 改成非 `@Singleton`——会创建多实例浪费 TTS engine
- ❌ 把 TTS 调用写到 Composable 内直接调（绕过 ViewModel）——破坏 MVVM 分层
- ❌ 给 `speak()` 加 `runBlocking` 等待 init 完成——会卡主线程
- ❌ 把 `Locale.US` 改成 `Locale.getDefault()`——中文 locale 时 TTS 会读 "hello" 用奇怪的中文发音规则
- ❌ 把 `engineCandidates` 移到 `init` 块**之后**——会重新触发 NPE 闪退
- ❌ 把 `try/catch` 包裹从 engine 操作里删掉——单引擎异常会让 app 闪退
- ❌ 删 `<queries>` 声明——Android 11+ 上 TTS 引擎不可见
- ❌ 给 TTS 加开关 / 设置页（语速、音调、音色）——P2 再说，YAGNI
- ❌ 给生词本 / 闪卡加自动发音（进入页面自动播放）——打扰用户
- ❌ 把喇叭按钮放在单词 Text 同行（挤占单词空间）——必须独立 IconButton

**已知陷阱**（曾在这台机器上踩过）：
1. **`const val WD_TAG = null`** —— `const val` 必须原语或 String，`null` 是 `Nothing?`。已删，直接传 `null` 给 `removeCallbacksAndMessages`。
2. **`init` 块读 `engineCandidates.size` NPE** —— Kotlin 按声明顺序初始化属性，`init` 早于其后声明的 val。`engineCandidates` 必须在 `init` 之前。
3. **PackageManager 永远返回空** —— Android 11+ 不声明 `<queries>` 就看不到 TTS 服务。

**测试验证**（小米 Mi 11 CN 实测）：
- `./gradlew assembleDebug` 通过 ✅
- TtsManager log 路径：`Discovered TTS engines via PackageManager: [com.xiaomi.mibrain.speech, com.iflytek.vflynote]` → `Engine [0] com.google.android.tts init SUCCESS` → `setLanguage(Locale.US)=1` (LANG_AVAILABLE) → `speak('protective') result=0` (SUCCESS) → `utterance onStart/onDone` 配对 ✅
- 闪卡正面 / 反面 / 生词本 喇叭按钮都能发声 ✅
- 闪退修复（之前 `engineCandidates.size` NPE）✅
- 优雅降级：所有引擎失败 → 显示 snackbar「设备未安装 TTS 引擎，请到应用商店安装...」✅

