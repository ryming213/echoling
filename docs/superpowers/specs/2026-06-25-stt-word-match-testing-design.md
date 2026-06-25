# 跟读测试改 STT 单词对比 — 设计文档

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「跟读练习」测试 Tab 的"发音评分"换成"按住录音 → STT 转写 → 编辑 → 单词对比通过/不通过"的简化流程，删除所有 DTW 评分相关代码。

**Architecture:** 用系统 `SpeechRecognizer` 边录边转文字，松开后用户可编辑/手动输入；提交时与原句做严格顺序匹配（normalize 后逐位置 ==）。录音不再落盘，纯 STT + 文字交互。

**Tech Stack:** Android `SpeechRecognizer`（platform API）、Jetpack Compose、StateFlow、Coroutines

---

## Context — 为什么要改

跟读练习的「测试」Tab 当前用 DTW 能量包络做发音评分（`PronunciationGrader`），用户实测反馈两个问题：
1. **DTW 边界 bug**：上一版刚修过（AIOOBE at `DtwAligner.kt:67`），评分管线脆弱
2. **评分反直觉**：用户录很少也只给 9 分，怀疑是 bug；实际是设计如此（fluency/completeness 拖分）

新的方案**只测"用户说的词和原句是否一致"**，不测"发音好不好"，用户能编辑 STT 结果应对识别错误，流程更直观、依赖更少。

## Core decisions

1. **STT 引擎**：系统 `SpeechRecognizer.createSpeechRecognizer(context)` + `RecognitionListener`
   - 设备不支持 / 识别失败 → 兜底进"手动输入"模式，永远不卡死
2. **单词对比**：`WordMatcher` 严格顺序匹配
   - normalize = `lowercase` + 去掉非 `a-z0-9\s'` 的字符 + 按空白分词
   - 通过条件：`orig.size == trans.size && orig.zip(trans).all { it == it }`
   - 不做词形归一、不做同义词、不做模糊匹配
3. **录音**：不落盘到 m4a，STT 本身就是"录音→文字"
4. **旧代码**：完全删除 11 个文件（详见 §6）
5. **新增依赖**：无（SpeechRecognizer 是 Android platform）

---

## §1 架构 & 数据流

### 1.1 组件

| 组件 | 职责 | 文件 |
|---|---|---|
| `SttRecognizer` | `@Singleton` 包装 `SpeechRecognizer` + `RecognitionListener`，对外暴露 `Flow<SttEvent>` | `speech/SttRecognizer.kt` |
| `WordMatcher` | 纯函数 `match(original, transcribed) → MatchResult` | `speech/WordMatcher.kt` |
| `PracticeViewModel` | 持有 `_sttTestState: MutableStateFlow<SttTestState>`，调 `SttRecognizer` + `WordMatcher`（`SttTestState` sealed class 放在 ViewModel 同文件） | `viewmodel/PracticeViewModel.kt` |
| `SttTestState` sealed class | 测试 Tab 的录音-转写-对比状态机 | 同 `PracticeViewModel.kt` |
| `TestingPage` | UI：长按录音 / 转写编辑 / 结果展示 | `ui/screens/practice/TestingPage.kt` |
| `RecordingOverlay` | 录音中浮层（脉冲动画 + 计时 + 振幅条） | `ui/screens/practice/components/RecordingOverlay.kt` |
| `TranscriptionEditor` | 转写编辑卡（文本框 + 提交/重录按钮） | `ui/screens/practice/components/TranscriptionEditor.kt` |
| `TestResultCard` | 通过/不通过结果卡（错词高亮） | `ui/screens/practice/components/TestResultCard.kt` |

### 1.2 数据流

