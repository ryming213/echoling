# 单词本 UI 美化（VocabularyScreen Beautification）— Design

> 用户（2026-07-03）反馈：「单词本中保存的单词，看起来有点乱，有 UI 美化的页面吗？帮我对该页进行美化，要求：单词本中的单词条例清晰，方便分辨和阅读。另外把单词最右边的 勾 按钮及其相应的功能取消掉」

## Context

当前 VocabularyScreen（`app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt`）每行右侧有 3 个图标（🔊 朗读 / ✓ 掌握 / 🗑 删除），与单词 + 音标争夺水平空间，视觉密度过高。整行没有视觉呼吸，行间用 2dp 间距挤压，连排时没有清晰的分隔线。

且 ✓ 勾勾按钮对应的「已掌握」状态在域层（ToggleWordMasteredUseCase）和 ViewModel 中都存在，但 UI 上是用户使用的唯一入口。用户已经明确要把这个功能和按钮一起去掉。

## Goals

1. **清晰可读**：每行视觉元素更少，留白更舒服，扫读单词更快。
2. **去掉 ✓ 按钮和对应功能**（含顶栏「全部 / 未掌握」筛选下拉，因筛选没有新数据源已无意义）。
3. **删除操作更安全**：左滑 → 露出红色「删除」按钮 → 点击才真删（非滑到底直接删）。
4. **保持所有现有数据**：Room schema、Word 模型字段（`isMastered` 等）不动；老数据中已是 `isMastered=true` 的词仍正常显示。

## Non-Goals

- 不改 Word 模型或 Room schema
- 不改记单词流程（ReciteScreen）或其他 tab
- 不重做空状态文案（当前「单词本还是空的…」已经合理）
- 不动主题（Theme.kt）或全局配色

## Final Layout（去掉头像 + 细灰线分隔）

```
abandon          /əˈbændən/             🔊
vt. 抛弃；放弃；遗弃
He had to abandon his car in the snow.
─────────────────────────────────────────  ← 0.5dp HorizontalDivider
apple            /ˈæpəl/                 🔊
n. 苹果
...
```

### Per-row 内部元素

| 元素 | 设计 |
|---|---|
| **单词** | 18sp Bold, `colorScheme.onSurface`，最多 1 行省略 |
| **音标** | 单词右侧 14sp Italic, `colorScheme.onSurfaceVariant`（中间 Spacer 8dp） |
| **🔊 喇叭** | 右侧对齐 32dp IconButton, `colorScheme.onSurfaceVariant`，整行可点（clickable 触发朗读） |
| **POS 标签** | 灰色小圆角 chip（"vt." / "n." 等），前缀在翻译前 |
| **翻译** | bodyMedium, `colorScheme.primary`，最多 2 行省略 |
| **例句** | bodySmall, `colorScheme.onSurfaceVariant`，1 行省略 |
| **行间分隔** | `HorizontalDivider(thickness = 0.5.dp, color = onSurfaceVariant.copy(alpha = 0.2f))` |
| **左滑删除** | M3 `SwipeToDismissBox`，`EndToStart` 方向，露出红色「删除」背景 + 按钮，点击才真删 |

### Container

- 不用 Card 容器
- 整页 background = `colorScheme.background`
- 行垂直 padding 12dp，水平 padding 16dp
- 列表最后一项不画分隔线（用 `if (index != lastIndex)` 判断）

## 移除项

| 项 | 位置 | 处理 |
|---|---|---|
| **✓ IconButton** | VocabularyScreen.kt `WordRow`（旧 298-311 行附近） | 删 |
| **`onToggleMastered` 回调参数** | `WordRow` + `WordList` | 删 |
| **顶栏 FilterList 按钮** | VocabularyScreen.kt PageHeader `actions` slot | 删 |
| **`showFilterMenu` 状态 + DropdownMenu** | VocabularyScreen.kt | 删 |
| **`toggleMastered()` 方法** | VocabularyViewModel.kt:56-60 | 删 |
| **`setShowMastered()` 方法** | VocabularyViewModel.kt:99-107 | 删 |
| **`showMastered` UI 状态字段** | `VocabularyUiState`（line 20） | 删 |
| **`ToggleWordMasteredUseCase` 注入** | VocabularyViewModel.kt:33 + import | 删 |
| **`ToggleWordMasteredUseCase.kt` 文件** | `domain/usecase/` | 删（已无 caller） |
| **Word.isMastered 字段** | `domain/model/Word.kt` | **保留**（Room schema 兼容，DB 字段不删） |

## 保留项

- ✅ `Word.isMastered` 数据字段（不删 DB 字段，向后兼容老数据）
- ✅ `ViewModel.deleteWord` / `pronounce`
- ✅ `getAllWords`（VocabularyViewModel 主用此，删 filter 后回到原始 init 行为）
- ✅ TTS 不可用 snackbar 提示逻辑
- ✅ 空状态文案「单词本还是空的…」
- ✅ PageHeader 两行标题（"单词本" + "— Review words daily —"）
- ✅ PageHeader 顶栏返回按钮（`onNavigateBack` 存在时显示）

