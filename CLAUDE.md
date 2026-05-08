# CLAUDE.md - 英语学习APP（Echo Ling）Claude Code 执行手册

> 文档定位：Claude Code 开发行为唯一约束准则 | 配套文件：同根目录 `plan.md` | 生效优先级：本文件为开发执行最高优先级规则，与 `plan.md` 冲突时以 `plan.md` 为准
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

---

## 二、最高优先级执行准则

1. **先读再写原则**：每次执行开发任务前，必须先读取本文件、`plan.md` 及当前项目结构
2. **编译优先原则**：任何代码修改后必须执行 `assembleDebug` 检查编译
3. **架构合规原则**：严格遵循 Clean Architecture 分层规则，禁止跨层调用
4. **MVP优先原则**：按 plan.md 定义的功能优先级开发
5. **离线优先原则**：核心功能必须优先保障离线可用
6. **安全合规原则**：严格遵循安卓官方权限规范

---

## 三、项目目录结构

```
com.echoling.app
├── EchoLingApplication.kt        # 应用入口
├── presentation/                  # UI 层
│   ├── MainActivity.kt
│   ├── ui/
│   │   ├── screens/             # 页面
│   │   ├── components/          # 通用组件
│   │   ├── navigation/          # 导航
│   │   └── theme/              # 主题
│   └── viewmodel/              # ViewModel
├── domain/                       # 域层
│   ├── model/                  # 数据模型
│   └── repository/             # 仓库接口
├── data/                         # 数据层
│   ├── repository/             # 仓库实现
│   ├── local/
│   │   ├── db/                # Room 数据库
│   │   └── datastore/          # DataStore
│   └── datasource/            # 数据源
├── player/                      # 播放引擎模块
├── speech/                      # 语音处理模块
├── di/                          # Hilt 依赖注入
└── utils/                      # 工具类
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

### 5.3 架构分层规范
1. **UI层**：仅负责 UI 渲染与用户事件转发，无业务逻辑
2. **ViewModel层**：持有 UI 状态，调用 UseCase 执行业务逻辑
3. **Domain层**：UseCase 仅封装单一业务逻辑，无平台依赖
4. **Data层**：Repository 统一数据出口，Dao 仅负责数据库操作
5. **核心模块层**：播放引擎、语音处理封装为独立模块，与业务解耦

### 5.4 Hilt 依赖注入
- 所有 ViewModel、Repository、UseCase 必须通过 Hilt 注入
- 禁止手动 new 创建实例
- Module 必须添加 @InstallIn 注解

### 5.5 Room 数据库
- Entity 必须定义主键、表名
- Dao 接口必须使用 suspend 函数/Flow 返回数据
- 数据库必须为单例，通过 Hilt 注入

### 5.6 Kotlin Coroutines + Flow
- 耗时操作必须使用协程，指定正确调度器
- IO 调度器处理数据库/文件操作
- Flow 必须正确处理生命周期

---

## 六、绝对禁止事项

1. 禁止引入任何 Java 代码
2. 禁止使用 XML 布局、View 系统
3. 禁止跨层调用、职责越界
4. 禁止主线程执行数据库、文件、网络等耗时操作
5. 禁止在 ViewModel、UseCase 中持有 Context、Activity 引用
6. 禁止生成无法正常编译的代码
7. 禁止硬编码颜色、字体、尺寸、字符串、路由地址

---

## 七、配套文件联动规则

1. 本文件与同根目录 `plan.md` 强绑定，所有开发任务必须同时遵循两个文件
2. 本文件与 `plan.md` 冲突时，以 `plan.md` 为准
3. 每次启动新的开发任务，必须先重新读取本文件与 `plan.md`
