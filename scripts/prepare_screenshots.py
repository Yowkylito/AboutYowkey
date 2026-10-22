"""Turn raw screenshots into the WebP pairs the site ships.

    python scripts/prepare_screenshots.py <source.png> <name> [<source.png> <name> ...]

For each source this writes, into composeApp/src/commonMain/composeResources/files/screens/:
  <name>.webp        full size for the viewer, long edge at most 1600 px, at most 254 KB
  <name>.thumb.webp  card thumbnail at 2x its on-card height, at most 62 KB

and prints the JSON fields (file, width, height) to paste into portfolio.json. The budgets match
SiteContentTest, which fails the build if a file outgrows them.
"""
import io
import sys
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "composeApp/src/commonMain/composeResources/files/screens"
FULL_LONG_EDGE = 1600
FULL_BUDGET = 254 * 1024
THUMB_BUDGET = 62 * 1024
# The card shows portrait thumbnails 200dp tall and landscape ones 124dp tall (ScreenshotStrip); 2x for dense screens.
THUMB_HEIGHT_PORTRAIT = 400
THUMB_HEIGHT_LANDSCAPE = 248


def encode_within(image: Image.Image, budget: int) -> bytes:
    for quality in range(82, 39, -6):
        buffer = io.BytesIO()
        image.save(buffer, "WEBP", quality=quality, method=6)
        if buffer.tell() <= budget:
            return buffer.getvalue()
    raise SystemExit(f"cannot fit under {budget // 1024} KB even at low quality; crop or shrink the source")


def prepare(source: Path, name: str) -> None:
    image = Image.open(source).convert("RGB")
    scale = min(1.0, FULL_LONG_EDGE / max(image.size))
    full = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
    thumb_height = THUMB_HEIGHT_PORTRAIT if full.height > full.width else THUMB_HEIGHT_LANDSCAPE
    thumb = full.resize((round(full.width * thumb_height / full.height), thumb_height), Image.LANCZOS)

    full_bytes = encode_within(full, FULL_BUDGET)
    thumb_bytes = encode_within(thumb, THUMB_BUDGET)
    (OUT / f"{name}.webp").write_bytes(full_bytes)
    (OUT / f"{name}.thumb.webp").write_bytes(thumb_bytes)
    print(
        f'{{ "file": "{name}.webp", "width": {full.width}, "height": {full.height} }}'
        f"  # {len(full_bytes) // 1024} KB + thumb {len(thumb_bytes) // 1024} KB"
    )


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or len(args) % 2:
        raise SystemExit(__doc__)
    OUT.mkdir(parents=True, exist_ok=True)
    for source, name in zip(args[::2], args[1::2]):
        prepare(Path(source), name)
