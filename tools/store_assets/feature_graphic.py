#!/usr/bin/env python3
"""Generate the Play Store feature graphic (1024×500) in English and Spanish.

Usage: python3 tools/store_assets/feature_graphic.py
Requires fontTools, Pillow and Google Chrome; reuses the icon's stage and mark from icon.py.
"""
import base64
import io
import subprocess
import tempfile
import zipfile
from pathlib import Path

from fontTools.misc.transform import Transform
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from PIL import Image

import icon

WIDTH, HEIGHT = 1024, 500
FONTS = Path(__file__).resolve().parent / "fonts"
SAMPLE_CBZ = icon.ROOT / "app/src/main/assets/sample.cbz"
SAMPLE_PAGE = "03.jpg"
OUTPUTS = {
    "en-US": ("Your collection, always with you", "CBZ · CBR · PDF · Guided View"),
    "es-ES": ("Tu colección, siempre contigo", "CBZ · CBR · PDF · Guided View"),
}
WORDMARK = "KAPOW!"
STAGE_SCALE = 1026 / icon.VIEWPORT
STAGE_SHIFT_Y = -(1026 - HEIGHT) / 2
MARK_SCALE = 3.7
MARK_CENTRE = (140, 244)
WORDMARK_SIZE = 116
WORDMARK_TRACKING = 4
WORDMARK_X = 262
WORDMARK_BASELINE = 232
CAPTION_TILT = -2
TAGLINE_SIZE = 25
TAGLINE_BOX = (278, 276)
FORMATS_SIZE = 17
FORMATS_BOX = (296, 350)
CAPTION_PAD = (18, 12)
PAGE_BOX = (724, 78, 236)
PAGE_TILT = 4
PANEL_STROKE = 8
PANEL_RADIUS = 18
PANEL_SHADOW = (14, 16)
CREAM = "#F4F1EA"


def load_archivo() -> TTFont:
    return instancer.instantiateVariableFont(TTFont(FONTS / "Archivo-Bold.ttf"), {"wght": 700, "wdth": 100})


def text_width(font: TTFont, text: str, size: float, tracking: float = 0) -> float:
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    return sum(glyphs[cmap[ord(c)]].width * scale + tracking for c in text) - tracking


def text_path(font: TTFont, text: str, size: float, x: float, baseline: float, tracking: float = 0) -> str:
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.1f}")
    advance = 0.0
    for char in text:
        name = cmap[ord(char)]
        transform = Transform().translate(x + advance, baseline).scale(scale, -scale)
        glyphs[name].draw(TransformPen(pen, transform))
        advance += glyphs[name].width * scale + tracking
    return pen.getCommands()


def stage_body(k: str) -> str:
    inner = icon.store_svg(k, icon.VIEWPORT)
    body = inner[inner.index(">") + 1 : inner.rindex("</svg>")]
    return body[: body.index('<g transform="translate(54 54)')]


def stage_svg(k: str) -> str:
    return f'<g transform="translate(0 {STAGE_SHIFT_Y}) scale({STAGE_SCALE})">{stage_body(k)}</g>'


def mark_svg(k: str) -> str:
    inner = icon.store_svg(k, icon.VIEWPORT)
    body = inner[inner.index(">") + 1 : inner.rindex("</svg>")]
    mark = body[body.index('<g transform="translate(54 54)') :]
    cx, cy = MARK_CENTRE
    return f'<g transform="translate({cx} {cy}) scale({MARK_SCALE}) translate(-54 -54)">{mark}</g>'


def shout_text(luckiest: TTFont, text: str, size: float, x: float, baseline: float, tracking: float) -> str:
    d = text_path(luckiest, text, size, x, baseline, tracking=tracking)
    return (
        f'<path d="{d}" fill="{icon.INK}" stroke="{icon.INK}" stroke-width="{size * 0.11:.1f}" stroke-linejoin="round" transform="translate({size * 0.045:.1f} {size * 0.055:.1f})"/>'
        f'<path d="{d}" fill="none" stroke="{icon.WHITE}" stroke-width="{size * 0.13:.1f}" stroke-linejoin="round"/>'
        f'<path d="{d}" fill="url(#kg)" stroke="{icon.INK}" stroke-width="{size * 0.055:.1f}" stroke-linejoin="round"/>'
    )


