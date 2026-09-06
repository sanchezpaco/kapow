#!/usr/bin/env python3
"""Compose the Play Store screenshots from raw device captures.

Usage: python3 tools/store_assets/screenshots.py
Reads tools/store_assets/raw/<shot>-<lang>.jpg (see SHOTS) and writes
fastlane/metadata/android/<lang>/images/{phoneScreenshots,sevenInchScreenshots}/NN.png.
Requires fontTools, Pillow and Google Chrome; reuses icon.py and feature_graphic.py.
"""
import io
from pathlib import Path

from fontTools.ttLib import TTFont
from PIL import Image

import feature_graphic as fg
import icon

RAW = Path(__file__).resolve().parent / "raw"
PHONE = (1080, 1920)
TABLET = (2560, 1440)
LANGS = ("en-US", "es-ES")

SHOTS = [
    ("phone", "bubbles", {"en-US": ("BIGGER BUBBLES!", "Balloons grow on the page, no more squinting at tiny print"),
                          "es-ES": ("¡BOCADILLOS XL!", "Los bocadillos crecen en la página y se acabó forzar la vista")}),
    ("phone", "guided", {"en-US": ("GUIDED VIEW!", "One tap and the next panel fills the screen"),
                         "es-ES": ("¡VIÑETA A VIÑETA!", "Un toque y la siguiente viñeta llena la pantalla")}),
    ("phone", "library", {"en-US": ("YOUR SHELF!", "Your comics folder becomes a shelf, grouped by series"),
                          "es-ES": ("¡TU ESTANTERÍA!", "Tu carpeta de cómics se vuelve una estantería, por series")}),
    ("phone", "pagelook", {"en-US": ("OLD SCANS, FIXED", "Four page looks rescue a dark or yellowed scan"),
                           "es-ES": ("ESCANEOS SALVADOS", "Cuatro aspectos de página rescatan un escaneo oscuro o amarillo")}),
    ("phone", "search", {"en-US": ("FIND IT FAST", "Ask for a writer, a series or a year and the shelf answers"),
                         "es-ES": ("ENCUÉNTRALO YA", "Pide un guionista, una serie o un año y la estantería responde")}),
    ("phone", "detail", {"en-US": ("RATE IT, FIX IT", "Stars, a summary and details you can correct, per comic"),
                         "es-ES": ("VALORA Y CORRIGE", "Estrellas, resumen y una ficha que puedes corregir, por cómic")}),
    ("phone", "settings", {"en-US": ("YOUR LOOK", "Black, graphite or paper, and seven accent colours"),
                           "es-ES": ("A TU GUSTO", "Negro, grafito o papel, y siete colores de acento")}),
    ("tablet", "spread", {"en-US": ("TWO PAGES, AS PRINTED", "Tablets and unfolded screens get the double-page spread"),
                          "es-ES": ("DOBLE PÁGINA", "Como en papel, en tablets y pantallas desplegadas")}),
    ("tablet", "bubbles-spread", {"en-US": ("BIGGER BUBBLES!", "The lettering grows across the double page too"),
                                  "es-ES": ("¡BOCADILLOS XL!", "La rotulación también crece en la doble página")}),
    ("tablet", "guided-spread", {"en-US": ("GUIDED VIEW ON SPREADS", "Panel by panel across the gutter, on either page"),
                                 "es-ES": ("VIÑETA A VIÑETA EN LA DOBLE", "Viñeta a viñeta a través del pliegue, en una página o en la otra")}),
    ("tablet", "library", {"en-US": ("A WALL OF COVERS", "Your whole collection at a glance on the big screen"),
                           "es-ES": ("PARED DE PORTADAS", "Toda tu colección de un vistazo en la pantalla grande")}),
]
COMPARE_SHOTS = {
    "bubbles": {"en-US": ("BEFORE", "AFTER"), "es-ES": ("ANTES", "DESPUÉS")},
    "bubbles-spread": {"en-US": ("BEFORE", "AFTER"), "es-ES": ("ANTES", "DESPUÉS")},
    "guided": {"en-US": ("PAGE", "PANEL"), "es-ES": ("PÁGINA", "VIÑETA")},
    "pagelook": {"en-US": ("ORIGINAL", "MORE CONTRAST"), "es-ES": ("ORIGINAL", "MÁS CONTRASTE")},
}

