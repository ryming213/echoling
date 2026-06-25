#!/usr/bin/env python3
"""Generate multi-density launcher icons from a single source PNG."""
import os
from PIL import Image, ImageDraw

SRC = r"c:\Users\MING\myagent\echoling\app\src\main\res\drawable\ic_app_icon.png"
RES = r"c:\Users\MING\myagent\echoling\app\src\main\res"

# Density bucket sizes (px)
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

def make_round(img: Image.Image) -> Image.Image:
    """Apply circular alpha mask (used for *_round icons)."""
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out

def main():
    if not os.path.exists(SRC):
        print(f"Source not found: {SRC}")
        return
    src = Image.open(SRC).convert("RGBA")
    print(f"Source: {src.size}, mode={src.mode}")

    # 1. Traditional square launcher icons (5 densities)
    for density, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        resized = src.resize((size, size), Image.LANCZOS)
        path = os.path.join(out_dir, "ic_launcher.png")
        resized.save(path, "PNG", optimize=True)
        print(f"  {density:8s} {size:3d}x{size:<3d} -> {path}")

    # 2. Traditional round launcher icons (5 densities)
    for density, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        resized = src.resize((size, size), Image.LANCZOS)
        rounded = make_round(resized)
        path = os.path.join(out_dir, "ic_launcher_round.png")
        rounded.save(path, "PNG", optimize=True)
        print(f"  {density:8s} {size:3d}x{size:<3d} (round) -> {path}")

    # 3. Adaptive icon foreground: 108x108 viewport, 72x72 centered content.
    #    For a full-frame design we use the source as-is centered.
    fg_size = 432  # xxxhdpi-equivalent for adaptive icon spec
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    inner = src.resize((fg_size * 2 // 3, fg_size * 2 // 3), Image.LANCZOS)  # 72/108 ratio
    offset = (fg_size - inner.size[0]) // 2
    fg.paste(inner, (offset, offset), inner)
    fg_dir = os.path.join(RES, "drawable")
    fg_path = os.path.join(fg_dir, "ic_app_icon_foreground.png")
    fg.save(fg_path, "PNG", optimize=True)
    print(f"  fg       432x432 (adaptive foreground) -> {fg_path}")

    # 4. Adaptive icon background: just the source at xxxhdpi equivalent
    bg_size = 432
    bg = src.resize((bg_size, bg_size), Image.LANCZOS)
    bg_path = os.path.join(fg_dir, "ic_app_icon_background.png")
    bg.save(bg_path, "PNG", optimize=True)
    print(f"  bg       432x432 (adaptive background) -> {bg_path}")

    # 5. HomeScreen drawable: 256x256 (displayed at 80dp on xxhdpi = 240px)
    home = src.resize((256, 256), Image.LANCZOS)
    home_path = os.path.join(fg_dir, "ic_app_icon.png")  # overwrite original
    home.save(home_path, "PNG", optimize=True)
    print(f"  home     256x256 (HomeScreen, overwrites source) -> {home_path}")

    print("\nDone.")

if __name__ == "__main__":
    main()