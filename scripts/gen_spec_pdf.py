#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为「听言英语」软著登记生成「程序设计说明书」PDF。

输入：docs/registration/听言英语_V1.0_程序设计说明书.md
输出：docs/registration/听言英语_V1.0_程序设计说明书.pdf

输出格式：A4 纵向 / 仿宋（simfang） / 小四号字（12pt）/ 1.5 倍行距 /
        页眉（左：听言英语 V1.0，右：第 N 页）/ 页脚（居中：程序设计说明书）

依赖：reportlab 5.x + markdown 3.x（已安装）。
"""

from __future__ import annotations
import re
import sys
from pathlib import Path
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle


# --------------------------------------------------------------------------
# ASCII art 行的全角化（解决 CJK / Box Drawing / ASCII 混合时列错位）
# --------------------------------------------------------------------------
# Windows 上任何"等宽"字体（MS Gothic / SimSun / 仿宋）都遵循 East Asian Width 标准：
# ASCII char 是半角 (advance 128)，CJK / Box Drawing / Hiragana / Katakana 是全角 (advance 256)。
# §5.1 架构图长横线 `───...───┐` 跟 `│  UI Layer (Compose)  │` 宽度不一致，
# 横线两端会"伸出去"。修复：检测 box-drawing art 行，把 ASCII 转全角（同 advance=256）。
#
# 启发式：行首字符 ∈ {┌ ├ └ │ ┬ ─ ┴ ┼ ┤} OR 行尾字符 ∈ {┐ ┘ │ ┤ ┴ ┼}
#        OR 包含 ≥ 5 个连续 ─
def _is_art_line(line: str) -> bool:
    if not line:
        return False
    first = line.lstrip()[:1]
    last = line.rstrip()[-1:] if line.rstrip() else ""
    box_chars = "┌├└│┬─┴┼┤"
    if first in box_chars or last in "┐┘│┤┴┼":
        return True
    run = 0
    for c in line:
        if c == "─":
            run += 1
            if run >= 5:
                return True
        else:
            run = 0
    return False


_FW_OFFSET = 0xFEE0  # ASCII → 全角基础偏移


def _ascii_to_fullwidth(ch: str) -> str:
    """单字符 ASCII → 全角。Space 用 U+3000；其他走 0xFEE0 偏移。"""
    cp = ord(ch)
    if cp == 0x20:
        return "　"  # 全角空格
    if cp == 0x00B7:  # '·' (middle dot)
        return "・"  # katakana middle dot
    if 0x21 <= cp <= 0x7E:
        return chr(cp + _FW_OFFSET)
    return ch


def _art_to_fullwidth(line: str) -> str:
    """如果是 ASCII art 行，转全角。"""
    if not _is_art_line(line):
        return line
    return "".join(_ascii_to_fullwidth(c) for c in line)
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    PageBreak,
    KeepTogether,
)
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.lib import colors

# --------------------------------------------------------------------------
# 配置
# --------------------------------------------------------------------------
PROJECT_ROOT = Path("c:/Users/MING/myagent/echoling")
MD_PATH = PROJECT_ROOT / "docs/registration/听言英语_V1.0_程序设计说明书.md"
OUT_PDF = PROJECT_ROOT / "docs/registration/听言英语_V1.0_程序设计说明书.pdf"

APP_NAME = "听言英语 V1.0"
FOOTER_NOTE = "听言英语 V1.0 程序设计说明书"

# 仿宋（最常用的软著字体），Windows 自带
SIMFANG_TTF = "c:/Windows/Fonts/simfang.ttf"
if not Path(SIMFANG_TTF).exists():
    raise SystemExit(f"找不到仿宋字体: {SIMFANG_TTF}")

# Courier New — 已不再用作代码块字体（它不含 CJK）
COUR_TTF = "c:/Windows/Fonts/cour.ttf"

# MS Gothic — 代码块字体（msgothic.ttc, Windows 自带）
# §5.1 架构图 / §8.1 流程图 等 ASCII art 需要 monospace + CJK + Box Drawing 三者同时支持，
# Windows 上只有 MS Gothic 同时满足这三项。Courier / Consolas / Lucida Console 都不含 CJK；
# Simfang 是 CJK 字体但 ASCII 字符宽度只有 CJK 的一半（128/256 advance），
# 导致长横线 `─` 跟 ASCII 空格 ` ` 不对齐 —— 表现就是 box top/bottom 线 "伸出去"。
# MS Gothic 是真 monospace：每个字符 advance 都一样，Box Drawing / CJK / ASCII 三者同宽。
MSGOTHIC_TTC = "c:/Windows/Fonts/msgothic.ttc"
if not Path(MSGOTHIC_TTC).exists():
    raise SystemExit(f"找不到 MS Gothic 字体: {MSGOTHIC_TTC}")

# 字体注册
pdfmetrics.registerFont(TTFont("Simfang", SIMFANG_TTF))
pdfmetrics.registerFont(TTFont("Simfang-Bold", SIMFANG_TTF))  # 仿宋无 Bold 字重，复用
# MS Gothic 在 msgothic.ttc 里是 subfontIndex=0（MS UI Gothic=1, MS PGothic=2）
pdfmetrics.registerFont(TTFont("MSGothic", MSGOTHIC_TTC, subfontIndex=0))

# 页面参数
PAGE_W, PAGE_H = A4  # 595 × 842 PT
MARGIN_L = 25 * mm   # 25mm 左（正文留宽裕）
MARGIN_R = 25 * mm   # 25mm 右
MARGIN_T = 22 * mm   # 22mm 顶
MARGIN_B = 22 * mm   # 22mm 底

# 字体与行距
BODY_FONT = "Simfang"
BODY_FONT_SIZE = 12          # 小四 ≈ 12pt
HEADING_SIZES = {1: 18, 2: 15, 3: 13, 4: 12, 5: 12, 6: 12}
LINE_HEIGHT_MULTIPLIER = 1.6  # 1.5 倍行距
HEADER_FONT_SIZE = 9
FOOTER_FONT_SIZE = 9


# --------------------------------------------------------------------------
# 段落样式
# --------------------------------------------------------------------------
def make_styles() -> dict:
    styles = {}
    # 正文
    styles["body"] = ParagraphStyle(
        "body",
        fontName=BODY_FONT,
        fontSize=BODY_FONT_SIZE,
        leading=BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER,
        alignment=TA_JUSTIFY,
        firstLineIndent=2 * BODY_FONT_SIZE,  # 首行缩进 2 字
        spaceBefore=2,
        spaceAfter=2,
    )
    styles["body_noindent"] = ParagraphStyle(
        "body_noindent",
        parent=styles["body"],
        firstLineIndent=0,
    )
    # 标题
    for level in (1, 2, 3, 4, 5, 6):
        size = HEADING_SIZES[level]
        styles[f"h{level}"] = ParagraphStyle(
            f"h{level}",
            fontName=BODY_FONT,
            fontSize=size,
            leading=size * 1.4,
            alignment=TA_LEFT,
            spaceBefore=12 if level <= 2 else 8,
            spaceAfter=8 if level <= 2 else 4,
            textColor=colors.black,
            keepWithNext=True,
        )
    # 标题 1 居中放大
    styles["h1"].alignment = TA_CENTER
    styles["h1"].spaceBefore = 18
    styles["h1"].spaceAfter = 14
    # 列表
    styles["list_bullet"] = ParagraphStyle(
        "list_bullet",
        parent=styles["body_noindent"],
        leftIndent=BODY_FONT_SIZE * 1.5,
        bulletIndent=BODY_FONT_SIZE * 0.5,
        spaceBefore=1,
        spaceAfter=1,
    )
    styles["list_number"] = ParagraphStyle(
        "list_number",
        parent=styles["body_noindent"],
        leftIndent=BODY_FONT_SIZE * 2,
        bulletIndent=BODY_FONT_SIZE * 0.5,
        spaceBefore=1,
        spaceAfter=1,
    )
    # 代码块
    styles["code"] = ParagraphStyle(
        "code",
        fontName=BODY_FONT,
        fontSize=BODY_FONT_SIZE - 2,  # 10pt
        leading=(BODY_FONT_SIZE - 2) * 1.3,
        alignment=TA_LEFT,
        leftIndent=BODY_FONT_SIZE,
        rightIndent=BODY_FONT_SIZE,
        backColor=colors.whitesmoke,
        borderColor=colors.lightgrey,
        borderWidth=0.5,
        borderPadding=6,
        spaceBefore=4,
        spaceAfter=4,
    )
    # 引用
    styles["quote"] = ParagraphStyle(
        "quote",
        parent=styles["body"],
        leftIndent=BODY_FONT_SIZE * 2,
        rightIndent=BODY_FONT_SIZE * 2,
        textColor=colors.darkblue,
        spaceBefore=4,
        spaceAfter=4,
    )
    # 表格单元格
    styles["th"] = ParagraphStyle(
        "th",
        fontName=BODY_FONT,
        fontSize=BODY_FONT_SIZE - 1,
        leading=(BODY_FONT_SIZE - 1) * 1.4,
        alignment=TA_CENTER,
        textColor=colors.white,
    )
    styles["td"] = ParagraphStyle(
        "td",
        fontName=BODY_FONT,
        fontSize=BODY_FONT_SIZE - 1,
        leading=(BODY_FONT_SIZE - 1) * 1.4,
        alignment=TA_LEFT,
    )
    styles["td_center"] = ParagraphStyle(
        "td_center",
        parent=styles["td"],
        alignment=TA_CENTER,
    )
    return styles


# --------------------------------------------------------------------------
# Markdown 行内格式转换（粗体 / 斜体 / 代码 / 链接）
# --------------------------------------------------------------------------
def inline_md_to_html(s: str) -> str:
    """将 Markdown 行内语法转换为 reportlab Paragraph 支持的 HTML 子集。"""
    # 转义 reportlab 视为特殊的字符（避免 XML 解析错误）
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # 还原已知标签
    # 先抽出 <...> 标签暂存
    preserved = []
    def _keep(m):
        preserved.append(m.group(0))
        return f"\x00P{len(preserved)-1}\x00"
    s = re.sub(r"<\/?[a-zA-Z][^>]*>", _keep, s)

    # 行内代码 `...` —— 必须先处理，它会把 `viewmodel/*` 中的 `*` 包到 font 标签内
    # 把代码 span 内容存到 pres_code 暂存（保留 `*` 等字面字符）
    code_spans = []
    def _code(m):
        code_spans.append(m.group(1))
        return f"\x00C{len(code_spans)-1}\x00"
    s = re.sub(r"`([^`]+)`", _code, s)

    # 粗体 **...** 或 __...__
    s = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"__([^_]+)__", r"<b>\1</b>", s)
    # 链接 [text](url)
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r'<font color="#0066cc"><u>\1</u></font>', s)

    # 还原代码 span（用 BODY_FONT 字体的 font 标签包裹，保留 `*` 等字面字符）
    def _restore_code(m):
        idx = int(m.group(1))
        # code_spans 在 line 195 转义之后才提取（line 210），所以 span 内容
        # 已经是 `&lt;String&gt;` 这种转义形式，**不要**再转义一次 —— 否则
        # `&lt;` → `&amp;lt;`，reportlab 解码后变成字面 `&lt;` 显示。
        text = code_spans[idx]
        return f'<font face="{BODY_FONT}" color="#333333">{text}</font>'
    s = re.sub(r"\x00C(\d+)\x00", _restore_code, s)

    # 还原保留的 HTML 标签
    def _restore(m):
        idx = int(m.group(1))
        return preserved[idx].replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    s = re.sub(r"\x00P(\d+)\x00", _restore, s)
    return s


# --------------------------------------------------------------------------
# Markdown → Flowables 转换
# --------------------------------------------------------------------------
class MarkdownConverter:
    def __init__(self, styles: dict):
        self.styles = styles
        self.flowables = []
        self.in_code_block = False
        self.code_buffer = []
        self.code_lang = ""

    def feed_line(self, line: str):
        """处理单行（已 strip 末尾换行）。"""
        # 代码块
        if line.startswith("```"):
            if not self.in_code_block:
                self.in_code_block = True
                self.code_buffer = []
                self.code_lang = line[3:].strip()
            else:
                self.in_code_block = False
                # 用 Paragraph 渲染代码（Paragraph 可跨页 split）
                from reportlab.platypus import Paragraph as _P
                code_style = ParagraphStyle(
                    "codeblock",
                    # 用 MS Gothic：唯一同时满足 monospace + CJK + Box Drawing 的 Windows 自带字体。
                    # 之前用 Simfang 牺牲等宽对齐换 CJK 字形覆盖，结果 ASCII space (128 advance)
                    # 跟 CJK / Box Drawing (256 advance) 不对齐，§5.1 长横线伸出去；
                    # 之前用 Courier 不含 CJK 导致 §5.1 连接线 `│ 事件回调` 里的中文变 tofu。
                    # MS Gothic (msgothic.ttc, subfontIndex=0) 是真 monospace，
                    # Box Drawing / CJK / ASCII 三者同 advance，ASCII art 列对齐正确。
                    fontName="MSGothic",
                    fontSize=BODY_FONT_SIZE - 2,
                    leading=(BODY_FONT_SIZE - 2) * 1.3,
                    alignment=TA_LEFT,
                    leftIndent=4,
                    rightIndent=4,
                    textColor=colors.black,
                    backColor=colors.whitesmoke,
                    borderColor=colors.lightgrey,
                    borderWidth=0.5,
                    borderPadding=4,
                )
                # 把每行代码转成一个独立的 Paragraph，每个都能跨页 split
                # 用 KeepTogether 把小段（最多 20 行）绑定，超出则分多个段
                # 为简单起见：每行一个 Paragraph，靠 backColor/borderPadding 自然形成视觉块
                self.flowables.append(Spacer(1, 4))
                # 用一个 1×1 Table 给整个代码块加底色，但 Paragraph 拆分到每行
                # —— 实际上最稳妥：每行一个 Paragraph 用 backColor 无边框，
                # 用 Spacer 模拟块首尾空白
                block_paras = []
                for ln in self.code_buffer:
                    # ASCII art 行（box-drawing 横线 / 边框）转全角，
                    # 否则 MS Gothic 把 ASCII (advance 128) 跟 CJK/Box Drawing (advance 256)
                    # 混排时列对不齐。Kotlin 等真实代码不受启发式影响。
                    fw = _art_to_fullwidth(ln)
                    escaped = (
                        fw.replace("&", "&amp;")
                          .replace("<", "&lt;")
                          .replace(">", "&gt;")
                    )
                    # 单行：用   不间断空格代替普通空格保持缩进
                    block_paras.append(_P(escaped.replace(" ", " ") or "&nbsp;", code_style))
                # 把整段代码块作为一个 Table 行（不可 split），但内部 Paragraph 是不可分的最小单元
                # —— 更安全的做法：直接顺序 append 每个 Paragraph，并加 Spacer 隔开
                # 退而求其次：用 KeepTogether 把小段绑一起
                from reportlab.platypus import KeepTogether
                chunk_size = 12  # 每 12 行一组
                for i in range(0, len(block_paras), chunk_size):
                    chunk = block_paras[i:i + chunk_size]
                    self.flowables.append(KeepTogether(chunk))
                self.flowables.append(Spacer(1, 4))
            return

        if self.in_code_block:
            self.code_buffer.append(line)
            return

        # 空格行

        # 空行

        # 空行
        if not line.strip():
            self.flowables.append(Spacer(1, BODY_FONT_SIZE * 0.5))
            return

        # 水平分割线 ---
        if re.match(r"^-{3,}$", line.strip()):
            # 简单用横线 paragraph
            self.flowables.append(Spacer(1, 4))
            line_para = Paragraph(
                '<hr width="100%" color="#999999" thickness="0.5"/>',
                ParagraphStyle("hr", fontName=BODY_FONT, fontSize=2),
            )
            self.flowables.append(line_para)
            self.flowables.append(Spacer(1, 4))
            return

        # 标题
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            level = len(m.group(1))
            text = m.group(2).strip()
            text_html = inline_md_to_html(text)
            self.flowables.append(Paragraph(text_html, self.styles[f"h{level}"]))
            return

        # 引用块
        m = re.match(r"^>\s?(.*)$", line)
        if m:
            text = m.group(1).strip()
            text_html = inline_md_to_html(text)
            self.flowables.append(Paragraph(text_html, self.styles["quote"]))
            return

        # 无序列表
        m = re.match(r"^(\s*)[-*+]\s+(.*)$", line)
        if m:
            indent = len(m.group(1)) // 2
            text = m.group(2).strip()
            text_html = inline_md_to_html(text)
            style = ParagraphStyle(
                f"list_bullet_{indent}",
                parent=self.styles["list_bullet"],
                leftIndent=BODY_FONT_SIZE * (1.5 + indent * 1.5),
                bulletIndent=BODY_FONT_SIZE * (0.5 + indent * 1.5),
            )
            bullet = "●" if indent == 0 else ("○" if indent == 1 else "·")
            self.flowables.append(Paragraph(
                f'{bullet}&nbsp;&nbsp;{text_html}', style
            ))
            return

        # 有序列表
        m = re.match(r"^(\s*)(\d+)\.\s+(.*)$", line)
        if m:
            num = m.group(2)
            text = m.group(3).strip()
            text_html = inline_md_to_html(text)
            self.flowables.append(Paragraph(
                f'{num}.&nbsp;&nbsp;{text_html}', self.styles["list_number"]
            ))
            return

        # 表格（简化处理：只识别 `| ... |` 行）
        if line.lstrip().startswith("|") and line.rstrip().endswith("|"):
            # 表格由调用方在外部累积处理（这里暂作段落处理，会跳过连续表格）
            self._table_buffer.append(line)
            return

        # 普通段落
        text_html = inline_md_to_html(line.strip())
        # 含中文标点的段落不加首行缩进（已经 inline 自然断行）
        self.flowables.append(Paragraph(text_html, self.styles["body"]))

    # 表格累积
    _table_buffer: list = []

    def feed(self, lines: list[str]):
        # 预处理：抽出连续表格
        cleaned = []
        i = 0
        while i < len(lines):
            if lines[i].lstrip().startswith("|") and lines[i].rstrip().endswith("|"):
                # 收集连续表格行
                tbl = []
                while i < len(lines) and lines[i].lstrip().startswith("|") and lines[i].rstrip().endswith("|"):
                    tbl.append(lines[i])
                    i += 1
                self._render_table(tbl)
            else:
                cleaned.append(lines[i])
                i += 1
        # 渲染剩余
        for ln in cleaned:
            self.feed_line(ln)

    def _render_table(self, tbl_lines: list[str]):
        if len(tbl_lines) < 2:
            return
        # 第二行是分隔符 `|---|---|`
        # 解析每行
        rows = []
        for ln in tbl_lines:
            cells = [c.strip() for c in ln.strip().strip("|").split("|")]
            rows.append(cells)
        # 第二行是分隔符（仅含 - : 等符号）
        if all(re.match(r"^[\s:|-]+$", c) for c in rows[1]):
            header = rows[0]
            data = rows[2:]
        else:
            header = None
            data = rows
        # 转 HTML
        def _cell(text: str, style_key: str) -> Paragraph:
            return Paragraph(inline_md_to_html(text), self.styles[style_key])
        table_data = []
        if header:
            table_data.append([_cell(c, "th") for c in header])
        for row in data:
            table_data.append([_cell(c, "td") for c in row])
        # 计算列宽：等分可用宽度
        n_cols = max(len(r) for r in table_data)
        avail_w = PAGE_W - MARGIN_L - MARGIN_R
        col_w = avail_w / n_cols
        # 创建 Table（允许跨页 split）
        t = Table(
            table_data,
            colWidths=[col_w] * n_cols,
            repeatRows=1 if header else 0,
            splitByRow=1,
        )
        style_cmds = [
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#7C3AED")),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
            ("FONTNAME", (0, 0), (-1, -1), BODY_FONT),
            ("FONTSIZE", (0, 0), (-1, -1), BODY_FONT_SIZE - 1),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
            ("LEFTPADDING", (0, 0), (-1, -1), 4),
            ("RIGHTPADDING", (0, 0), (-1, -1), 4),
            ("TOPPADDING", (0, 0), (-1, -1), 4),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ]
        # 斑马纹
        for i in range(1, len(table_data)):
            if i % 2 == 0:
                style_cmds.append(
                    ("BACKGROUND", (0, i), (-1, i), colors.HexColor("#F3EEFE"))
                )
        t.setStyle(TableStyle(style_cmds))
        self.flowables.append(Spacer(1, 4))
        self.flowables.append(t)
        self.flowables.append(Spacer(1, 4))


# --------------------------------------------------------------------------
# 页眉 / 页脚绘制回调
# --------------------------------------------------------------------------
def draw_header_footer(canvas_obj, doc):
    canvas_obj.saveState()
    # 页眉横线
    canvas_obj.setStrokeColor(colors.black)
    canvas_obj.setLineWidth(0.5)
    canvas_obj.line(MARGIN_L, PAGE_H - MARGIN_T + 12, PAGE_W - MARGIN_R, PAGE_H - MARGIN_T + 12)
    # 页眉左：软件名称
    canvas_obj.setFont(BODY_FONT, HEADER_FONT_SIZE)
    canvas_obj.drawString(MARGIN_L, PAGE_H - MARGIN_T + 14, APP_NAME)
    # 页眉右：第 N 页
    canvas_obj.drawRightString(PAGE_W - MARGIN_R, PAGE_H - MARGIN_T + 14, f"第 {doc.page} 页")
    # 页脚横线
    canvas_obj.line(MARGIN_L, MARGIN_B - 12, PAGE_W - MARGIN_R, MARGIN_B - 12)
    # 页脚居中：说明
    canvas_obj.setFont(BODY_FONT, FOOTER_FONT_SIZE)
    canvas_obj.drawCentredString(PAGE_W / 2, MARGIN_B - 22, FOOTER_NOTE)
    canvas_obj.restoreState()


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def main():
    if not MD_PATH.exists():
        raise SystemExit(f"找不到 Markdown 源文件: {MD_PATH}")
    print(f"读取 Markdown → {MD_PATH.name}")
    md_text = MD_PATH.read_text(encoding="utf-8")
    lines = md_text.split("\n")

    styles = make_styles()
    converter = MarkdownConverter(styles)
    converter.feed(lines)

    print(f"生成 Flowables: {len(converter.flowables)} 项")

    # Document Template
    doc = BaseDocTemplate(
        str(OUT_PDF),
        pagesize=A4,
        leftMargin=MARGIN_L,
        rightMargin=MARGIN_R,
        topMargin=MARGIN_T,
        bottomMargin=MARGIN_B,
        title="听言英语 V1.0 程序设计说明书",
        author="听言英语开发组",
    )
    frame = Frame(
        MARGIN_L,
        MARGIN_B,
        PAGE_W - MARGIN_L - MARGIN_R,
        PAGE_H - MARGIN_T - MARGIN_B,
        id="main",
        showBoundary=0,
    )
    template = PageTemplate(id="main", frames=[frame], onPage=draw_header_footer)
    doc.addPageTemplates([template])

    doc.build(converter.flowables)
    print(f"完成 → {OUT_PDF}")

    # 报页数
    import fitz
    pdf = fitz.open(str(OUT_PDF))
    print(f"PDF 总页数：{pdf.page_count}")
    pdf.close()


if __name__ == "__main__":
    main()