def caption_svg(font: TTFont, text: str, size: float, origin: tuple, fill: str) -> str:
    width = text_width(font, text, size)
    px, py = CAPTION_PAD
    x, y = origin
    box_w, box_h = width + 2 * px, size * 1.05 + 2 * py
    d = text_path(font, text, size, x + px, y + py + size * 0.82)
    return (
        f'<g transform="rotate({CAPTION_TILT} {x} {y})">'
        f'<rect x="{x + 6}" y="{y + 7}" width="{box_w:.1f}" height="{box_h:.1f}" fill="{icon.INK}"/>'
        f'<rect x="{x}" y="{y}" width="{box_w:.1f}" height="{box_h:.1f}" fill="{fill}" stroke="{icon.INK}" stroke-width="5"/>'
        f'<path d="{d}" fill="{icon.INK}"/></g>'
    )


def panel_svg(image_bytes: bytes, mime: str, x: float, y: float, w: float, h: float, tilt: float,
              stroke: int = PANEL_STROKE, radius: int = PANEL_RADIUS, shadow: tuple = PANEL_SHADOW, clip_id: str = "shot") -> str:
    data = base64.b64encode(image_bytes).decode()
    sx, sy = shadow
    cx, cy = x + w / 2, y + h / 2
    return (
        f'<g transform="rotate({tilt} {cx:.1f} {cy:.1f})">'
        f'<rect x="{x + sx}" y="{y + sy}" width="{w}" height="{h}" rx="{radius}" fill="{icon.INK}"/>'
        f'<clipPath id="{clip_id}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}"/></clipPath>'
        f'<image href="data:{mime};base64,{data}" x="{x}" y="{y}" width="{w}" height="{h}" preserveAspectRatio="xMidYMid slice" clip-path="url(#{clip_id})"/>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="none" stroke="{icon.WHITE}" stroke-width="{stroke + 8}"/>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="none" stroke="{icon.INK}" stroke-width="{stroke}"/>'
        "</g>"
    )


def sample_page() -> tuple:
    with zipfile.ZipFile(SAMPLE_CBZ) as archive:
        data = archive.read(SAMPLE_PAGE)
    return data, Image.open(io.BytesIO(data)).size


def page_svg() -> str:
    data, (img_w, img_h) = sample_page()
    x, y, w = PAGE_BOX
    return panel_svg(data, "image/jpeg", x, y, w, round(w * img_h / img_w), PAGE_TILT)


def feature_svg(k: str, tagline: str, formats: str, luckiest: TTFont, archivo: TTFont) -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">'
        + stage_svg(k)
        + f'<rect width="{WIDTH}" height="{HEIGHT}" fill="#000" opacity=".22"/>'
        + page_svg()
        + mark_svg(k)
        + shout_text(luckiest, WORDMARK, WORDMARK_SIZE, WORDMARK_X, WORDMARK_BASELINE, WORDMARK_TRACKING)
        + caption_svg(archivo, tagline, TAGLINE_SIZE, TAGLINE_BOX, CREAM)
        + caption_svg(archivo, formats, FORMATS_SIZE, FORMATS_BOX, icon.YELLOW)
        + "</svg>"
    )


def render(svg: str, size: tuple, out: Path) -> None:
    chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(f"<!doctype html><body style='margin:0'>{svg}</body>")
        subprocess.run(
            [chrome, "--headless=new", "--hide-scrollbars", f"--window-size={size[0]},{size[1]}",
             f"--screenshot={out}", f"file://{page}"],
            check=True, capture_output=True,
        )
    Image.open(out).convert("RGB").save(out, optimize=True)


def main() -> None:
    k = icon.k_path()
    luckiest = TTFont(icon.FONT)
    archivo = load_archivo()
    for lang, (tagline, formats) in OUTPUTS.items():
        out = icon.ROOT / "fastlane/metadata/android" / lang / "images/featureGraphic.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        render(feature_svg(k, tagline, formats, luckiest, archivo), (WIDTH, HEIGHT), out)
        print(out)


if __name__ == "__main__":
    main()
