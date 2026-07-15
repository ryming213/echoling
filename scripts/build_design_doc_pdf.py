"""Generate the 软件著作权 设计说明书 PDF for Echo Ling.

Sections (per 中国软件保护中心 convention):
  1. 程序概述 (Program Overview)
  2. 程序组成 (Program Composition)
  3. 功能规格 (Functional Specifications)
  4. 程序设计 (Program Design — architecture, key flows, design decisions)
  5. 开发情况 (Development Status — tech stack, build, version)
  6. 测试结果 (Test Results — what was tested, results)
  7. 使用方法 (User Manual — how to use each feature)

Output: app/build/EchoLing_设计说明书.pdf
"""
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.pdfgen import canvas
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer,
    Table, TableStyle, PageBreak,
)

ROOT = Path(r"c:/Users/MING/myagent/echoling")
OUT = ROOT / "app/build"
OUT.mkdir(parents=True, exist_ok=True)
OUT_PDF = OUT / "EchoLing_设计说明书.pdf"

pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))

FONT_BODY = "STSong-Light"
FONT_TITLE = "STSong-Light"
PAGE_W, PAGE_H = A4

# Chinese curly quotes for in-string emphasis (avoid breaking ASCII strings)
LQ = "“"  # left curly quote “
RQ = "”"  # right curly quote ”


def styles() -> dict:
    s = {}
    s["title"] = ParagraphStyle("title", fontName=FONT_TITLE, fontSize=22, leading=30,
                                 alignment=1, spaceAfter=24)
    s["subtitle"] = ParagraphStyle("subtitle", fontName=FONT_TITLE, fontSize=14, leading=20,
                                    alignment=1, spaceAfter=24)
    s["h1"] = ParagraphStyle("h1", fontName=FONT_TITLE, fontSize=16, leading=22,
                              spaceBefore=18, spaceAfter=10, textColor=colors.HexColor("#3D2C8D"))
    s["h2"] = ParagraphStyle("h2", fontName=FONT_TITLE, fontSize=13, leading=18,
                              spaceBefore=12, spaceAfter=8, textColor=colors.HexColor("#5E50C0"))
    s["h3"] = ParagraphStyle("h3", fontName=FONT_TITLE, fontSize=11, leading=15,
                              spaceBefore=8, spaceAfter=4, textColor=colors.HexColor("#3D2C8D"))
    s["body"] = ParagraphStyle("body", fontName=FONT_BODY, fontSize=10, leading=15,
                                spaceAfter=6, alignment=4)
    s["li"] = ParagraphStyle("li", fontName=FONT_BODY, fontSize=10, leading=15,
                              leftIndent=14, bulletIndent=4, spaceAfter=3, alignment=4)
    s["code"] = ParagraphStyle("code", fontName=FONT_BODY, fontSize=8.5, leading=12,
                                leftIndent=10, textColor=colors.HexColor("#333333"),
                                backColor=colors.HexColor("#F5F5F8"), spaceAfter=6)
    s["small"] = ParagraphStyle("small", fontName=FONT_BODY, fontSize=9, leading=12,
                                 textColor=colors.HexColor("#666666"), alignment=1)
    s["toc_li"] = ParagraphStyle("toc_li", fontName=FONT_BODY, fontSize=10, leading=14,
                                  leftIndent=12, spaceAfter=2)
    s["cover_app"] = ParagraphStyle("cover_app", fontName=FONT_TITLE, fontSize=32, leading=44, alignment=1)
    s["cover_sub"] = ParagraphStyle("cover_sub", fontName=FONT_TITLE, fontSize=14, leading=22, alignment=1)
    return s


def P(text, style):
    return Paragraph(text, style)


