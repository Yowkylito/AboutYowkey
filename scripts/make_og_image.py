#!/usr/bin/env python3
"""
The link preview image (og:image): the first screen's screenshot, cut to the 1200 x 630 that LinkedIn, Slack,
Messenger and the rest draw a large preview from, as a PNG (not every one of them takes WebP).

    python3 scripts/make_og_image.py

Reads docs/images/hero.webp (the README's screenshot of the first screen at 1600 px wide) and writes
composeApp/src/wasmJsMain/resources/og.png, which ships at the site's root beside favicon.svg. Run it again whenever
the first screen changes and hero.webp is retaken. SiteContentTest holds the result to its size and weight.

The card is the bar, the name, the role, the buttons and the three numbers. The summary paragraph under the buttons
is left out: a preview is drawn small, where a paragraph is unreadable, and the page's description travels with the
link as text anyway (og:description). The places below are where those parts sit in a 1600 px wide screenshot of the
desktop layout; if the first screen is laid out differently one day, measure them again.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs/images/hero.webp"
TARGET = ROOT / "composeApp/src/wasmJsMain/resources/og.png"
WIDTH, HEIGHT = 1200, 630

# In a screenshot 1600 px wide: the numbers' card ends at 667, so the card stops a little under it; the summary
# starts at 519, under the buttons (which end at 481), and stays left of the numbers' card (which starts at 953).
SHOT_WIDTH = 1600
CONTENT_BOTTOM = 700
SUMMARY_TOP = 500
SUMMARY_RIGHT = 930


def main() -> None:
    shot = Image.open(SOURCE).convert("RGB")
    if shot.width != SHOT_WIDTH or shot.height < CONTENT_BOTTOM:
        raise SystemExit(f"{SOURCE.name} is {shot.width} x {shot.height}; this expects {SHOT_WIDTH} px wide and at least {CONTENT_BOTTOM} tall")
    paper = shot.getpixel((4, CONTENT_BOTTOM // 2))  # the page's own colour, from its empty left margin
    top = shot.crop((0, 0, SHOT_WIDTH, CONTENT_BOTTOM))
    top.paste(paper, (0, SUMMARY_TOP, SUMMARY_RIGHT, CONTENT_BOTTOM))
    # The same shape as the preview, with what is kept centred between equal margins of the page's colour.
    tall = round(SHOT_WIDTH * HEIGHT / WIDTH)
    card = Image.new("RGB", (SHOT_WIDTH, tall), paper)
    card.paste(top, (0, (tall - CONTENT_BOTTOM) // 2))
    card = card.resize((WIDTH, HEIGHT), Image.LANCZOS)
    # A screenshot is a few flat colours: 64 of them keep the type crisp at a fraction of the weight.
    card = card.quantize(colors=64, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    card.save(TARGET, optimize=True)
    print(f"{TARGET.relative_to(ROOT)}: {WIDTH} x {HEIGHT}, {TARGET.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
