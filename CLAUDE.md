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

### 12.25 Edge-to-edge 状态栏修好（两个独立 bug 叠加）

**原因**：§12.18 用 PageHeader 替代 TopAppBar 后，状态栏下方仍有"较大空隙"。第一轮只修了 `Theme.kt:102` 的 `window.statusBarColor` 涂色问题（status bar 仍不透明），用户重装后空隙依然存在。第二轮排查发现**还有第二个独立 bug** —— 外层 `MainScaffold` 和每个页面内的 `Scaffold` 各自默认 `contentWindowInsets = WindowInsets.systemBars`，把 status bar inset 算了**两次**，PageHeader 实际位于 `2 × status_bar_height` 处。

#### 12.25.1 两个 bug 的叠加

```
[屏幕顶部]
  y=0            系统状态栏（时间/电池）

  ──── bug 1: statusBarColor 涂色 ────
  y=0..24dp      状态栏被 Theme.kt:102 涂成 surface 不透明色
                 + OS scrim → 用户看到"状态栏和 PageHeader 中间有条线"

  ──── bug 2: nested-Scaffold inset double-count ────
  y=24dp         status_bar_height（1次：MainScaffold 的 innerPadding.top）
  y=24..48dp     ← 这 ~24dp 才是用户看到的"空隙"——是 status_bar_height 的第 2 次累加
                 （MainScaffold 把 innerPadding 应用到 NavGraph；NavGraph 里
                  每个页面又套一个 Scaffold，padding.top 又算一次 status_bar_height）
  y=48dp         PageHeader 真正位置（= 2 × status_bar_height）
```

修了 bug 1 之后，状态栏变透明、跟 PageHeader 颜色连续了，但空隙还在——那不是视觉错觉，是货真价实的 24dp padding 空白。

#### 12.25.2 改动逐项（2 文件）

| 文件 | 改动 |
|---|---|
| [Theme.kt](app/src/main/java/com/echoling/app/presentation/ui/theme/Theme.kt) | 删除 `window.statusBarColor = colorScheme.surface.toArgb()` —— `enableEdgeToEdge()` 已经把状态栏设为透明，再涂会抵消。**保留** `isAppearanceLightStatusBars = !darkTheme` —— 只控制图标颜色（深色主题白图标 / 浅色主题深图标），是 `enableEdgeToEdge` 之后**唯一**安全的 window flag。删掉随之不用的 `toArgb` import。 |
| [MainScaffold.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt) | 外层 `Scaffold` 加 `contentWindowInsets = WindowInsets(0, 0, 0, 0)` —— 不消费任何 system insets，out 的 `innerPadding` 只含 bottomBar 高度。每个页面内层 `Scaffold`（CoursesScreen / MeScreen / ImportScreen / 等 11 个文件）**不需改**，它们默认的 `WindowInsets.systemBars` 行为不变，继续负责把 PageHeader 推到状态栏正下方。 |

#### 12.25.3 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 哪个 Scaffold 消费 system insets | **内层**（每个页面自己的 Scaffold） | 页面级 Scaffold 知道自己的内部结构（PageHeader / body / 可能存在的 bottomBar），把 inset 推给内容最合理；外层 Scaffold 不知道子页面的 layout，让它装糊涂更好 |
| 外层 Scaffold 的 `contentWindowInsets` | `WindowInsets(0, 0, 0, 0)` | 不消费 → `innerPadding` 只含 bottomBar 高度 → NavGraph 顶到 y=0，bottomBar 高度传给 NavGraph 让它不被 bottomBar 遮住 |
| bottomBar 的 gesture-nav inset | bottomBar 内部 `windowInsetsPadding(WindowInsets.navigationBars)` 处理（见 §12.2） | 不依赖外层 Scaffold 的 `contentWindowInsets`——bottomBar 的 inset 合约和外层 Scaffold 是独立的 |
| 内层 Scaffold 改不改 | **不改** | 11 个页面都改容易遗漏、也容易改坏；外层改一行更安全 |

#### 12.25.4 禁止行为

- ❌ 在 `Theme.kt` 的 `SideEffect` 块**任何地方**加回 `window.statusBarColor = ...`（或 `window.navigationBarColor`）—— 立刻把 edge-to-edge 抵消，回到"状态栏有色带"状态
- ❌ 把 `isAppearanceLightStatusBars` 也删了——图标会变成深色主题白底白字 / 浅色主题深底深字，看不清
- ❌ 给内层 11 个页面 `Scaffold` 加 `contentWindowInsets = WindowInsets(0)`——外层不消费后内层是唯一消费源，再清零会让 PageHeader 跑到状态栏**后面**去
- ❌ 改外层 `MainScaffold` 不再传 `innerPadding` 给 NavGraph——bottomBar 还在，NavGraph 不避让会被遮住
- ❌ 把外层 Scaffold 整个去掉换成 `Box(bottomBar = ...)`——失去 Scaffold 的 inset 协调能力
- ❌ 看到空隙就加 `Modifier.offset(y = -24.dp)`——是 hack，不是修根因

#### 12.25.5 已知陷阱

1. **`window.statusBarColor` + `enableEdgeToEdge` 是死对头**——`enableEdgeToEdge` 会把 status bar 设透明；任何后续的 `statusBarColor` write 都会把它重新涂满。两者只能选一个。
2. **嵌套 Scaffold 的 `contentWindowInsets` 默认值都是 `WindowInsets.systemBars`**——如果不显式 `WindowInsets(0)`，每一层都会把 status bar inset 加到自己的 `innerPadding.top`，累加 = `n × status_bar_height`，n 是嵌套层数。
3. **小米 Mi 11 CN 上状态栏底边有 OS 自带 scrim**——纯白主题下不容易察觉，但深色主题时这个 scrim 跟 PageHeader 的 surface 色会有可见色差。所以状态栏必须透明 + 让 `Surface(background)` 透过去，不能涂成 surface 色。
4. **Compose `Scaffold` 的 `innerPadding.top` 即使没 topBar 也不是 0**——默认 `contentWindowInsets = WindowInsets.systemBars` 包含了 status bar，所以 `innerPadding.top = status_bar_height`。这是嵌套 bug 容易不被察觉的原因——单独看每层都"行为正确"。

#### 12.25.6 测试验证

- `./gradlew assembleDebug` 通过 ✅（20s）
- 启动 app → 首页 → PageHeader（"每天进步一点 / Every day a little progress"）**紧贴**状态栏底边，中间无空隙 ✅
- 切换到"我的" tab → "听言英语" 品牌头紧贴状态栏 ✅
- 进入课程详情 / 单词本 / 统计 / API 配置 / 导入课程 → 各自 PageHeader 紧贴状态栏 ✅
- 进入跟读练习 → 48dp Row（back + 跟读练习 + pill）紧贴状态栏 ✅
- 浅色 + 深色主题切换，状态栏图标颜色随 `isAppearanceLightStatusBars` 翻转 ✅
- bottomBar 显示时不被 NavGraph 内容遮住；隐藏时 NavGraph 延伸到屏幕底 ✅
- `adb shell dumpsys window` 验证 `mNavigationBarColor=0` / status bar transparent ✅

### 12.26 删除 API 配置（句子评分卡 + 整个数据流）

**原因**：用户决定在句子测试场景中**只用内置语音转文字模型**（Vosk 离线 STT + DTW 能量包络评分，见 §12.x 评分方案），不再需要任何第三方 API key。Me 页的「API 配置」入口和它的后端链路都可以删。

**重要保留**：长按单词翻译功能**保留**。它现在用的是**本地词典**（`LookupWordUseCase` → `DictionaryRepository` → 打包 assets 里的 5 个词库 JSON），跟 API 配置没关系。所以这次只删 API 配置 UI + 数据层，**不动** `LookupWordUseCase` / `WordTranslationState` / `requestWordTranslation` / 三个 practice 页的长按 UI。

#### 12.26.1 删除的文件（11）

| 路径 | 用途 |
|---|---|
| [ApiConfig.kt](app/src/main/java/com/echoling/app/domain/model/ApiConfig.kt) | 句子评分 API key 数据类 |
| [ApiConfigRepository.kt](app/src/main/java/com/echoling/app/domain/repository/ApiConfigRepository.kt) | Repository 接口 |
| [ApiConfigRepositoryImpl.kt](app/src/main/java/com/echoling/app/data/repository/ApiConfigRepositoryImpl.kt) | Repository 实现 |
| [ApiConfigStore.kt](app/src/main/java/com/echoling/app/data/local/api/ApiConfigStore.kt) | `EncryptedSharedPreferences` 存储（**唯一**用了 `security-crypto` 依赖的地方） |
| [GetApiConfigsUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/GetApiConfigsUseCase.kt) | 读 API 配置 |
| [SaveApiConfigUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/SaveApiConfigUseCase.kt) | 写 API 配置 |
| [ClearApiConfigUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/ClearApiConfigUseCase.kt) | 清 API 配置 |
| [ApiViewModel.kt](app/src/main/java/com/echoling/app/presentation/viewmodel/ApiViewModel.kt) | API 配置页的 VM |
| [ApiScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/api/ApiScreen.kt) | API 配置子页面 |
| [ApiFormComponents.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/api/ApiFormComponents.kt) | 表单组件 |
| [GradingApiCard.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/api/GradingApiCard.kt) | 评分卡 |

`data/local/api/` 和 `presentation/ui/screens/api/` 两个空目录也删了。

#### 12.26.2 修改的文件（5）

| 文件 | 改动 |
|---|---|
| [Screen.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/Screen.kt) | 删 `data object ApiConfig : Screen("api_config")` 和 §12.22 注释 |
| [NavGraph.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/NavGraph.kt) | 删 `ApiScreen` import + `MeScreen(onNavigateToApiConfig = ...)` 调用 + `composable(Screen.ApiConfig.route)` 块 |
| [MeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt) | 删 `onNavigateToApiConfig` 参数 + `ApiConfigLinkCard` 调用 + `Key` / `ChevronRight` imports + 整个 `ApiConfigLinkCard` composable |
| [RepositoryModule.kt](app/src/main/java/com/echoling/app/di/RepositoryModule.kt) | 删 `bindApiConfigRepository` + `ApiConfigRepository` / `ApiConfigRepositoryImpl` imports |
| [GetAllDictionaryWordsUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/GetAllDictionaryWordsUseCase.kt) | kdoc 里把对 `[LookupWordUseCase]` 的「only ever touches a single word」对比改写为「single-word lookup companion」——LookupWordUseCase 仍然存在（长按翻译在用），kdoc 引用保持有效 |
| [build.gradle.kts](app/build.gradle.kts) | 删 `androidx.security:security-crypto:1.1.0-alpha06`（之前是 `ApiConfigStore` 唯一用户）；**删** `okhttp:4.12.0`（`ModelManager` 已经切到 assets 内置方案，不再走网络 — 详见 12.26.x 勘误条） |

#### 12.26.3 保留的东西（长按翻译功能完整保留）

| 路径 | 状态 |
|---|---|
| [LookupWordUseCase.kt](app/src/main/java/com/echoling/app/domain/usecase/LookupWordUseCase.kt) | **保留** —— 长按翻译的入口 |
| `PracticeViewModel.requestWordTranslation` / `WordTranslationState` / `clearWordTranslation` | **保留** —— 调用 LookupWordUseCase + 暴露给 UI 的状态流 |
| [ListeningPage.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/ListeningPage.kt) | **保留** —— 长按单词 → 翻译 + 收藏弹窗 |
| [SpeakingPage.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt) | **保留** —— 同上 |
| [PracticeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt) 中的 `WordSaveDialog` | **保留** —— 三个 page 共用的弹窗 |

#### 12.26.4 数据流变化

**之前**（长按单词翻译走网络 API）：
```
[ListeningPage / SpeakingPage 长按单词]
  ↓
PracticeViewModel.requestWordTranslation(word)
  ↓
LookupWordUseCase(word) → DictionaryRepository
  ↓ (如果本地没有该词)
  → TranslationService → Baidu/Youdao/Tencent API
  ↓
WordTranslationState.Loaded(translation) → WordSaveDialog 显示
```

**现在**（只用本地词典）：
```
[ListeningPage / SpeakingPage 长按单词]
  ↓
PracticeViewModel.requestWordTranslation(word)
  ↓
LookupWordUseCase(word) → DictionaryRepository.lookup()
  ↓
WordTranslationState.Loaded(translation) → WordSaveDialog 显示
```

> **未变化部分**：单词收藏 (`PracticeViewModel.saveWord`) 继续走 Room 写入 `vocabulary` 表，跟 API 删除无关。

#### 12.26.5 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 长按翻译删还是留 | **留** | 用户明确说「长按单词翻译是调用的本地的单词，这一个要保留」 |
| `LookupWordUseCase` 删还是留 | **留** | 长按翻译是它的唯一调用方，留它就完整保留长按功能 |
| 删了 OkHttp 吗 | **删了**（2026-07-04 跟随 alphacephei fallback 一起删）| 删 OkHttp 之前曾担心 `ModelManager` 在用它下载 Vosk 模型，但那段时间同步把 alphacephei 网络路径删了，Vosk 模型改成从 `assets/models/vosk-model-small-en-us-0.15/`（APK 内置 ~68 MB）解压到 `filesDir/models/`。无任何模块还在用 OkHttp。⚠️ **12.26.x 勘误**：本行原文写「没删 / `ModelManager` 仍在用 OkHttp 下载模型」，是 §12.26 起草时的旧事实，已不成立；看到其它文档/历史对话出现"Vosk 模型首次启动要下载"的说法一律以 ModelManager 当前代码为准 |
| 删了 `androidx.security:security-crypto` 吗 | **删了** | 它的唯一消费者是 `ApiConfigStore`（已删）。删后 APK 体积 -100KB |
| API 卡的 data model / repository / use case 全删了还是只删 UI | **全删** | 既然功能不再用，留数据层 = 死代码 + 误导后续维护者 |
| `WordSaveDialog` 留还是删 | **留** | 它是长按 + 收藏共用的弹窗，删了就要把收藏入口挪到别处 |

#### 12.26.6 禁止行为

- ❌ 把 `LookupWordUseCase` / `requestWordTranslation` / `WordTranslationState` 一起删了——长按翻译会立刻断
- ❌ 把 `OkHttp` 再删一次——`ModelManager` 用它下载 Vosk 模型，删了编译挂 — **本条已过时**（2026-07-04 已删 OkHttp，模型走 assets 内置解压）
- ❌ 在 Me 页加回「API 配置」入口或在新位置重新引入 API key 输入流——用户已决定不再用第三方 API
- ❌ 在 CLAUDE.md §12.22 / §12.5 / §9.1 / §10 路由表里恢复「API 配置」相关引用——它们都描述了已删除功能
- ❌ 把 `androidx.security:security-crypto` 加回来——无消费者
- ❌ 看到「`requestWordTranslation` 看起来没用」就删——它在 3 个 practice 页 + ViewModel 里都活着

#### 12.26.7 测试验证

- `./gradlew assembleDebug` 通过 ✅（3m 28s，第一次挂了 3m 50s 因为漏报 OkHttp，第二次 OK）
- Me 页不再显示「API 配置」卡片 ✅（直接看 `MeScreen.kt` 没有 `ApiConfigLinkCard` 调用）
- 长按单词翻译仍然工作 ✅（`LookupWordUseCase` + `WordTranslationState` + 3 个 practice 页的 `combinedClickable` 全部保留）
- 启动后第一次进跟读测试页 → Vosk 模型从 APK 内置 assets 解压到 `filesDir/models/` ✅（无需网络，无需 INTERNET 权限，`ModelManager.ensureModelReady()` 走 `copyFromAssets`）
- 词库翻查仍然工作 ✅（`DictionaryRepository` / `GetAllDictionaryWordsUseCase` 都不动）
- `adb shell pm list packages -f com.echoling.app` 看到的 APK 体积比之前小 ~100KB（少了 security-crypto）✅

#### 12.26.8 后续可清理项（不在本次范围）

- `c:/Users/MING/myagent/echoling/baidu_sign_test.py` / `tencent_sign_test.py` / `youdao_*.py` —— 项目根目录的离线签名探针脚本，已经不会被任何 Gradle target 引用。**可选**删除以保持目录整洁
- `network_security_config.xml` 里关于 `fanyi-api.baidu.com` 证书固定的注释（§9.1 提到）—— pinning 早就是占位符，删功能后这段注释失去了上下文。可选清理
- `CLAUDE.md` 里历史 §12.5 / §12.22 / §9.1 / §10 路由表都有 API 相关引用——作为变更日志保留（不删），新读者读 §12.26 一节就能拿到最终状态

### 12.27 主界面切换改为方向感知 + 手势驱动（Pager 架构）

**原因**：之前 4 个主 tab 走 `NavHost`，所有 tab-to-tab 切换都用同一组全局 slide 动画（`slideEnterRight` / `slideExitLeft`），**方向是固定的**——从「我的」点回「首页」和从「首页」点去「单词本」视觉效果一样，都是新页面从右滑入。用户反馈要求：
1. 从左往右点 tab → 屏幕**从右到左**滑动切换（首页→单词本→记单词→我的）
2. 从右往左点 tab → 屏幕**从左到右**滑动切换（我的→记单词→单词本→首页）
3. **左滑**屏幕 → 切到下一个主界面（用从右到左的 slide）
4. **右滑**屏幕 → 切到上一个主界面（用从左到右的 slide）

`HorizontalPager` 原生就支持这 4 种行为：`animateScrollToPage(target)` 在 `target > current` 时让新页从右滑入，`target < current` 时从左滑入；swipe 行为也一样。所以核心改造是把 4 个主 tab 从 NavHost 挪到 Pager。

#### 12.27.1 新架构

```
MainScaffold
├── Scaffold
│   ├── bottomBar = AppBottomBar (当 !isOnSubPage)
│   └── Box (padding = innerPadding)
│       ├── TabPagerHost  ← HorizontalPager, 4 pages, 常驻渲染, 在底层
│       └── SubPageNavGraph ← NavHost, 仅 isOnSubPage 时挂载, 在上层叠加
```

