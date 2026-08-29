#!/usr/bin/env python3
"""Generate the Kapow launcher icon (adaptive VectorDrawables) and the 512 px store icon.

Usage: python3 tools/store_assets/icon.py [--png]
Requires fontTools (pip install fonttools); --png additionally needs Google Chrome.
"""
import math
import random
import subprocess
import sys
import tempfile
from pathlib import Path

from fontTools.misc.transform import Transform
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
FONT = Path(__file__).resolve().parent / "fonts/LuckiestGuy.ttf"
STORE_ICONS = [ROOT / "fastlane/metadata/android" / lang / "images/icon.png" for lang in ("en-US", "es-ES")]

VIEWPORT = 108
INK = "#0B0B0F"
WHITE = "#FFFFFF"
YELLOW = "#FFC107"
RED = "#FF3D45"
PANEL_DARK = "#0E2445"
PANEL_MID = "#17396A"
GUTTER = "#4C86C4"

PAGE_TILT = -8
PANELS = [
    (-50, -50, 10, 60), (14, -50, 80, 20), (84, -50, 160, 40), (14, 24, 46, 60), (50, 24, 80, 60),
    (84, 44, 160, 100), (-50, 64, 30, 160), (34, 64, 80, 110), (84, 104, 160, 160), (34, 114, 80, 160),
]
PANEL_STROKE = 1.6
VIGNETTE_CENTRE = (54, 48.6)
VIGNETTE_RADIUS = 81

MARK_SCALE = 0.8
LAUNCHER_VISIBLE_FRACTION = 72 / 90
LAUNCHER_MARK_SCALE = MARK_SCALE * LAUNCHER_VISIBLE_FRACTION
BUBBLE = "M20,50 a34,25 0 1 1 68,0 a34,25 0 0 1 -19,22 L70,88 L56,75 a34,25 0 0 1 -36,-25 z"
BUBBLE_HALO = 7
BUBBLE_STROKE = 3
K_HEIGHT = 34
K_LEAN = -6
K_CENTRE = (54.07, 48.91)
K_STROKE = 2.2
K_SHADOW_STROKE = 3.6
K_SHADOW_OFFSET = (1.1, 1.3)


def k_path() -> str:
    font = TTFont(FONT)
    glyphs = font.getGlyphSet()
    glyph = glyphs[font.getBestCmap()[ord("K")]]
    bounds = BoundsPen(glyphs)
    glyph.draw(bounds)
    x0, y0, x1, y1 = bounds.bounds
    scale = K_HEIGHT / (y1 - y0)
    cx, cy = K_CENTRE
    transform = (
        Transform()
        .translate(cx, cy)
        .rotate(math.radians(K_LEAN))
        .scale(scale, -scale)
        .translate(-(x0 + x1) / 2, -(y0 + y1) / 2)
    )
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}")
    glyph.draw(TransformPen(pen, transform))
    return pen.getCommands()


def rect_path(x0, y0, x1, y1) -> str:
    return f"M{x0},{y0} H{x1} V{y1} H{x0} Z"


def vector_header() -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        f'    android:width="{VIEWPORT}dp"\n    android:height="{VIEWPORT}dp"\n'
        f'    android:viewportWidth="{VIEWPORT}"\n    android:viewportHeight="{VIEWPORT}">\n'
    )


def background_vector() -> str:
    panels = "".join(
        f'        <path android:pathData="{rect_path(*p)}" android:fillColor="{PANEL_MID if i % 3 else PANEL_DARK}"\n'
        f'            android:strokeColor="{INK}" android:strokeWidth="{PANEL_STROKE}" />\n'
        for i, p in enumerate(PANELS)
    )
    vx, vy = VIGNETTE_CENTRE
    return (
        vector_header()
        + f'    <path android:pathData="{rect_path(0, 0, VIEWPORT, VIEWPORT)}" android:fillColor="{GUTTER}" />\n'
        + f'    <group android:rotation="{PAGE_TILT}" android:pivotX="54" android:pivotY="54">\n'
        + panels
        + "    </group>\n"
        + f'    <path android:pathData="{rect_path(0, 0, VIEWPORT, VIEWPORT)}">\n'
        + "        <aapt:attr name=\"android:fillColor\">\n"
        + f'            <gradient android:type="radial" android:centerX="{vx}" android:centerY="{vy}" android:gradientRadius="{VIGNETTE_RADIUS}">\n'
        + '                <item android:offset="0.35" android:color="#00000000" />\n'
        + '                <item android:offset="1" android:color="#8C000000" />\n'
        + "            </gradient>\n        </aapt:attr>\n    </path>\n"
        + "</vector>\n"
    )


def foreground_vector(k: str) -> str:
    sx, sy = K_SHADOW_OFFSET
    return (
        vector_header()
        + f'    <group android:scaleX="{LAUNCHER_MARK_SCALE}" android:scaleY="{LAUNCHER_MARK_SCALE}" android:pivotX="54" android:pivotY="54">\n'
        + f'        <path android:pathData="{BUBBLE}" android:fillColor="{WHITE}"\n'
        + f'            android:strokeColor="{WHITE}" android:strokeWidth="{BUBBLE_HALO}" android:strokeLineJoin="round" />\n'
        + f'        <path android:pathData="{BUBBLE}" android:fillColor="{WHITE}"\n'
        + f'            android:strokeColor="{INK}" android:strokeWidth="{BUBBLE_STROKE}" android:strokeLineJoin="round" />\n'
        + f'        <group android:translateX="{sx}" android:translateY="{sy}">\n'
        + f'            <path android:pathData="{k}" android:strokeColor="{INK}" android:strokeWidth="{K_SHADOW_STROKE}" android:strokeLineJoin="round" />\n'
        + "        </group>\n"
        + f'        <path android:pathData="{k}">\n'
        + "            <aapt:attr name=\"android:fillColor\">\n"
        + '                <gradient android:type="linear" android:startX="54" android:startY="32" android:endX="54" android:endY="66">\n'
        + f'                    <item android:offset="0" android:color="{YELLOW}" />\n'
        + f'                    <item android:offset="1" android:color="{RED}" />\n'
        + "                </gradient>\n            </aapt:attr>\n        </path>\n"
        + f'        <path android:pathData="{k}" android:strokeColor="{INK}" android:strokeWidth="{K_STROKE}" android:strokeLineJoin="round" />\n'
        + "    </group>\n</vector>\n"
    )