```
[User: 长按 mic 按钮 ≥ 200ms]
   ↓
TestingPage: onPress
   ↓
viewModel.startStt()
   ├─ SttRecognizer.start(language="en-US")
   ├─ _sttTestState.value = Listening
   └─ startElapsedTimerJob()    // 每 100ms 更新 elapsedMs
   ↓
[User: 松手]
   ↓
TestingPage: onRelease / onCancel
   ↓
viewModel.stopStt()
   ├─ SttRecognizer.stop()
   ├─ 等待 onResults(String) → _sttTestState.value = Transcribed(text)
   ├─ 或 onError → _sttTestState.value = Transcribed("")   // 兜底手动输入
   └─ stopElapsedTimerJob()
   ↓
[User: 编辑 / 不编辑]
   ↓
[User: 点 "提交"]
   ↓
viewModel.submit()
   ├─ val result = WordMatcher.match(currentTest.contentEn, transcribed)
   ├─ if (result.passed) {
   │     _sttTestState.value = Passed(text)
   │     markSentenceTested(id, true)
   │  } else {
   │     _sttTestState.value = Failed(text, original, reason)
   │  }
   ↓
[UI 展示 TestResultCard / 提示用户可重录或下一题]
```

### 1.3 状态机：`SttTestState`

```kotlin
sealed class SttTestState {
    data object Idle : SttTestState()
    data class Listening(val elapsedMs: Long = 0L) : SttTestState()
    data class Transcribed(val text: String) : SttTestState()
    data class Passed(val text: String) : SttTestState()
    data class Failed(
        val transcribed: String,
        val original: String,
        val origWords: List<String>,
        val transWords: List<String>,
        val reason: String
    ) : SttTestState()
}
```

---

## §2 关键 API 设计

### 2.1 `SttRecognizer`

```kotlin
@Singleton
class SttRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class SttEvent {
        data class PartialResults(val text: String) : SttEvent()  // v1 不暴露
        data class Results(val text: String) : SttEvent()
        data class Error(val code: Int, val message: String) : SttEvent()
    }

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SttEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var listener: RecognitionListener? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String = "en-US") {
        if (recognizer != null) stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            listener = makeListener()
            setRecognitionListener(listener)
            startListening(intent)
        }
    }

    fun stop() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        listener = null
    }

    private fun makeListener() = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            _events.tryEmit(SttEvent.Results(text))
        }
        override fun onError(error: Int) {
            _events.tryEmit(SttEvent.Error(error, "Error code: $error"))
        }
        // 其他回调：onReadyForSpeech / onBeginningOfSpeech / onRmsChanged / onEndOfSpeech / onPartialResults / onEvent
        // v1 暂不处理；onRmsChanged 留接口供 v2 波形动画用
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { /* v2 用：v1 用随机振幅占位 */ }
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partial: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
```

### 2.2 `WordMatcher`

```kotlin
object WordMatcher {
    data class MatchResult(
        val passed: Boolean,
        val origWords: List<String>,
        val transWords: List<String>,
        val reason: String  // "ok" | "empty_transcription" | "missing_word" | "extra_word" | "wrong_word"
    )

    private val NORMALIZE_REGEX = Regex("[^a-z0-9\\s']")

    fun match(original: String, transcribed: String): MatchResult {
        val orig = normalize(original)
        val trans = normalize(transcribed)
        if (trans.isEmpty()) return MatchResult(false, orig, trans, "empty_transcription")
        if (orig.size != trans.size) {
            val reason = if (trans.size > orig.size) "extra_word" else "missing_word"
            return MatchResult(false, orig, trans, reason)
        }
        for (i in orig.indices) {
            if (orig[i] != trans[i]) return MatchResult(false, orig, trans, "wrong_word")
        }
        return MatchResult(true, orig, trans, "ok")
    }

    private fun normalize(s: String): List<String> =
        s.lowercase()
            .replace(NORMALIZE_REGEX, " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
}
```

**为什么不过滤 `'`**：英文撇号在 "don't / it's / I'll" 里是单词一部分，去掉会破坏语义。如果用户写 "dont" 不会被识别为 "don't"——这正是设计预期（用户编辑时改回）。

---

## §3 UI 设计

### 3.1 `TestingControlBar` 改造

