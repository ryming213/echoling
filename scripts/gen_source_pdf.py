#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为「听言英语」软著登记生成源程序 PDF。

输出格式：A4 纵向 / 仿宋（simfang） / 5 号字 / 单倍行距 /
        页眉（左：听言英语 V1.0，右：第 N 页）/ 页脚（居中：源程序）

输出页结构：
  - 一份「全本」PDF（所有 113 个 .kt 文件按包路径排序），方便校对；
  - 一份「前 30 页 + 后 30 页」裁剪版，作为软著提交版。

依赖：reportlab 5.x + pymupdf 1.27.x（已安装）。
"""

from __future__ import annotations
import os
import sys
import glob
from pathlib import Path
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.lib.colors import black
import fitz  # PyMuPDF

# 1 PT = 1 reportlab unit; 1 mm = 2.83464567 PT
PT = 1.0
MM = 72.0 / 25.4

# --------------------------------------------------------------------------
# 配置
# --------------------------------------------------------------------------
PROJECT_ROOT = Path("c:/Users/MING/myagent/echoling")
SRC_DIR = PROJECT_ROOT / "app/src/main/java"
OUT_FULL = PROJECT_ROOT / "docs/registration/听言英语_V1.0_源程序_全本.pdf"
OUT_TRIMMED = PROJECT_ROOT / "docs/registration/听言英语_V1.0_源程序_前30页_后30页.pdf"

APP_NAME = "听言英语 V1.0"
FOOTER_NOTE = "听言英语源程序（仅供计算机软件著作权登记使用）"

# 仿宋（最常用的软著字体），Windows 自带
SIMFANG_TTF = "c:/Windows/Fonts/simfang.ttf"
if not Path(SIMFANG_TTF).exists():
    raise SystemExit(f"找不到仿宋字体: {SIMFANG_TTF}")

# 字体注册
pdfmetrics.registerFont(TTFont("Simfang", SIMFANG_TTF))

# 页面参数
PAGE_W, PAGE_H = A4  # 595 × 842 PT
MARGIN_L = 18 * MM   # 18mm 左
MARGIN_R = 18 * MM   # 18mm 右
MARGIN_T = 22 * MM   # 22mm 顶（留空间给页眉）
MARGIN_B = 20 * MM   # 20mm 底

CODE_FONT = "Simfang"
CODE_FONT_SIZE = 8.5  # 5 号 ≈ 10.5pt，为放代码调小到 8.5pt（接近 7 号）
HEADER_FONT_SIZE = 9
FOOTER_FONT_SIZE = 8

LINE_HEIGHT = 11.4  # 单倍行距（适于 8.5pt 字体）


# --------------------------------------------------------------------------
# 收集 / 排序源码文件
# --------------------------------------------------------------------------
def collect_kt_files() -> list[Path]:
    files = sorted(SRC_DIR.rglob("*.kt"))
    # 软著/包路径排序：先按相对 SRC_DIR 的路径字符串字典序
    return files


# --------------------------------------------------------------------------
# PDF 生成
# --------------------------------------------------------------------------
class SourcePDF:
    def __init__(self, out_path: Path):
        self.out_path = out_path
        self.c = canvas.Canvas(str(out_path), pagesize=A4)
        self.page_num = 0
        # 初始化第一页（设置 text_x / text_y / max_y）
        self._new_page()

    def _draw_header(self):
        # 横线
        self.c.setStrokeColor(black)
        self.c.setLineWidth(0.4)
        self.c.line(MARGIN_L, PAGE_H - MARGIN_T + 12 * PT,
                    PAGE_W - MARGIN_R, PAGE_H - MARGIN_T + 12 * PT)

        # 页眉左：软件名称
        self.c.setFont(CODE_FONT, HEADER_FONT_SIZE)
        self.c.drawString(MARGIN_L, PAGE_H - MARGIN_T + 14 * PT, APP_NAME)
        # 页眉右：第 N 页 / 共 ? 页（这里只写当前页）
        self.c.drawRightString(PAGE_W - MARGIN_R,
                               PAGE_H - MARGIN_T + 14 * PT,
                               f"第 {self.page_num} 页")

    def _draw_footer(self):
        # 横线
        self.c.setStrokeColor(black)
        self.c.setLineWidth(0.4)
        self.c.line(MARGIN_L, MARGIN_B - 8 * PT,
                    PAGE_W - MARGIN_R, MARGIN_B - 8 * PT)
        # 页脚居中：源程序水印
        self.c.setFont(CODE_FONT, FOOTER_FONT_SIZE)
        self.c.drawCentredString(PAGE_W / 2, MARGIN_B - 18 * PT,
                                 FOOTER_NOTE)

    def _new_page(self):
        if self.page_num > 0:
            self.c.showPage()
        self.page_num += 1
        self._draw_header()
        self._draw_footer()

        # 文字区域
        self.text_x = MARGIN_L
        self.text_y = PAGE_H - MARGIN_T  # 顶部开始往下
        self.max_y = MARGIN_B + 4 * PT   # 文字底线

    def _write_line(self, line: str) -> bool:
        """写一行；如本页装不下则开新页。"""
        text_w = self.c.stringWidth(line, CODE_FONT, CODE_FONT_SIZE)
        max_w = PAGE_W - MARGIN_L - MARGIN_R

        # 长行自动按字符折行（保留 \n 在写入前的预处理里）
        if text_w > max_w:
            # 按宽度切：在视觉宽度安全处切。
            # 我们用近似（每字符按 ASCII/CJK 平均宽度 4.5pt 估）。
            approx_chars = int(max_w / (CODE_FONT_SIZE * 0.5)) - 2
            chunks = []
            buf = ""
            for ch in line:
                if self.c.stringWidth(buf + ch, CODE_FONT, CODE_FONT_SIZE) > max_w:
                    chunks.append(buf)
                    buf = ch
                else:
                    buf += ch
            if buf:
                chunks.append(buf)
            for chunk in chunks:
                if self.text_y < self.max_y + LINE_HEIGHT:
                    self._new_page()
                self.c.setFont(CODE_FONT, CODE_FONT_SIZE)
                self.c.drawString(self.text_x, self.text_y, chunk)
                self.text_y -= LINE_HEIGHT
            return True

        if self.text_y < self.max_y + LINE_HEIGHT:
            self._new_page()
        self.c.setFont(CODE_FONT, CODE_FONT_SIZE)
        self.c.drawString(self.text_x, self.text_y, line)
        self.text_y -= LINE_HEIGHT
        return True

    def write_file(self, path: Path):
        rel = path.relative_to(PROJECT_ROOT)
        # 文件分隔行
        for _ in range(2):
            self._write_line("")
        self._write_line(f"// === {rel} ===")
        self._write_line("")
        # 文件正文（去 BOM、统一 LF→CRLF）
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            text = path.read_text(encoding="gbk", errors="replace")
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        for line in text.split("\n"):
            # 替换 tab 为 4 空格（避免单 char tab 让报告库算宽时偏小）
            line = line.replace("\t", "    ")
            self._write_line(line)

    def finalize(self):
        self.c.save()


# --------------------------------------------------------------------------
# 用 PyMuPDF 裁剪「前 30 页 + 后 30 页」
# --------------------------------------------------------------------------
def extract_first_last_pages(full_pdf: Path, out_pdf: Path,
                              first_n: int = 30, last_n: int = 30):
    doc = fitz.open(str(full_pdf))
    total = doc.page_count
    if total <= first_n + last_n:
        print(f"全本仅 {total} 页，少于 {first_n + last_n} 页，跳过裁剪，"
              f"直接拷贝全本。")
        out_pdf.write_bytes(full_pdf.read_bytes())
        doc.close()
        return

    keep_idx = list(range(first_n)) + list(range(total - last_n, total))

    out = fitz.open()
    for i in keep_idx:
        out.insert_pdf(doc, from_page=i, to_page=i)

    # §trimmer-opt: 不重写页眉/页脚文本，避免 PyMuPDF 在每页都嵌入完整 Simfang 字体
    # 导致 PDF 暴增（实测 17.9 MB → 改成不重写后约 750 KB）。
    # 原始页眉「第 N 页」保留：前 30 页显示 1..30，后 30 页显示 241..271，
    # 软著审查只关心「60 页是否齐全」，不要求页码连续。

    out.save(str(out_pdf), garbage=4, deflate=True, clean=True)
    out.close()
    doc.close()
    print(f"裁剪完毕：保留前 {first_n} 页 + 后 {last_n} 页 → {out_pdf.name}")


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def main():
    files = collect_kt_files()
    print(f"收集到 {len(files)} 个 Kotlin 源文件")
    if not files:
        raise SystemExit("没找到源文件")

    print(f"写入全本 → {OUT_FULL}")
    pdf = SourcePDF(OUT_FULL)
    for i, p in enumerate(files, 1):
        print(f"  [{i:3}/{len(files)}] {p.relative_to(PROJECT_ROOT)}")
        pdf.write_file(p)
    pdf.finalize()
    print(f"全本完成，共 {pdf.page_num} 页")

    print(f"裁剪前 30 + 后 30 → {OUT_TRIMMED}")
    extract_first_last_pages(OUT_FULL, OUT_TRIMMED, 30, 30)
    print(f"裁剪完成：{OUT_TRIMMED}")


if __name__ == "__main__":
    main()