PANEL_TILT = -2
PANEL_STROKE = 10
PANEL_RADIUS = 36
SHADOW = (18, 22)
COMPARE_STRIP = 0.47
SPREAD_STRIP = 0.34
COMPARE_GAP = 70
COMPARE_TOP = 500
COMPARE_BOTTOM = 70
COMPARE_WIDTH = PHONE[0] - 120
SAME_PAGE_TOLERANCE = 0.05
BRIGHT_ROW = 200
PHONE_SHOT_TOP = 470
PHONE_SHOT_BOTTOM = 60
PHONE_SHOT_MAX_WIDTH = 960
TABLET_SHOT = (1000, 190, 1700)
TABLET_COMPARE = (1000, 130, 1400)
TABLET_COMPARE_BOTTOM = 40
CREAM = fg.CREAM


def stage(width: int, height: int, k: str) -> str:
    scale = max(width, height) / icon.VIEWPORT * 1.05
    dx = (width - icon.VIEWPORT * scale) / 2
    dy = (height - icon.VIEWPORT * scale) / 2
    return (f'<g transform="translate({dx:.1f} {dy:.1f}) scale({scale:.3f})">{fg.stage_body(k)}</g>'
            f'<rect width="{width}" height="{height}" fill="#000" opacity=".25"/>')


def shout_title(luckiest: TTFont, text: str, size: float, cx: float, baseline: float, max_width: float) -> str:
    width = fg.text_width(luckiest, text, size, tracking=2)
    if width > max_width:
        size *= max_width / width
        width = max_width
    return fg.shout_text(luckiest, text, size, cx - width / 2, baseline, 2)


def wrap(font: TTFont, text: str, size: float, max_width: float, tracking: float = 0) -> list:
    lines, current = [], ""
    for word in text.split():
        candidate = f"{current} {word}".strip()
        if fg.text_width(font, candidate, size, tracking) > max_width and current:
            lines.append(current)
            current = word
        else:
            current = candidate
    return lines + [current]


def caption(archivo: TTFont, text: str, size: float, cx: float, y: float, max_width: float, fill: str = CREAM) -> str:
    lines = wrap(archivo, text, size, max_width)
    pad_x, pad_y, leading = size * 0.6, size * 0.45, size * 1.25
    box_w = max(fg.text_width(archivo, line, size) for line in lines) + 2 * pad_x
    box_h = leading * len(lines) + 2 * pad_y - (leading - size)
    x = cx - box_w / 2
    paths = "".join(
        f'<path d="{fg.text_path(archivo, line, size, cx - fg.text_width(archivo, line, size) / 2, y + pad_y + size * 0.82 + i * leading)}" fill="{icon.INK}"/>'
        for i, line in enumerate(lines)
    )
    return (
        f'<g transform="rotate({fg.CAPTION_TILT} {cx} {y})">'
        f'<rect x="{x + 8:.1f}" y="{y + 9:.1f}" width="{box_w:.1f}" height="{box_h:.1f}" fill="{icon.INK}"/>'
        f'<rect x="{x:.1f}" y="{y:.1f}" width="{box_w:.1f}" height="{box_h:.1f}" fill="{fill}" stroke="{icon.INK}" stroke-width="6"/>'
        f"{paths}</g>"
    )


def panel(image: Image.Image, x: float, y: float, w: float, h: float, clip_id: str = "shot") -> str:
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=92)
    return fg.panel_svg(buffer.getvalue(), "image/jpeg", x, y, w, h, PANEL_TILT, PANEL_STROKE, PANEL_RADIUS, SHADOW, clip_id)


def bright_rows(capture: Image.Image) -> list:
    grey = capture.convert("L")
    return [y for y in range(grey.height) if grey.crop((0, y, grey.width, y + 1)).getextrema()[1] > BRIGHT_ROW]


def page_bounds(capture: Image.Image) -> tuple:
    rows = bright_rows(capture)
    return 0, rows[0], capture.width, rows[-1] + 1