**当前**：`[上一题] [播放] [录音] [标记完成]` — 录音是 IconButton 短按。

**改造后**：

```kotlin
@Composable
private fun TestingControlBar(
    currentIndex: Int,
    totalCount: Int,
    isTested: Boolean,
    isSttListening: Boolean,
    onPrevious: () -> Unit,
    onPlayAudio: () -> Unit,
    onPressMic: () -> Unit,     // 改：长按 onPress
    onReleaseMic: () -> Unit,   // 新：长按 onRelease
    onMarkTested: () -> Unit,
) {
    // 上一题 / 播放原音 保持不变
    // 录音按钮改为 Box + pointerInput.detectTapGestures
    Box(
        modifier = Modifier
            .size(54.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressMic()
                        tryAwaitRelease()  // 等松手
                        onReleaseMic()
                    }
                )
            }
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSttListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxSize()
                .scale(if (isSttListening) 1.1f else 1f)  // 长按时放大
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = if (isSttListening) "正在录音，松开结束" else "按住录音",
                modifier = Modifier.size(26.dp).align(Alignment.Center),
                tint = if (isSttListening) MaterialTheme.colorScheme.onError
                       else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    // 下一题 / 标记完成 保持不变
}
```

**外层 `TestingPage` 在控制条之上叠加 3 个组件**（按 `sttTestState` 切换）：
- `Idle`：什么都不显示
- `Listening`：覆盖 `RecordingOverlay`（带脉冲动画 + 计时）
- `Transcribed` / `Passed` / `Failed`：覆盖 `TranscriptionEditor` 或 `TestResultCard`

### 3.2 `RecordingOverlay` 设计

**位置**：TestingSubtitleCard 下方，控制条上方（覆盖在中间区域）

```kotlin
@Composable
fun RecordingOverlay(
    elapsedMs: Long,
    amplitudeBars: List<Float>,  // 5-7 个 0..1 浮点，驱动条形图
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：红点 + 文字
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingRedDot()  // 无限脉冲动画（scale 1f ↔ 1.3f, 1s）
                Spacer(Modifier.width(8.dp))
                Text("正在录音… 松开结束", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(12.dp))
            // 中间：5 条振幅条
            AmplitudeBars(values = amplitudeBars)  // 高度 8dp ↔ 32dp
            Spacer(Modifier.height(8.dp))
            // 底部：计时 + 取消按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${elapsedMs / 1000.0}s", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}
```

**AmplitudeBars 实现**：5 个竖向 Box，高度 = `8dp + (value * 24dp).dp`，每 100ms 用新随机值（v1 占位）。v2 接 `SttRecognizer.onRmsChanged` 真实振幅。

**PulsingRedDot 实现**：
```kotlin
@Composable
fun PulsingRedDot() {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Red)
    )
}
```

### 3.3 `TranscriptionEditor` 设计

```kotlin
@Composable
fun TranscriptionEditor(
    initialText: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(initialText) }
    // 外部传 initialText 变化时（如重新录音）同步
    LaunchedEffect(initialText) { text = initialText }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("你说的是：", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { onTextChange(it); text = it },
                placeholder = { Text("未识别到语音，请手动输入") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                minLines = 3
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("重录")
                }
                Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("提交对比")
                }
            }
        }
    }
}
```

### 3.4 `TestResultCard` 设计