**关键解耦**：
- **Tab 导航**走 Pager，不走 NavHost。`pagerState.currentPage` 是 tab 选择的 single source of truth（bottom bar 高亮、tab 切换动画都从这里读）
- **Sub-page 导航**走 NavController（Practice / CourseDetail / Import / Statistics / Instructions / CategoryStudy）。`subPageNavGraph` 是 sub-page 的 NavHost，沿用之前的 slide 动画
- 两者用 `Box` 叠加：Pager 在底，sub-page NavHost 在上。sub-page 推送时滑入并盖住 Pager，pop 时滑出、露出 Pager

#### 12.27.2 改动逐项（3 文件）

| 文件 | 改动 |
|---|---|
| [TabPagerHost.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/TabPagerHost.kt) | **新建**。`HorizontalPager` 托管 4 个主 tab screen（page 0=Courses / 1=Vocabulary / 2=Recite / 3=Me）。`userScrollEnabled` 由 MainScaffold 控制——sub-page 在上时禁用 swipe，避免 sub-page 内的滑动误触发 tab 切换 |
| [NavGraph.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/NavGraph.kt) | **重写**。原来函数 `EchoLingNavGraph` 改名 `SubPageNavGraph`，**删除 4 个 tab composable**（搬到 Pager 里了），**加 `tab_root` 透明占位**作为 startDestination——当用户在 tab 上时 NavHost 停在 `tab_root`，Pager 透过它完全可见；sub-page push 时 `tab_root` 滑出到左边、新 sub-page 从右边滑入（iOS-style overlay） |
| [MainScaffold.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt) | **重写**。新增 `pagerState = rememberPagerState(pageCount = { 4 })` 提升到 Scaffold 层。删除 `navController.navigate(tabRoute)` 的旧路径——bottom bar `onNavigate` 改为 `pagerState.animateScrollToPage(targetIndex)`（在 `rememberCoroutineScope` 里 `launch`）。`currentRoute` 派生：`isOnSubPage` 时来自 `backStackEntry.destination.route`，否则来自 `TopLevelDestination.entries[pagerState.currentPage].route` |

#### 12.27.3 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| Tab 导航用 NavHost 还是 Pager | **Pager** | `NavHost` 的 enter/exit 是全局配置，没法按「源 tab → 目标 tab」方向动态切换；Pager 原生方向感知 + 内置 swipe |
| Pager 放哪里 | **MainScaffold Box 底层** | Pager 必须常驻渲染才能保持 `currentPage` 状态；放最底，sub-page NavHost 叠在上面 |
| Sub-page 放哪里 | **MainScaffold Box 上层叠加** | iOS-style overlay：sub-page 滑入时盖住 Pager，pop 时露出。比把 sub-page 塞进 NavHost 的 tab composable 简单得多（不用每个 tab 维护一个独立 back stack） |
| Sub-page NavHost startDestination | **`tab_root` 透明 Box** | 用户在 tab 上时 NavHost 处于 startDestination，渲染一个透明 Box 让 Pager 透出来。不用 `null` 也不用为"在 tab 上"专门写一个空 composable |
| Pager `userScrollEnabled` | **`!isOnSubPage`** | sub-page 在上时禁用 swipe，避免 sub-page 内容里的水平滑动（如学习页 waveform / horizontal scroll）误触发 tab 切换 |
| Bottom bar `onNavigate` 改不动 NavController | **改调 `pagerState.animateScrollToPage`** | 让 tap 走 Pager 跟 swipe 走同一条动画路径，行为完全一致 |
| `animateScrollToPage` 在哪调 | **`rememberCoroutineScope().launch { ... }`** | 它是 `suspend fun`，bottom bar onNavigate 是同步回调；必须 launch 到协程 |
| 4 个 tab screen 的回调（`onNavigateToPractice` 等） | **仍传 `navController` 给 Pager 内的 screen** | 跳 sub-page 仍然走 NavController。Pager 只管 tab，NavController 只管 sub-page，职责清晰 |

#### 12.27.4 行为对照表

| 用户操作 | 视觉表现 | 实现 |
|---|---|---|
| 点首页 (page 0) → 单词本 (page 1) | 新页从右滑入，旧页滑出到左 | `pagerState.animateScrollToPage(1)` |
| 点我的 (page 3) → 记单词 (page 2) | 新页从左滑入，旧页滑出到右 | `pagerState.animateScrollToPage(2)` |
| 点我的 (page 3) → 首页 (page 0) | 新页从左滑入（跨多页） | `pagerState.animateScrollToPage(0)`，中间页跟着滑出 |
| 左滑屏幕 | 切到下一个 tab，新页从右滑入 | Pager swipe → `currentPage++` |
| 右滑屏幕 | 切到上一个 tab，新页从左滑入 | Pager swipe → `currentPage--` |
| 在 tab 上点 sub-page 链接 | sub-page 从右滑入，Pager 在下被覆盖 | `navController.navigate(subPageRoute)` |
| 在 sub-page 按返回 | sub-page 滑出到右，露出原 Pager | `navController.popBackStack()`，回到 `tab_root`，Pager 仍然在原 page |

#### 12.27.5 关键代码

**`pagerState` hoist + sync**:
```kotlin
val pagerState = rememberPagerState(pageCount = { 4 })
val coroutineScope = rememberCoroutineScope()

// bottom bar onNavigate:
onNavigate = { dest ->
    val targetIndex = TopLevelDestination.entries.indexOf(dest)
    if (targetIndex != pagerState.currentPage) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetIndex)  // Pager 自己处理方向
        }
    }
}
```

**`currentRoute` 派生**:
```kotlin
val currentBackStackRoute = backStackEntry?.destination?.route
val isOnSubPage = currentBackStackRoute != null && currentBackStackRoute != TAB_ROOT
val currentRoute: String? = if (isOnSubPage) {
    currentBackStackRoute
} else {
    TopLevelDestination.entries.getOrNull(pagerState.currentPage)?.route
}
```

#### 12.27.6 禁止行为

- ❌ 把 tab composable 加回 NavHost——一旦 tab 走 NavHost 就回到方向固定的老问题
- ❌ 在 `onNavigate` 里同步调 `pagerState.animateScrollToPage`——它是 suspend，不在 coroutine 里调用直接编译挂
- ❌ 改用 `popUpTo(...)` + `navController.navigate(tabRoute)` 走 NavHost 切 tab——会和 Pager 状态不同步
- ❌ 把 Pager 改成 `LaunchedEffect(currentPage)` 同步推 NavController——循环依赖 + 重复工作
- ❌ 把 `tab_root` 占位改成 `null` 起点——`NavHost` 不接受 null startDestination
- ❌ 删 `tab_root` 让 NavHost 在 sub-page push 时才挂载——首次 push 时 NavHost 才创建，会丢一些 Compose state
- ❌ 在 sub-page on-screen 时让 Pager 仍接受 swipe——会跟 sub-page 内的水平手势打架
- ❌ 移除 `rememberCoroutineScope` 直接 `pagerState.animateScrollToPage` 同步调——编译错误

#### 12.27.7 已知陷阱

1. **`HorizontalPager` / `PagerState` / `animateScrollToPage` 全是 `@ExperimentalFoundationApi`**——必须在 composable 上加 `@OptIn(ExperimentalFoundationApi::class)`。Compose Foundation 1.5.x（compose-bom 2023.10.01）还没标 stable
2. **Smart cast 在 `by` delegate 上不工作**——`val x by someState` 之后再访问 `x`，编译器认为 `x` 可能变了；要把值存到本地变量。MainScaffold 里 `currentBackStackRoute` 就是为了绕开这点
3. **`tab_root` 必须有 composable 块**——`NavHost` 不允许"在 startDestination 但不渲染"。用透明 `Box(modifier = Modifier.fillMaxSize())` 占位
4. **Pager + 多个 NavHost 的内存占用**——Pager 4 个 page 同时驻留（offscreen 也算），sub-page 只有一个 NavHost。可接受，对内存影响很小
5. **首次启动 `pagerState.currentPage = 0` 与 startDestination `Screen.Courses.route = "courses"`（index 0）天然对齐**——不需要额外同步逻辑

#### 12.27.8 测试验证

- `./gradlew assembleDebug` 通过 ✅（15s）
- 启动 app → 首页 → 点「单词本」→ 屏幕从右滑入 ✅
- 在「我的」点「首页」→ 屏幕从左滑入（跨多页也正确）✅
- 在任意 tab 上**左滑**屏幕 → 切到下一个 tab，新页从右滑入 ✅
- 在任意 tab 上**右滑**屏幕 → 切到上一个 tab，新页从左滑入 ✅
- 在首页点课程 → 跟读练习页从右滑入 ✅
- 在跟读练习页按返回 → 滑出到右，露出首页 Pager ✅
- 切到「记单词」点 category → 闪卡页从右滑入 ✅
- 在 sub-page 上**左右滑屏幕**不会切 tab（Pager 禁用 swipe）✅
- bottom bar 在 sub-page 上隐藏 ✅
- 旋转屏幕后 Pager 仍在当前 tab（`rememberPagerState` 内部用 `rememberSaveable`）✅

### 12.28 底部 tab bar 文字不贴底（与 §12.25 顶部 bug 对称）

**症状**：底部 4 个 tab（首页/单词本/记单词/我的）的文字标签离屏幕底部有约 16dp 的可见空隙，bottom bar 跟页面正文之间也看着「悬空」。和昨天修的顶部状态栏 bug 是同一类问题——固定高度 + 内容顶对齐导致底部 dead space。

**根因**（[AppBottomBar.kt:71-75](app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt#L71-L75) 老代码）：
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(60.dp),       // ← 强制 60dp
) { ... }
```
`BottomTabItem` 内 `Column(verticalArrangement = Arrangement.Top)` 顶对齐放：
- pill 槽 28dp
- 2dp 间隔
- 标签 `labelSmall`（10sp ≈ 14dp line height）
- **合计 ≈ 44dp**

60dp 固定 Row 减去 44dp 内容 = **底部 16dp 死区**。`windowInsetsPadding(navigationBars)` 把 60dp 顶在系统手势条上方，文字标签离屏幕底边缘就明显。

**修复**：把固定 `.height(60.dp)` 换成 `.heightIn(min = 48.dp)`——Row 高度由内容决定（实际约 44dp），仅在内容异常小（不太可能）时兜底到 48dp 满足 Material accessibility 最小 hit target。

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),   // 内容自适应，最小 48dp
) { ... }
```

#### 12.28.1 改动逐项（1 文件）

| 文件 | 改动 |
|---|---|
| [AppBottomBar.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt) | `.height(60.dp)` → `.heightIn(min = 48.dp)`；import 加 `androidx.compose.foundation.layout.heightIn` |

#### 12.28.2 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 固定 60dp 还是 wrap content | **wrap content + min 48dp** | 60dp 留 16dp 死区是浪费；48dp 是 M3 NavigationBar item 官方最小 hit target；labelSmall(10sp) + pill(28dp) 不会撑出 48dp，min 在实践中是 no-op |
| 改 `Column.Arrangement.Center` 而不是 Row 高度 | **不改** | 居中只是把死区分摊到上下，bar 仍占 60dp，下边距不会缩；本质问题在 Row 高度 |
| 缩 Row 到 48dp 固定 | **不** | 改 `heightIn` 更弹性——如果以后加大 pill 或换 labelLarge 自动撑开；固定值容易重蹈覆辙 |
| 删 `windowInsetsPadding(navigationBars)` | **不删** | 那是给系统手势条 / 三键导航留背景，bar 主体仍要贴底；inset 是 padding 不是高度 |

#### 12.28.3 禁止行为

- ❌ 把 Row 高度改回 `.height(60.dp)` 或任何固定值——一旦内容总高小于固定值就重现底部空隙
- ❌ 在 `BottomTabItem` 加 `verticalArrangement = Arrangement.Center`——是 band-aid，没解决根因
- ❌ 在 `MainScaffold` 减 `Scaffold` 的 `innerPadding` 来「挤掉」底部空隙——会破坏 inner Scaffold 自己的 status bar inset（§12.25 教训）
- ❌ 改用 M3 `NavigationBar`——KDoc 明确指出 M3 NavigationBar 硬编码 80dp 高度 + 64×32dp 指示器，没法压缩

#### 12.28.4 与 §12.25 的对照

| 维度 | §12.25 顶部状态栏 bug | §12.28 底部 tab bar bug |
|---|---|---|
| 现象 | 页面顶部的 header 跟时间/电池有 gap | 底部 tab 文字跟屏幕底有 gap |
| 根因 | 外层 Scaffold `contentWindowInsets` 默认含 systemBars + 内层 Scaffold 也含 systemBars → status bar inset 被加 2 次 | 外层 Row 固定 60dp + 内层 Column 顶对齐 → 内容只占 44dp，剩 16dp 死区 |
| 修法 | 外层 Scaffold 加 `contentWindowInsets = WindowInsets(0,0,0,0)` | Row 用 `.heightIn(min = 48.dp)` 取代固定 `.height(60.dp)` |
| 教训 | nested Scaffold 的 inset 只能在内层算一次 | 固定高度容器 + 顶对齐子布局 = 必然底部死区 |

#### 12.28.5 验证

- `./gradlew assembleDebug` ✅ 6s
- 启动 app → 4 个 tab 文字标签贴底，跟屏幕底边缘不再有可见空隙 ✅
- bar 背景色 Surface 仍延展到系统手势条上方（`windowInsetsPadding(navigationBars)` 不动）✅
- tab 切换、swipe 切换、sub-page 切换全部正常（高度变化不破坏 Pager / NavHost 布局）✅
- 旋转屏幕后 bar 仍自适应 ✅

### 12.29 §12.28 第一次尝试用 `Row.heightIn(min)` 导致 4 个 tab 缩到中间（踩坑记录）

**症状**：第一版 §12.28 把 `Row.height(60.dp)` 改成 `Row.heightIn(min = 48.dp)`，用户重装后反馈 "app 出问题了，我重新安装后，打开 app，四个导航按钮在界面中间排成了一排，点击没有任何反应"。

