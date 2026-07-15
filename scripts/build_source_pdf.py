"""Generate 中国软件保护中心 source code submission PDF for Echo Ling.

- Front 30 pages: from the beginning of the most foundational files
- Back 30 pages: from the end of the latest UI modules
- Page layout: A4, monospace 9pt, ~50 lines/page, header shows file path + running page #
- File separators: visible filename banner every file break

Output:
  app/build/EchoLing_源程序_前30页.pdf
  app/build/EchoLing_源程序_后30页.pdf
"""
from pathlib import Path
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.pdfgen import canvas

ROOT = Path(r"c:/Users/MING/myagent/echoling")
SRC = ROOT / "app/src/main/java/com/echoling/app"
OUT = ROOT / "app/build"
OUT.mkdir(parents=True, exist_ok=True)

# Register the bundled Chinese CID font (STSong-Light ships with reportlab).
# It handles both ASCII (covers ~30% of the codepoints) and CJK Unified Ideographs.
# Trade-off: it's a proportional font, not monospace — Chinese strings won't
# align in columns. Acceptable for 软著 since reviewers verify intent, not
# byte-perfect alignment.
pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
FONT_NAME = "STSong-Light"
FONT_SIZE = 8.5
LINE_HEIGHT = 11.5  # pt, gives ~50 lines on A4 with margins
LEFT_MARGIN = 18 * mm
RIGHT_MARGIN = 18 * mm
TOP_MARGIN = 22 * mm
BOTTOM_MARGIN = 22 * mm
PAGE_W, PAGE_H = A4
USABLE_W = PAGE_W - LEFT_MARGIN - RIGHT_MARGIN
USABLE_H = PAGE_H - TOP_MARGIN - BOTTOM_MARGIN
LINES_PER_PAGE = int(USABLE_H / LINE_HEIGHT)  # ≈ 47 lines default

print(f"Pages: A4, font {FONT_NAME} {FONT_SIZE}pt, line height {LINE_HEIGHT}pt")
print(f"Lines per page: ~{LINES_PER_PAGE}")
print(f"Usable text region: {USABLE_W/72*25.4:.1f}mm x {USABLE_H/72*25.4:.1f}mm")


# ---------------------------------------------------------------------------
# Collect source files in logical order
# ---------------------------------------------------------------------------
# 中国软著 convention: arrange by architectural layer (domain → data → di → presentation → player → speech)
# so the front pages show the foundation and the back pages show the UI.
LAYER_ORDER = [
    "EchoLingApplication.kt",
    # domain layer (foundation)
    "domain/model",
    "domain/repository",
    "domain/usecase",
    # data layer
    "data/local/db",
    "data/local/db/entity",
    "data/local/db/dao",
    "data/repository",
    # di
    "di",
    # core
    "player",
    "speech",
    # presentation (UI on top)
    "presentation",
]

def collect_files() -> list[Path]:
    """Return .kt files sorted by architectural layer, then by path."""
    files = []
    # Use a set to dedupe if a path matches multiple globs
    seen = set()
    for layer in LAYER_ORDER:
        if layer.endswith(".kt"):
            f = SRC / layer
            if f.exists() and f.is_file():
                if f not in seen:
                    seen.add(f)
                    files.append(f)
        else:
            for f in sorted(SRC.rglob("*.kt")):
                if layer in str(f.relative_to(SRC)).replace("\\", "/"):
                    if f not in seen:
                        seen.add(f)
                        files.append(f)
    return files


# ---------------------------------------------------------------------------
# Slice front N / back N pages worth of content
# ---------------------------------------------------------------------------
LINES_PER_PAGE_TARGET = 50


def slice_front_back(blocks: list[tuple[Path, list[str]]], n_pages: int) -> tuple[list[tuple[Path, list[str], int]], list[tuple[Path, list[str], int]]]:
    """Return (front_blocks, back_blocks).

    Each block = (path, lines_of_this_chunk_for_this_file, start_line_number_in_file).
    The "start_line_number" lets the PDF header show "FILE: path (line N)" so the
    reviewer can verify we didn't fabricate content.

    Front: take from start until we hit n_pages worth of lines.
    Back: take from end backward (preferring complete files) until n_pages worth.
    """
    # Flatten to (file, lineno, text) for slicing
    flat = []
    for path, lines in blocks:
        for i, txt in enumerate(lines, 1):
            flat.append((path, i, txt))
    total_lines = len(flat)
    front_count = n_pages * LINES_PER_PAGE_TARGET
    front = flat[:front_count]
    back = flat[-n_pages * LINES_PER_PAGE_TARGET:]

    def to_blocks(slice_):
        out = []
        cur_path, cur_start, cur_lines = None, None, []
        for path, lineno, txt in slice_:
            if path != cur_path:
                if cur_path is not None:
                    out.append((cur_path, cur_lines, cur_start))
                cur_path = path
                cur_start = lineno
                cur_lines = [txt]
            else:
                cur_lines.append(txt)
        if cur_path is not None:
            out.append((cur_path, cur_lines, cur_start))
        return out

    return to_blocks(front), to_blocks(back)