```kotlin
@Composable
fun TestResultCard(
    state: SttTestState,  // Passed | Failed
    originalEn: String,
    onNextItem: () -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, title) = when (state) {
        is SttTestState.Passed -> Triple(
            Color(0xFFE8F5E9),  // 浅绿
            Color(0xFF1B5E20),  // 深绿
            "✓ 通过！"
        )
        is SttTestState.Failed -> Triple(
            Color(0xFFFFEBEE),  // 浅红
            Color(0xFFB71C1C),  // 深红
            "✗ 不通过"
        )
        else -> return
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = contentColor)
            Spacer(Modifier.height(8.dp))
            Text("原句：", style = MaterialTheme.typography.labelMedium, color = contentColor)
            Text(originalEn, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            if (state is SttTestState.Failed) {
                Text("你说：", style = MaterialTheme.typography.labelMedium, color = contentColor)
                WordChipsRow(
                    origWords = state.origWords,
                    transWords = state.transWords,
                    contentColor = contentColor
                )
                Spacer(Modifier.height(8.dp))
                Text(failureReasonText(state.reason), color = contentColor)
            } else if (state is SttTestState.Passed) {
                Text(state.text, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) { Text("重录") }
                if (state is SttTestState.Passed) {
                    Button(onClick = onNextItem) {
                        Text("下一题")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChipsRow(
    origWords: List<String>,
    transWords: List<String>,
    contentColor: Color
) {
    // 按位置对齐，max(orig.size, trans.size) 个 chip
    val maxLen = maxOf(origWords.size, transWords.size)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until maxLen) {
            val orig = origWords.getOrNull(i)
            val trans = transWords.getOrNull(i)
            val (display, bg) = when {
                trans == null -> "[$orig]" to Color(0xFFFFCDD2)  // 缺失：红
                orig == null -> "[$trans]" to Color(0xFFFFF9C4)  // 多余：黄
                orig == trans -> orig to Color(0xFFC8E6C9)        // 一致：绿
                else -> "$orig/$trans" to Color(0xFFFFCDD2)        // 错：红
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg
            ) {
                Text(display, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
```

---

## §4 `PracticeViewModel` 改动

### 4.1 删

```kotlin
// 删注入
- private val pronunciationGrader: PronunciationGrader
- private val updateSentenceReadScoreUseCase: UpdateSentenceReadScoreUseCase

// 删 sealed class
- sealed class GradeState { ... }   // 整个文件
- private val _gradeState
- val gradeState
- private var gradeJob: Job?

// 删方法
- fun startRecordingForGrading()
- fun stopAndGrade()
- fun cancelGrading()
- private fun cancelGradingInternal()
- private fun currentGradingSentence()
```

### 4.2 加

```kotlin
// 注入
+ private val sttRecognizer: SttRecognizer

// sealed class（放 ViewModel 同文件）
sealed class SttTestState {
    data object Idle : SttTestState()
    data class Listening(val elapsedMs: Long = 0L) : SttTestState()
    data class Transcribed(val text: String) : SttTestState()
    data class Passed(val text: String) : SttTestState()
    data class Failed(
        val transcribed: String,
        val original: String,
        val origWords: List<String>,
        val transWords: List<String>,
        val reason: String
    ) : SttTestState()
}

private val _sttTestState = MutableStateFlow<SttTestState>(SttTestState.Idle)
val sttTestState: StateFlow<SttTestState> = _sttTestState.asStateFlow()

private val _sttAmplitudeBars = MutableStateFlow(List(5) { 0.4f })
val sttAmplitudeBars: StateFlow<List<Float>> = _sttAmplitudeBars.asStateFlow()

private var sttElapsedJob: Job? = null
private var sttEventCollectionJob: Job? = null
```

### 4.3 新方法