def top_strip(bounds: tuple, fraction: float) -> tuple:
    x0, y0, x1, y1 = bounds
    return x0, y0, x1, y0 + round((y1 - y0) * fraction)


def same_page(before: tuple, after: tuple) -> bool:
    before_h, after_h = before[3] - before[1], after[3] - after[1]
    return abs(before_h - after_h) <= SAME_PAGE_TOLERANCE * before_h


def fitting_fraction(bounds: tuple, band_h: float, panel_w: float, most: float) -> float:
    panel_h = (band_h - COMPARE_GAP) / 2
    strip_px = panel_h * (bounds[2] - bounds[0]) / panel_w
    return min(most, strip_px / (bounds[3] - bounds[1]))


def pair_crops(before: Image.Image, after: Image.Image) -> tuple:
    before_bounds, after_bounds = page_bounds(before), page_bounds(after)
    band_h = PHONE[1] - COMPARE_TOP - COMPARE_BOTTOM
    if same_page(before_bounds, after_bounds):
        strip = top_strip(before_bounds, fitting_fraction(before_bounds, band_h, COMPARE_WIDTH, COMPARE_STRIP))
        return before.crop(strip), after.crop(strip)
    return before.crop(before_bounds), after.crop(after_bounds)


def right_page_bounds(spread: Image.Image) -> tuple:
    grey = spread.convert("L")
    columns = [x for x in range(grey.width) if grey.crop((x, 0, x + 1, grey.height)).getextrema()[1] > BRIGHT_ROW]
    rows = bright_rows(spread)
    middle = (columns[0] + columns[-1]) // 2
    return middle, rows[0], columns[-1] + 1, rows[-1] + 1


def spread_crops(before: Image.Image, after: Image.Image) -> tuple:
    bounds = right_page_bounds(after)
    shot_x, top, shot_w = TABLET_COMPARE
    band_h = TABLET[1] - top - TABLET_COMPARE_BOTTOM
    strip = top_strip(bounds, fitting_fraction(bounds, band_h, shot_w, SPREAD_STRIP))
    return before.crop(strip), after.crop(strip)


def phone_frame(k: str, luckiest: TTFont, archivo: TTFont, title: str, text: str, body: str) -> str:
    w, h = PHONE
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
        + stage(w, h, k)
        + shout_title(luckiest, title, 96, w / 2, 200, 960)
        + caption(archivo, text, 40, w / 2, 262, 880)
        + body
        + "</svg>"
    )