# ---------------------------------------------------------------------------
# PDF drawing
# ---------------------------------------------------------------------------
def draw_pdf(out_path: Path, chunks: list[tuple[Path, list[str], int]], title: str):
    c = canvas.Canvas(str(out_path), pagesize=A4)
    c.setTitle(title)
    c.setAuthor("Echo Ling")
    c.setFont(FONT_NAME, FONT_SIZE)

    page_num = 0
    line_num = 0  # running total for the PDF

    def new_page():
        nonlocal page_num
        page_num += 1
        c.showPage()
        # header
        c.setFont(FONT_NAME, 7.5)
        c.setFillGray(0.4)
        c.drawString(LEFT_MARGIN, PAGE_H - 12 * mm,
                     f"Echo Ling 源程序 — {title}  /  第 {page_num} 页")
        c.setFillGray(0)
        c.setFont(FONT_NAME, FONT_SIZE)

    # Cover page
    c.setFont(FONT_NAME, 16)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 + 20, "Echo Ling")
    c.setFont(FONT_NAME, 12)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2, "英语学习 Android 应用")
    c.setFont(FONT_NAME, 10)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 - 18, title)
    c.drawCentredString(PAGE_W / 2, PAGE_H / 2 - 36, "源程序打印件")
    c.setFont(FONT_NAME, 8)
    c.drawCentredString(PAGE_W / 2, 30 * mm, "中国软件保护中心 软件著作权登记 申请材料")
    c.showPage()
    page_num = 1

    # Render content
    pages_in_pdf = 1  # cover page
    for path, lines, start_lineno in chunks:
        rel = path.relative_to(ROOT).as_posix()
        # File banner — uses half a line, so treat as content
        banner = f"── FILE: {rel}  (lines {start_lineno}..{start_lineno + len(lines) - 1}) ──"
        banner_pad = [banner] + [""]  # blank line after banner

        all_lines = banner_pad + lines
        for txt in all_lines:
            if line_num % LINES_PER_PAGE == 0:
                # start a new page (but skip if there's < 5 lines left; would be a near-empty page)
                if pages_in_pdf > 1:  # always start content on a fresh page after cover
                    new_page()
                elif pages_in_pdf == 1:
                    # start fresh content page after cover
                    c.showPage()
                    page_num = 1
                    c.setFont(FONT_NAME, 7.5)
                    c.setFillGray(0.4)
                    c.drawString(LEFT_MARGIN, PAGE_H - 12 * mm,
                                 f"Echo Ling 源程序 — {title}  /  第 {page_num} 页")
                    c.setFillGray(0)
                    c.setFont(FONT_NAME, FONT_SIZE)
                pages_in_pdf += 1
            # Wrap long lines (basic; preserves ASCII by char-trim if necessary)
            text = txt if len(txt) <= 95 else txt[:95]
            y = PAGE_H - TOP_MARGIN - ((line_num % LINES_PER_PAGE) + 1) * LINE_HEIGHT
            c.drawString(LEFT_MARGIN, y, text)
            line_num += 1

    c.save()
    return pages_in_pdf


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    files = collect_files()
    print(f"Collected {len(files)} Kotlin files in layer order")

    # Read each file's lines
    blocks = []
    for f in files:
        text = f.read_text(encoding="utf-8")
        lines = text.splitlines()
        # Drop a single trailing blank if present
        while lines and not lines[-1].strip():
            lines.pop()
        blocks.append((f, lines))

    front_chunks, back_chunks = slice_front_back(blocks, n_pages=30)

    total_front_lines = sum(len(lines) for _, lines, _ in front_chunks)
    total_back_lines = sum(len(lines) for _, lines, _ in back_chunks)
    print(f"Front chunks: {len(front_chunks)} files, {total_front_lines} lines")
    print(f"Back chunks: {len(back_chunks)} files, {total_back_lines} lines")

    print("\nFront page files (in order):")
    for path, lines, start in front_chunks:
        rel = path.relative_to(ROOT).as_posix()
        print(f"  {rel} (lines {start}..{start + len(lines) - 1}, {len(lines)} lines)")

    print("\nBack page files (in order):")
    for path, lines, start in back_chunks:
        rel = path.relative_to(ROOT).as_posix()
        print(f"  {rel} (lines {start}..{start + len(lines) - 1}, {len(lines)} lines)")

    front_path = OUT / "EchoLing_源程序_前30页.pdf"
    back_path = OUT / "EchoLing_源程序_后30页.pdf"

    pages_front = draw_pdf(front_path, front_chunks, title="前 30 页")
    pages_back = draw_pdf(back_path, back_chunks, title="后 30 页")

    print(f"\nGenerated: {front_path.name}  ({pages_front} pages)")
    print(f"Generated: {back_path.name}  ({pages_back} pages)")


if __name__ == "__main__":
    main()