```kotlin
/** Start STT recording. Called by UI onPress of mic button. */
fun startStt() {
    if (_sttTestState.value is SttTestState.Listening) return
    if (!sttRecognizer.isAvailable()) {
        // 兜底：直接进 Transcribed 空文本，让用户手动输入
        _sttTestState.value = SttTestState.Transcribed("")
        _sttAmplitudeBars.value = List(5) { 0.4f }
        return
    }
    _sttTestState.value = SttTestState.Listening(0L)
    // 先启动 collection job（避免 race：start() 后 onResults 早于 collector 启动）
    // SharedFlow 的 extraBufferCapacity=4 也会兜底缓存。
    sttEventCollectionJob = viewModelScope.launch {
        sttRecognizer.events.collect { event ->
            when (event) {
                is SttRecognizer.SttEvent.Results -> onSttResults(event.text)
                is SttRecognizer.SttEvent.Error -> onSttResults("")  // 兜底
                is SttRecognizer.SttEvent.PartialResults -> { /* v1 ignore */ }
            }
        }
    }
    sttRecognizer.start(language = "en-US")
    startSttTimers()
}

/** Stop STT. Called by UI onRelease of mic button. */
fun stopStt() {
    if (_sttTestState.value !is SttTestState.Listening) return
    sttRecognizer.stop()
    stopSttTimers()
    // 状态由 onSttResults() 切到 Transcribed
}

private fun onSttResults(text: String) {
    stopSttTimers()
    _sttTestState.value = SttTestState.Transcribed(text)
}

private fun startSttTimers() {
    val startTime = System.currentTimeMillis()
    sttElapsedJob = viewModelScope.launch {
        while (isActive && _sttTestState.value is SttTestState.Listening) {
            val elapsed = System.currentTimeMillis() - startTime
            (_sttTestState.value as? SttTestState.Listening)?.let {
                _sttTestState.value = it.copy(elapsedMs = elapsed)
            }
            // 随机振幅（v1 占位）
            _sttAmplitudeBars.value = List(5) { (Math.random().toFloat() * 0.7f + 0.3f) }
            delay(100)
        }
    }
}

private fun stopSttTimers() {
    sttElapsedJob?.cancel()
    sttElapsedJob = null
    sttEventCollectionJob?.cancel()
    sttEventCollectionJob = null
}

/** Cancel STT (user swipes out or taps "取消"). */
fun cancelStt() {
    if (_sttTestState.value is SttTestState.Listening) {
        sttRecognizer.stop()
    }
    stopSttTimers()
    _sttTestState.value = SttTestState.Idle
    _sttAmplitudeBars.value = List(5) { 0.4f }
}

/** User submitted the transcription for matching. */
fun submitTranscription(text: String) {
    val currentTest = _testState.value.testItems.getOrNull(_testState.value.currentTestIndex)
        ?: run {
            _sttTestState.value = SttTestState.Failed(
                transcribed = text,
                original = "",
                origWords = emptyList(),
                transWords = emptyList(),
                reason = "no_test_item"
            )
            return
        }
    val result = WordMatcher.match(currentTest.contentEn, text)
    if (result.passed) {
        _sttTestState.value = SttTestState.Passed(text)
        markSentenceTested(currentTest.index, true)
    } else {
        _sttTestState.value = SttTestState.Failed(
            transcribed = text,
            original = currentTest.contentEn,
            origWords = result.origWords,
            transWords = result.transWords,
            reason = result.reason
        )
    }
}

/** Reset to Idle for next attempt. */
fun resetStt() {
    _sttTestState.value = SttTestState.Idle
    _sttAmplitudeBars.value = List(5) { 0.4f }
}
```

### 4.4 `onCleared` 清理

```kotlin
override fun onCleared() {
    super.onCleared()
    // ... 现有清理 ...
    sttRecognizer.stop()  // 新增
    sttElapsedJob?.cancel()
    sttEventCollectionJob?.cancel()
}
```

---

## §5 `TestingPage` 改动

### 5.1 删

```kotlin
- val permissionLauncher = rememberLauncherForActivityResult(...)   // 整个 block
- LaunchedEffect(gradeState) { ... }   // 整个 block
- ScoreCard(...)  // 整个 if block
- val successState = (gradeState as? GradeState.Success)
```

### 5.2 改

```kotlin
// 替换：gradeState  →  sttTestState + sttAmplitudeBars
val sttTestState by viewModel.sttTestState.collectAsState()
val sttAmplitudeBars by viewModel.sttAmplitudeBars.collectAsState()

// 把 sttTestState 传给 TestingControlBar（不显示录音图标变色，由 TestingPage 浮层显示）
TestingControlBar(
    isSttListening = sttTestState is SttTestState.Listening,
    onPressMic = { viewModel.startStt() },
    onReleaseMic = { viewModel.stopStt() },
    ...
)
```