def phone_svg(shot: Image.Image, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    w, h = PHONE
    band_h = h - PHONE_SHOT_TOP - PHONE_SHOT_BOTTOM
    shot_h = band_h
    shot_w = round(shot_h * shot.width / shot.height)
    if shot_w > PHONE_SHOT_MAX_WIDTH:
        shot_w = PHONE_SHOT_MAX_WIDTH
        shot_h = round(shot_w * shot.height / shot.width)
    shot_x = (w - shot_w) / 2
    shot_y = PHONE_SHOT_TOP + (band_h - shot_h) / 2
    return phone_frame(k, luckiest, archivo, title, text, panel(shot, shot_x, shot_y, shot_w, shot_h))


def stacked_sizes(crops: tuple) -> list:
    band_h = PHONE[1] - COMPARE_TOP - COMPARE_BOTTOM - COMPARE_GAP
    sizes = [(COMPARE_WIDTH, round(COMPARE_WIDTH * crop.height / crop.width)) for crop in crops]
    overflow = sum(h for _, h in sizes) - band_h
    if overflow <= 0:
        return sizes
    tallest = max(range(len(sizes)), key=lambda i: sizes[i][1])
    h = sizes[tallest][1] - overflow
    sizes[tallest] = (round(h * crops[tallest].width / crops[tallest].height), h)
    return sizes


def compare_svg(crops: tuple, labels: tuple, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    w, h = PHONE
    body, y = "", COMPARE_TOP
    for i, (crop, label, fill, (shot_w, shot_h)) in enumerate(zip(crops, labels, (CREAM, icon.YELLOW), stacked_sizes(crops))):
        shot_x = (w - shot_w) / 2
        body += panel(crop, shot_x, y, shot_w, shot_h, f"shot{i}") + caption(archivo, label, 34, shot_x + 150, y - 30, 300, fill=fill)
        y += shot_h + COMPARE_GAP
    assert y - COMPARE_GAP <= h - COMPARE_BOTTOM, "comparison panels overflow the phone canvas"
    return phone_frame(k, luckiest, archivo, title, text, body)


def shout_lines(luckiest: TTFont, text: str, size: float, cx: float, baseline: float, max_width: float) -> tuple:
    lines = wrap(luckiest, text, size, max_width, tracking=2)
    leading = size * 1.1
    first = baseline - (len(lines) - 1) * leading / 2
    svg = "".join(shout_title(luckiest, line, size, cx, first + i * leading, max_width) for i, line in enumerate(lines))
    return svg, first + (len(lines) - 1) * leading


def tablet_frame(k: str, luckiest: TTFont, archivo: TTFont, title: str, text: str, text_cx: float, body: str) -> str:
    w, h = TABLET
    title_svg, last_baseline = shout_lines(luckiest, title, 130, text_cx, 560, text_cx * 2 - 200)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
        + stage(w, h, k)
        + title_svg
        + caption(archivo, text, 46, text_cx, last_baseline + 70, text_cx * 2 - 240)
        + body
        + "</svg>"
    )


def tablet_svg(shot: Image.Image, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    shot_x, shot_y, shot_w = TABLET_SHOT
    shot_h = round(shot_w * shot.height / shot.width)
    return tablet_frame(k, luckiest, archivo, title, text, shot_x / 2, panel(shot, shot_x, shot_y, shot_w, shot_h))


def tablet_compare_svg(crops: tuple, labels: tuple, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    shot_x, top, shot_w = TABLET_COMPARE
    shot_h = round(shot_w * crops[0].height / crops[0].width)
    body = ""
    for i, (crop, label, fill) in enumerate(zip(crops, labels, (CREAM, icon.YELLOW))):
        y = top + i * (shot_h + COMPARE_GAP)
        body += panel(crop, shot_x, y, shot_w, shot_h, f"shot{i}") + caption(archivo, label, 40, shot_x + 170, y - 34, 340, fill=fill)
    assert top + 2 * shot_h + COMPARE_GAP <= TABLET[1] - TABLET_COMPARE_BOTTOM, "comparison panels overflow the tablet canvas"
    return tablet_frame(k, luckiest, archivo, title, text, shot_x / 2, body)


def compare(kind: str, name: str, lang: str, shot: Image.Image, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    before = Image.open(RAW / f"{name}-off-{kind}-{lang}.jpg")
    labels = COMPARE_SHOTS[name][lang]
    if kind == "phone":
        return compare_svg(pair_crops(before, shot), labels, title, text, k, luckiest, archivo)
    return tablet_compare_svg(spread_crops(before, shot), labels, title, text, k, luckiest, archivo)


def main() -> None:
    k = icon.k_path()
    luckiest = TTFont(icon.FONT)
    archivo = fg.load_archivo()
    counters = {}
    for kind, name, copy in SHOTS:
        for lang in LANGS:
            raw = RAW / f"{name}-{kind}-{lang}.jpg"
            if not raw.exists():
                print(f"missing {raw.name}")
                continue
            folder = "phoneScreenshots" if kind == "phone" else "sevenInchScreenshots"
            index = counters.get((kind, lang), 0) + 1
            counters[(kind, lang)] = index
            out = icon.ROOT / "fastlane/metadata/android" / lang / "images" / folder / f"{index:02d}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            title, text = copy[lang]
            shot = Image.open(raw)
            if name in COMPARE_SHOTS:
                svg = compare(kind, name, lang, shot, title, text, k, luckiest, archivo)
            elif kind == "phone":
                svg = phone_svg(shot, title, text, k, luckiest, archivo)
            else:
                svg = tablet_svg(shot, title, text, k, luckiest, archivo)
            fg.render(svg, PHONE if kind == "phone" else TABLET, out)
            print(out.relative_to(icon.ROOT))


if __name__ == "__main__":
    main()
