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
    ("phone", "library", {"en-US": ("YOUR SHELF!", "Every CBZ, CBR and PDF in one place, grouped by series"),
                          "es-ES": ("¡TU ESTANTERÍA!", "Todos tus CBZ, CBR y PDF en un sitio, agrupados por serie")}),
    ("phone", "detail", {"en-US": ("PICK UP WHERE YOU LEFT OFF", "Progress, per-comic settings and a cover you can tap"),
                         "es-ES": ("SIGUE DONDE LO DEJASTE", "Progreso, ajustes por cómic y una portada que puedes tocar")}),
    ("phone", "reader", {"en-US": ("CLEAN READING", "Edge taps turn the page; the centre shows the controls"),
                         "es-ES": ("LECTURA LIMPIA", "Toca los bordes para pasar página; el centro muestra los controles")}),
    ("phone", "guided", {"en-US": ("GUIDED VIEW!", "Panel by panel, detected on-device — no cloud"),
                         "es-ES": ("¡GUIDED VIEW!", "Viñeta a viñeta, detectadas en el dispositivo — sin nube")}),
    ("phone", "bubbles", {"en-US": ("BIGGER BUBBLES!", "Speech bubbles enlarged so you never squint again"),
                          "es-ES": ("¡BOCADILLOS XL!", "Bocadillos ampliados para no volver a entrecerrar los ojos")}),
    ("phone", "settings", {"en-US": ("YOUR LOOK", "Black, graphite or paper themes and seven accents"),
                           "es-ES": ("A TU GUSTO", "Temas negro, grafito o papel y siete colores de acento")}),
    ("tablet", "spread", {"en-US": ("TWO PAGES, AS PRINTED", "Tablets and unfolded screens get the double-page spread"),
                          "es-ES": ("DOBLE PÁGINA", "Como en papel, en tablets y pantallas desplegadas")}),
    ("tablet", "guided-spread", {"en-US": ("GUIDED VIEW ON SPREADS", "Panels jump across both pages"),
                                 "es-ES": ("GUIDED VIEW EN LA DOBLE", "Las viñetas saltan entre las dos páginas")}),
    ("tablet", "library", {"en-US": ("A WALL OF COVERS", "Your whole collection at a glance on the big screen"),
                           "es-ES": ("PARED DE PORTADAS", "Toda tu colección de un vistazo en la pantalla grande")}),
]
COMPARE_SHOT = "bubbles"
COMPARE_LABELS = {"en-US": ("BEFORE", "AFTER"), "es-ES": ("ANTES", "DESPUÉS")}

PANEL_TILT = -2
PANEL_STROKE = 10
PANEL_RADIUS = 36
SHADOW = (18, 22)
COMPARE_STRIP = 0.47
COMPARE_GAP = 70
TABLET_SHOT = (1000, 160, 1500)
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


def page_bounds(capture: Image.Image) -> tuple:
    grey = capture.convert("L")
    rows = [y for y in range(grey.height) if grey.crop((0, y, grey.width, y + 1)).getextrema()[1] > 200]
    return 0, rows[0], capture.width, rows[-1] + 1


def compare_crops(before: Image.Image, after: Image.Image) -> tuple:
    x0, y0, x1, y1 = page_bounds(after)
    strip = (x0, y0, x1, y0 + round((y1 - y0) * COMPARE_STRIP))
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
    shot_y, bottom_margin = 470, 60
    shot_h = h - shot_y - bottom_margin
    shot_w = round(shot_h * shot.width / shot.height)
    shot_x = (w - shot_w) / 2
    return phone_frame(k, luckiest, archivo, title, text, panel(shot, shot_x, shot_y, shot_w, shot_h))


def compare_svg(crops: tuple, labels: tuple, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    w, h = PHONE
    top, bottom_margin = 500, 70
    shot_w = w - 120
    shot_h = round(shot_w * crops[0].height / crops[0].width)
    shot_x = (w - shot_w) / 2
    body = ""
    for i, (crop, label, fill) in enumerate(zip(crops, labels, (CREAM, icon.YELLOW))):
        y = top + i * (shot_h + COMPARE_GAP)
        body += panel(crop, shot_x, y, shot_w, shot_h, f"shot{i}") + caption(archivo, label, 34, shot_x + 150, y - 30, 300, fill=fill)
    assert top + 2 * shot_h + COMPARE_GAP <= h - bottom_margin, "comparison panels overflow the phone canvas"
    return phone_frame(k, luckiest, archivo, title, text, body)


def shout_lines(luckiest: TTFont, text: str, size: float, cx: float, baseline: float, max_width: float) -> tuple:
    lines = wrap(luckiest, text, size, max_width, tracking=2)
    leading = size * 1.1
    first = baseline - (len(lines) - 1) * leading / 2
    svg = "".join(shout_title(luckiest, line, size, cx, first + i * leading, max_width) for i, line in enumerate(lines))
    return svg, first + (len(lines) - 1) * leading


def tablet_svg(shot: Image.Image, title: str, text: str, k: str, luckiest: TTFont, archivo: TTFont) -> str:
    w, h = TABLET
    shot_x, shot_y, shot_h = TABLET_SHOT
    shot_w = round(shot_h * shot.width / shot.height)
    text_cx = shot_x / 2
    title_svg, last_baseline = shout_lines(luckiest, title, 130, text_cx, 560, shot_x - 200)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
        + stage(w, h, k)
        + title_svg
        + caption(archivo, text, 46, text_cx, last_baseline + 70, shot_x - 240)
        + panel(shot, shot_x, shot_y, shot_w, shot_h)
        + "</svg>"
    )


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
            if name == COMPARE_SHOT:
                crops = compare_crops(Image.open(RAW / f"{name}-off-{kind}-{lang}.jpg"), shot)
                svg = compare_svg(crops, COMPARE_LABELS[lang], title, text, k, luckiest, archivo)
            elif kind == "phone":
                svg = phone_svg(shot, title, text, k, luckiest, archivo)
            else:
                svg = tablet_svg(shot, title, text, k, luckiest, archivo)
            fg.render(svg, PHONE if kind == "phone" else TABLET, out)
            print(out.relative_to(icon.ROOT))


if __name__ == "__main__":
    main()
