"""Generate a PDF from PracticeViewModel.kt: first 30 pages + last 30 pages.

Layout: A4, monospace 8.5pt, ~50 lines/page (lines_of_code + banner).
Per file banner shows the start/end line numbers so the reviewer can
cross-check with the source file.

Output: app/build/PracticeViewModel_源程序_首尾各30页.pdf
"""
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.pdfgen import canvas

ROOT = Path(r"c:/Users/MING/myagent/echoling")
SRC_FILE = ROOT / "app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt"
OUT = ROOT / "app/build"
OUT.mkdir(parents=True, exist_ok=True)

# STSong-Light = bundled reportlab Chinese CID font (handles ASCII + CJK).
pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
FONT_NAME = "STSong-Light"
FONT_SIZE = 8.5
# Layout tuned so LINES_PER_PAGE == 50 (软著 standard ~50 lines/A4 page).
# A4 page height = 842pt. With 25mm top + 25mm bottom = 71pt+71pt, USABLE_H = 700pt.
# 700pt / 14pt-per-line = 50 lines/page exactly.
LINE_HEIGHT = 14.0
LEFT_MARGIN = 18 * mm
RIGHT_MARGIN = 18 * mm
TOP_MARGIN = 25 * mm
BOTTOM_MARGIN = 25 * mm
PAGE_W, PAGE_H = A4
USABLE_H = PAGE_H - TOP_MARGIN - BOTTOM_MARGIN  # ≈ 700pt
LINES_PER_PAGE = int(round(USABLE_H / LINE_HEIGHT))  # = 50
LINES_PER_PAGE_TARGET = 50
N_PAGES = 30


def read_source() -> list[str]:
    """Read all lines from PracticeViewModel.kt, drop trailing blank lines."""
    text = SRC_FILE.read_text(encoding="utf-8")
    lines = text.splitlines()
    while lines and not lines[-1].strip():
        lines.pop()
    return lines


def slice_front_back(lines: list[str], n_pages: int) -> tuple[list[tuple[int, str]], list[tuple[int, str]]]:
    """Return (front, back) where each entry is (lineno_1based, text).

    Each slice is n_pages * LINES_PER_PAGE_TARGET lines, picked from start/end.
    """
    chunk_size = n_pages * LINES_PER_PAGE_TARGET
    total = len(lines)

    front = [(i + 1, l) for i, l in enumerate(lines[:chunk_size])]
    back_raw = lines[total - chunk_size:] if total > chunk_size else lines[:]
    back_start_lineno = total - len(back_raw) + 1
    back = [(back_start_lineno + i, l) for i, l in enumerate(back_raw)]
    return front, back


def draw_pdf(out_path: Path, slices: list[tuple[int, str]], title_suffix: str, src_total_lines: int):
    """Render one section (front OR back) to a PDF."""
    c = canvas.Canvas(str(out_path), pagesize=A4)
    c.setTitle(f"PracticeViewModel.kt — {title_suffix}")
    c.setAuthor("Echo Ling")
    c.setFont(FONT_NAME, FONT_SIZE)

    line_num = 0   # running line index inside this PDF
    page_num = 1

    # Cover page
    c.setFont(FONT_NAME, 16)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 + 30, "Echo Ling")
    c.setFont(FONT_NAME, 13)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 + 10, "PracticeViewModel.kt")
    c.setFont(FONT_NAME, 11)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 - 6, f"源程序 — {title_suffix}")
    c.setFont(FONT_NAME, 10)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 - 24, f"全文 {src_total_lines} 行")
    c.setFont(FONT_NAME, 8)
    c.drawCentredString(PAGE_W / 2, 30 * mm, "中国软件保护中心 软件著作权登记 申请材料")
    c.showPage()

    # Content pages
    def start_page_header(pnum: int):
        c.setFont(FONT_NAME, 7.5)
        c.setFillGray(0.4)
        c.drawString(LEFT_MARGIN, PAGE_H - 12 * mm,
                     f"Echo Ling 源程序 — PracticeViewModel.kt / {title_suffix}  /  第 {pnum} 页")
        c.setFillGray(0)
        c.setFont(FONT_NAME, FONT_SIZE)

    start_page_header(page_num)
    # File banner — take 2 lines (1 banner + 1 blank)
    banner = f"── FILE: app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt (lines {slices[0][0]}..{slices[-1][0]}) ──"
    for txt in [banner, ""] + [t for _, t in slices]:
        if line_num % LINES_PER_PAGE == 0 and line_num > 0:
            c.showPage()
            page_num += 1
            start_page_header(page_num)
        # Wrap long lines conservatively
        text = txt if len(txt) <= 95 else txt[:95]
        y = PAGE_H - TOP_MARGIN - ((line_num % LINES_PER_PAGE) + 1) * LINE_HEIGHT
        c.drawString(LEFT_MARGIN, y, text)
        line_num += 1

    c.save()
    return page_num


def main():
    lines = read_source()
    total = len(lines)
    print(f"PracticeViewModel.kt: {total} lines total")

    front, back = slice_front_back(lines, N_PAGES)
    print(f"前 {N_PAGES} 页对应: 1..{front[-1][0]} ({len(front)} lines)")
    print(f"后 {N_PAGES} 页对应: {back[0][0]}..{back[-1][0]} ({len(back)} lines)")
    print(f"重叠区: {back[0][0]}..{front[-1][0]} ({front[-1][0] - back[0][0] + 1} lines)")

    front_path = OUT / f"PracticeViewModel_源程序_前{N_PAGES}页.pdf"
    back_path  = OUT / f"PracticeViewModel_源程序_后{N_PAGES}页.pdf"
    merged_path = OUT / f"PracticeViewModel_源程序_首尾各{N_PAGES}页合并.pdf"

    pages_front = draw_pdf(front_path, front, f"前 {N_PAGES} 页", total)
    pages_back  = draw_pdf(back_path,  back,  f"后 {N_PAGES} 页", total)

    # Merge front + back into one PDF (matches 软著 "front 30 + back 30" convention)
    import pypdf
    writer = pypdf.PdfWriter()
    for src in [front_path, back_path]:
        r = pypdf.PdfReader(str(src))
        for p in r.pages:
            writer.add_page(p)
    with open(merged_path, "wb") as f:
        writer.write(f)
    merged_reader = pypdf.PdfReader(str(merged_path))

    print()
    print(f"Generated: {front_path.name}      ({pages_front} pages)")
    print(f"Generated: {back_path.name}       ({pages_back} pages)")
    print(f"Generated: {merged_path.name}  ({len(merged_reader.pages)} pages)")


if __name__ == "__main__":
    main()