def monochrome_vector(k: str) -> str:
    return (
        vector_header()
        + f'    <group android:scaleX="{LAUNCHER_MARK_SCALE}" android:scaleY="{LAUNCHER_MARK_SCALE}" android:pivotX="54" android:pivotY="54">\n'
        + f'        <path android:pathData="{BUBBLE}" android:strokeColor="{INK}" android:strokeWidth="{BUBBLE_STROKE + 1}" android:strokeLineJoin="round" />\n'
        + f'        <path android:pathData="{k}" android:fillColor="{INK}" />\n'
        + "    </group>\n</vector>\n"
    )


def halftone_svg(index: int, x0: int, y0: int, x1: int, y1: int) -> str:
    rng = random.Random(index * 7 + 1)
    spacing = rng.choice([2.4, 3.0, 3.6])
    angle = rng.choice([15, 30, 45])
    gx, gy = rng.choice(["0", "1"]), rng.choice(["0", "1"])
    return (
        f'<pattern id="p{index}" width="{spacing}" height="{spacing}" patternUnits="userSpaceOnUse" patternTransform="rotate({angle})">'
        f'<circle cx="{spacing / 2}" cy="{spacing / 2}" r="{0.36 * spacing:.2f}" fill="{INK}"/></pattern>'
        f'<linearGradient id="g{index}" x1="{gx}" y1="{gy}" x2="{1 - int(gx)}" y2="{1 - int(gy)}">'
        '<stop offset="0" stop-color="#fff" stop-opacity=".85"/><stop offset="1" stop-color="#fff" stop-opacity=".05"/></linearGradient>'
        f'<mask id="m{index}"><rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="url(#g{index})"/></mask>'
        f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="url(#p{index})" mask="url(#m{index})"/>'
    )


def store_svg(k: str, size: int) -> str:
    panels = "".join(
        f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="{PANEL_MID if i % 3 else PANEL_DARK}"/>'
        + halftone_svg(i, x0, y0, x1, y1)
        + f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="none" stroke="{INK}" stroke-width="{PANEL_STROKE}"/>'
        for i, (x0, y0, x1, y1) in enumerate(PANELS)
    )
    vx, vy = VIGNETTE_CENTRE
    sx, sy = K_SHADOW_OFFSET
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {VIEWPORT} {VIEWPORT}">'
        f'<rect width="{VIEWPORT}" height="{VIEWPORT}" fill="{GUTTER}"/>'
        f'<g transform="rotate({PAGE_TILT} 54 54)">{panels}</g>'
        f'<defs><radialGradient id="vg" cx="{vx / VIEWPORT}" cy="{vy / VIEWPORT}" r="{VIGNETTE_RADIUS / VIEWPORT}">'
        '<stop offset=".35" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".55"/></radialGradient>'
        f'<linearGradient id="kg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{YELLOW}"/><stop offset="1" stop-color="{RED}"/></linearGradient></defs>'
        f'<rect width="{VIEWPORT}" height="{VIEWPORT}" fill="url(#vg)"/>'
        f'<g transform="translate(54 54) scale({MARK_SCALE}) translate(-54 -54)">'
        f'<path d="{BUBBLE}" fill="{WHITE}" stroke="{WHITE}" stroke-width="{BUBBLE_HALO}" stroke-linejoin="round"/>'
        f'<path d="{BUBBLE}" fill="{WHITE}" stroke="{INK}" stroke-width="{BUBBLE_STROKE}" stroke-linejoin="round"/>'
        f'<path d="{k}" fill="none" stroke="{INK}" stroke-width="{K_SHADOW_STROKE}" stroke-linejoin="round" transform="translate({sx} {sy})"/>'
        f'<path d="{k}" fill="url(#kg)" stroke="{INK}" stroke-width="{K_STROKE}" stroke-linejoin="round"/>'
        "</g></svg>"
    )


def rasterise(svg: str, size: int, out: Path) -> None:
    chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "icon.html"
        page.write_text(f"<!doctype html><body style='margin:0;background:#000'>{svg}</body>")
        subprocess.run(
            [chrome, "--headless=new", "--hide-scrollbars", f"--window-size={size},{size}",
             f"--screenshot={out}", f"file://{page}"],
            check=True, capture_output=True,
        )


def main() -> None:
    k = k_path()
    (RES / "drawable/ic_launcher_background.xml").write_text(background_vector())
    (RES / "drawable/ic_launcher_foreground.xml").write_text(foreground_vector(k))
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(monochrome_vector(k))
    if "--png" in sys.argv:
        first, *others = STORE_ICONS
        first.parent.mkdir(parents=True, exist_ok=True)
        rasterise(store_svg(k, 512), 512, first)
        for other in others:
            other.parent.mkdir(parents=True, exist_ok=True)
            other.write_bytes(first.read_bytes())
    print("icon written")


if __name__ == "__main__":
    main()