### 5.3 加（在 TestingSubtitleCard 之后，Spacer 之前）

```kotlin
when (val s = sttTestState) {
    SttTestState.Idle -> { /* nothing */ }
    is SttTestState.Listening -> {
        RecordingOverlay(
            elapsedMs = s.elapsedMs,
            amplitudeBars = sttAmplitudeBars,
            onCancel = { viewModel.cancelStt() }
        )
    }
    is SttTestState.Transcribed -> {
        TranscriptionEditor(
            initialText = s.text,
            onTextChange = { /* VM 不需要；Editor 内部管理 text state */ },
            onSubmit = { text -> viewModel.submitTranscription(text) },
            onRerecord = { viewModel.resetStt() }
        )
    }
    is SttTestState.Passed -> {
        TestResultCard(
            state = s,
            originalEn = currentTest?.contentEn.orEmpty(),
            onNextItem = { ... },
            onRerecord = { viewModel.resetStt() }
        )
    }
    is SttTestState.Failed -> {
        TestResultCard(
            state = s,
            originalEn = s.original,
            onNextItem = { ... },
            onRerecord = { viewModel.resetStt() }
        )
    }
}
```

**注意**：`TranscriptionEditor` 内部维护 text state，提交时回调 `onSubmit(text)`——需要把回调签名改成 `onSubmit: (String) -> Unit`。

---

## §6 删除清单（11 个文件）

| 路径 | 用途 | 删后影响 |
|---|---|---|
| `speech/PronunciationGrader.kt` | DTW 评分主入口 | 无（grading 完全删除） |
| `speech/DtwAligner.kt` | Sakoe-Chiba DTW | 无 |
| `speech/RmsExtractor.kt` | RMS 能量提取 | 无 |
| `speech/WavReader.kt` | WAV 解码 | 无 |
| `speech/M4aDecoder.kt` | AAC 解码 | 无（测试 Tab 不录音频文件了） |
| `speech/Resampler.kt` | 采样率重采样 | 无 |
| `speech/TtsReferenceCache.kt` | TTS 参考音缓存 | 无 |
| `domain/model/ScoreResult.kt` | 评分数据类 + `ScoreTier` | 无 |
| `domain/usecase/UpdateSentenceReadScoreUseCase.kt` | 写 readScore | 无（DB 字段保留，下次用） |
| `ui/screens/practice/components/ScoreCard.kt` | 评分卡片 UI | 无 |
| `player/TtsManager.kt` 中的 `synthesizeToFile()` 方法 | TTS 落盘 | 闪卡/生词本 TTS 不受影响（用的是 `speak()`） |

**保留**：
- `Sentence.readScore` 字段（`SentenceEntity.kt:18` + `Sentence.kt:12`）— DB 兼容，下次想用
- `SentenceDao.updateReadScore` 方法 — 同上
- `TtsManager.speak()` — 闪卡/生词本继续用

---

## §7 Manifest 改动

