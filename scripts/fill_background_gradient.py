#!/usr/bin/env python3
"""Rebuild the icon as 3 layers (no seam, solid white text/circle):

Layer 1: BACKGROUND (outside icon body)  -> gradient
Layer 2: ICON BODY (rounded rectangle)    -> gradient
Layer 3: WHITE DETAILS (circle + text)    -> SOLID white on top

Key fix vs the previous version:
- Bbox and corner radius are now derived from the icon-body mask (G<200),
  not from a "non-white" pixel scan that was polluted by the anti-aliased
  rounded-corner halo. The previous scan underestimated the corner radius
  (~194) while the actual icon body has corner radius ~276, which made the
  rounded-rectangle mask spill into the white corner margins and produce
  the "red arcs" in the text mask.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

BACKUP = r"c:\Users\MING\myagent\echoling\scripts\ic_app_icon_original.png"
DEST = r"c:\Users\MING\myagent\echoling\app\src\main\res\drawable\ic_app_icon.png"
DEBUG_MASK = r"c:\Users\MING\myagent\echoling\scripts\debug_text_mask.png"

# Gradient stops (matches original purple gradient)
TOP = (167, 108, 241)
MID = (148, 87, 232)
BOT = (122, 43, 224)


def gradient_color(y_norm):
    y_norm = max(0.0, min(1.0, y_norm))
    if y_norm < 0.5:
        t = y_norm / 0.5
        return tuple(int(TOP[i] + (MID[i] - TOP[i]) * t) for i in range(3))
    else:
        t = (y_norm - 0.5) / 0.5
        return tuple(int(MID[i] + (BOT[i] - BOT[i]) * t) for i in range(3))


def is_white(px, thresh):
    return px[0] >= thresh and px[1] >= thresh and px[2] >= thresh


def main():
    src = Image.open(BACKUP).convert("RGBA")
    w, h = src.size
    sp = src.load()
    print(f"Source: {w}x{h}")

    # Step 1: build icon-body mask. G<200 catches the solid gradient
    # (G<=108) plus a thin anti-aliasing halo. The white background
    # (G=255) and the text/circle (G=255) are NOT in the mask.
    icon_mask = Image.new("L", (w, h), 0)
    imask_px = icon_mask.load()
    for y in range(h):
        for x in range(w):
            if sp[x, y][1] < 200:
                imask_px[x, y] = 255

    # Step 2: find the icon-body bbox from the mask.
    min_x, min_y, max_x, max_y = w, h, 0, 0
    for y in range(h):
        for x in range(w):
            if imask_px[x, y] == 255:
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y
    print(f"Icon body bbox: ({min_x},{min_y}) to ({max_x},{max_y})")

    # Step 3: detect the actual corner radius by sampling the corner
    # curve. At x = min_x + 5, the topmost y in the icon-body mask lies
    # on the corner curve y = min_y + r - sqrt(10r - 25). Solving:
    #     r^2 - (2z + 10) r + (z^2 + 25) = 0
    # where z = test_y - min_y. Take the larger root.
    corner_radius = 0
    sample_x = min_x + 5
    for test_y in range(min_y, max_y + 1):
        if imask_px[sample_x, test_y] == 255:
            z = test_y - min_y
            a, b, c = 1.0, -(2.0 * z + 10.0), z * z + 25.0
            disc = b * b - 4.0 * a * c
            if disc >= 0:
                r1 = (-b + math.sqrt(disc)) / 2.0
                r2 = (-b - math.sqrt(disc)) / 2.0
                corner_radius = int(round(max(r1, r2)))
            else:
                corner_radius = z
            break
    print(f"Detected corner radius: {corner_radius}px (from sample x={sample_x})")

    # Step 4: build a CLEAN rounded-rectangle mask (no holes, geometric).
    # This is the "icon body" layer, with no leak into the corner margins.
    rr_mask = Image.new("L", (w, h), 0)
    rr_draw = ImageDraw.Draw(rr_mask)
    rr_draw.rounded_rectangle(
        [min_x, min_y, max_x, max_y],
        radius=corner_radius,
        fill=255,
    )

    # Step 5: erode the rounded-rectangle mask to exclude the boundary
    # anti-aliasing and any white margin in the corners. 30px is enough
    # because the original anti-aliasing is only ~5px wide.
    erode_px = 30
    eroded_mask = rr_mask.filter(ImageFilter.MinFilter(2 * erode_px + 1))
    eroded_mask_px = eroded_mask.load()

    # Step 6: detect text/circle as white pixels inside the eroded mask.
    # Use threshold 220 to catch the anti-aliased text edges (not just the
    # pure-white centers, which would make the text look broken/dotted).
    text_thresh = 220
    text_mask = Image.new("L", (w, h), 0)
    text_mask_px = text_mask.load()
    initial = 0
    for y in range(h):
        for x in range(w):
            if eroded_mask_px[x, y] == 255 and is_white(sp[x, y], text_thresh):
                text_mask_px[x, y] = 255
                initial += 1
    print(f"Initial text mask (thresh={text_thresh}): {initial} pixels")

    # Step 7: dilate twice with a 3x3 max filter to fill the 1-2 px gaps
    # between stroke centers and make the text/circle look solid.
    text_mask = text_mask.filter(ImageFilter.MaxFilter(3))
    text_mask = text_mask.filter(ImageFilter.MaxFilter(3))
    text_mask_px = text_mask.load()
    final = sum(1 for y in range(h) for x in range(w) if text_mask_px[x, y] == 255)
    print(f"After 2x dilation: {final} pixels")

    # Save debug visualization: red = text mask, black = elsewhere.
    debug = Image.new("RGB", (w, h), (0, 0, 0))
    debug_px = debug.load()
    for y in range(h):
        for x in range(w):
            if text_mask_px[x, y] == 255:
                debug_px[x, y] = (255, 0, 0)
    debug.save(DEBUG_MASK, "PNG", optimize=True)
    print(f"Debug mask saved: {DEBUG_MASK}")

    # Step 8: build the output. Gradient everywhere, solid white where
    # the text mask is set. Since layer 1 (background) and layer 2 (icon
    # body) use the SAME gradient color, there is no visible seam.
    out = Image.new("RGBA", (w, h))
    out_px = out.load()
    for y in range(h):
        y_norm = y / (h - 1)
        r, g, b = gradient_color(y_norm)
        for x in range(w):
            if text_mask_px[x, y] == 255:
                out_px[x, y] = (255, 255, 255, 255)  # solid white
            else:
                out_px[x, y] = (r, g, b, 255)        # gradient

    out.save(DEST, "PNG", optimize=True)
    print(f"Saved to: {DEST}")


if __name__ == "__main__":
    main()