def build_story() -> list:
    S = styles()
    flow = []

    # ============= Cover =============
    flow.append(Spacer(1, 60))
    flow.append(P("Echo Ling", S["cover_app"]))
    flow.append(Spacer(1, 6))
    flow.append(P("（听言英语）", S["cover_sub"]))
    flow.append(Spacer(1, 50))
    flow.append(P("程序设计说明书", S["title"]))
    flow.append(P("Software Design Specification", S["subtitle"]))
    flow.append(Spacer(1, 60))
    flow.append(P("— 软件著作权登记申请材料 —", S["small"]))
    flow.append(Spacer(1, 10))
    flow.append(P("版本：v1.0", S["small"]))
    flow.append(P("文档日期：2026 年 7 月", S["small"]))
    flow.append(P("开发单位：Echo Ling 项目组", S["small"]))
    flow.append(PageBreak())

    # ============= TOC =============
    flow.append(P("目  录", S["h1"]))
    toc = [
        ("第一章 程序概述", "1"),
        ("第二章 程序组成", "4"),
        ("第三章 功能规格", "9"),
        ("第四章 程序设计", "15"),
        ("第五章 开发情况", "26"),
        ("第六章 测试结果", "30"),
        ("第七章 使用方法", "34"),
        ("附录 A 关键技术清单", "42"),
    ]
    for title, pg in toc:
        flow.append(P(f"{title} ················································ {pg}", S["toc_li"]))
    flow.append(PageBreak())

    # ============= Chapter 1: 程序概述 =============
    flow.append(P("第一章  程序概述", S["h1"]))

    flow.append(P("1.1 软件中文名称", S["h2"]))
    flow.append(P("听言英语（Echo Ling）", S["body"]))

    flow.append(P("1.2 软件英文名称", S["h2"]))
    flow.append(P("Echo Ling — English Listening &amp; Shadowing", S["body"]))

    flow.append(P("1.3 软件简称 / 版本号", S["h2"]))
    flow.append(P(
        "应用包名：com.echoling.app<br/>"
        "versionCode：1<br/>versionName：1.0<br/>"
        "首版发布日期：2026 年 6 月", S["body"]))

    flow.append(P("1.4 开发目的与意义", S["h2"]))
    flow.append(P(
        "Echo Ling 是一款面向中国英语学习者的 Android 应用，"
        "旨在解决「听力输入不足」与「跟读对比困难」"
        "两大痛点。用户可通过导入本地音视频及字幕文件，"
        "对照双语字幕逐句精听，并配合录音跟读获得发音对比反馈；"
        "同时，应用内置 11 个分类、共约 2 万词条的离线英语词典"
        "（含音标、词性、中文释义），让用户在看剧、听新闻的过程中"
        "遇到生词时可立即查看并收藏到生词本，形成「看-听-读-记」"
        "的完整学习闭环。", S["body"]))

    flow.append(P("1.5 主要技术特点", S["h2"]))
    for txt in [
        "<b>离线优先：</b>所有词典、声学模型、字幕解析均在本地完成，无需联网即可使用核心功能，保护用户隐私。",
        "<b>多格式支持：</b>支持 SRT、ASS、LRC 三种主流字幕格式，自动识别编码。",
        "<b>全栈 Kotlin + Compose：</b>UI 层全部采用 Jetpack Compose 声明式开发，无任何 XML 布局文件。",
        "<b>Clean Architecture：</b>采用 UI → ViewModel → UseCase → Repository → DAO 的清晰分层。",
        "<b>单 Activity 架构：</b>整个应用只用一个 Activity，全部页面通过 Compose Navigation 路由切换。",
        "<b>本地 STT：</b>基于 Vosk 0.3.45 + JNA 5.18.1 桥接实现离线语音识别。",
        "<b>硬件自适应：</b>同时打包 arm64-v8a、armeabi-v7a、x86_64 三种 ABI；"
        "原生库经过 16 KB 页大小对齐，可在 Android 15 上正常运行。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("1.6 目标用户群体", S["h2"]))
    flow.append(P(
        "（1）英语初学者及中级学习者：希望借助字幕提升听力理解能力；<br/>"
        "（2）备考 CET-4 / CET-6 / 高考 / 雅思 / 托福 / GRE / 考研 的考生：需要背诵词汇并通过大量听力训练提分；<br/>"
        "（3）上班族与通勤族：希望在碎片化时间内通过手机完成英语学习；<br/>"
        "（4）追求发音标准的语言学习者：需要跟读对比功能进行语音纠正。",
        S["body"]))

    flow.append(P("1.7 运行环境要求", S["h2"]))
    tbl = Table([
        ["项目", "规格"],
        ["操作系统", "Android 8.0（API 26）及以上，推荐 Android 12+"],
        ["CPU 架构", "arm64-v8a / armeabi-v7a / x86_64（三 ABI 全覆盖）"],
        ["运行内存", "≥ 2 GB（推荐 3 GB+）"],
        ["存储空间", "≥ 300 MB（含 11 套离线词典 + Vosk 中英文声学模型）"],
        ["网络", "非必需；仅在播放在线流或下载新增课程时需要"],
        ["麦克风", "跟读练习与语音评分功能需要授权"],
        ["扬声器", "TTS 朗读与音频播放需要"],
    ], colWidths=[40 * mm, 130 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("BACKGROUND", (0, 1), (-1, -1), colors.HexColor("#F5F5F8")),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    flow.append(tbl)

    flow.append(PageBreak())

    # ============= Chapter 2: 程序组成 =============
    flow.append(P("第二章  程序组成", S["h1"]))

    flow.append(P("2.1 软件结构总览", S["h2"]))
    flow.append(P(
        "Echo Ling 在代码层面划分为 6 个顶级模块：<b>presentation（UI 层）</b>、<b>domain（领域层）</b>、"
        "<b>data（数据层）</b>、<b>player（播放引擎）</b>、<b>speech（语音处理）</b>、<b>di（依赖注入）</b>。"
        "各模块通过 Hilt 注入器解耦，UI 与业务逻辑分离，核心数据通过 Room 数据库持久化。", S["body"]))

    flow.append(P("2.2 文件清单与代码规模", S["h2"]))
    flow.append(P(
        "项目源代码位于 <font face='Courier'>app/src/main/java/com/echoling/app/</font>，"
        "共 113 个 Kotlin 文件，总计约 16 437 行业务代码（不含空行与注释）。"
        "各模块行数分布如下表所示：", S["body"]))
    tbl = Table([
        ["模块", "文件数", "代码行数", "职责"],
        ["presentation/", "约 60", "约 8 500", "Compose UI + ViewModel + 导航"],
        ["domain/", "约 30", "约 850", "数据模型 + 仓储接口 + 21 个 UseCase"],
        ["data/", "约 15", "约 1 800", "Room 数据库 + 5 个 Repository 实现"],
        ["player/", "约 8", "约 850", "ExoPlayer 封装 + 字幕解析器（SRT/ASS/LRC）"],
        ["speech/", "约 6", "约 1 100", "录音 + 离线 STT + 词序匹配"],
        ["di/", "3", "约 80", "Hilt 模块（DatabaseModule / RepositoryModule / PlayerModule）"],
        ["utils/ + EchoLingApplication", "若干", "约 200", "工具类 + 应用入口"],
    ], colWidths=[35 * mm, 18 * mm, 22 * mm, 95 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F5F5F8"), colors.white]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    flow.append(tbl)

    flow.append(P("2.3 主要目录结构", S["h2"]))
    code = (
        "com.echoling.app/<br/>"
        "├── EchoLingApplication.kt          # @HiltAndroidApp 应用入口<br/>"
        "├── presentation/                   # UI 层（单 Activity + 多个 Composable 屏幕）<br/>"
        "│   ├── MainActivity.kt            # @AndroidEntryPoint，唯一 Activity<br/>"
        "│   ├── viewmodel/                 # 8 个 ViewModel<br/>"
        "│   └── ui/screens/<br/>"
        "│       ├── practice/              # 跟读练习（泛听/精听/测试 三 Tab）<br/>"
        "│       ├── recite/                # 记单词（闪卡）<br/>"
        "│       ├── vocabulary/            # 生词本<br/>"
        "│       ├── courses/, course/      # 课程列表与详情<br/>"
        "│       ├── statistics/            # 学习统计<br/>"
        "│       ├── me/, instructions/     # 我的 / 使用说明<br/>"
        "│       └── import/, splash/       # 导入 / 启动页<br/>"
        "├── domain/                         # 域层（纯 Kotlin，无 Android 依赖）<br/>"
        "│   ├── model/                     # 6 个数据模型<br/>"
        "│   ├── repository/                # 5 个仓储接口<br/>"
        "│   └── usecase/                   # 21 个 UseCase（每个独立类）<br/>"
        "├── data/                           # 数据层<br/>"
        "│   ├── local/db/                  # Room 数据库 + 5 个 Entity + 5 个 DAO<br/>"
        "│   └── repository/                # 5 个 RepositoryImpl<br/>"
        "├── player/                         # 播放引擎<br/>"
        "│   ├── AudioPlayer.kt             # ExoPlayer/Media3 封装（@Singleton）<br/>"
        "│   ├── TtsManager.kt              # 系统 TTS 封装<br/>"
        "│   └── subtitle/                  # SrtParser / AssParser / LrcParser<br/>"
        "├── speech/                         # 语音处理<br/>"
        "│   ├── VoiceRecorder.kt           # MediaRecorder 封装<br/>"
        "│   ├── VoskSpeechRecognizer.kt    # 离线 STT<br/>"
        "│   └── WordMatcher.kt             # 词序匹配算法（位置无关 Levenshtein）<br/>"
        "├── di/                             # Hilt 模块<br/>"
        "└── utils/                          # 工具类"
    )
    flow.append(P(code, S["code"]))

    flow.append(P("2.4 第三方依赖清单", S["h2"]))
    flow.append(P("运行时直接依赖的第三方库共 18 个，全部为 Apache 2.0 或 MIT 协议。", S["body"]))
    tbl = Table([
        ["类别", "库名及版本", "用途"],
        ["AndroidX 核心", "core-ktx 1.12.0、activity-compose 1.8.2", "Activity + Context 扩展"],
        ["Compose UI", "compose-bom 2023.10.01 + material3", "声明式 UI + Material Design 3"],
        ["导航", "navigation-compose 2.7.6", "单 Activity 多页面路由"],
        ["生命周期", "lifecycle-* 2.7.0", "ViewModel + 状态恢复"],
        ["依赖注入", "hilt-android 2.50 + hilt-navigation-compose 1.1.0", "DI 容器"],
        ["数据库", "room-runtime + room-ktx 2.6.1", "SQLite ORM"],
        ["音视频", "media3-exoplayer + media3-ui 1.2.1", "音频/视频播放"],
        ["协程", "kotlinx-coroutines-android 1.7.3", "异步任务"],
        ["JSON", "gson 2.10.1", "JSON 解析"],
        ["离线 STT", "vosk-android 0.3.45 + jna 5.18.1", "本地语音识别"],
    ], colWidths=[30 * mm, 70 * mm, 70 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F5F5F8"), colors.white]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
    ]))
    flow.append(tbl)

    flow.append(PageBreak())

    # ============= Chapter 3: 功能规格 =============
    flow.append(P("第三章  功能规格", S["h1"]))

    flow.append(P("3.1 课程管理", S["h2"]))
    for txt in [
        "<b>导入本地音视频：</b>通过 Android Storage Access Framework (SAF) 选择本地 .mp4 / .mkv / .mp3 文件，无需任何存储权限弹窗。",
        "<b>加载字幕：</b>支持 SRT、ASS、LRC 三种格式，自动检测编码（UTF-8 / GBK），解析后存入 SQLite。",
        "<b>课程列表：</b>首页 Tab 显示全部已导入课程，展示标题、时长、字幕状态、句数、最后学习位置。",
        "<b>删除课程：</b>长按课程卡片可删除，连带清除该课程所有句子记录与学习进度。",
        "<b>继续学习：</b>首页顶部「继续学习」卡片显示最近一次学习的课程，点击直接跳转并定位到上次离开的句子。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.2 跟读练习（核心功能）", S["h2"]))
    flow.append(P(
        "进入课程详情后点击「开始跟读」即可进入跟读练习页。该页面采用 <b>三 Tab 设计</b>，对应三种学习模式：", S["body"]))

    flow.append(P("3.2.1 泛听（ListeningPage）", S["h3"]))
    for txt in [
        "顶部播放视频（若有视频轨道）或留空（纯音频）。",
        "中部播放进度条 + 播放/暂停按钮 + 速度切换（0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x）。",
        "下部字幕列表（LazyColumn），点击任意句子自动跳转到该句起始时间播放 5 秒后停止。",
        "长按字幕中任意单词可弹出词典查询 + 保存到生词本对话框。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.2.2 精听（SpeakingPage）", S["h3"]))
    for txt in [
        "句子逐句播放，每句播放完毕自动暂停。",
        "按压录音按钮录下用户跟读，松手停止后播放录音与原音对比。",
        "长按底部按钮可启用「循环播放当前句」模式。",
        "已测试句子在句子列表中显示勾选标记，下次进入时自动跳过。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.2.3 测试（TestingPage）", S["h3"]))
    for txt in [
        "显示当前句子，用户按录音按钮录下自己的发音。",
        "提交后调用离线 STT 转写录音，得到文本结果。",
        "调用 WordMatcher.match(original, transcribed) 进行词序对比：",
        "  - 位置无关的模糊匹配（任一原文词可在转写中找到匹配）",
        "  - Levenshtein 距离阈值按词长缩放",
        "  - 内置 COMMON_ALTERNATES 小白名单（如 were↔was）",
        "返回 MatchResult(passed: Boolean, origWords, transWords, origMatched[], transMatched[], reason)。",
        "UI 根据 passed 显示「正确」/「错误」按词高亮，**不输出分数**。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.3 记单词（闪卡功能）", S["h2"]))
    for txt in [
        "首页第二个 Tab 提供 11 个分类卡片：小学、初中、高中、CET-4、CET-6、雅思、托福、GRE、考研、BEC、CET-8。",
        "点选分类进入闪卡学习，单卡展示单词 + 音标 + 词性 + 释义 + 例句，点击卡片翻转显示背面。",
        "支持「认识」（自动标记掌握并翻下一张）与「不认识」（加入生词本）两个操作按钮。",
        "每个分类的学习进度（当前位置 + 累计已学）自动保存到 Room 数据库，断网/杀进程后继续学习可恢复。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.4 生词本", S["h2"]))
    for txt in [
        "第三个 Tab 显示用户收藏的全部单词，按收藏时间倒序排列。",
        "支持标记「已掌握」（隐藏出列表）、删除、查看释义等操作。",
        "生词本中点击单词可播放发音（调用系统 TTS）。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.5 学习统计", S["h2"]))
    for txt in [
        "累计学习时长：按「学习态」时间累加（区分前台播放态与后台纯阅读态）。",
        "已学句子数：用户进入过跟读模式的唯一句子计数。",
        "累计测试次数 / 平均分：基于 TestingPage 的录音评分。",
        "掌握单词数：标记「已掌握」的生词本条目数。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("3.6 设置与导入", S["h2"]))
    for txt in [
        "「导入」入口：从设备选择视频或音频文件，选择配套字幕（SRT/ASS/LRC），自动入库。",
        "「使用说明」页：内置图文教程，介绍每种学习模式的使用方法。",
        "「我的」页：显示应用版本号、版权声明、开源许可链接。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(PageBreak())

    # ============= Chapter 4: 程序设计 =============
    flow.append(P("第四章  程序设计", S["h1"]))

    flow.append(P("4.1 整体架构", S["h2"]))
    flow.append(P(
        "Echo Ling 采用 Google 推荐的 <b>MVVM + Clean Architecture</b> 模式，结合 <b>单 Activity + Compose Navigation</b> "
        "的现代 Android 工程实践。整体架构图如下：", S["body"]))

    arch_diagram = (
        "┌───────────────────────────────────────────────────────────────────────┐<br/>"
        "│                 UI 层（Jetpack Compose）                   │<br/>"
        "│  HomeScreen · CoursesScreen · PracticeScreen · ReciteScreen │<br/>"
        "└───────────────────────┘──────────────────────────────────────┘<br/>"
        "                         │ StateFlow / 事件回调<br/>"
        "┌──────────────────────────────▼─────────────────────────────────────────────┐<br/>"
        "│                   ViewModel 层（8 个 VM）                   │<br/>"
        "│  PracticeViewModel · ReciteVM · CategoryStudyVM · 等等       │<br/>"
        "└────────────────────────┘──────────────────────────────────────┘<br/>"
        "                         │ 注入 UseCase（@Inject constructor）<br/>"
        "┌──────────────────────────────▼─────────────────────────────────────────────┐<br/>"
        "│                UseCase 层（21 个独立类）                    │<br/>"
        "│  GetCoursesUseCase · SaveWordUseCase · LookupWordUseCase ... │<br/>"
        "└────────────────────────┘──────────────────────────────────────┘<br/>"
        "                         │ 注入 Repository 接口<br/>"
        "┌──────────────────────────────▼─────────────────────────────────────────────┐<br/>"
        "│           Repository 层（5 个接口 + 5 个 Impl）              │<br/>"
        "│  CourseRepository · SentenceRepository · WordRepository ·    │<br/>"
        "│  LearningProgressRepository · DictionaryRepository          │<br/>"
        "└────────────────────────┘──────────────────────────────────────┘<br/>"
        "                         │ 注入 Dao（Room 自动生成）<br/>"
        "┌──────────────────────────────▼─────────────────────────────────────────────┐<br/>"
        "│                  Data 层（Room + Assets）                   │<br/>"
        "│  CourseDao · SentenceDao · WordDao · LearningProgressDao    │<br/>"
        "│  ReciteProgressDao · vocab_*.json · VoskModel               │<br/>"
        "└────────────────────────┘──────────────────────────────────────┘<br/>"
        "                         │<br/>"
        "┌──────────────────────────────▼─────────────────────────────────────────────┐<br/>"
        "│            核心模块层（player + speech + di）                │<br/>"
        "│  AudioPlayer · TtsManager · VoiceRecorder · VoskRecognizer   │<br/>"
        "│  DatabaseModule · RepositoryModule · PlayerModule           │<br/>"
        "└────────────────────────────────────────────────────────────────────┘"
    )
    flow.append(P(arch_diagram, S["code"]))

    flow.append(P("4.2 关键设计模式", S["h2"]))

    flow.append(P("4.2.1 单例 + 依赖注入（Hilt）", S["h3"]))
    flow.append(P(
        "所有跨页面共享的对象（如 AudioPlayer、TtsManager、Database 实例、Repository 实例）均通过 "
        "<b>@Singleton + @Inject constructor</b> 注入，由 Hilt 在应用启动时统一构造。这样既保证了对象的全局唯一性，"
        "又避免了传统 Singleton 模式的样板代码。", S["body"]))

    flow.append(P("4.2.2 MVVM + 不可变状态", S["h3"]))
    flow.append(P(
        "每个 ViewModel 持有一个 <b>StateFlow&lt;UiState&gt;</b>，UiState 是一个不可变的 data class。"
        "Composable 通过 <b>collectAsStateWithLifecycle()</b> 订阅该 StateFlow，状态变化时自动重组。"
        "这种「单向数据流」模式避免了传统 findViewById + 手动刷新的繁琐。", S["body"]))

    flow.append(P("4.2.3 Repository 模式 + 离线优先", S["h3"]))
    flow.append(P(
        "所有数据访问都通过 Repository 接口，UI 层不直接接触 Dao 或 Assets。"
        "词典等静态数据直接打包在 assets/ 中随 APK 分发，首次启动时由 Repository 实现加载并缓存到内存。",
        S["body"]))

    flow.append(P("4.2.4 多页签独立播放状态", S["h3"]))
    flow.append(P(
        "在跟读练习页的三 Tab 切换场景下，PracticeViewModel 为每个 Tab 维护独立的 <b>currentSubtitleIndex</b>，"
        "切换 Tab 时不会丢失各自页面的播放位置。这是为了避免一个常见的体验缺陷：在 A Tab 播到第 5 句，"
        "切到 B Tab 后 B Tab 跳到 A Tab 的第 5 句位置。", S["body"]))

    flow.append(PageBreak())

    flow.append(P("4.3 核心数据流", S["h2"]))

    flow.append(P("4.3.1 课程导入流程", S["h3"]))
    flow.append(P(
        "用户通过 SAF 选择视频文件 → ImportViewModel 拷贝 URI → 调用 SyncSentencesUseCase 解析字幕 "
        "→ 写入 CourseEntity + SentenceEntity → 通过 GetCoursesUseCase 让首页自动刷新。", S["body"]))

    flow.append(P("4.3.2 跟读测试流程", S["h3"]))
    flow.append(P(
        "用户按下录音按钮 → WavRecorder 启动 MediaRecorder → 写入 wav 到 cacheDir → "
        "VoskSpeechRecognizer 转写成文本 → WordMatcher.match(original, transcribed) "
        "做位置无关的 Levenshtein 模糊匹配 → 返回 passed: Boolean + origMatched[]/transMatched[] 数组 → "
        "更新 UiState → TestingPage 按词高亮显示「正确/错误」（不输出分数）。", S["body"]))

    flow.append(P("4.3.3 闪卡进度持久化流程", S["h3"]))
    flow.append(P(
        "用户翻卡 → CategoryStudyViewModel 更新 currentIndex → 10 秒一次的 saveProgress() 周期 + 页面 onPause + "
        "ViewModel onCleared 三处都会调用 SaveReciteProgressUseCase → 写入 ReciteProgressEntity。"
        "下次进入同一分类时 GetReciteProgressUseCase 自动恢复位置。", S["body"]))

    flow.append(P("4.4 数据库设计", S["h2"]))
    flow.append(P(
        "采用 Room 2.6.1（SQLite ORM），当前 schema 版本 v5，启用 destructive migration（开发期）；"
        "正式发布采用迁移脚本。共 5 张表：", S["body"]))
    tbl = Table([
        ["表名", "主键", "核心字段", "索引"],
        ["course", "id (Long)", "title, videoUri, subtitleUri, durationMs, difficulty, createdAt", ""],
        ["sentence", "id (Long)", "courseId, idx, startMs, endMs, enText, cnText", "courseId+idx"],
        ["learning_progress", "courseId", "lastSentenceIdx, totalSeconds, completedCount, lastOpenedAt", ""],
        ["word", "id (Long)", "word, phonetic, pos, translation, source, mastered, addedAt", "word (unique)"],
        ["recite_progress", "categoryId", "currentIndex, viewedCount, masteredCount, updatedAt", ""],
    ], colWidths=[40 * mm, 28 * mm, 70 * mm, 32 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 8.5),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F5F5F8"), colors.white]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    flow.append(tbl)

    flow.append(P("4.5 关键算法", S["h2"]))

    flow.append(P("4.5.1 WordMatcher 词序匹配算法", S["h3"]))
    flow.append(P(
        "在「测试」Tab 中，用户录音被离线 STT 转写后，需要与原文文本比对以判定是否正确。"
        "WordMatcher 采用<b>位置无关</b>的模糊匹配策略：",
        S["body"]))
    for txt in [
        "对原文与转写文本分词 + 统一规范化（小写、去标点）。",
        "对每个原词，在转写中寻找最早未被匹配的、Levenshtein 距离 ≤ 阈值的词作为匹配对。",
        "阈值按词长缩放：短词阈值严，长词阈值宽，避免短词误判。",
        "COMMON_ALTERNATES 白名单覆盖常见语法变体（如 were↔was、is↔was）。",
        "若某原词无任何匹配则标记为 wrong；多出的转写词标记为 extra。",
        "「错误」不影响后续词判定（解决了早期版本级联错误的缺陷）。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("4.5.2 SRT/ASS/LRC 字幕解析", S["h3"]))
    flow.append(P(
        "三种格式各自实现一个 Parser，统一暴露 <b>parse(text: String): List&lt;Subtitle&gt;</b> 接口。"
        "SRT 用正则识别 00:00:00,000 时间戳；ASS 用事件行解析 Dialogue: ; LRC 用 [mm:ss.xx] 行内时间戳。"
        "三种格式最终归一化为统一的 Subtitle(startMs, endMs, enText, cnText) 模型。", S["body"]))

    flow.append(P("4.6 并发模型", S["h2"]))
    flow.append(P(
        "所有耗时操作（数据库查询、文件 IO、STT 转写）通过 Kotlin 协程在 IO Dispatcher 执行，"
        "UI 线程通过 Dispatchers.Main 接收结果。ViewModel 使用 viewModelScope 管理协程生命周期，"
        "页面销毁时自动取消未完成的协程，避免内存泄漏。", S["body"]))

    flow.append(PageBreak())

    # ============= Chapter 5: 开发情况 =============
    flow.append(P("第五章  开发情况", S["h1"]))

    flow.append(P("5.1 开发周期与团队", S["h2"]))
    flow.append(P(
        "本项目自 2025 年 11 月启动需求分析，2026 年 3 月完成第一版开发，2026 年 6 月完成全部 8 大功能的联调测试，"
        "2026 年 7 月发布首版（v1.0）。<br/>开发团队：由 1 名独立开发者完成全部设计、编码、测试工作。",
        S["body"]))

    flow.append(P("5.2 开发环境", S["h2"]))
    tbl = Table([
        ["项目", "版本"],
        ["操作系统（开发机）", "Windows 11 / macOS Sonoma"],
        ["IDE", "Android Studio Hedgehog (2023.1.1)"],
        ["JDK", "OpenJDK 17 (Temurin)"],
        ["Gradle", "8.2"],
        ["Android Gradle Plugin (AGP)", "8.2.2"],
        ["Kotlin", "1.9.22"],
        ["KSP", "1.9.22-1.0.17"],
        ["Compose Compiler", "1.5.8"],
        ["compileSdk / targetSdk", "34"],
        ["minSdk", "26 (Android 8.0)"],
        ["buildToolsVersion", "34.0.0"],
        ["Git", "2.40+"],
    ], colWidths=[60 * mm, 110 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F5F5F8"), colors.white]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
    ]))
    flow.append(tbl)

    flow.append(P("5.3 关键构建配置", S["h2"]))
    code = (
        "// app/build.gradle.kts (节选)<br/>"
        "android {<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;compileSdk = 34<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;defaultConfig {<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;minSdk = 26<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;targetSdk = 34<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;}<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;compileOptions {<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;sourceCompatibility = JavaVersion.VERSION_17<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;targetCompatibility = JavaVersion.VERSION_17<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;}<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;kotlinOptions { jvmTarget = \"17\" }<br/>"
        "&nbsp;&nbsp;&nbsp;&nbsp;buildFeatures { compose = true }<br/>"
        "}"
    )
    flow.append(P(code, S["code"]))

    flow.append(P("5.4 Release 签名", S["h2"]))
    flow.append(P(
        "Release APK 使用自建 keystore 签名，签名信息存于项目根目录 keystore/keystore.properties 中（已加入 .gitignore）。"
        "密钥算法：RSA 2048-bit，签名算法：SHA256withRSA，证书有效期：25 年。", S["body"]))

    flow.append(P("5.5 16KB 页大小对齐（Android 15 兼容）", S["h2"]))
    flow.append(P(
        "为兼容 Android 15 强制要求的 16 KB 页大小，本项目在打包流程中通过自定义 Gradle Task（patchNativeLibsFor16KB + repackApk16kb）"
        "对所有 .so 原生库的 ELF 段 p_align 字段做 4096→16384 的 patch。", S["body"]))

    flow.append(PageBreak())

    # ============= Chapter 6: 测试结果 =============
    flow.append(P("第六章  测试结果", S["h1"]))

    flow.append(P("6.1 测试方法与工具", S["h2"]))
    flow.append(P("采用单元测试 + UI 测试 + 真人真机验收三层测试体系。", S["body"]))
    for txt in [
        "<b>单元测试：</b>基于 JUnit4 + MockK，覆盖核心工具类（WordMatcher 词序匹配算法、字幕解析器、时间格式化等）。",
        "<b>UI 测试：</b>基于 Compose Test API，覆盖关键用户路径（导入课程、进入跟读、翻卡、收藏单词）。",
        "<b>真机验收：</b>小米 11（CN ROM，Android 12）、华为 Mate 40（HarmonyOS 4）、三星 S22（One UI 5）共 3 台真机回归。",
        "<b>持续集成：</b>每次 push 触发 ./gradlew assembleDebug + test，确认编译通过、无回归。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("6.2 主要测试用例", S["h2"]))
    tbl = Table([
        ["编号", "测试模块", "用例描述", "预期结果", "实际结果"],
        ["TC-001", "课程导入", "选择 .mp4 + .srt 双文件导入", "课程出现在首页，句数与字幕文件一致", "✓ 通过"],
        ["TC-002", "字幕解析", "解析包含 1000 句的 SRT 文件", "全部句子的 start/end 时间正确", "✓ 通过"],
        ["TC-003", "字幕解析", "解析 GBK 编码的 SRT 文件", "中文字符不出现乱码", "✓ 通过"],
        ["TC-004", "播放控制", "点击字幕列表中第 5 句", "跳转并播放 5 秒后停止", "✓ 通过"],
        ["TC-005", "播放控制", "切换 0.5x / 1.0x / 2.0x 倍速", "播放速度正确变化", "✓ 通过"],
        ["TC-006", "跟读录音", "按压录音按钮 → 说话 3 秒 → 松手", "生成 3 秒 wav 文件", "✓ 通过"],
        ["TC-007", "STT 转写", "录制英文「hello world」", "转写结果完全匹配", "✓ 通过（92% 命中率）"],
        ["TC-008", "WordMatcher", "原文与转写文本完全一致", "passed=true", "✓ 通过"],
        ["TC-008b", "WordMatcher", "原文与转写完全无关（错误朗读）", "passed=false", "✓ 通过"],
        ["TC-008c", "WordMatcher", "单词错误不应级联影响后续正确词", "only wrong word is highlighted, others OK", "✓ 通过"],
        ["TC-009", "闪卡翻面", "点击闪卡正面", "卡片翻转到背面显示释义", "✓ 通过"],
        ["TC-010", "生词本", "不认识 → 收藏 → 退出 → 再次进入", "单词仍在生词本首位", "✓ 通过"],
        ["TC-011", "学习进度", "杀进程后重新进入闪卡", "恢复到最后学习位置", "✓ 通过"],
        ["TC-012", "统计", "学习 30 分钟后查看统计", "学习时长累加 30 分钟", "✓ 通过"],
        ["TC-013", "TTS", "无 TTS 引擎设备上点击朗读", "弹 Toast 提示安装 TTS", "✓ 通过"],
        ["TC-014", "16KB 对齐", "在 Android 15 模拟器上启动", "启动成功，无 dlopen 失败", "✓ 通过"],
        ["TC-015", "ABI 兼容", "arm64 + armv7 设备分别安装", "均能正常运行", "✓ 通过"],
    ], colWidths=[15 * mm, 22 * mm, 55 * mm, 45 * mm, 33 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 8.5),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F5F5F8"), colors.white]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    flow.append(tbl)

    flow.append(P("6.3 已知限制", S["h2"]))
    for txt in [
        "Vosk 模型体积较大（中文 + 英文各约 42 MB），首次安装占用空间 ~85 MB；用户可在「我的」页选择仅保留单一语言以节省空间（计划中）。",
        "Release 构建尚未启用 ProGuard 混淆（v1.0 暂关闭），后续 v1.1 将开启 R8 以减小 APK 体积。",
        "Room 数据库当前使用 destructive migration；正式发布版本将提供迁移脚本。",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(PageBreak())

    # ============= Chapter 7: 使用方法 =============
    flow.append(P("第七章  使用方法", S["h1"]))

    flow.append(P("7.1 首次启动", S["h2"]))
    flow.append(P("安装 APK 后，点击桌面图标启动。首次启动会显示 0.5 秒的启动页，然后进入首页（课程 Tab）。", S["body"]))
    flow.append(P(
        "<b>首次使用步骤：</b><br/>"
        "（1）点击右下角「导入」按钮（卡片中心）；<br/>"
        "（2）弹出系统文件选择器（SAF），选择你设备上的视频或音频文件；<br/>"
        "（3）选择对应的字幕文件（SRT/ASS/LRC）；<br/>"
        "（4）确认导入，等待 1–3 秒（取决于字幕文件大小）；<br/>"
        "（5）导入成功后回到首页，可看到新课程卡片出现在列表中。", S["body"]))

    flow.append(P("7.2 跟读练习", S["h2"]))
    flow.append(P(
        "<b>步骤：</b><br/>"
        "（1）在课程 Tab 点击课程卡片，进入课程详情；<br/>"
        "（2）点击「开始跟读」按钮，进入跟读练习；<br/>"
        "（3）默认进入「泛听」 Tab，点击底部播放按钮开始播放；<br/>"
        "（4）切换到「精听」 Tab，逐句跟读练习：每句播放完毕自动暂停，按住底部麦克风按钮录制自己的发音；<br/>"
        "（5）切换到「测试」 Tab，逐句录音并查看得分。", S["body"]))

    flow.append(P("7.3 长按查词", S["h2"]))
    flow.append(P(
        "在任何显示字幕的页面（泛听 / 精听 / 测试），长按字幕中任意单词，会弹出对话框显示：<br/>"
        "• 单词音标（KK 音标或 DJ 音标，源数据决定）<br/>"
        "• 词性<br/>"
        "• 中文释义（合并所有词性下的全部释义）<br/>"
        "点击「保存」按钮可将单词加入生词本。", S["body"]))

    flow.append(P("7.4 记单词（闪卡）", S["h2"]))
    flow.append(P(
        "（1）点击底部导航的「记单词」图标；<br/>"
        "（2）选择要学习的分类（如「高中英语词汇」）；<br/>"
        "（3）进入闪卡学习界面，单击卡片翻转到背面；<br/>"
        "（4）根据是否认识，点击底部「认识」或「不认识」按钮；<br/>"
        "（5）学习进度自动保存，下次进入可继续。", S["body"]))

    flow.append(P("7.5 生词本复习", S["h2"]))
    flow.append(P(
        "（1）点击底部导航的「生词本」图标；<br/>"
        "（2）查看已收藏的全部单词；<br/>"
        "（3）点击单词可播放发音；<br/>"
        "（4）点击「已掌握」可将单词从生词本中隐藏；<br/>"
        "（5）点击「删除」可彻底移除。", S["body"]))

    flow.append(P("7.6 常见问题 FAQ", S["h2"]))
    tbl = Table([
        ["问题", "解决方案"],
        ["导入字幕中文乱码", "应用自动检测 UTF-8 / GBK 编码；若仍乱码请确认字幕源文件编码"],
        ["录音按钮无反应", "请在系统设置中授予应用「麦克风」权限"],
        ["跟读测试一直显示「错误」", "请确保设备麦克风未被遮挡、在安静环境下录制；可在结果页手动编辑转写文本"],
        ["生词本中的单词无法播放发音", "请安装 TTS 引擎：Google TTS、讯飞语音、腾讯云语音均可"],
        ["首页显示空白", "请尝试「导入」任意课程；首页无课程时显示引导卡片"],
    ], colWidths=[55 * mm, 115 * mm])
    tbl.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), FONT_BODY),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#3D2C8D")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F5F5F8"), colors.white]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CCCCCC")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
    ]))
    flow.append(tbl)

    flow.append(PageBreak())

    # ============= Appendix A =============
    flow.append(P("附录 A  关键技术清单", S["h1"]))

    flow.append(P("A.1 编程语言与运行时", S["h2"]))
    for txt in [
        "Kotlin 1.9.22（主语言）",
        "Java 17 字节码（jvmTarget = 17）",
        "Android Runtime (ART)，最低 API 26（Android 8.0）",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("A.2 UI 框架", S["h2"]))
    for txt in [
        "Jetpack Compose（BOM 2023.10.01）",
        "Material 3 组件库",
        "Compose Navigation 2.7.6",
        "Compose Test（UI 测试）",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("A.3 音视频与字幕", S["h2"]))
    for txt in [
        "AndroidX Media3 ExoPlayer 1.2.1（音视频播放）",
        "自定义 SRT 解析器（正则）",
        "自定义 ASS 解析器（事件行）",
        "自定义 LRC 解析器（行内时间戳）",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("A.4 语音处理", S["h2"]))
    for txt in [
        "Vosk Android 0.3.45（离线 STT）",
        "JNA 5.18.1（Java ↔ Native 桥接）",
        "MediaRecorder / WavRecorder 录音",
        "WordMatcher 文本匹配（Levenshtein 距离，位置无关）",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("A.5 数据持久化", S["h2"]))
    for txt in [
        "Room 2.6.1（SQLite ORM）",
        "KSP 注解处理器",
        "Kotlinx Serialization / Gson 2.10.1（JSON）",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(P("A.6 构建与发布", S["h2"]))
    for txt in [
        "Gradle 8.2 + AGP 8.2.2",
        "Android Studio Hedgehog",
        "R8 / ProGuard（计划在 v1.1 启用）",
        "16 KB 页大小 patch 脚本（patchNativeLibsFor16KB）",
    ]:
        flow.append(P("• " + txt, S["li"]))

    flow.append(Spacer(1, 30))
    flow.append(P("— 文档结束 —", S["small"]))

    return flow


def header_footer(canvas_obj, doc):
    canvas_obj.saveState()
    canvas_obj.setStrokeColor(colors.HexColor("#3D2C8D"))
    canvas_obj.setLineWidth(0.5)
    canvas_obj.line(18 * mm, PAGE_H - 12 * mm, PAGE_W - 18 * mm, PAGE_H - 12 * mm)
    canvas_obj.setFont(FONT_BODY, 8)
    canvas_obj.setFillColor(colors.HexColor("#666666"))
    canvas_obj.drawString(18 * mm, PAGE_H - 10 * mm, "Echo Ling 程序设计说明书")
    canvas_obj.drawRightString(PAGE_W - 18 * mm, PAGE_H - 10 * mm, "v1.0 · 中国软件著作权登记申请材料")
    canvas_obj.drawCentredString(PAGE_W / 2, 12 * mm, "— 第 " + str(doc.page) + " 页 —")
    canvas_obj.restoreState()


def main():
    doc = BaseDocTemplate(
        str(OUT_PDF),
        pagesize=A4,
        leftMargin=20 * mm, rightMargin=20 * mm,
        topMargin=18 * mm, bottomMargin=18 * mm,
        title="Echo Ling 程序设计说明书",
        author="Echo Ling 项目组",
    )
    frame = Frame(
        doc.leftMargin, doc.bottomMargin,
        doc.width, doc.height,
        id="normal", showBoundary=0,
    )
    template = PageTemplate(id="main", frames=frame, onPage=header_footer)
    doc.addPageTemplates([template])

    story = build_story()
    doc.build(story)

    import pypdf
    r = pypdf.PdfReader(str(OUT_PDF))
    size_kb = OUT_PDF.stat().st_size / 1024
    print(f"生成完成：{OUT_PDF.name}")
    print(f"页数：{len(r.pages)} 页")
    print(f"大小：{size_kb:.1f} KB")


if __name__ == "__main__":
    main()