```xml
<!-- 在 <manifest> 顶部、<application> 之前加 -->
<queries>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

**为什么需要**：Android 11+ package visibility，`isRecognitionAvailable()` 内部走 `queryIntentServices`，没 `<queries>` 永远返回 false。

---

## §8 Edge cases & invariants

| 场景 | 表现 | 处理 |
|---|---|---|
| 设备无 STT 引擎 | `isRecognitionAvailable() = false` | `startStt()` 直接进 `Transcribed("")`，用户手动输入 |
| STT 报错（ERROR_NO_MATCH/ERROR_SPEECH_TIMEOUT） | `onError` 触发 | 进 `Transcribed("")`，snackbar 提示"未识别到语音，请重试或手动输入" |
| 用户长按后立刻松手（< 200ms） | onPress 未触发 | 无效果（pointerInput 的 `detectTapGestures` 默认要求 200ms+ 才算 tap） |
| 用户长按后滑出 mic 按钮 | onPress 已触发，tryAwaitRelease 仍会等 onRelease | v1 不做滑出取消；v2 加 `onCancel` 回调 |
| 录音中切后台 | Activity onStop | `onCleared` 不一定触发；需要在 `LifecycleEventObserver` 加 `onStop` 调 `cancelStt()` |
| 用户编辑时按"重录" | 文本丢失 | v1 接受丢失；v2 加 "确定要重录？" 确认 |
| 用户长按时长 > 60s | STT 自动超时 | Android 系统行为，进 `Transcribed("")` |
| 中英混合句（如专有名词） | STT 识别成 "i love 北京" | `normalize` 只去标点，"北京" 还在；用户编辑改回 |
| 多句连续 | 每句独立 state | 每题独立 `startStt` / `submit` / `nextItem` |
| 重复录音 | 第二次 startStt | 检查当前 state：若 Listening 则 noop；否则先 cancelStt 再 startStt |

---

## §9 验证

### 单元测试（`app/src/test/.../speech/`）

1. `WordMatcherTest`：
   - 完全相同 → passed
   - 大小写差异 → passed
   - 标点差异 → passed
   - 漏一个词 → failed, reason=missing_word
   - 多一个词 → failed, reason=extra_word
   - 单词错 → failed, reason=wrong_word
   - 空转写 → failed, reason=empty_transcription
   - "don't" vs "dont" → failed（撇号有意义）

### 集成（手动）

| 场景 | 期望 |
|---|---|
| 长按 mic → 说 "I love you" → 松手 | Transcribed = "I love you" |
| 长按 → 沉默 → 松手 | Transcribed = ""，snackbar 提示 |
| 长按 → STT 不可用 | 直接进空 Transcribed，可手动输入 |
| 转写显示 "I love her" | 提交 → Failed，错词 "her" 红 |
| 用户编辑 "her" → "you" | 提交 → Passed，标记 tested |
| 通过后点"下一题" | 切到下一题，sttTestState = Idle |
| 短句 "Hi." | normalize 后 ["hi"]，用户说 "hi" → Passed |

### 真机（小米 Mi 11 CN）

- `adb logcat | grep -E 'SpeechRecognition|SttRecognizer|RecognitionListener'`
- 确认 `isRecognitionAvailable() = true`
- 关闭网络测离线 STT 是否工作（取决于设备是否装 Google Speech）
- 听 `onRmsChanged` 的回调频率（v2 用，v1 验证接口）

### 编译

```bash
cd "c:/Users/MING/myagent/echoling"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

---

## §10 禁止行为（v1）

- ❌ 不要 partial results 实时显示（v1 只看最终结果）
- ❌ 不要真实振幅动画（用随机占位）
- ❌ 不要滑动取消录音
- ❌ 不要"播放我的录音"功能（v1 纯文字交互，audio file 不存）
- ❌ 不要语音评分相关（彻底删除，不要留 dormant 代码）
- ❌ 不要词形归一 / 同义词替换 / 模糊匹配
- ❌ 不要保留任何 `ScoreCard` / `GradeState` / `PronunciationGrader` 相关代码
- ❌ 不要给 `SttTestState.Failed` 加动画过渡（v1 直接显示文字）
- ❌ 不要把 `RecordingOverlay` 替换 TestingSubtitleCard（叠加在它下方）

## §11 后续可能

- v2 实时波形（接 `onRmsChanged` 真实振幅）
- v2 滑动取消（pointerInput 检测拖出范围）
- v2 partial results 实时显示
- v2 多句连录（一段录音覆盖 3-5 句）
- v2 词形归一（"is/are"、"color/colour" 等用户词典）
- v2 TTS 重听对照（"▶ 听原音 vs ▶ 听我的录音"）

---

## Related

- 之前的发音评分 plan：`C:\Users\MING\.claude\plans\glittery-dancing-gosling.md`（仅参考，已被本设计取代）
- 跟读 shadowing（SpeakingPage）不受影响 — 仍用 `VoiceRecorder` 录 m4a
- Android 11+ package visibility：见 `[[android-11-package-visibility-queries]]`