## 文件改动清单

### 修改

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/echoling/app/presentation/ui/screens/vocabulary/VocabularyScreen.kt` | 重写 `WordRow`（去 ✓ 按钮、改 SwipeToDismissBox、加 HorizontalDivider），删 `WordList` 的 `onToggleMastered` 参数，删 `VocabularyScreen` 顶栏 FilterList + DropdownMenu，删相关 import |
| `app/src/main/java/com/echoling/app/presentation/viewmodel/VocabularyViewModel.kt` | 删 `showMastered` 字段、`toggleMastered()`、`setShowMastered()`、`ToggleWordMasteredUseCase` 注入 + import |

### 删除

| 文件 | 原因 |
|---|---|
| `app/src/main/java/com/echoling/app/domain/usecase/ToggleWordMasteredUseCase.kt` | 无 caller（VocabularyViewModel 是唯一使用者，已删注入） |

### 不动

- `Word.kt`（isMastered 字段保留）
- `WordDao.kt` / `WordRepository*.kt`（isMastered 列、getUnmasteredWords 仍被 GetStatisticsUseCase 使用）
- `CLAUDE.md`（章节 §12.22 关于行布局的注释将在实现时同步更新，但不在本 spec 范围）

## 关键技术决策

### 用 M3 `SwipeToDismissBox`

M3 1.1.2 已包含 `androidx.compose.material3.SwipeToDismissBox`。使用 `SwipeToDismissBoxValue.EndToStart` 监听滑动方向。

**关键陷阱**：默认值会「滑到底自动 dismiss」。我们要的是「滑到中间停住露出按钮」——需要在 `confirmValueChange` 里：

```kotlin
confirmValueChange = { value ->
    if (value == SwipeToDismissBoxValue.EndToStart) {
        // 显示「删除」按钮；不真正 dismiss；用户需点按钮才删
        showConfirm = true
        false  // 不让 box 消失
    } else {
        true
    }
}
```

点击「删除」按钮时：调用 `onDelete()` 并把 `dismissState.snapTo(SwipeToDismissBoxValue.Settled)` 重置回原位。

### 整行 clickable + 喇叭按钮共存

`Modifier.clickable` 应用在 Row 整体上，🔊 IconButton 嵌套其中。`clickable` 不会拦截子 IconButton 的点击事件（M3 默认行为），但 ripple 会扩散到整行。

### Divider 末项处理

在 `LazyColumn` 的 `items` 内用 `if (index != items.lastIndex)` 包住 `HorizontalDivider`，避免最后一项底部也有一条线。

## Verification

### 步骤 1：编译

```bash
cd c:/Users/MING/myagent/echoling
./gradlew assembleDebug
```

期望：`BUILD SUCCESSFUL`，无未使用 import 警告。

### 步骤 2：APK 装机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 3：真机验证

1. **进 app → 单词本 tab**：每行无左侧头像；单词 + 音标 + 🔊 一行；POS + 翻译一行；例句（若有）一行；行底 0.5dp 灰线分隔。
2. **点 🔊**：TTS 朗读该词；如未装 TTS 引擎，弹 snackbar。
3. **点整行（任意非图标位置）**：也应朗读。
4. **左滑一行**：露出红色背景 + 「删除」按钮。
5. **点「删除」按钮**：词从列表消失。
6. **不点按钮往回滑**：行回到原位。
7. **空状态**：单词本为空时显示「单词本还是空的…」。
8. **顶栏**：FilterList 图标已消失；只有标题 + 返回。
9. **回归**：进记单词 tab / 学习 tab，确认无 crash。

### 步骤 4：logcat 扫描

```bash
adb logcat -d | grep -iE "FATAL|echoling" | tail -50
```

期望：无 FATAL，无 VocabularyViewModel 相关报错。

## Risks

| 风险 | 缓解 |
|---|---|
| `SwipeToDismissBox` 滑到底仍会触发 dismiss（默认行为） | `confirmValueChange` 拦截 EndToStart，弹自定义「删除」按钮，点击才真正删 + 重置 state |
| `clickable` 在 Row + IconButton 共存时图标点击被吞 | M3 默认：子 `IconButton` 的 `onClick` 比父 `clickable` 优先；测试可验证 |
| 删 `ToggleWordMasteredUseCase` 误删被其他模块引用 | 已 `grep` 验证全工程仅 VocabularyViewModel 引用，删除安全 |
| 老数据中 `isMastered=true` 的词仍正常显示 | Word.isMastered 字段保留（仅 UI 不再 toggle） |
| 18sp 单词 + 14sp 音标同行，长音标截断 | 单词 `weight(1f)` + 音标 `weight(1f, fill=false)` 不冲突；用户可滚动屏幕浏览 |

## Open follow-ups (NOT in this spec)

- 单词按字母 / 时间排序（用户当前没要）
- 单词搜索（用户当前没要）
- 批量操作（用户当前没要）