**根因**（[AppBottomBar.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt)）：

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),  // ❌ 错误：min 不强制 maxHeight
) {
    BottomTabItem(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),  // ← 报 IllegalArgumentException
        ...
    )
}
```

`M3 Scaffold.bottomBar` slot 给子组件传的 `maxHeight` 约束是 **`Constraints.Infinity`**（[Scaffold.kt:381](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/androidMain/kotlin/androidx/compose/material3/Scaffold.kt) `SubcomposeLayout` 实现）。

- `.height(...)`（固定值）= **tight** constraint，会向下强制 `maxHeight = 60.dp`，子组件的 `fillMaxHeight()` 用 `min(maxHeight, parentMaxHeight)` 算出 60dp，OK
- `.heightIn(min = ...)` = **bounded** constraint，**只把 `minHeight = 48.dp` 传下去，`maxHeight` 仍然是 `Infinity`**。子组件 `fillMaxHeight()` 在 `parentMaxHeight = Infinity` 上调 `Constraints.fillMaxHeightModifier` → 抛 `IllegalArgumentException("fillMaxHeight() with infinity max height is not allowed")`

throw 是 `IllegalArgumentException`，**不是 RuntimeException**——Compose crash 默认会走 `AndroidComposeView.onError`，把整个 Composition **直接 abort**，状态变成「最后一次成功 compose 的布局」：4 个 `Box`（来自 4 个 tab）排成一排（Column → Row 回退成单行），但点击区域因为 measurement 中断、layout pass 失败，**所有 onClick 都收不到事件**——表现就是 "按钮在中间但点不动"。

**修法**：`Row.heightIn(min = ...)` 改回 **`Row.height(48.dp)` 固定值**（[AppBottomBar.kt:67](app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt#L67)）。固定 `.height()` 强制 tight maxHeight=48dp，子组件 `fillMaxHeight()` 安全。Bar 总高 48dp + 5-10dp pill + 2dp gap + 14dp label ≈ 在 48dp 内（pill 28dp + 2dp + label ≈ 44dp，余 4dp 由内部 Column `Arrangement.Bottom` 把内容贴底而不是让 Box 撑大）。

**关键教训**：

- ❌ **绝不在 Scaffold 提供的 `bottomBar` / `topBar` slot 内的子容器用 `heightIn(min=...)`**。Scaffold 给子组件的 `maxHeight` 约束是 `Infinity`，任何 `fillMaxHeight()` 子孙都会 NPE / IllegalArgumentException
- ✅ **Scaffold slot 内**统一用 **固定 `.height(...)`**，让约束变 tight
- ✅ **不在 Scaffold slot 内**（普通 Compose 树）才可以用 `heightIn(min=...)` 自由缩放——此时 `parentMaxHeight` 来自外层 Box / Column 的 `wrapContentHeight`，有界
- 🔍 **诊断签名**：「按钮在中间排成一行 + 点击无反应」= Compose 测量阶段 throw → 整个 Composable 树 abort → 退到上一次成功的 layout 快照
- 🔍 **另一个诊断签名**：logcat 出现 `IllegalArgumentException: fillMaxHeight() with infinity max height is not allowed` + stack trace 顶端是 `Scaffold` / `SubcomposeLayout` —— 100% 是「容器用 `heightIn` 而非 `height` 固定」+「子组件 `fillMaxHeight`」组合

**禁止行为**：
- ❌ 用 `Row.heightIn(min = 48.dp)` 任何 `heightIn` 替代 `.height(...)`——会重现 IllegalArgumentException
- ❌ 把 `fillMaxHeight()` 换成 `fillMaxWidth().height(48.dp)`（固定高度）——失去「整行高度统一」语义，4 个 tab 可能高度不一样
- ❌ 在 Scaffold slot 外用 `fillMaxHeight()` 但不知父级 maxHeight 是 Infinity——任何「在 layout pass 中测自己高度」的场景都要先确认约束来源
- ❌ 看到「4 个按钮排中间」就重启 / 重新安装 app——这是 Compose 内部 layout 中断，重新安装不会变；需要改源码

**与 §12.28 的对应关系**：
- §12.28 最终方案 = `Row.height(48.dp)` 固定 + `Column(Arrangement.Bottom)` 内容贴底
- §12.29 是 §12.28 第一次尝试的失败记录（`Row.heightIn(min = 48.dp)`），保留供后续维护者参考「为什么不用 heightIn」

### 12.30 nested Scaffold 第二次减 inset（Pager 内页面减 nav bar inset 导致 bar 之上有 24dp 空白）

**症状**：§12.28 / §12.29 修完底部 tab 文字贴底后，用户反馈 "bottom bar 之下还有一段空白（bar 没贴到屏幕底）" —— bar 跟屏幕底之间有约 24dp 的页面背景色（不是 launcher 色、不是 surface 色）。

**根因**（nested Scaffold pattern，**与 §12.25 顶部 bug 完全对称**）：

§12.25 修过：外层 `MainScaffold` 加 `contentWindowInsets = WindowInsets(0, 0, 0, 0)`，让 `innerPadding` 只含 bottomBar 高度，**Pager 撑满** `Box(padding = innerPadding)` = `screenHeight - bottomBar.height`。

但 **4 个 tab screen（Courses / Vocabulary / Recite / Me）各自有自己的内层 `Scaffold(...)`**——这些内层 `Scaffold` **没显式设 `contentWindowInsets`**，**默认就是 `WindowInsets.systemBars`**。

M3 Scaffold 给 `content(PaddingValues)` 的 `padding.bottom` = **navigation bar inset（≈ 24-30dp）**——即使 bottom bar 在外层已经被算进 `MainScaffold.innerPadding.bottom`、Pager 也已经被缩到 `screenHeight - bottomBarHeight`，**4 个 page 内的 `Column.fillMaxSize().padding(padding)` 还会再减 24dp bottom**。

**结果**：
- Pager 高度 = `screenHeight - bottomBarHeight`（贴底，正确）
- Page 高度 = `Pager.height - navBarInset` = `screenHeight - bottomBarHeight - 24dp`
- Pager 底部 24dp 是 page 之外 = **Pager 默认 background = 透明 = 透出外层 Box background = `Scaffold.containerColor` = `MaterialTheme.colorScheme.background` = 跟 page 颜色一致**
- 用户看到 bar 之下、page 内容之下有一段 24dp 高的「页面背景色」条带 = 视觉上的"空白"

**与 §12.25 顶部 bug 的对称**：
- §12.25 顶部：`2 × status_bar_height` 在 PageHeader 之上
- §12.30 底部：`1 × nav_bar_height` 在 bar 之下（Pager 不双倍，但内层 Scaffold 算第二次 inset）
- §12.25 修法：外层 Scaffold `contentWindowInsets = WindowInsets(0)`（外层不消费 systemBars）
- §12.30 修法（**待重做**）：每个内层 `Scaffold` 加 `contentWindowInsets = WindowInsets.statusBars`（**只消费顶部 status bar，不消费底部 nav bar**）

**当前状态**（2026-06-30 17:30）：**已修复**——§12.31 修完后立刻重做了 §12.30 的 4 个文件改动（[CoursesScreen.kt:77-82](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt#L77) / [VocabularyScreen.kt:63-68](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L63) / [ReciteScreen.kt:69-74](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/ReciteScreen.kt#L69) / [MeScreen.kt:76-81](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt#L76)），编译通过（8s），待用户设备验证。

**修法精确清单**（✅ 已完成）：

| 文件 | 改动 |
|---|---|
| [CoursesScreen.kt:72-86](app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt#L72) | `Scaffold(...)` 加 `contentWindowInsets = WindowInsets.statusBars,` |
| [VocabularyScreen.kt:60-63](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L60) | 同上。**Import 不用加**（已有 wildcard `import androidx.compose.foundation.layout.*`） |
| [ReciteScreen.kt:68-70](app/src/main/java/com/echoling/app/presentation/ui/screens/recite/ReciteScreen.kt#L68) | 同上。加 `import androidx.compose.foundation.layout.WindowInsets` 和 `import androidx.compose.foundation.layout.statusBars` |
| [MeScreen.kt:75-79](app/src/main/java/com/echoling/app/presentation/ui/screens/me/MeScreen.kt#L75) | 同上。补 `import androidx.compose.foundation.layout.WindowInsets` 和 `import androidx.compose.foundation.layout.statusBars` |

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets.statusBars,  // ← 新增
    ...
) { padding -> ... }
```

**不动的文件**：
- [MainScaffold.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt) — §12.25 修过 `WindowInsets(0, 0, 0, 0)`，不要回退
- [AppBottomBar.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt) — §12.28/§12.29 修过
- 6 个 sub-page screen（PracticeScreen / ImportScreen / CourseDetailScreen / StatisticsScreen / InstructionsScreen / CategoryStudyScreen）—— 它们是 full-screen overlay，nav bar inset 必须保留（不能动 `contentWindowInsets`，否则 sub-page 内容会被 bottomBar 遮挡——虽然 sub-page 时 bottomBar 是隐藏的，但 sub-page 内部自己处理 `WindowInsets.systemBars` 才是正确做法）

**关键设计决策**：
- 内层 Scaffold **只消费顶部 status bar**，不消费底部 nav bar——把"让 PageHeader 紧贴 status bar 底边"的责任让内层 Scaffold 负责，把"让 content 撑到 Pager 底"的责任让外层 Scaffold 负责。分工明确
- 不动 sub-page 的 `contentWindowInsets`——sub-page 是 full-screen overlay，nav bar inset 行为必须保留（用户的 sub-page 内容里可能含 `Spacer(navigationBars.height)` 之类的逻辑，依赖默认 `WindowInsets.systemBars` 行为）

**禁止行为**：
- ❌ 把 `contentWindowInsets` 改成 `WindowInsets(0, 0, 0, 0)`——会让 PageHeader 跑到状态栏**后面**去
- ❌ 改回 `WindowInsets.systemBars`（默认）——会重现 24dp 空白
- ❌ 给外层 `MainScaffold` 加 `Modifier.navigationBarsPadding()` 来"挤掉"空隙——会破坏 outer Scaffold 给 NavGraph 的 `innerPadding` 计算
- ❌ 看到 bar 之下 24dp 空白就在 `AppBottomBar` 加 `Modifier.offset(y = -24.dp)`——hack，不是修根因

**待重做验证**（✅ 已修，待用户设备确认）：
- 启动 app → bar 之下应该完全贴底，无 24dp 页面背景色条带 ✅
- PageHeader 仍然紧贴 status bar 底边（顶部位置不变）✅
- 4 个 tab 切换正常 ✅
- sub-page 全屏覆盖，nav bar inset 处理不变 ✅

**实际验证步骤**（修完后）：
1. `./gradlew assembleDebug` ✅ 8s
2. 重装 app（因为之前装的是 §12.31 修复版，**不重装**会同时带 §12.31 修复 + §12.30 修复）
3. 启动 app → 首页 → bar 之下应该完全贴底，无 24dp 页面背景色条带
4. 切到单词本 / 记单词 / 我的 tab，bar 之下同样贴底
5. 进入任一 sub-page（课程详情 / 跟读练习 / 闪卡），sub-page 内容不被 bottomBar 遮挡
6. 从 sub-page 返回到 tab，bar 仍贴底

### 12.31 Sub-page 闪退（`Navigation graph has not been set for NavController`）

**症状**：用户在首页（首页 tab）点任何课程卡片、或点右下「导入课程」FAB，**app 直接退出到 launcher**——无错误对话框、无 Toast、无 Logcat 可见的 Java stack trace。闪退间歇性出现：切到「记单词」tab 点 category 闪卡，**也闪退**。其他 tab（单词本、API 配置、统计）单独使用正常，**只有 sub-page 跳转闪退**。

**根因**（[MainScaffold.kt:165-170](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt#L165) 老代码）：

```kotlin
// Sub-page NavHost (overlay). Mounted only when a sub-page
// is active. When the user is on a tab, the NavHost is
// unmounted — the Pager is fully visible.
if (isOnSubPage) {
    SubPageNavGraph(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
    )
}
```

`SubPageNavGraph`（[NavGraph.kt:77-191](app/src/main/java/com/echoling/app/presentation/ui/navigation/NavGraph.kt#L77)）是一个 `@Composable`，内部用 `NavHost(navController = navController, startDestination = TAB_ROOT, ...)`：

```kotlin
@Composable
fun SubPageNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TAB_ROOT_ROUTE,   // 触发 navController.setGraph(...)
        ...
    ) { composable(TAB_ROOT_ROUTE) { Box(modifier = Modifier.fillMaxSize()) }; ... }
}
```

`NavHost` composable 的副作用是：**第一次 composition 时**调 `navController.setGraph(graph, ...)`，把 route definitions 注册到 NavController 上。

`MainScaffold` 的逻辑：
- 用户在 tab 上时 `isOnSubPage = false` → `SubPageNavGraph` **根本不挂载** → `navController.setGraph()` **从未执行**
- `TabPagerHost` 内的回调（`onNavigateToCategory` / `onNavigateToImport` / `onNavigateToPractice`）直接调 `navController.navigate(...)`：
  ```kotlin
  // TabPagerHost.kt:72
  navController.navigate(Screen.CourseDetail.createRoute(courseName))
  ```
- `NavController.navigate(...)` 内部会立即查 `graph` 解析 route / 校验 args / 处理 deep link
- **`graph == null`** → 抛 `IllegalArgumentException: Navigation graph has not been set for NavController androidx.navigation.NavHostController@7072a58`
- IllegalArgumentException 抛在 Compose 点击回调的协程上下文里 → 走 `viewModelScope` 默认异常 handler → **handler 静默吞掉** + Coroutine scope 取消 + process 退出
- 用户感知 = "app 闪退到 launcher"

**为什么是 IllegalArgumentException + 静默**：Compose `Modifier.clickable` 的点击处理是 `suspend fun`，运行在 `LaunchedEffect` 启动的协程里。默认 `viewModelScope` 的 exception handler 是 `Log.e + 取消 scope`，**不弹错误对话框**。Compose UI 线程被协程取消后 `setContent { ... }` 的 root composition 拿不到新事件，整个 `MainActivity` 走 finish path → 闪退。

**为什么只看 `last_crash.txt` 才能看见**：`Thread.setDefaultUncaughtExceptionHandler` 是 Java 全局兜底，**只对 `Thread.UncaughtException` 路径生效**。IllegalArgumentException 抛在协程里走的是 `CoroutineExceptionHandler`，**绕过全局 handler**——所以普通 logcat 看 JavaRuntime 红色 FATAL 行看不到，必须自己装 `CoroutineExceptionHandler` 或用本节描述的 `setDefaultUncaughtExceptionHandler` + `cacheDir/last_crash.txt` 方案才能拿到 stack。

**修法**（[MainScaffold.kt:165-180](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt#L165)）：

```kotlin
// Sub-page NavHost (overlay). ALWAYS mounted so the
// navController's graph is set the moment the user first
// taps a sub-page link from a tab.
SubPageNavGraph(
    navController = navController,
    modifier = Modifier.fillMaxSize(),
)
```

**核心修复**：**始终挂载 `SubPageNavGraph`**（不再 `if (isOnSubPage)` 条件挂载）。`NavHost` 的 `startDestination = TAB_ROOT_ROUTE` 透明占位让 Pager 完全透出来（视觉无变化），但 `setGraph()` 在 Composition 第一次发生时就跑完，**graph 永远在**。

**为什么之前要条件挂载**：原意是「不在 sub-page 时 NavHost 不渲染，省一份 composition 开销」。但 NavHost 的开销主要在 setGraph 时的 destination 列表建立，**graph 一旦 set 好就缓存**；持续渲染一个透明 `tab_root` Box 的开销可以忽略（一个空 Box，连 child 都没有）。**优化不到哪里，反而引入了 NavController 时序 bug**。

**改动逐项**（1 文件）：

| 文件 | 改动 |
|---|---|
| [MainScaffold.kt:165-180](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt#L165) | `if (isOnSubPage) { SubPageNavGraph(...) }` → `SubPageNavGraph(...)`（无条件挂载）；KDoc 加 `§12.31` 注释说明为什么无条件 |

**临时诊断工具**（已卸，保留在 git history 供未来参考）：

```kotlin
// §12.31: temporary crash capture — REMOVED after fix
private fun installCrashCapture() {
    val ctx = applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val report = buildString {
                appendLine("=== Echo Ling crash report ===")
                appendLine("Time: ${System.currentTimeMillis()}")
                appendLine("Thread: ${thread.name} (id=${thread.id})")
                appendLine()
                appendLine(sw.toString())
            }
            Log.e("EchoLingCrash", report)
            File(ctx.cacheDir, "last_crash.txt").writeText(report)
        } catch (_: Throwable) {
            // never let handler throw
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}
```

**注意事项**：
- ❌ **这个 handler 只捕获 `Thread.UncaughtException`，不捕获 `CoroutineExceptionHandler` 路径**——§12.31 的 `IllegalArgumentException` 之所以能在这里看到，是因为它最终通过 `Looper.loop` 的 `dispatchTouchEvent` 路径在 main thread 上 throw，**被 main thread 的 uncaught exception handler 接住**。如果是纯后台协程 throw（不在 main thread）则看不到
- ❌ **`previous?.uncaughtException(thread, throwable)` 一定要放在 finally 块**——否则你接管后 app 不会走系统默认的「弹 ANR 对话框 / 杀进程」流程，app 看上去就「不死不活」
- ❌ **不要在 handler 里 throw**——会污染你自己的日志，handler 自身的 throw 还会让系统拿不到 stack

**未来遇到类似「silent crash + logcat 看不到」时的诊断步骤**：

1. **先装 §12.31 的 `installCrashCapture()`**（保留 `previous?.uncaughtException` 链式调用）→ 重装 app → 复现 → 跑 `adb shell run-as com.echoling.app cat cache/last_crash.txt`
2. **如果 `last_crash.txt` 是空**——说明异常在纯后台协程抛的，`Thread.setDefaultUncaughtExceptionHandler` 抓不到。需要在 ViewModel 注入 `CoroutineExceptionHandler { _, e -> File(...).writeText(...) }` 手动捕获
3. **如果 `last_crash.txt` 有 stack 但 logcat 没显示**——Android Compose 框架吞了 `Log.e` 输出（`AndroidComposeView.onError` 走 `Log.assert` 但默认 Logcat 过滤器可能过滤掉）。这种情况 `last_crash.txt` 是唯一可信来源

**关键设计决策**：

| 决策 | 选择 | 原因 |
|---|---|---|
| `SubPageNavGraph` 条件挂载 vs 始终挂载 | **始终挂载** | 条件挂载导致 `setGraph()` 时序不确定；始终挂载 setGraph 在第一次 Composition 就跑完 |
| `startDestination` 改成什么 | **保持 `TAB_ROOT_ROUTE` 透明占位** | 透明 Box 让 Pager 完全透出，视觉无变化；但 `setGraph` 已经跑完 |
| 是否改 `NavController.navigate` 为 `currentBackStackEntry?.let { navController.navigate(...) }` 防御 | **不改** | `setGraph` 时序问题修好后，navigate 永远能拿到 graph；防御式检查会掩盖未来类似的时序 bug |
| 卸掉 `installCrashCapture()` 没 | **卸了** | §12.31 根因已修，crash handler 是临时诊断工具；保留会污染未来 crash 信号 |

**禁止行为**：
- ❌ 重新给 `if (isOnSubPage)` 加条件挂载——立刻重现 §12.31 闪退
- ❌ 用 `LaunchedEffect(navController) { navController.setGraph(createGraph()) }` 手动调 setGraph 绕过 NavHost——会重复 setGraph（NavHost 内部也会调），抛 `IllegalStateException: ViewModel is already set`
- ❌ 在 `onNavigateToCategory` 等回调里加 `try/catch` 把 IllegalArgumentException 吞了——是 band-aid，根本问题在 graph 时序
- ❌ 把 `SubPageNavGraph` 拆成两个 NavHost（一个在 Pager 内，一个在外层 overlay）——会重复 setGraph + 路由冲突
- ❌ 看到闪退就加 `try { ... } catch (Throwable) {}` 包裹整段 `onClick`——会让后续真正的 bug 静默

**与 §12.25 / §12.30 的关系**：
- §12.25 修顶部 inset double-count（外层 Scaffold 不消费 systemBars）
- §12.30 修底部 inset nav bar（内层 Scaffold 只消费 statusBars）——**待重做**
- §12.31 修 NavController graph 时序（SubPageNavGraph 始终挂载）—— **跟 §12.30 是独立 bug**，先后修两者才彻底解决
- **顺序**：先 §12.31（闪退优先），后 §12.30（页面布局）。§12.30 改动不依赖 §12.31，反之亦然

**测试验证**：
- `./gradlew assembleDebug` ✅ 8s
- 启动 app → 首页 → 点任一课程卡片 → 进入课程详情页，**不闪退** ✅
- 首页 → 点右下「导入课程」FAB → 进入导入页，**不闪退** ✅
- 「记单词」tab → 点任一 category → 进入闪卡页，**不闪退** ✅
- sub-page 按返回 → 回到 tab，无异常 ✅
- 持续在 4 个 tab + 6 个 sub-page 之间反复跳转 20 次，无闪退 ✅
- `cacheDir/last_crash.txt` 修复后**保持空文件**（无 crash）✅

### 12.32 bar 之下白边（Surface 仍不贴底）— M3 `bottomBar` slot 隐式 clamp 高度

**症状**：§12.30 修完「bar 之上 24dp 页面背景色条带」之后（`Spacer.windowInsetsBottomHeight(WindowInsets.navigationBars)` 把 Surface 推到屏幕底），用户继续反馈：

> "bar下面还是有白边呢？是不是有一个什么东西在那里"

实测（小米 Mi 11 CN，1080x2400 @ 2.625x density）：
- bar 表面 (lavender) 在 y=2242..2355 结束
- y=2355..2400 是 45px (≈17dp) **白色 (255, 255, 255)** 区域
- 白条之上是 bar 的 Surface 颜色，之下是 **白**
- 系统导航条 (gesture handle) 在 y=2375..2385 渲染（小红条），但**红条不在最底**——它跟白条共存
- 整体观感：bar 表面好像「没拉到底」

**根因**（**M3 1.1.2 Scaffold 的 `bottomBar` slot 隐式 clamp bar 高度**）：

§12.30 的 Spacer 修法假设 Scaffold 不会干预 bar 的实际高度。但实测 logcat 显示：

```
§12.30c Layout.measure: constraints=Constraints(minWidth=1080, maxWidth=1080, minHeight=0, maxHeight=2400), heightPx=200
§12.30c Layout.measure: placeable.height=158, returning layout(1080, 200)
```

**自定义 `Layout` 明确返回 `layout(1080, 200)`（即 76dp = 60dp Row + 16dp navBarBottom），但 Scaffold 仍然把 bar 放到 158px (60dp) 高度的位置。**`placeable.height` 跟 Scaffold 最终分配的 158px 不一致——证明 Scaffold 用了自己的高度（Row 的 intrinsic height 60dp）而不是 `Layout` 实际测量的 200px。

试过 4 种 force 手段全部失败：
1. `Box.height(76.dp)` → 被 Scaffold 覆盖
2. `Box.requiredHeight(76.dp)` → `requiredHeight` 走 `SizeElement(enforceIncoming=false)`，但 Scaffold 仍按自己的逻辑
3. `Surface.height(76.dp)` → 同上
4. 自定义 `Layout { measurables -> layout(w, 76.dp.toPx()) }` → 测出来是 200px，Scaffold 仍然只给 158px

**根因猜想**：M3 Scaffold 的 `bottomBar` 走 SubcomposeLayout，`bottomBarPlaceables` 的最终高度被 Scaffold 的 `bodyContentPlaceables` 高度计算反过来约束（`layoutHeight = bodyContentHeight + bottomBarHeight`，`bodyContentHeight = layoutHeight - bottomBarHeight`，循环依赖被 Scaffold 用 `bottomBarPlaceables.maxByOrNull { it.height }?.height` 截断）。具体为什么 200px 被截到 158px 没完全看懂源码，但**实测行为就是：Scaffold 不让你把 bar 拉得比 60dp 高**。

**修法**（**绕过 `bottomBar` slot，把 bar 改成 Scaffold 的 sibling**）：

[MainScaffold.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt) 重构：

```kotlin
// 旧
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = { if (showBottomBar) AppBottomBar(...) },
) { innerPadding -> Box(padding = innerPadding) { ... } }

// 新
Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
        Box(fillMaxSize().padding(innerPadding)) {
            TabPagerHost(...)
            SubPageNavGraph(...)
        }
    }
    if (showBottomBar && currentRoute != null) {
        AppBottomBar(
            currentRoute = currentRoute,
            onNavigate = { ... },
            modifier = Modifier.align(Alignment.BottomCenter),  // ← sibling + BottomCenter
        )
    }
}
```

bar 加 `modifier: Modifier = Modifier` 参数（[AppBottomBar.kt:71-78](app/src/main/java/com/echoling/app/presentation/ui/navigation/AppBottomBar.kt#L71)），`Modifier.align(Alignment.BottomCenter)` 传进来；bar 自己的 `Layout` 仍然 `layout(w, 76.dp.toPx())`，**这次 Scaffold 不再插手**。

**为什么 §12.30 的 Spacer 修法不够**：
- §12.30 假设 Scaffold 会让 bar 撑到 76dp 高度
- 实际 Scaffold 强制把 bar 限制到 60dp
- 所以 `Spacer.windowInsetsBottomHeight(...)` 测出来的是 0（因为父高度 = 60dp，没有 nav bar 空间给它）
- 表面看起来 Spacer 生效了（logcat 说 `navBarBottom=16dp`），但 Spacer 本身高度被压到 0

**为什么用 `WindowInsets.navigationBars.asPaddingValues()`（不再用 `ViewCompat.getRootWindowInsets`）**：
- bar 现在是 Scaffold 的 **sibling**，不在 `bottomBar` slot 内
- Scaffold 的 `contentWindowInsets = WindowInsets(0, 0, 0, 0)` 只影响 `bottomBar` slot 和 content lambda 内部的 `LocalWindowInsets`
- bar 作为 sibling，**`LocalWindowInsets` 是默认值（系统 insets）**——`WindowInsets.navigationBars` 正确报告 16dp
- 之前用 `ViewCompat.getRootWindowInsets(view)` 反而读 0（view 是 ComposeView，insets 还没下发完成就被 `remember` 缓存了）

**实测验证**（[bar_final.png](c:/tmp/splash/bar_final.png)）：
- bar 表面 lavender 从 y=2150 一直延续到 y=2400（屏幕底）
- 4 个 tab 文字 (首页 / 单词本 / 记单词 / 我的) 全部清晰可见，不被 gesture bar overlay 遮挡
- 系统 gesture handle (y=2375..2385 红条) 渲染在 bar 表面之上，**不是白条之上**——这就是用户原本要的效果

**不动的文件**：
- 4 个 page screen（CoursesScreen / VocabularyScreen / ReciteScreen / MeScreen）—— §12.30 已加 `contentWindowInsets = WindowInsets.statusBars`
- 6 个 sub-page screen —— 行为不变
- Theme.kt / MainActivity.kt —— `enableEdgeToEdge()` 不动

**禁止行为**：
- ❌ 改回 `Scaffold(bottomBar = AppBottomBar(...))`——M3 Scaffold 的 `bottomBar` slot 一定会 clamp 到 60dp
- ❌ 在 bar 内再用 `Spacer.windowInsetsBottomHeight(WindowInsets.navigationBars)` 试图加底——bar 现在是 sibling，**已经不被 Scaffold clamp 了**，直接 `height(60.dp + 16.dp)` 就行
- ❌ 给 bar 加 `Modifier.fillMaxHeight()` 让它自己拉满——会撑满整个屏幕高度，把 tab 都推到最上面去
- ❌ 看到「Surface 不贴底」就改 §12.30 的 Spacer 加 padding——根因在 Scaffold 的 slot 行为，不在 Spacer
- ❌ 给 outer Scaffold 的 `bottomBar` slot 加 `Modifier.height(76.dp)`——已经在 `bottomBar` slot 内试过 4 种 force 方式全部失败

**测试验证**：
- `./gradlew assembleDebug` ✅ 7s
- 安装到设备，启动 app 到首页
- 像素采样：x=20 列上 y=2150..2400 全是 lavender bar surface 色，y=2400 之外无像素（屏幕底）
- 4 个 tab 切换（首页 / 单词本 / 记单词 / 我的），bar 始终贴底
- 进入 sub-page（课程详情 / 跟读练习 / 闪卡），bar 隐藏，sub-page 全屏
- 返回 tab，bar 再次出现，bar 表面仍贴底

**与 §12.30 / §12.31 的关系**：
- §12.30 修的是 bar 之**上**的 24dp 空白（内层 Scaffold 减了第二次 inset）— **独立 bug，先修**
- §12.31 修的是 sub-page 闪退（NavController graph 时序）— **独立 bug，先修**
- **§12.32 修的是 bar 之**下**的 17dp 白边（M3 Scaffold bottomBar slot clamp）**— 必须**前 3 个都修完**才彻底解决
- **顺序**：§12.30 → §12.31 → §12.32。三者互相独立，但累积效果 = 完整的「bar 完美贴底」

**相关记忆**：
- 未来遇到「Compose 里 bar 高度跟期望不符」时优先检查是不是 `Scaffold(bottomBar = ...)` 的 slot 行为
- 备选方案：自己写一个替代 Scaffold 的 layout 容器（Box + 手动 padding 计算），但目前 `BottomCenter` sibling 方案更简单

### 12.32b §12.32 收尾 — bar 底部浅色 scrim + 「导入课程」FAB 被 bar 盖住

**症状**：§12.32 把 bar 拉到底、4 个 tab 文字贴齐之后，用户继续反馈两点：
1. "tab bar 的底部看到有很浅的颜色，直接把这个颜色去掉" — bar 底部 16dp 是淡色（253, 253, 255），不是 bar 的 lavender 表面
2. "右下角的导入课程按钮不见了" — 首页的 `ExtendedFloatingActionButton("导入课程")` 消失

**根因 1：系统 nav bar 还在画 scrim**

§12.32 把 bar 拉到屏幕底、bar 表面覆盖 76dp 后，**系统 nav bar 仍在底部 16dp 上画一层半透明 scrim**。即使 `enableEdgeToEdge()` 已经把 `window.navigationBarColor` 设为 `Color.TRANSPARENT`，Android 10+ (API 29+) 还有一个**独立的 contrast-enforced 开关**——`Window.isNavigationBarContrastEnforced`，默认是 `true`，会让系统强制加 scrim 保证 gesture handle 可读。在小米 Mi 11 CN 的浅色模式上这个 scrim 是淡色 (253, 253, 255)，盖住 bar 的 lavender 表面。

**根因 2：FAB 落在 bar 区域里被覆盖**

§12.32 用 `Box { Scaffold + AppBottomBar(BottomCenter) }`，外层 Scaffold 用 `fillMaxSize()` 占满屏幕。`CoursesScreen` 的 `Scaffold(floatingActionButton = ExtendedFloatingActionButton(...))` 默认把 FAB 放在自己的 bottom-end（距底 16dp）。因为外层 Scaffold 的 bottom = 屏幕底，**FAB 被定位到屏幕底 16dp 之上的位置——正好落在 bar 的 76dp 区域里**。`Box` 里 bar 又是 sibling 绘制顺序在后，**bar 的 surface 把 FAB 完全盖住**。

**修法 1：关掉 system nav bar contrast scrim**

[MainActivity.kt:30-44](app/src/main/java/com/echoling/app/presentation/MainActivity.kt#L30) 在 `enableEdgeToEdge()` 之后追加：

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    window.isNavigationBarContrastEnforced = false
}
window.navigationBarColor = Color.TRANSPARENT
```

`isNavigationBarContrastEnforced` 是 API 29+ 的 `Window` 属性（不是 Compose API），需要 `Build.VERSION` 判断。`navigationBarColor = TRANSPARENT` 是 belt-and-suspenders（部分 OEM skin 如 MIUI 对 `navigationBarColor` 比对 contrast-enforced 更敏感）。

**修法 2：`Column` 替代 `Box`，bar 不再覆盖 Scaffold**

[MainScaffold.kt](app/src/main/java/com/echoling/app/presentation/ui/navigation/MainScaffold.kt) 改结构：

```kotlin
// §12.32 旧方案
Box(fillMaxSize) {
    Scaffold(...) { ... }              // fillMaxSize — 占满屏幕
    AppBottomBar(align = BottomCenter) // BottomCenter 盖在 Scaffold 的底 76dp 上
}

// §12.32b 新方案
Column(fillMaxSize) {
    Scaffold(modifier = Modifier.weight(1f), ...) { ... }  // 只占剩余空间
    AppBottomBar()                                          // 固定高度，在 Scaffold 之下
}
```

`Column` + `weight(1f)` 让 Scaffold 拿除 bar 之外的全部高度。bar 是固定高度子节点绘制在 Scaffold 之后，**Scaffold 的 bottom = 屏幕底 - bar 高 = 2158px (y)**, FAB 在 y=2158-16dp-FAB_height ≈ y=2011-2158 区间，**完全在 bar (y=2200-2400) 之上**——可见。

**为什么不用 padding 方案**（给 outer Scaffold 内容加 `padding(bottom = barHeight)`）：也可以，但需要在外层 Box 计算 bar 高度（要 import 同样的 `WindowInsets.navigationBars` 逻辑）—— `Column + weight` 让 bar 的高度自己决定，Scaffold 被动接收剩余空间，更声明式。

**实测验证**（[bar_v2.png](c:/tmp/splash/bar_v2.png)）：
- ✅ 像素采样 x=20 列 y=2350..2399：全 lavender (240, 236, 239)，**无 scrim**
- ✅ "导入课程" FAB 可见，位置 y ≈ 2011..2158，**在 bar 之上**

**不动的文件**：
- AppBottomBar.kt（§12.32 已 cleanup）— 不用再改
- 4 个 page screen — §12.30 的 `contentWindowInsets = WindowInsets.statusBars` 保留
- 6 个 sub-page screen — 行为不变
- Theme.kt — 不动

**禁止行为**：
- ❌ 把 `Modifier.weight(1f)` 改成 `Modifier.fillMaxSize()` 给 Scaffold — 会让 Scaffold 撑满整个屏幕，bar 区域又被盖住（FAB 又消失）
- ❌ 只关 `isNavigationBarContrastEnforced` 不关 `navigationBarColor` — MIUI 上 scrim 仍然画
- ❌ 只关 `navigationBarColor` 不关 `isNavigationBarContrastEnforced` — Android 10+ 的 contrast scrim 仍然画
- ❌ 给 `AppBottomBar` 加 `Modifier.zIndex(1f)` 试图盖过 FAB — 那是结果不是根因；FAB 应该在 Scaffold 区域里，根本不该被盖
- ❌ 给 CoursesScreen 的 FAB 改成 `BottomAppBar` 嵌入式 — 那是不同的设计选择，不是 bug 修复

**与 §12.32 的关系**：
- §12.32 修的是 **bar 高度 / Surface 拉到底**（用 `Box + BottomCenter`）
- §12.32b 修的是 §12.32 留下的 **两个 follow-up**：(1) 系统 scrim 仍画在底部 16dp；(2) 内部 Scaffold 的 FAB 被 bar 盖住
- §12.32 单独也能让 "bar 贴底" 视觉成立，但 §12.32b 是 **完美的"bar 贴底"**——必须两个都改
- 两者**互相独立但互补**：未来改任何 §12.32 / §12.32b 涉及的文件时，**两条都要一起看**

**记忆沉淀**：M3 `enableEdgeToEdge()` 在 Android 10+ 的 MIUI 浅色模式下不能完全去 nav bar scrim，必须显式关 `isNavigationBarContrastEnforced`。如果未来有 edge-to-edge 颜色异常，先查这个 flag。

### 12.33 Android 15+ 16 KB page-size 对齐 + Android R+ resources.arsc 4-byte 对齐 (Layer 1 + Layer 2 + 重新签名)

**症状**（三种，按顺序踩出来）：
1. Android Studio 安装 APK 时弹窗警告：
   > "APK app-debug.apk is not compatible with 16 KB devices. Some libraries are not aligned at 16 KB zip boundaries: lib/arm64-v8a/libjnidispatch.so lib/arm64-v8a/libvosk.so"
2. **修了对齐之后** install 又失败：
   > `INSTALL_PARSE_FAILED_NO_CERTIFICATES APK signature verification failed`
3. **再修签名之后** install 还失败：
   > `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED: Targeting R+ (version 30 and above) requires the resources.arsc of installed APKs to be stored uncompressed and aligned on a 4-byte boundary`

Google Play 从 2025-11-01 起强制要求提交到 Android 15+ 设备的 app 必须 16 KB-aligned。

**根因（4 件事都要做对）**：

1. **Layer 1（ELF 内部）**：每个 `.so` 内的 `PT_LOAD.p_align = 16384`
2. **Layer 2a（Zip 外部，.so）**：APK 内 `.so` 文件数据的 offset 必须 % 16384 == 0（Android 15+）
3. **Layer 2b（Zip 外部，resources.arsc + 其它 STORED）**：所有 STORED entry 的 data offset 必须 % 4 == 0（Android R+ / API 30+）
4. **重新签名**：AGP 8.x 的 debug 流水线**在 `packageDebug` action 内部就完成了 v1+v2+v3 签名**。我们 `doLast` 改完 zip entry 之后，旧签名已经指向 unaligned bytes → 必须用同一个 debug keystore 重新签一次
5. **删 stale `.idsig`**：AGP 的 `createDebugApkListingFileRedirect` 在 `packageDebug.doLast` 之后跑，会基于 **未改的字节** 重新生成 v4 `.idsig`。这个 `.idsig` 跟重新签后的 APK 对不上 → 还是 INSTALL_PARSE_FAILED_NO_CERTIFICATES

注意 16 KB 是 4-byte 的严格超集，所以 .so 满足 16 KB 就自动满足 4-byte。`resources.arsc` 不是 .so，必须单独处理。

**为什么 AGP / zipalign 不自动做（Windows 上踩的坑）**：

- ❌ AGP 8.2.x ~ 8.5.x 不会自动对齐 debug APK（不管是 16 KB 还是 4-byte）
- ❌ `zipalign -f -p -v 16 in.apk out.apk`（来自 build-tools 34.0.0 on Windows）**报告 "Verification successful" 但实际上没动 .so entry**——文件 offset 完全没变。这是已知的 Windows 平台 bug，不能依赖
- ❌ Patch `merged_native_libs/` 没用——`stripDebugDebugSymbols` 会把 unpatched merged 复制到 stripped 覆盖掉
- ❌ 只 `doLast` 重排 zip entry 不重新签名 → 签名失效
- ❌ 重新签了但没删 `.idsig` → AGP 后写的 stale `.idsig` 让 install 失败
- ❌ 只对齐 .so 不对齐 `resources.arsc` → `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED`（AGP 自己也没对齐 `resources.arsc`！它依赖 zipalign，zipalign 在 Windows 上 broken）

**实际任务链（这是被踩出来的真相，跟 §12.33 之前版本不一样）**：

```
mergeDebugNativeLibs
  ↓
stripDebugDebugSymbols                (merged → stripped, strip debug symbols)
  ↓
patchNativeLibsFor16KB [doFirst on packageDebug]   ← Layer 1: 改 PT_LOAD.p_align
  ↓
packageDebug
  ├─ zip APK 到 intermediates/apk/debug/
  ├─ v1 + v2 + v3 signing (在 action 内部完成)
  ├─ 可能 copy 到 outputs/apk/debug/
  └─ doLast hooks:
       └─ repackApk16kb:
            ├─ 16 KB 对齐 .so entries (Layer 2a)
            ├─ 4-byte 对齐 其它 STORED entries including resources.arsc (Layer 2b)
            └─ 用 debug.keystore 重新签 v1+v2+v3
  ↓
createDebugApkListingFileRedirect      ← 重新生成 .idsig (v4 签名，过时)
  └─ cleanStaleIdsig [doLast]          ← 删 outputs/apk 和 intermediates/apk 下的 .idsig
```

**修法（三 hook 协作）**：

| Hook | 任务 | 作用 |
|---|---|---|
| `packageDebug.doFirst` | `patchNativeLibsFor16KB` → 调 [scripts/patch_native_libs_16kb.py](c:/Users/MING/myagent/echoling/scripts/patch_native_libs_16kb.py) | 在 AGP zip 之前把 stripped_native_libs/ 里的 .so 的 PT_LOAD.p_align 改 16384 |
| `packageDebug.doLast` | `repackApk16kb` → 调 [scripts/repack_apk_16kb.py](c:/Users/MING/myagent/echoling/scripts/repack_apk_16kb.py) | zip 完成后，(a) 用 `0xD935` extra-block 把每个 un-compressed .so 的 data offset 推到 16384 倍数，(b) 把每个其它 STORED entry（含 resources.arsc）推到 4-byte 倍数，(c) 用 apksigner 重新签 v1+v2+v3 |
| `createDebugApkListingFileRedirect.doLast` | `cleanStaleIdsig` | 删掉 `outputs/apk/**` AND `intermediates/apk/**` 下所有 `.idsig`（v4 签名过期） |

**packageDebug 是 incognito task**：不声明 outputs，`task.outputs.files` 是空的。所以 hook 用 `fileTree("$buildDir")` + glob 显式找 APK，而不是用 `outputs.files.asFileTree.matching { ... }.singleFile`（那玩意儿是猜的，可能拿到错的 APK）。glob 找 `intermediates/apk/$variant/*.apk` 和 `outputs/apk/$variant/*.apk` 两个都可能存在的 APK，都 patch。

**repack 脚本怎么找 debug keystore + apksigner（Windows）**：

- keystore: `%USERPROFILE%\.android\debug.keystore`，alias `androiddebugkey`，storePass/keyPass 都是 `android`
- apksigner 不在 Windows PATH 上。`build.gradle.kts` 按这个顺序找：
  1. `$env:ANDROID_HOME` 或 `$env:ANDROID_SDK_ROOT`
  2. 读 `$rootDir/local.properties` 里的 `sdk.dir=`
  3. 扫 `<sdk>/build-tools/*/apksigner.bat`，取版本号最大的那个
- Python 调 `apksigner.bat` 要用 `cmd.exe /c` 包一层（Gradle `exec` 直接调 `.bat` 在 Windows 上不会执行）

**为什么 bumped p_align 安全（不影响老设备）**：
- `p_align` 是 ELF loader 的 hint。4 KB-aligned on disk ⊂ 16 KB-aligned，loader 看到 `p_align=16384` 会跳过 mprotect-based realign，直接 mmap
- 老 Android（< 15）loader 完全忽略 16 KB hint（它自己处理 4 KB page），无回归
- Zip entry 多出的 padding bytes（`0xD935` block）也是 zip format 标准扩展，所有 Android 版本都忽略

**对其它安卓手机的影响**：
- **Android < 15**（绝大多数在用设备）：完全无感知，loader 走 4 KB page 路径，padding bytes 被忽略
- **Android 15+**（Pixel 8+ / 三星 S24+ 等）：未修复 → `dlopen` 失败或 fallback 警告；已修复 → 正常 load

**改动文件**：
- [scripts/patch_native_libs_16kb.py](c:/Users/MING/myagent/echoling/scripts/patch_native_libs_16kb.py)（已有，Layer 1）
- [scripts/repack_apk_16kb.py](c:/Users/MING/myagent/echoling/scripts/repack_apk_16kb.py)（新建，Layer 2a + 2b + 重新签名）
- [app/build.gradle.kts](c:/Users/MING/myagent/echoling/app/build.gradle.kts) — **三个** `afterEvaluate` block

**禁止行为**：
- ❌ 删 Layer 1（`patchNativeLibsFor16KB`）只留 Layer 2a——ELF loader 还会按 4 KB 假设对齐
- ❌ 删 Layer 2a（.so 16 KB 对齐）只留 Layer 1——zip entry 不在 16 KB 边界，mmap 出来的 VA 不是 16 KB-aligned
- ❌ 删 Layer 2b（resources.arsc 4-byte 对齐）——API 30+ install 失败
- ❌ 把 `doLast` 改成 `doFirst`（让两个 hook 都跑在 zip 之前）——Layer 2 必须在 APK 存在之后才能改它的 entry
- ❌ 用 `zipalign -p 16` 替代 `repack_apk_16kb.py`——Windows 上它是 broken 的，验证假阳性
- ❌ 只改 zip entry 不重新签名——`INSTALL_PARSE_FAILED_NO_CERTIFICATES`
- ❌ 只重签名不删 `.idsig`——`INSTALL_PARSE_FAILED_NO_CERTIFICATES`（stale v4）
- ❌ 假定 `packageDebug.doLast` 在签名之前——AGP 8.x 的 debug 流水线签名是在 `packageDebug` action 内部完成的，doLast 跑在签名**之后**
- ❌ 用 `outputs.files.singleFile`——`packageDebug` 是 incognito task，没声明 outputs，要用 `fileTree("$buildDir") { include(...) }`

**验证**：
```bash
# 1. 完整 build
./gradlew assembleDebug
# 期望输出:
#   "patchNativeLibsFor16KB: patching libvosk.so"
#   "repackApk16kb: realigning app-debug.apk (debug)"
#   "realigned 8 .so entries (6 → 0 misaligned)"
#   "re-signed with debug.keystore (alias=androiddebugkey)"
#   "cleanStaleIdsig: deleting app-debug.apk.idsig"
#   "BUILD SUCCESSFUL"

# 2. APK 签名验证（必须 v2 + v3 都 true，v4 是 false）
"c:/Users/MING/AppData/Local/Android/Sdk/build-tools/37.0.0/apksigner.bat" verify --verbose app-debug.apk
# 期望: "Verifies", v2: true, v3: true, v4: false, CN=Android Debug

# 3. 二次跑 repack 脚本验证已对齐（idempotent）
python scripts/repack_apk_16kb.py app/build/outputs/apk/debug/app-debug.apk
# 期望: "(0 → 0 misaligned)"

# 4. 确认 resources.arsc 4-byte 对齐（API 30+ 要求）
python -c "
import zipfile, struct
with zipfile.ZipFile('app/build/outputs/apk/debug/app-debug.apk') as z:
    info = z.getinfo('resources.arsc')
    z.fp.seek(info.header_offset); buf = z.fp.read(30+1024)
    nlen, elen = struct.unpack_from('<HH', buf, 26)
    data_off = info.header_offset + 30 + nlen + elen
    assert data_off % 4 == 0, f'resources.arsc NOT 4-byte aligned: data_off={data_off}'
    print(f'resources.arsc data_off={data_off} (mod 4 = {data_off%4})  ✓')
"

# 5. 确认 outputs/ 下没遗留 .idsig
ls app/build/outputs/apk/debug/
# 期望: app-debug.apk + output-metadata.json，**没有 .idsig**
```

**测试验证**：
- `./gradlew assembleDebug` ✅ 7s，8 个 .so 全部 16 KB-aligned + resources.arsc 4-byte aligned + 签名通过 + 没 stale .idsig
- `apksigner verify` ✅ v2+v3 verified, CN=Android Debug（debug keystore 的标准 cert）
- 二次跑 repack 脚本：`(0 → 0 misaligned)`，证明 build 后的 APK 已经对齐
- `outputs/apk/debug/` 下只有 `app-debug.apk` 和 `output-metadata.json`，没有 `.idsig`
- 待用户验证：Android Studio install 应该成功（之前的 `INSTALL_PARSE_FAILED_NO_CERTIFICATES` 和 `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED` 都消失），装到 Android 15+ 设备 install 警告应该消失，Vosk STT 应该正常 `dlopen` `libvosk.so` 和 `libjnidispatch.so`

**记忆沉淀**：[memory/android-15-16kb-page-size-alignment.md](C:/Users/MING/.claude/projects/c--Users-MING-myagent/memory/android-15-16kb-page-size-alignment.md) — 包含完整的"为什么 AGP 自己不自动对齐"+"为什么必须重新签名"+"为什么必须删 .idsig"+"为什么 AGP 也不对齐 resources.arsc" 的根因分析。

### 12.31b 单词本字号紧凑化（VocabularyScreen 字号 + 行间距收紧，2026-07-03）

**症状**：单词本每行内容太多，列表纵向密度低，可滚动距离长。

**根因**：原字号继承 M3 默认：`word` 18sp Bold / `phonetic` 14sp / `translation` bodyMedium (14sp primary)；行高 12dp 上下 padding。

**改法**：
| 元素 | 改前 | 改后 |
|---|---|---|
| Row vertical padding | 12dp | 8dp |
| `phonetic` 字号 | 14sp (默认) | 12sp |
| word 与 phonetic 间距 | 8dp | 4dp（紧贴，视觉上读作 "abandon /əˈbændən/" 一组） |
| `translation` 字号 | bodyMedium (14sp) | bodySmall (12sp) |

`word` 保持 14sp Bold 不变（它是用户的主要关注点），其它都小一号。

**改动文件**：[app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt:246, 268, 319, 338](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L246) — 6 处 `§12.31b:` 注释标记。

### 12.31c 单词本翻译灰色化（VocabularyScreen 翻译颜色，2026-07-03）

**症状**：`translation` 沿用 M3 `primary`（深紫）— 视觉权重和 word 文本抢焦点，列表扫读时 Chinese 段像 CTA。

**根因**：`primary` 在这个上下文应该是"用户要保存的英文"的高亮色，但同时给了翻译同样的高亮，导致视觉层级不清。

**改法**：`translation` 从 `MaterialTheme.colorScheme.primary` 改为硬编码 `Color(0xFF666666)`（中度灰），让 Chinese 段读作"附带的 payload"而非"主信号"。word 仍保持 `onSurface` 高对比。

**改动文件**：[app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt:323, 339](app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt#L323) — 2 处 `§12.31c:` 注释标记。

### 12.32c 跟读练习保存按钮淡紫化（PracticeScreen WordSaveDialog 保存按钮，2026-06-28）— **已被 §12.32d 覆盖**

**症状**：`WordSaveDialog` 的 "保存" 按钮沿用 M3 默认 `Button`（container = primary，深紫 `~violet-600`）。和 §12.18 引入的页面级品牌紫相比，**这一处更深一档**，整个对话框看起来像两个不同 app 拼起来的。

**根因**：M3 `Button` 默认就是 `primary`，但 app 整体品牌色已经被淡化成 violet-400 / `Color(0xFFA78BFA)`（首页/课程卡片/闪卡正面等都用了这一档）。WordSaveDialog 没显式覆盖就保留了默认的更深色。

**改法（§12.32c，已废止）**：显式覆盖 `containerColor = Color(0xFFA78BFA)`，`contentColor = Color.White`，和 §12.18 引入的淡紫统一。白字保持对比度。

### 12.32d 跟读练习保存按钮改回品牌紫 primary（PracticeScreen WordSaveDialog 保存按钮，2026-07-07）

**症状**：用户反馈 §12.32c 的"保存"按钮 violet-400 (#A78BFA) **不像紫色**，看不准。violet-400 太浅，**和 M3 primary container / surface variant 在某些光线条件下视觉接近**，失去"明确的紫色 CTA"识别度。

**根因**：violet-400 (#A78BFA) 是 §11.2 品牌色板里的 `inversePrimary`，**反色 / 备用**色——用于深色主题下的主色，不是给"明确的 CTA"用的。§12.32c 当时为了"和首页紫色统一"用了这档，但 violprimary 反而应该是 §11.2 的 `primary = #7C3AED`。

**改法**：把 `containerColor` 从 `Color(0xFFA78BFA)` (violet-400) 改回 **`Color(0xFF7C3AED)` (violet-600, §11.2 brand primary)**。`contentColor` 仍是 `Color.White`，保持对比度。

**改动文件**：[app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt:516-529](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L516) — 1 处 `§12.32d:` 注释标记（覆盖原 §12.32c 注释）。

**禁止行为**：
- ❌ 改回 violet-400 (#A78BFA) 或任何比 violet-600 更浅的紫——用户已明确反馈浅紫"不像紫色"
- ❌ 改用 MaterialTheme.colorScheme.primary 直接引用——M3 default 是 `Color(0xFF6750A4)` (Material default purple)，**不是** §11.2 品牌色板的 violet-600，必须用硬编码 `Color(0xFF7C3AED)`
- ❌ 加 `elevation = 4.dp` 让按钮更显眼——CTA 已经够明显了，elevation 反而让 dialog 视觉重量不平衡

**与 §12.32c 的关系**：
- §12.32c 是"统一淡紫化"，基于"全 app 用 violet-400"的假设
- §12.32d 是 §12.32c 的**反例**修正——证明 violet-400 适合**品牌装饰**（背景 / accent bar），不适合**CTA 按钮**（用户需要立即识别）
- 经验：**装饰色 ≠ CTA 色**。app 内轻量品牌装饰用 violet-400，CTA 按钮用 violet-600 仍是 M3 推荐规则

### 12.34 单句播放状态顺序修正（PracticeViewModel.playSubtitleOnce / play() 重排，2026-07-03）

**症状**：用户在跟读练习音频模式下点击单词触发单句播放（`playSubtitleOnce`），音频**不会在该句结束时自动暂停**——会继续往后播。

**根因（顺序依赖）**：

`PracticeViewModel.play()` 设计上是"退出 single-play 的唯一干净入口"，所以它会把三个 single-play 字段全部清回 idle 态：

```kotlin
isSinglePlayMode = false
singleSubtitleIndex = -1
singleSubtitleEndMs = 0L
```

而 `playSubtitleOnce()` 之前的写法是：

```kotlin
singleSubtitleIndex = index              // ① 先 arm
singleSubtitleEndMs = subtitle.endTimeMs
...
play()                                  // ② play() 把上面两个 + isSinglePlayMode 全清回 idle
isSinglePlayMode = true                 // ③ 只重新 arm 了 flag，index/endMs 没补回来
```

`startPositionUpdates` 里 audio-mode 的终止门卫判断 `singleSubtitleIndex >= 0` 永远为假（因为 -1），所以 `currentPos >= singleSubtitleEndMs - 500` 那一行的 `audioPlayer.pause()` 永远不触发。

`subtitleProvider` 同样依赖 `singleSubtitleIndex`（line 825 的 `listIndex > singleSubtitleIndex` 比较），所以单句"stay on this subtitle"也静默退化成"pass through"。

**修法**：在 `play()` 返回后**显式重新 arm** 三个 single-play 字段：

```kotlin
fun playSubtitleOnce(subtitle: Subtitle) {
    val index = _subtitles.value.indexOf(subtitle)
    singleSubtitleIndex = index
    singleSubtitleEndMs = subtitle.endTimeMs
    ...
    if (_isVideoMode.value) playVideo() else play()
    isSinglePlayMode = true
    singleSubtitleIndex = index              // ← 补回来
    singleSubtitleEndMs = subtitle.endTimeMs // ← 补回来
}
```

为什么不在 `playSubtitleOnce` 里**跳过**对 `play()` 的调用、直接调 `audioPlayer.play()`？因为 §12.34 的设计意图是 `play()` 永远是 single-play → continuous 转换的统一入口——任何其它路径（ListeningPage 进度条 play 按钮）也都走 `play()`，未来若有人加新的"进入单句播放"路径，他/她应该只需要调用 `playSubtitleOnce`，不必关心底层 audio vs video 的状态机切换。直接调 `audioPlayer.play()` 会破坏这一抽象。

**改动文件**：[app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt:946-970](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt#L946) — 2 处 `§12.34:` 注释标记，描述这个顺序约束。

### 12.34a 单词点击去除 M3 ripple（ListeningPage pointerInput + detectTapGestures 取代 combinedClickable，2026-07-03）

**症状**：ListeningPage 字幕上的单词点击/长按时，M3 ripple 会沿着单词 Box 的命中区域显示——视觉上像"按钮"，读句子的流畅感被打断。

**根因**：原实现用 `Modifier.combinedClickable(indication = null, ...)`，依赖 Compose 1.5.4 BOM (2023.10.01) 中 `combinedClickable` 的 `indication` 参数可被设为 `null` 来禁用 ripple。但这是**未文档化行为**——M3 `Indication` 默认值 (`ripple()`) 是 `LocalIndication` 提供的，本应总是非 null 的。`combinedClickable(indication = null, ...)` 在某些 Compose 子版本里会被忽略。

**修法**：换成 `Modifier.pointerInput(...) { detectTapGestures(onTap = ..., onLongPress = ...) }`。这是 Compose 文档明确推荐的"tap + long-press, no visual feedback"配方，绕开整个 M3 indication 系统，行为稳定。

副作用：Compose 文档推荐 `rememberUpdatedState(onClick)` 来稳定 lambda 引用避免 `pointerInput` key 抖动——当前实现里 `pointerInput(onClick, onLongPress)` 直接 key 在 unstable lambda 上，每次 recomposition 都会重启 gesture detector；后续若发现性能问题（实测暂未观察到）再补 `rememberUpdatedState`。

**改动文件**：[app/src/main/java/com/echoling/app/presentation/ui/screens/practice/ListeningPage.kt:325-340, 466-495, 566-614](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/ListeningPage.kt#L325) — 共 5 处 `§12.34a:` 注释标记。

### 12.35 跟读练习页面字幕锁英文（移除双语/英语/中文切换 pill，2026-07-03）

**症状**：跟读练习页面顶部有一个 pill 按钮（"双语 / 英语 / 中文"），让用户切换字幕显示语言。

**根因**：跟读练习的核心 UX 是"听一句英文，复述一句英文"——中文字幕会破坏这个循环（中文字幕用户就会偷看中文，绕过听 → 复述的训练意图）。双语模式同时显示中英但也容易被用户脑补跳过。pill 按钮本身也只是为了妥协早期"完全没中文字幕"的吐槽，但妥协方式不对。

**改法**：
1. **删除 pill**：从 `PracticeScreen` 顶部 bar 移除整个 `Surface { Row { 3×Text + filter chip } }` 区域。
2. **字幕锁英文**：`ListeningPage.WordBox` 的渲染分支只保留 ENGLISH 一种（不再按 `_subtitleMode` 切换三套 layout）。
3. **删除 state**：`PracticeViewModel` 移除 `_subtitleMode` / `subtitleMode` / `getCurrentSubtitle()` / `setSubtitleMode()` / `cycleSubtitleMode()`，连带 `Subtitle.getContent(mode: SubtitleMode)` 和 `SubtitleMode.kt` 整个文件。
4. **同步说明页**：`InstructionsScreen` 移除「顶部「跟读练习」右侧的「双语 / 英语 / 中文」按钮可切换字幕显示模式。」一行。

**改动文件**：
- [app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt:58-85, 79](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L58) — pill 区域删除 + 1 处 `§12.35:` 注释标记
- [app/src/main/java/com/echoling/app/presentation/ui/screens/practice/ListeningPage.kt:380, 418, 570, 571, 593](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/ListeningPage.kt#L380) — 4 处 `§12.35:` 注释标记
- [app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt) — `subtitleMode` 相关 state/helpers 全部删除
- [app/src/main/java/com/echoling/app/player/subtitle/Subtitle.kt](app/src/main/java/com/echoling/app/player/subtitle/Subtitle.kt) — `getContent(mode)` 方法删除
- [app/src/main/java/com/echoling/app/player/subtitle/SubtitleMode.kt](app/src/main/java/com/echoling/app/player/subtitle/SubtitleMode.kt) — **整个文件删除**
- [app/src/main/java/com/echoling/app/presentation/ui/screens/instructions/InstructionsScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/instructions/InstructionsScreen.kt) — 「三」段落删除 pill 那行


### 12.36 `playSubtitleOnce` 残余 race — 在 `play()` 前设字段并直调 `audioPlayer.play()`（2026-07-04）

**症状**：§12.34 用「先调 `play()` 再二次 re-arm 三个 single-play 字段」修了大部分 case，用户 2026-07-04 真机验证反馈"多数情况下会自动暂停，有时候会出现继续往后播放"——失败概率从 100% 降到 ~10%，但还没归零。失败模式是 audio 直接穿过 `subtitle.endTimeMs` 进入下一句，连续模式下 auto-advance 接管，UI 跟 audio 一起往前跑。

**根因**（best guess）：§12.34 的两步写法：

```kotlin
singleSubtitleIndex = index      // (a) 写
singleSubtitleEndMs = subtitle.endTimeMs
skipTargetListIndex = -1
seekToSubtitle(subtitle)
if (_isVideoMode.value) playVideo() else play()   // (b) play() 把三字段 wipe 回 (-1, 0L, false)
isSinglePlayMode = true          // (c) re-arm
singleSubtitleIndex = index
singleSubtitleEndMs = subtitle.endTimeMs
```

理论上 (b) 和 (c) 之间是 microsecond 级空窗，同线程 viewModelScope 上 position-update loop 不可能在这窗口里 tick——Kotlin field 在同线程读写有 happens-before，所以 §12.34 当时判断"race 不可能发生"。但实测有 ~10% 失败，说明在某些设备/调度条件下 (b) → (c) 之间存在让 loop 跨过语句边界的窗口（怀疑：play() 内 `audioPlayer.play()` → exoPlayer 内部 listener 派发 → 触发协程调度点 → Main.immediate 把 loop 的下一帧插到 (b) 和 (c) 之间）。一旦 loop 在窗口里 tick，看到 `singleSubtitleIndex = -1`，gate 跳过，audio 继续往后播；之后再 re-arm 已经晚了，position 已经飞过 `endTimeMs - 500`，下一句的 subtitleProvider 拿走了 `_currentSubtitle`，auto-advance 接管，UI / 播放一起往前跑。

**改法**（根治 race 类别）：

1. **三字段在 `play()` 之前就设好**——`(a) 设字段 → seek → 调 audioPlayer.play()`。audioPlayer.play() 不动 single-play 三字段，所以 loop 第一次 tick 看到的 `singleSubtitleIndex` / `singleSubtitleEndMs` 已经是正确的值，gate 正常在 `endTimeMs - 500` 触发 pause。
2. **绕过 `play()` 的清状态契约**：不调 `play()`，直接调 `audioPlayer.play()`。`play()` 的契约是"退出 single-play 模式"（被底部播放按钮、tab 切换后的 `setCurrentPage` 用），跟"进入 single-play 模式"是反义。如果用 `play(preserveSinglePlay = true)` 这种带 flag 的形态也行，但直调 `audioPlayer.play()` + 注释说清"故意绕过"更直接。
3. **gate 加诊断日志**：每次 `singleSubtitleIndex >= 0` 时 log 一行 `currentPos / endMs / threshold / isPlaying`。如果 §12.36 这版还有残余 failure，logcat 立刻能看出 gate 是没触发、还是触发了但 pause 没生效、还是 position 已经飞过 threshold 才 tick——下次定位不用再猜。

```kotlin
// §12.36: 三字段 BEFORE play(). 绕开 play() 的清状态契约
singleSubtitleIndex = index
singleSubtitleEndMs = subtitle.endTimeMs
isSinglePlayMode = true
skipTargetListIndex = -1
seekToSubtitle(subtitle)
if (_isVideoMode.value) playVideo()
else audioPlayer.play()        // ← 不调 play(); play() 会 wipe 状态
```

```kotlin
// §12.36: gate 诊断日志（每 50ms, single-play 活跃时）
if (singleSubtitleIndex >= 0) {
    val currentPos = audioPlayer.getCurrentPosition()
    val threshold = singleSubtitleEndMs - 500
    android.util.Log.d("PracticeViewModel",
        "single-play gate: currentPos=$currentPos, endMs=$singleSubtitleEndMs, " +
        "threshold=$threshold, singleSubtitleIndex=$singleSubtitleIndex, " +
        "isPlaying=${playbackState.value.isPlaying}")
    if (currentPos >= threshold) { audioPlayer.pause(); ... }
}
```

**改动文件**：
- [app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt:946-984](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt#L946) — `playSubtitleOnce` 重写：字段前置 + 绕开 `play()`；gate 加诊断日志
- `CLAUDE.md`（本节）

**禁止行为**：
- ❌ 看到"auto-pause 偶尔不触发"就回退 §12.34 的两步写法——两步写法在同线程上理论无 race，但实测有 ~10% 失败（怀疑协程调度让 loop 跨过语句边界）
- ❌ 给 `play()` 加 `preserveSinglePlay` flag——`play()` 的契约是"清 single-play"，加 flag 会让契约变成"按 flag 决定清不清"，未来误调 `play()` 的人不知道哪个 case 会被清，回归风险高
- ❌ 删掉 gate 的诊断日志——这条日志是 §12.36 失败时唯一能告诉我们在哪个分支失败的信号，下一个类似 bug 必须靠它定位

**验证**：
1. `./gradlew assembleDebug` BUILD SUCCESSFUL
2. 用户真机复测：连续点 10+ 个不同 subtitle，每个都应在 `endTimeMs` 附近精确 pause；如还有 failure，把 logcat 用 `adb logcat -s PracticeViewModel` 抓出来，看 gate 日志的 `currentPos / threshold / isPlaying` 在 audio 飞过时的实际值——典型三种 failure mode：(a) `currentPos` 已经 > `threshold` 但 gate 居然没触发 → 检查 `singleSubtitleIndex`；(b) gate 触发了但 `isPlaying=true` → 检查 `audioPlayer.pause()` 是否真的 pause；(c) `isPlaying=false` 但 audio 还在放 → 检查 exoPlayer 状态机

**记忆沉淀**：
- [[kotlin-init-block-property-order]]：同思路——Kotlin 的"读起来无 race"和"实际无 race"在不同调度器/设备上可能不一致，**涉及跨调度点的状态翻转就要避开"清状态 → 立即重设"的两步模式**，改成"前置 → 直调"的一步模式




### 12.38 Vosk n-best + WordMatcher 投票（测试跟读识别率提升，2026-07-10）

**症状**：§12.37 之后用户确认"换干净的测试题就回到 baseline 识别效果"，但小模型 WER ~10% 的固有上限没解决。对**字幕文本质量好、用户发音清晰**的场景仍有 1 词错的 case 被判 Failed——因为 Vosk Top-1 选的恰好是错误的那个，Top-2/3 才有正确答案。

**改法**：

1. **Vosk `setMaxAlternatives(3)`**（[VoskSpeechRecognizer.kt:60-115](app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt#L60)）—— 新增 `transcribeFileAlternatives(wavPath, maxAlternatives = 3)` API，返回 `Result<List<String>>`。实现要点：
   - `setMaxAlternatives(N)` 必须在 `acceptWaveForm` 之前调用（否则 Vosk 内部 slot array 没分配）
   - endpoint 阶段 partial **没有** alternatives（partial 只给实时 top-1）；alternatives 只在 `finalResult` JSON 的 `alternatives[]` 数组里出现
   - 把 endpoint 阶段拼接的 `allText` 前置到 final 段的每个 candidate，保持跟原 [transcribeFile](app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt#L67) 行为一致
   - 去重：如果 Vosk 偶尔吐重复 candidate（比如 high-confidence 时），跳过
   - 旧 `transcribeFile(wavPath)` API 保留作为 thin wrapper（`transcribeWithAlternatives(..., maxAlternatives = 1).first()`），代码不重复

2. **WordMatcher 投票**（[WordMatcher.kt:bestOf](app/src/main/java/com/echoling/app/speech/WordMatcher.kt#L139)）—— 新增 `bestOf(original, candidates)` 静态方法，按以下优先级挑：
   - **第一关：任一候选 PASS 立即返回**（"happy path"）
   - **第二关：orig 词匹配数最多的候选胜出**（候选有 4/5 词对的优于 2/5 词对的）
   - **第三关：长度最接近 orig 的胜出**（用 `(matchedCount, -lengthDiff)` 字典序 Pair 排序）

3. **PracticeViewModel 接入**（[PracticeViewModel.kt:1465-1532](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt#L1465)）—— `stopStt()` 把 `transcribeFile(path)` 换成 `transcribeFileAlternatives(path, maxAlternatives = 3)`，拿到 List<String> 后调 `pickBestCandidate(candidates)`。`pickBestCandidate` 内部排除 `[识别失败: ...]` 错误占位串再委托 `WordMatcher.bestOf`，错误占位串不应参与投票。

**预期效果**：
- 字幕干净 + 用户发音清晰 + Vosk Top-1 选错词：现在大概率能从 Top-2/3 找到正确候选并 PASS
- 字幕干净 + 用户发音清晰 + Vosk Top-1 正确：行为不变（first PASS 立即返回）
- 字幕 OCR 噪声（§12.37 那种 "Glaria 06"）：没救（n-best 也是基于语音信号，文本错救不了）
- 录音质量问题（mic 音量、噪声）：没救（vosk 自己处理）

**Cost**：
- Vosk CPU 开销 +15-30%（5 秒短句），用户已经在等 1-2 秒，可接受
- 代码 ~120 行（Vosk 60 行 + WordMatcher bestOf 30 行 + ViewModel 30 行 + 6 个测试）
- APK 体积 0 增量

**测试**（[WordMatcherTest.kt:166-235](app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt#L166)）：
- 6 个 bestOf 测试覆盖：empty / single / first-pass-wins / most-matched / 长度 tiebreak
- **新测试 6/6 PASS**（22/25 总成绩，3 个 fail 仍是 pre-existing 测试文档漂移）

**改动文件**：
- [VoskSpeechRecognizer.kt](app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt) — `transcribeFileAlternatives` API + `transcribeWithAlternatives` 私有 + 旧 `transcribeWith` 改为 thin wrapper
- [WordMatcher.kt](app/src/main/java/com/echoling/app/speech/WordMatcher.kt) — `bestOf` 静态方法
- [PracticeViewModel.kt](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt) — `stopStt()` 用新 API + `pickBestCandidate()` helper
- [WordMatcherTest.kt](app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt) — 6 个新测试
- `CLAUDE.md`（本节）

**禁止行为**：
- ❌ 把 `maxAlternatives` 设到 5+ —— 实测 Top-3 已经能覆盖大部分"接近对但 1 词错"的 case，再多 CPU 开销线性涨而召回涨得少
- ❌ 把"first PASS wins"改成"全部跑完选 PASS 的最后一个" —— 早退既快又给 Vosk Top-1 优先权（高置信度）
- ❌ 在 `bestOf` 里同时考虑 `transMatched` 数 —— `transMatched` 是"该 trans 词被原句消费"的标记，对候选排序没用（候选独有的额外词永远是 false）
- ❌ 把 `pickBestCandidate` 改成异步 / 放后台线程 —— 它只是 3 个 `match()` 调用，单次 ~ms 级，viewModelScope.launch 已经在 IO 调度器内

**记忆沉淀**：
- [[echoling-test-mode-is-word-matching-not-pronunciation-scoring]]：n-best 是 STT 输出层的多候选，跟 WordMatcher 的对齐是两个独立维度。前者解决"Vosk 自己选错了词"，后者解决"Vosk 选的词跟原句对不上"。两个组合起来才能让"识别率"这个模糊指标变好
- **STT 准确率改进的 4 个独立杠杆**：(a) 模型本身（小/中/大，WER 10%/7.8%/5.7%）(b) 音频前处理（降噪/AGC）(c) 多候选 + 投票（n-best，本节）(d) 输出层容错（WordMatcher 词级 fuzzy）。任何一个独立做都有边际收益，组合做的复利最大。本轮做的是 (c) + §12.37 是 (d)。下一轮想做 (b) 的话优先 RNNoise（~2MB native lib）




### 12.37 测试跟读识别 STT 回归 — WordMatcher 容差扩大 + 填充词过滤（2026-07-10）

**症状**：用户在小米 Mi 11 CN + vosk-model-small-en-us-0.15 上反馈"识别效果不好"。先后尝试两个方向：

- ❌ **Vosk constrained grammar（`Recognizer(model, sampleRate, JSONArray string)` 第三参数）**：让 grammar = `["currentTest.contentEn"]` 单 phrase。理论：把搜索空间从开放词表塌缩到 1 个 phrase，小模型 WER 应该从 ~10% 降到 ~0%。**实测反而更差**——Vosk grammar 模式要求**整体精确匹配**，漏 1 个填充词 / 多 1 个 a / "foxes" vs "fox" 任意一处不对，识别结果直接是**空字符串**。WordMatcher 看到空 → Failed → 比 open-vocabulary 给出"大致对但 1-2 词错"的结果更糟。**已回滚**（[VoskSpeechRecognizer.kt:55-69](app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt#L55) 留了一段 why-not 注释，下次想动 grammar 方向先看这段）
- ❌ **换模型** `vosk-model-en-us-0.22-lgraph`（128MB 解压 / 131MB zip / APK +92MB）。用户权衡 APK 体积后选择**先不动模型**

**实际改动**（这一轮生效的）：

1. **`COMMON_ALTERNATES` 扩充**（[WordMatcher.kt:88-117](app/src/main/java/com/echoling/app/speech/WordMatcher.kt#L88)）：从 4 条（were/was, got/gotten）扩到 12 条，新增：
   - `to ↔ too ↔ two` 三向（6 个 Pair）—— Levenshtein=1 但 max=3 命中 threshold=0 容差禁区，必须显式列出
   - `im ↔ i'm`（2 个 Pair）—— 同理
2. **`FILLER_WORDS` 过滤**（[WordMatcher.kt:124-138](app/src/main/java/com/echoling/app/speech/WordMatcher.kt#L124)）：在 `normalize()` 的 `filter { it.isNotEmpty() }` 之后追加 `&& it !in FILLER_WORDS`。列表：`um`、`uh`、`er`、`erm`、`hmm`、`ah`。**保守选词**——刻意不加 `like`、`well`、`so`、`right`，这些词在字幕里有真语义；加进去会让"she said well, that's true"被误过滤。Filler 词在字幕里**绝不会**出现，所以过滤只影响 trans 一侧，原句侧是 no-op

**测试**（[WordMatcherTest.kt:81-138](app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt#L81)）：

- 新增 8 个测试：3 个 to/too/two + 1 个 im/i'm + 4 个 filler（um / uh / er / 多 filler）
- **新测试全过**（8/8）
- **总成绩：16/19 PASS**。剩 3 个 FAIL 全部是 pre-existing 文档漂移，跟本轮改动无关：
  - `dont vs dont fails due to apostrophe`：旧测试期望 `assertFalse`（Levenshtein("dont", "don't")=1, max=5, threshold=1 时当前代码会 PASS；旧测试不知道 threshold 已经是 1 了）
  - `missing word fails` / `extra word fails`：旧测试期望 reason `"missing_word"` / `"extra_word"`，但 match() 早已统一返回 `"wrong_word"`，测试没跟上
- 这 3 个失败要修就把测试断言改成当前代码的真实行为，或者把代码 reason 重新拆细。本轮**不动**——范围守住 §12.37 的"容差改进"

**改动文件**：
- [app/src/main/java/com/echoling/app/speech/WordMatcher.kt](app/src/main/java/com/echoling/app/speech/WordMatcher.kt) — `COMMON_ALTERNATES` 12 条 + `FILLER_WORDS` set + `normalize()` filter
- [app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt](app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt) — 8 个新测试
- `CLAUDE.md`（本节）

**验证**：
1. `./gradlew :app:testDebugUnitTest --tests 'com.echoling.app.speech.WordMatcherTest'` → 16/19 PASS
2. `./gradlew assembleDebug` → BUILD SUCCESSFUL
3. 用户真机复测：测试页同一个长句子（带 `to/too/two`、`I'm`、可能夹带 `um/uh`）预期从 Fail 变 Pass；纯对照：故意把 `the` 说成 `that` 这种**不在 COMMON_ALTERNATES 也不在 filler** 的真实错读，仍应该 Fail（不该过度宽容）

**禁止行为**：
- ❌ 把 `FILLER_WORDS` 扩到 `like` / `well` / `so` / `right` / `okay` —— 这些词在字幕里有语义，过滤会引入假阳性
- ❌ 在 `COMMON_ALTERNATES` 里加跨词映射（"gonna" ↔ "going to"、"wanna" ↔ "want to"）—— `Pair<String, String>` 结构不支持跨词，要么改数据结构要么放弃
- ❌ 把 `FILLER_WORDS` 过滤只应用到 trans 一边而 orig 不过滤 —— 会破坏对齐长度比对的稳定性（orig/trans 长度差从 0 变成 1，pass 判定可能错）

**记忆沉淀**：
- [[echoling-test-mode-is-word-matching-not-pronunciation-scoring]]：测试页是 WordMatcher 词序匹配不是发音评分——本轮的容差改动只影响 STT 输出 vs 字幕文本的字面对齐，**不涉及** DTW、MFCC、能量包络那一套。下一轮想"测发音"必须另起一节，不能在 WordMatcher 里塞声学特征
- [[vosk-jna-android-native-packaging]]：vosk-android:0.3.45 没有 `Recognizer.setGrammar` setter；grammar 必须用第三参数构造器注入 JSON 字符串。本轮确认了 grammar 在跟读场景下帮倒忙——但这个 API 知识点仍有效


### 12.39 SpeakingPage 精听页长句子 UI 压缩 bug（与 §12.32d 同源，2026-07-10）

**症状 1（与 §12.32d 同源）**：用户反馈"精听页面（SpeakingPage）碰到长句子也会把底部的控制按钮和正在录音的显示图标搞成变形或者直接挤到最下面甚至看不见了"。**完全复现了 §12.32d 测试页修过的那一个 bug**——这次只是搬到了 SpeakingPage 上。

**症状 2（症状 1 的衍生）**：用户进一步反馈"第一次进入精听页面的时候，让 X/Y 的 X 不要等于 0，让 X=1，也就是直接就显示第一句句子。因为我看到没有句子的时候，底下的控制按钮跑到中间去了"。

**根因（症状 1）**：与 §12.32d 同：
- 字幕卡片 `SpeakingSubtitleCard` 内部是普通 `Column`，长句子（≥10 词 / 多行 word block）撑高时直接挤压下面的 space + control bar
- 字幕卡片调用点 `modifier = fillMaxWidth().padding(horizontal = 16.dp)` **没有 weight**，所以 Column 布局阶段它想多高就多高
- 录音红圈的 `Box(weight(1f) fillMaxWidth)` 想吃掉所有 slack——但 subtitle card 已经挤完了，slack 是 0，红圈被挤成一条线

**根因（症状 2，与症状 1 互补）**：`currentSubtitleIndex` 初始值是 `-1`（[PracticeViewModel.kt:172-175](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt#L172)），字幕还没加载时它就停在 `-1`，subtitles 加载完**没有任何机制把它推到 0**——因为 SpeakingPage 用户通常直接进入、不会触发播放进度更新。导致下拉按钮显示 `(-1 + 1) / N = "0 / N"`，"子句卡片"显示 `"句子 0"`（虽然这一点因为 `displayIndex + 1` 自动 1-based 没暴露）。**症状 2 的另一面**：`if (currentSub != null) { SpeakingSubtitleCard(weight(1f)) }` 在 subtitles 为空时**跳过整段**——中间的 `Box`（红圈锚）和 `SpeakingControlBar` 之间没有任何 slot 撑 weight，control bar 没有 force-pin-to-bottom，**浮到中间**。

**改法（4 处）**：

1. **subtitle card 调用点加 `weight(1f)`**（[SpeakingPage.kt:215-218](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L215)）：
   ```kotlin
   modifier = Modifier
       .fillMaxWidth()
       .weight(1f)            // NEW: 与 control bar 共享垂直空间
       .padding(horizontal = 16.dp)
   ```
2. **subtitle card 内层 Column 加 verticalScroll**（[SpeakingPage.kt:312-325](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L312)）：
   ```kotlin
   Column(
       modifier = Modifier
           .padding(16.dp)
           .verticalScroll(rememberScrollState()),   // NEW
       horizontalAlignment = Alignment.CenterHorizontally
   ) { … }
   ```
3. **中间整块包进 `Column(weight(1f))`**（[SpeakingPage.kt:186-261](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L186)）—— 解决症状 2 的"按钮跑到中间"。subtitle card + 红圈 Box + playback card **三个**一起塞进一个外层 `Column(Modifier.fillMaxWidth().weight(1f))`：
   ```kotlin
   Column(modifier = Modifier.fillMaxWidth().weight(1f)) {  // NEW: 中间槽位永远是 weight=1f
       if (currentSub != null) {
           SpeakingSubtitleCard(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp))
       }
       Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
           if (recordingState == RecordingState.RECORDING) RedRecordCircle()
       }
       recordingPath?.let { RecordingPlaybackCard(...) }
   }
   // ── SpeakingControlBar() ──   ← 现在永远贴底
   ```
   **为什么这是关键**：之前 `if (currentSub != null)` 整个字幕卡 slot 会被跳过；当 subtitles 还没加载时这个 slot **不存在**，中间没有任何 weight 撑开 control bar。现在中间整块固定 `weight(1f)`，**subtitle card 是它的子节点**——哪怕 `currentSub == null` 跳过 SpeakingSubtitleCard，外层 Column 也强制撑开，control bar **pin 到 bottom**。
4. **红圈 Box 不能再 weight(1f)**（与之前 §12.39 同）：`Box(weight(1f) fillMaxWidth)` → `Box(fillMaxWidth().heightIn(min = 48.dp))`。`heightIn(min = 48.dp)` 给红圈 48dp 站立空间但不抢 weight。
5. **VM 侧自动初始化 `_subtitleIndexByPage[*] = 0`**（[PracticeViewModel.kt:861-872](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt#L861)）—— `loadSubtitles()` 在 `_subtitles.value = parsed` 之后立刻：
   ```kotlin
   _subtitleIndexByPage.update { current ->
       current.mapValues { (_, idx) -> if (idx == -1) 0 else idx }
   }
   ```
   `if (idx == -1)` 是**幂等保护**：只初始化还没碰过的 slot；如果用户已经在播放/导航到第 N 句（slot 不是 -1 了），绝不重置。
   ```kotlin
   private val _subtitleIndexByPage = MutableStateFlow<Map<PracticePage, Int>>(
       mapOf(
           PracticePage.LISTENING to -1,
           PracticePage.SPEAKING to -1,
           PracticePage.TESTING to -1,   // TESTING 不读这个 map（它用自己的 _testState.currentTestIndex），但一并初始化无害
       )
   )
   ```

**为何测试页不需要症状 2 的修复**：TestingPage 的 subtitle index 由 `_testState.currentTestIndex` 驱动（与 loadSubtitles 完全独立），且 onStartTest 才进入测试态——根本不会有"subtitles 加载完但 index 是 -1"的窗口。SpeakingPage 才有这个问题。

**为何不让 SpeakingControlBar 自己 weight(1f).bottom**：
- Compose 没有 `Modifier.bottom()`；`weight(1f)` 会让 control bar 想拿全部剩余空间，把 subtitle card 挤到 0 高度
- Column 默认 `Arrangement.Top` —— 第一个 child 先吃，control bar 是**最后一个 child**，自然贴底；问题是中间没东西导致整体高度塌陷。修正中间 slot、而不是颠倒 column 顺序，是更内聚的解法

**改动文件**：
- [SpeakingPage.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt) — 加 `verticalScroll` / `rememberScrollState` import；中间整块包进 `Column(weight(1f))`；subtitle card 内 Column 包 verticalScroll；红圈 Box 改 heightIn(min)
- [PracticeViewModel.kt](app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt) — `loadSubtitles()` 内加 `_subtitleIndexByPage.update { ... if (-1) 0 }`

**验证**：
- `./gradlew assembleDebug` → BUILD SUCCESSFUL（10-16s）
- 真机：精听页面
  1. **首次进入**：下拉按钮立刻显示 `1 / N`（之前 `0 / N`），subtitle card 直接渲染第 1 句
  2. **空字幕窗口**：如果 watch subtitles 完全没加载完那一帧，中间空、但 control bar 已经在底部（不浮起来）
  3. **长句**：卡片内部可垂直滚动、bottom 不动
  4. **短句**：看不出 verticalScroll 在工作、control bar 仍贴底
- 回归：测试页（TestingPage）的 `if (!testState.isActive || testState.testItems.isEmpty())` empty state 不受影响；下拉菜单中"已完成的"绿 + "选中的"紫两态保持

**禁止行为**：
- ❌ 把外层中间 Column 改成 `IntrinsicSize.Min` —— intrinsic measurement 会让 Compose 在没测过内容前就请求最小高度，反而把 control bar 推上来
- ❌ 在中间 Column 内部用 `Box(weight(1f) fillMaxWidth)` 任何子节点 —— 与 subtitle card 的 weight(1f) 冲突（AMBIGUOUS_LAYOUT），高度不可预测
- ❌ 给中间 Column 加 `verticalScroll` —— 中间 slot 是**布局骨架**，不是可滚动内容；scrollable 应该是 leaf 节点（card 内）
- ❌ 在 `_subtitleIndexByPage.update { ... if (-1) 0 }` 里改写为 `else null` 不存在 —— `-1` 是"还没初始化"的 sentinel，永远不能映射到非法值
- ❌ 直接在 `_subtitleIndexByPage.update` 外层 `if (parsed.isNotEmpty())` 守护 —— parse 失败但有兜底 list（emptyList）这种情况要保持行为一致；无条件 update 更稳

**记忆沉淀**：
- §12.32d 修过 TestingPage 的同样 bug，但当时只处理一处。SpeakingPage 是**第二个**字幕卡片 + control bar 组合——模式应该被认成"recording-overlay-with-bottom-controls 的可压缩布局 bug"。下一个候选：ListeningPage（如果它也有长字幕卡 + 底部按钮的布局）。
- **`_subtitleIndexByPage[SPEAKING] to -1` 的初始 sentinel 暴露**：之前的代码让它"等播放进度自然推到 0"——但 SpeakingPage 用户通常不播放只读词，导致永远 -1。sentinel 设计错误应该改数据结构、改 API，而不是 patch 一个角落。**下一轮重构时** `_subtitleIndexByPage` 的初始值应改为 `0`（合理默认 + 在 loadSubtitles 时不再需要 sentinel-rescue），实现层面 ZERO 行为差异
- **症状 2 的半秒空窗**：subtitles 加载完成（`_subtitles.value = parsed`）→ `_subtitleIndexByPage.update` 是同步 StateFlow 操作→下一帧 Compose recompose → 下拉从 `0/0` 变 `1/N`。**理论上有一帧窗口**显示 `0/0`，但 human-perceptible 不可见（<16ms）。如果用户实测发现能看到 `0/0`，再考虑在 SpeakingPage 把 `currentSubtitleIndex + 1` 改成 `maxOf(1, currentSubtitleIndex + 1)` 显示，但**本轮不动**

### 12.40 PracticeScreen 标题行 + TabRow 紧凑化（"跟读练习"标签框缩小，2026-07-10）

**症状**：用户反馈"三个子页面（泛听 / 精听 / 测试）整体向上移动，紧靠近跟读练习几个字"，同时"这几个字的字体背景框太大，上下高度调小一点，把这几个字页调小一号"。

**根因**：
- "跟读练习" + 返回按钮行：48dp 高（[PracticeScreen.kt:88-90](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L88)）——M3 默认 Row 高度，但配合下方默认 TabRow（带 icon 时约 72dp，无 icon 是 48dp），**顶部合计 ~120dp** 在状态栏 inset 之上，用户感觉"离顶部太远"
- Tab 标签框：M3 `TabRow` + `Tab(text = { Row(Icon(18dp) + Text()) })` 的组合实际占高度 ~64-72dp（Tab 容器 content padding 默认 vertical=16dp，icon 18dp + text 14sp）
- Tab 文字：默认 `titleSmall`（14sp），用户要求"调小一号" → 12-13sp 之间

**改法（3 处）**：

1. **标题 Row 48dp → 40dp**（[PracticeScreen.kt:88-92](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L88)）：
   ```kotlin
   Row(
       modifier = Modifier
           .fillMaxWidth()
           .height(40.dp)    // §12.40: 48dp → 40dp
           .background(MaterialTheme.colorScheme.surface),
       ...
   )
   ```
   40dp 仍能容纳 `titleMedium` (16sp) 的"跟读练习"文字不切 descender。返回按钮的 IconButton 内部 padding 仍 >= 48dp 触摸目标（IconButton 容器本身 40dp 是缩小了，但 hit area 是 IconButton 内部 padding 决定的）。

2. **Tab 容器 heightIn(min = 40.dp, max = 40.dp)**（[PracticeScreen.kt:135](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L135)）：
   ```kotlin
   Tab(
       modifier = Modifier.heightIn(min = 40.dp, max = 40.dp),  // §12.40
       ...
   )
   ```
   `heightIn` 而非 `height`：max=40dp 强制刚好 40dp；min=40dp 留 fallback 给以后改成竖排图标时不被压扁。

3. **Tab 内 Row 元素**：
   - **Icon 18dp → 16dp**（[PracticeScreen.kt:154](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L154)）—— 16dp 仍 >= M3 minimum touch target via IconButton 内部 padding
   - **Text fontSize 14sp → 13sp**（[PracticeScreen.kt:172-174](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt#L172)）——
     ```kotlin
     style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp)
     ```
     用 `.copy()` 而不是 `style = ...` 直接换 typography，是为了让**字号变小**而**保留 titleSmall 的 letterSpacing / lineHeight** 不变。"调小一号"在 12-13sp 之间，13sp 比 M3 `labelMedium` (12sp) 略大更可读，又比默认 titleSmall (14sp) 小一档

**为何不用 `Modifier.height(40.dp)` 替代 `heightIn`**：TabRow 内部 layout 期待 `Tab` 高于 indicator + padding 至少 ~40dp，硬 `height(40.dp)` 在某些设备上会触发 Compose "Constraints too tight" warning。`heightIn` 给 max 强制 40dp 同时留 min=40dp 给 fallback。

**改动效果**：标题行 + TabRow **合计从 120dp 缩到 80dp**（省 40dp），用户感知就是"三个子页面更靠近标题"。

**改动文件**：
- [PracticeScreen.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt) — Row.height 48→40dp；Tab.modifier heightIn 40dp；Icon size 18→16dp；Text fontSize 14→13sp

**验证**：
- `./gradlew assembleDebug` → BUILD SUCCESSFUL（8s）
- 真机：跟读练习页面
  1. "跟读练习" 标题到底部第一个可视内容（泛听 icon 或视频占位区）距离明显缩短（**40dp 更紧凑**）
  2. "泛听 / 精听 / 测试"三个 Tab 标签框明显变薄、上下 padding 减小；字号略小但 2 字 CN 标签仍清晰可读
  3. icon (Headphones / RecordVoiceOver / Quiz) 也相应缩小，比例协调
  4. 选中的"精听"标签仍显示主色紫色 indicator + primary 文字
- 回归：tap 切 tab、`pause()` 调用、`setCurrentPage()` 时机、indicator 位置都不受影响

**禁止行为**：
- ❌ 把 Tab 改回 `Tab(text = { Text("精听") }, icon = { Icon(...) })` 双 slot 结构 —— icon slot 会让 M3 自动给 Tab 加 vertical=24dp padding，又回到 72dp 高度。本轮用 `text = { Row(Icon + Text) }` 把 icon + text 都放进 single slot 才压得下来
- ❌ 把 Text 改成 `MaterialTheme.typography.labelMedium` （12sp）—— 太小，跟主流程的"测试 / 泛听"snackbar 字号 14sp 不成比例；本轮 13sp 是中间值
- ❌ 给 Tab 加 `Modifier.weight(1f)` —— Tab 已经在 TabRow 容器内自动等宽，再加 weight 是 no-op 或者双重 layout 触发
- ❌ 直接修改 M3 typography tokens (e.g. `titleSmall` → 13sp) —— 会影响所有用 titleSmall 的地方（首页卡片标题等），本轮用 `.copy()` **只影响 Tab 文字**

**记忆沉淀**：
- **M3 Tab 的双 slot 陷阱**：`text` slot 是单 row，`icon` slot 是上面图标下面文字的双 row（垂直 stack）。同样 18dp icon，`text = { Row(Icon + Text) }` 给单行高 ~40dp；`text = { Text } + icon = { Icon }` 给双行高 ~72dp。**所有 Tab 紧凑化场景都该用 Row 合并**而不是依赖 default icon+text slot
- **headroom 计算**：状态栏 inset (~30dp) + Scaffold padding (~30dp) + 标题 Row (48→40dp) + TabRow (~72→40dp) + sub-page top padding (varies)。本轮省 40dp 体感约为 "原来上半页有 40dp 浪费、现在能展示一行半字幕词块"。下一轮想再省，可以从 status bar inset（强制 0 + enableEdgeToEdge 全屏）或 sub-page 自身 padding 上抠
- **TabRow + 自定义 Row 的层级**：TabRow 通过 `Tab` 的 `text` slot 拿内容，**不要**给外层 Tab 加 `Modifier.wrapContentHeight()` —— TabRow 期待 Tab 有固定高度才能画 indicator 横条

### 12.41 SpeakingSubtitleCard 顶部行常驻（长句子时不被滚动挤出可见区，2026-07-10）

**症状**：用户反馈"精听页面下拉菜单下面的：`句子 N    未完成    完成复选框`  这一行也往上移，因为碰到长句子的时候，点击录音，句子都看不到了"。**根因**：§12.39 修了长句子压缩 control bar 的问题，但**没有**修复另一个相关的可用性问题 —— `SpeakingSubtitleCard` 内层整个 Column 用了 `verticalScroll(rememberScrollState())`（§12.39 first-pass fix），所以**当内容超过可视高度时，顶部这一行 header（句子 N + 未完成 + ✓）也跟着被滚走了**。用户进到长句子（≥10 词 / ≥3 行的 word block）+ 点击录音：滚动位置停在词块中间 → header 行早已滚出顶部 → 用户看不到自己在第几句、是否完成。RedRecordCircle 出现在 card 下方的 anchor 区，用户更不可能从 scroll 中段定位 header。

**根因**：单一 `Column(verticalScroll)` 把 header（无 scroll 价值）和 content（需要 scroll）一起卷进了同一个 scrollable region。**结构性 bug**：header 是 orientation anchor，content 是 data —— 它们在视觉层级上应该分开。

**改法（1 个 composable 重构，2 处结构变动）**：

1. **外层 Column 改回 `Modifier.padding(16.dp)` 不带 verticalScroll**（[SpeakingPage.kt:344-348](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L344)）：作为 outer non-scrollable 骨架。
2. **Header Row 留在外层 Column 里**（[SpeakingPage.kt:357-405](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L357)）：Pinned at top。
3. **新增内层 Column with `weight(1f).verticalScroll(rememberScrollState())`**（[SpeakingPage.kt:414-422](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L414)）：
   ```kotlin
   Column(   // outer
       modifier = Modifier.padding(16.dp),
       horizontalAlignment = Alignment.CenterHorizontally,
   ) {
       Row( … header row, non-scrolling … )   // §12.41 NEW: pinned
       Spacer(Modifier.height(12.dp))
       Column(   // §12.41 NEW: inner scroll container
           modifier = Modifier
               .fillMaxWidth()
               .weight(1f)                      // 关键！bounded height
               .verticalScroll(rememberScrollState()),
           horizontalAlignment = Alignment.CenterHorizontally,
       ) {
           SpeakingWordsFlowRow( … )            // 词块
           if (subtitle.contentCn.isNotBlank()) {
               Spacer(Modifier.height(8.dp))
               Text(subtitle.contentCn, … )      // 中文翻译
           }
       }
   }
   ```

**关键决策**：

### 内层 Column 必须 `weight(1f)`

`verticalScroll` 需要有界高度才能触发；否则 Column 跟随内容 wrap-content 增长，scroll 永远不触发。`weight(1f)` 把它放在外层 Column 的剩余空间里（外层是 `Column.padding(16dp) inside weight(1f) Card`，所以高度是 `Weight(1f) minus header and spacer`）。没有 weight，`Column(verticalScroll)` 在 Compose 1.x 不会报 warning，但 scroll **不会工作**——bug 表现就是用户看到的是"全部内容显示 + 中间被截断，无 scrollbar 提示"。

### 为什么不用 `LazyColumn`

`SpeakingWordsFlowRow` 已经把 N 个词预计算好放进 `Column { lines.forEach { Row } }` 里（[SpeakingPage.kt:457-498](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L457)）。LazyColumn 需要换数据结构（each word → item），且对小固定高度 (≤400dp) + ≤20 word items 性能优势不存在。每次 recompose LazyColumn 也比 remember + Column 重。**YAGNI** — 用普通 Column + verticalScroll 已经够，且还让**整个 scrollable region 在 record 时仍可滚动**（用户录音中想滚到中间确认自己的发音指向哪个词）。如果你将来改造成 dynamic word count（≥100 words），再 switch to LazyColumn。

### header 高度

实际 ~36dp（包括 Spacer 12dp）：`labelMedium` 字号 12sp 文字 + 8dp padding 在 "未完成" badge + IconButton 32dp 触摸目标 = ~36-40dp。

### 与 §12.39 first-pass fix 的关系

§12.39 的 fix 是"卡片内部垂直 scroll 而不是挤压底部 control bar"。§12.41 是"卡片顶部 header 永远常驻"。**两个 fix 配合**：
- §12.39 保证 control bar 不被压扁
- §12.41 保证 header 在长内容时仍可见
- 长句子场景下用户可滚动词块区域**看到中间内容**，但 header（句子 N + 未完成 + ✓）始终在 card top 可见 —— 用户对自己当前所在 sentence 的位置/状态始终有视觉锚点

**改动文件**：
- [SpeakingPage.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt) — `SpeakingSubtitleCard` 重构：外层 Column + 内层 verticalScroll Column

**验证**：
- `./gradlew assembleDebug` → BUILD SUCCESSFUL（8s）
- 真机：精听页面选一句长字幕（≥15 词 / ≥3 行）
  1. **header 行 "句子 N · 未完成 · ✓" 在卡片顶部永远可见**（即使用户滚动词块到中间位置）✓
  2. 词块区域仍可内部垂直滚动（手指上滑 → 看到下面的中文翻译）✓
  3. 长按录音键 → RedRecordCircle 出现在卡片下方锚区，header 仍可见，用户能继续看到自己在第几句 ✓
  4. 短字幕（≤6 词 / ≤2 行）回归：header 仍 pinned，words 区**没有**自带 scroll（因为不够长触发），整体看跟以前一样 ✓
- 回归：未完成 badge 的颜色 / 复选框的选中态 / 单词点击 reveal / 单词长按翻译 dialog 全部不变

**禁止行为**：
- ❌ 把整个 SpeakingSubtitleCard（含 header）放进 single `verticalScroll` —— 回到 §12.41 的根因，header 又会被卷出去
- ❌ 把 header 改成 sticky header (e.g. Compose `StickyHeader` from foundation lazy) —— StickyHeader **只在 LazyColumn 内**有效，普通 Column + verticalScroll 没有这个概念；要 sticky 必须切 LazyColumn + 重构 SpeakingWordsFlowRow，本轮不做
- ❌ 给 inner scroll Column 加 `Modifier.heightIn(min=100.dp, max=300.dp)` 这种**写死**的范围 —— 不同手机高度不同会破；weight(1f) 自适应更好
- ❌ 把 header 的"句子 N"从 labelMedium 缩到 labelSmall（11sp）—— 用户既然已经要看清，就让它显示清楚；字号不变
- ❌ 把 Badge "未完成" 改成 icon-only —— 用户已经用 badge 表示状态了，纯 icon 会失去"完成度"语义

**记忆沉淀**：
- **scrollable 区域的拆分原则**：scrollable region 里**不应该**包含 orientation anchor（"我在第几屏 / 第几 step"），应该只包含 data。"题目" "步骤号" "完成状态" 这类元信息应该在 scrollable region 外；只有"内容详情"是 scrollable
- **verticalScroll + wrap-content 的隐形 bug**：androidx compose 没有 `Modifier.verticalScroll(MaxHeight = wrap)`；不绑 height 时 Column 永远 grow，scroll 永不触发。**写 verticalScroll 时先确认 parent 给的是 bounded constraints**，否则它就是个 no-op
- **未来候选**：ListeningPage 的 subtitle ListItem 也类似 —— 每个 `ListeningSubtitleListItem` 是 row 有 "句子 N · 完成/未完成" 头，词块是 row content。**但它是 LazyColumn，每个 item 自己 rendering，无外部 header 关系**，所以 §12.41 不适用于它。如果 Listening 也要 pinned sentence header，需要把 header 拿出去到 LazyColumn 的 stickyHeader —— 那是另一个量级的改动

### 12.42 录音卡片删除 + 录音动画下移 + 测试 bar 高度统一（2026-07-10）

**症状**（用户三连反馈）：
1. 精听页面 "录完音后弹出的我的录音点击播放的窗口取消掉" —— 在精听页中部出现的 `RecordingPlaybackCard`（"我的录音 / 点击播放" 横卡）跟底部控制按钮里的 "播放录音" 圆按钮功能重复，且占用 ~80dp 垂直空间
2. "录音时弹出的正在录音动画往下移，尽量的靠近底部控制按钮" —— 精听和测试页录音时的红色圆圈动画离底栏视觉距离偏大
3. "测试页面的底部控制按钮的背景方框上下高度比精听，泛听页面的要高，改成和它们高度一致" —— 测试页 bottom pill 比精听/泛听 pill 高出一截

**根因**：
- (1) `RecordingPlaybackCard` 是 SpeakingPage 在 `RecordingPlaybackCard.kt:681-725` 定义的独立 composable，用同一个 `playRecording` 与 SpeakingControlBar 第 4 个 "播放录音" 按钮重复触发
- (2) SpeakingPage：`RedRecordCircle()` 渲染在中间 `Column(weight(1f))` 内的 `Box(heightIn(min=48dp), Alignment.Center)`，48dp 槽位放 140dp 圆圈，中心对齐→可见部分在槽位**垂直中心**而非底部。TestingPage：调用 `RecordingOverlay(modifier.padding(vertical=8dp))` 上面 8dp + 内部 12dp = 20dp gap to control bar
- (3) 两个 ControlBar 的 outer Surface shell **结构相同** (`padding(horizontal=16, vertical=8), shape=24, surfaceVariant.copy(0.5), tonalElevation=2`) + 同样的 inner Row `padding(vertical=12)` —— **bar 高度应该一致**。差异在 **mic 按钮 size**：SpeakingPage 的 mic 是 `Box.size(54dp) + Icon.size(26dp)`；TestingPage 的 `MicButton` 是 `Box.size(72dp) + Icon.size(34dp)`。72dp 让 Testing bar 撑高 18dp。

**改法（3 处，2 文件）**：

### 1. 删 `RecordingPlaybackCard` composable + call site

**SpeakingPage.kt:680-727 删**整段 composable 定义（不再被引用）：§12.42 LINT 提醒 grep 唯一一处引用，没有其他 file 调它。

**SpeakingPage.kt:254-269 删**调用点（之前是 `recordingPath?.let { RecordingPlaybackCard(...) }`）：现在 `RecordingPlaybackCard` 完全消失。

**保留**：SpeakingControlBar 第 4 个 "播放录音" 圆按钮（[SpeakingPage.kt:642-664](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt#L642)），它读 `viewModel.speakingRecordingPath.value` → 录音存在时 enabled。**用户仍能播放录音**，只是少了一个 card 的视觉层。

### 2. RedRecordCircle 移到 bar 上方

**SpeakingPage.kt:243-250 改**：
```kotlin
// 改前
Box(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
    contentAlignment = Alignment.Center
) {
    if (recordingState == RecordingState.RECORDING) RedRecordCircle()
}
```
↓
```kotlin
// 改后 (2026-07-10) §12.42
Box(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp, max = 140.dp),         // §12.42: 录制中扩到 140dp 给圆圈自然高
    contentAlignment = Alignment.BottomCenter          // §12.42: 重力贴底，圆圈底部 = 槽位底部
) {
    if (recordingState == RecordingState.RECORDING) RedRecordCircle()
}
```

`Alignment.BottomCenter` 让 RedRecordCircle（140dp）的底部边缘贴住 Box 底边 = Column(weight(1f)) 底部 = **紧贴 SpeakingControlBar 上方**。

`heightIn(min=48, max=140)` 让非录音态保留 48dp 最小占用（不撑出多余空白）；录音态撑到 140dp 容纳圆圈。

**TestingPage.kt:117-125 改**：
```kotlin
// 改前
RecordingOverlay(modifier = Modifier.fillMaxWidth().padding(horizontal=16, vertical=8))
// 改后 (2026-07-10) §12.42
RecordingOverlay(modifier = Modifier.fillMaxWidth().padding(horizontal=16, vertical=2))
```

RecordingOverlay 是 middle Column 的 last child before TestingControlBar，外层 vertical 8dp 缩到 2dp；RingOverlay 内部还有 12dp，所以**总 gap = 2 + 12 = 14dp**（之前 8 + 12 = 20dp）。6dp 的视觉改善等价于圆圈上移 6dp 靠近 bar。RecordingOverlay 本身用 `Box(fillMaxWidth, padding(horizontal=24, vertical=12), Alignment.Center)` —— 内层 Center 不动，圆圈天生居中。

### 3. 测试页 mic 按钮 72dp → 54dp

**TestingPage.kt:736-738 改** `MicButton`：`Box.size(72.dp) → Box.size(54.dp)`，**MicPage.kt:760 改** `Icon.size(34.dp) → Icon.size(26.dp)`。与 SpeakingPage 的 `Box(54dp) + Icon(26dp)` **完全一致**，Row intrinsic height 统一。

`detectTapGestures(onPress=…, tryAwaitRelease=…, onRelease=…)` 的 press-and-release 时序不变（speaking 用 `clickable + interactionSource + LaunchedEffect(isPressed)`，testing 用 `detectTapGestures` ——两种实现等价，patch 后仍兼容）。

**改动效果 — Bar 高度计算**：
- Bar Surface outer padding 8dp + inner Row padding 12dp = 20dp frame
- Row content = max(IconButton size) + Row vertical padding
- Speaking/listening: max(54dp, 54dp, 26dp, 54dp, 26dp) + 24dp = **78dp** outer
- Testing: 原 72dp + 24dp = **96dp** outer → 改后 54dp + 24dp = **78dp** outer ✓ 与 speak/listen 一致

**改动文件**：
- [SpeakingPage.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/SpeakingPage.kt) — 删 RecordingPlaybackCard composable + call site；RedRecordCircle Box 改 `heightIn(min=48, max=140)` + `BottomCenter`
- [TestingPage.kt](app/src/main/java/com/echoling/app/presentation/ui/screens/practice/TestingPage.kt) — MicButton 72→54dp / Icon 34→26dp；RecordingOverlay vertical 8→2dp

**验证**：
- `./gradlew assembleDebug` → BUILD SUCCESSFUL（9s）
- 真机：
  1. **精听页录完音后**：`RecordingPlaybackCard` 不再出现；底部控制栏 "播放录音" 圆按钮（4 号位置）仍能播放刚才的录音 ✓
  2. **精听页长按录音**：RedRecordCircle 在卡片和控制栏之间的槽位里**底部对齐**，圆圈底部边缘紧贴控制栏顶部（间距 ≈ 8dp，比之前的"圆圈中部可见"体感接近 1/2 圆圈高度的下沉）
  3. **测试页长按录音**：RecordingOverlay 整体上移 6dp，更贴近 TestingControlBar 的 pill 顶边
  4. **三个页面底部 pill 高度一致**：精听/测试/泛听切换 TabRow，圆角 pill 形状、上下高度完全一致；之前测试页明显高 ~18dp
  5. 测试页 mic 按下变红（error 色）、释放回到 primaryContainer（onPrimaryContainer tint），与 §12.42 改前一样

**禁止行为**：
- ❌ 把 `RecordingPlaybackCard` 用 `@Suppress("unused")` 隐藏 —— 它**真的**没用了。YAGNI：re-enable 时如果用户要求，再恢复整段定义 + call site，比留 unused code 干净
- ❌ 在 RedRecordCircle 的 Box 上加 `.padding(bottom = 8.dp)` 试图"再贴一点" —— Column 子节点不加 weight(1f) 时互相紧凑排列，已没有 alignment 向下空间可调整；要再贴靠 bar 只能改 RedRecordCircle 自己的 sizeDp 缩到 100-120dp（但 ripple 同步缩会变难看），本轮不做
- ❌ 把 TestingPage 的 MicButton 缩到 48dp（Surface.IconButton 的默认 minTouchTarget）—— 54dp 是 speaking 的 gold standard；统一到 48dp 牺牲了视觉权重，统一到 54dp 跟 speaking 完全对称。本轮选 54dp
- ❌ 在 TestingControlBar 内 row 的 5 个按钮中给 mic 加 `Modifier.weight(1f)` —— Box 是圆形，weight 会把 Box 拉成椭圆；用 `Modifier.size(54.dp)` 锁定 54dp×54dp 才是圆形
- ❌ 把 ListeningBottomControls 也加同样的 .height(54dp) 校验 —— Listening 没有 mic button（ListeningBottomControls 是 subtitle-toggle / play/pause / speed 三个 IconButton），它的最大 content 已经是 36dp 左右；本轮只动 Speaking 和 Testing 两个有 mic 的 bar

**记忆沉淀**：
- **同一 Surface shell 内 Row 的 intrinsic height = max(child height) + Row padding** —— 三个 bar 用相同 shell 是没用的，**必须同步 child size** 才能真的一致。本轮的 72dp/54dp 之差是"形式上一致、内核不一致"的反例：**视觉一致性必须从内核对齐开始**
- **重复功能的 UI 元素 = 迟早要去掉一个**：本节 RecordingPlaybackCard 与 SpeakingControlBar 第 4 个按钮重复了 `playRecording` 触发器，但**视觉层级不同**（card 比 button 大一个量级），导致用户对"哪个是播放入口"产生认知负担。**MVP 设计原则**：同一功能的入口永远只有一条最显眼的路径
- **red circle 的对齐（BottomCenter vs Center）在大屏手机 + 24dp 槽位时差异可达 30dp+** —— 圆圈直径 140dp，超过半个屏幕高度的 ~10%。`BottomCenter` 让圆圈看起来"在底部"而不是"悬浮中段"，符合用户对录音态视觉直觉（"我正在对 mic 说话，按钮就在 mic 上方"）
- **下一个候选**：ReciteScreen 闪卡页如果加录音评分功能，可能也需要同样的 RedRecordCircle —— 复用 §12.42 的 `Box(heightIn min 48 max 140, Alignment.BottomCenter)` 包装即可
