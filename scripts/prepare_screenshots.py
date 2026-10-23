"""Turn raw screenshots, covers and screen recordings into the WebP files the site ships.

    python scripts/prepare_screenshots.py <source.png> <name> [<source.png> <name> ...]
    python scripts/prepare_screenshots.py --cover <source.png> <name>
    python scripts/prepare_screenshots.py --clip <source.mp4 | frames-folder> <name> [--start S] [--duration S] [--fps N]
    python scripts/prepare_screenshots.py --clip <source.mp4> <name> --video [--start S] [--duration S]

Everything is written into composeApp/src/commonMain/composeResources/files/screens/.

Screenshots (the default):
  <name>.webp          full size for the viewer, long edge at most 1600 px, at most 254 KB
  <name>.thumb.webp    card thumbnail at 2x its on-card height, at most 62 KB

--cover, the project's splash screen at the top of its card:
  <name>.webp          full size for the viewer, as for screenshots
  <name>.thumb.webp    the card's hero image: 960 px wide for landscape (480dp at 2x), 640 px tall for portrait
                       (the 320dp phone frame at 2x), at most 62 KB

--clip, a short animated highlight:
  <name>.webp          animated WebP for the viewer: at most 6 s, 12 fps, long edge 720 px, 700 KB
  <name>.poster.webp   its first frame as the card thumbnail, same size rules as a screenshot thumbnail

  The source is an mp4 (decoded by ffmpeg, which must be on PATH; --start and --duration pick the stretch,
  default the first 5 s) or a folder of PNG frames in name order (--fps is their frame rate, default 12; frames
  are dropped evenly to reach at most 12 fps).

  --video also writes, from the same stretch of the mp4:
  <name>.mp4           H.264 at 60 fps for the web viewer: long edge 1440 px (2x the WebP's), no audio, the index
                       first so it starts playing while it downloads, at most 1536 KB
  The source must be recorded at 60 fps (a phone's or the Mac's screen recorder is); slower sources are refused
  rather than padded with repeated or invented frames. The WebP and its poster stay: the desktop app plays the
  WebP, and every card shows the poster.

Each run prints the JSON fields to paste into portfolio.json; write the caption yourself (it is also the alt
text). The budgets match SiteContentTest, which fails the build if a file outgrows them.
"""
import io
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "composeApp/src/commonMain/composeResources/files/screens"
FULL_LONG_EDGE = 1600
FULL_BUDGET = 254 * 1024
THUMB_BUDGET = 62 * 1024
# The card shows portrait thumbnails 200dp tall and landscape ones 124dp tall (ScreenshotStrip); 2x for dense screens.
THUMB_HEIGHT_PORTRAIT = 400
THUMB_HEIGHT_LANDSCAPE = 248
# The cover fills the card's width (at most ~480dp) when landscape, or a 320dp-tall phone frame when portrait.
COVER_THUMB_WIDTH_LANDSCAPE = 960
COVER_THUMB_HEIGHT_PORTRAIT = 640
CLIP_MAX_SECONDS = 6.0
CLIP_MAX_FPS = 12
CLIP_LONG_EDGE = 720
CLIP_BUDGET = 700 * 1024
VIDEO_FPS = 60
VIDEO_LONG_EDGE = 1440
VIDEO_BUDGET = 1536 * 1024


def encode_within(image: Image.Image, budget: int) -> bytes:
    for quality in range(82, 39, -6):
        buffer = io.BytesIO()
        image.save(buffer, "WEBP", quality=quality, method=6)
        if buffer.tell() <= budget:
            return buffer.getvalue()
    raise SystemExit(f"cannot fit under {budget // 1024} KB even at low quality; crop or shrink the source")


def fit_long_edge(image: Image.Image, long_edge: int) -> Image.Image:
    scale = min(1.0, long_edge / max(image.size))
    if scale == 1.0:
        return image
    return image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)


def resize_to_height(image: Image.Image, height: int) -> Image.Image:
    return image.resize((round(image.width * height / image.height), height), Image.LANCZOS)


def card_thumbnail(image: Image.Image) -> Image.Image:
    return resize_to_height(image, THUMB_HEIGHT_PORTRAIT if image.height > image.width else THUMB_HEIGHT_LANDSCAPE)


def cover_thumbnail(image: Image.Image) -> Image.Image:
    if image.height > image.width:
        return resize_to_height(image, min(COVER_THUMB_HEIGHT_PORTRAIT, image.height))
    width = min(COVER_THUMB_WIDTH_LANDSCAPE, image.width)
    return image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)


def prepare_still(source: Path, name: str, thumbnail) -> None:
    full = fit_long_edge(Image.open(source).convert("RGB"), FULL_LONG_EDGE)
    full_bytes = encode_within(full, FULL_BUDGET)
    thumb_bytes = encode_within(thumbnail(full), THUMB_BUDGET)
    (OUT / f"{name}.webp").write_bytes(full_bytes)
    (OUT / f"{name}.thumb.webp").write_bytes(thumb_bytes)
    print(
        f'{{ "file": "{name}.webp", "caption": "", "width": {full.width}, "height": {full.height} }}'
        f"  # {len(full_bytes) // 1024} KB + thumb {len(thumb_bytes) // 1024} KB"
    )


def video_frames(source: Path, start: float, duration: float, workdir: Path) -> list[Path]:
    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg is not on PATH; it decodes the mp4")
    # ffmpeg drops the frame rate and downscales, so Pillow only ever holds clip-sized frames.
    edge = CLIP_LONG_EDGE
    scale = f"scale='if(gt(iw,ih),min({edge},iw),-2)':'if(gt(iw,ih),-2,min({edge},ih))':flags=lanczos"
    subprocess.run(
        [
            "ffmpeg", "-v", "error", "-y", "-ss", f"{start}", "-t", f"{duration}", "-i", str(source),
            "-vf", f"fps={CLIP_MAX_FPS},{scale}", "-an", str(workdir / "frame-%04d.png"),
        ],
        check=True,
    )
    return sorted(workdir.glob("frame-*.png"))


def folder_frames(folder: Path, source_fps: float) -> tuple[list[Path], float]:
    frames = sorted(folder.glob("*.png"))
    if not frames:
        raise SystemExit(f"no PNG frames in {folder}")
    step = max(1, -(-int(source_fps) // CLIP_MAX_FPS))  # ceil, so the result never exceeds 12 fps
    return frames[::step], source_fps / step


def encode_clip(frames: list[Image.Image], fps: float) -> tuple[bytes, Image.Image]:
    """
    Lossy animated WebP within the budget. Quality stays at 50 or above (below it moving 3D scenes smear), so a
    clip that does not fit is made smaller instead, by the factor its size overshoots (bytes grow roughly with
    pixel count). Each encode of a few seconds takes tens of seconds, so the estimate saves blind retries.
    """
    frame_ms = round(1000 / fps)
    current = frames
    while True:
        size = 0
        for quality in (60, 50):
            buffer = io.BytesIO()
            current[0].save(
                buffer, "WEBP", save_all=True, append_images=current[1:], duration=frame_ms, loop=0,
                quality=quality, method=4,
            )
            size = buffer.tell()
            if size <= CLIP_BUDGET:
                return buffer.getvalue(), current[0]
        long_edge = round(max(current[0].size) * min(0.95, (CLIP_BUDGET / size) ** 0.5 * 0.97))
        if long_edge < 360:
            raise SystemExit(f"cannot fit the clip under {CLIP_BUDGET // 1024} KB; use a shorter or calmer stretch")
        print(f"  {size // 1024} KB at quality 50; retrying with the long edge at {long_edge} px", flush=True)
        current = [fit_long_edge(frame, long_edge) for frame in frames]


def source_frame_rate(source: Path) -> float:
    """The video's nominal frame rate. Screen recorders write variable frame rates, whose average says little."""
    rate = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=r_frame_rate", "-of", "csv=p=0",
         str(source)],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    numerator, _, denominator = rate.partition("/")
    return float(numerator) / float(denominator or 1)


def require_video_source(source: Path) -> None:
    """Checked before any encoding, so a wrong source fails in a second, not after the WebP's tens of seconds."""
    if source.is_dir():
        raise SystemExit("--video needs an mp4 source, not a folder of frames")
    if shutil.which("ffprobe") is None:
        raise SystemExit("ffprobe is not on PATH; it reads the mp4's frame rate")
    rate = source_frame_rate(source)
    if rate < VIDEO_FPS - 1:
        raise SystemExit(f"{source.name} is {rate:g} fps; --video needs a {VIDEO_FPS} fps recording")


def encode_video(source: Path, start: float, duration: float) -> bytes:
    """
    H.264 at a constant 60 fps within the budget. Quality steps down by CRF; past 31, text in a screen recording
    blurs, so a clip that still does not fit has to be shorter or calmer instead.
    """
    edge = VIDEO_LONG_EDGE
    scale = f"scale='if(gt(iw,ih),min({edge},iw),-2)':'if(gt(iw,ih),-2,min({edge},ih))':flags=lanczos"
    size = 0
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "clip.mp4"
        for crf in (22, 25, 28, 31):
            subprocess.run(
                [
                    "ffmpeg", "-v", "error", "-y", "-ss", f"{start}", "-t", f"{duration}", "-i", str(source),
                    "-vf", f"fps={VIDEO_FPS},{scale}", "-an", "-c:v", "libx264", "-profile:v", "high",
                    "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", str(crf), "-tag:v", "avc1",
                    "-movflags", "+faststart", str(out),
                ],
                check=True,
            )
            size = out.stat().st_size
            if size <= VIDEO_BUDGET:
                return out.read_bytes()
            print(f"  {size // 1024} KB at CRF {crf}; retrying at lower quality", flush=True)
    raise SystemExit(f"cannot fit the video under {VIDEO_BUDGET // 1024} KB; use a shorter or calmer stretch")


def prepare_clip(source: Path, name: str, start: float, duration: float, source_fps: float, video: bool) -> None:
    if video:
        require_video_source(source)
    with tempfile.TemporaryDirectory() as tmp:
        if source.is_dir():
            paths, fps = folder_frames(source, source_fps)
        else:
            paths, fps = video_frames(source, start, duration, Path(tmp)), CLIP_MAX_FPS
        if not paths:
            raise SystemExit("no frames decoded; check --start against the video's length")
        seconds = len(paths) / fps
        if seconds > CLIP_MAX_SECONDS + 1e-6:
            raise SystemExit(f"the clip is {seconds:.1f} s; at most {CLIP_MAX_SECONDS:.0f} s")
        frames = [fit_long_edge(Image.open(path).convert("RGB"), CLIP_LONG_EDGE) for path in paths]

    clip_bytes, first = encode_clip(frames, fps)
    poster_bytes = encode_within(card_thumbnail(first), THUMB_BUDGET)
    video_bytes = encode_video(source, start, duration) if video else None
    (OUT / f"{name}.webp").write_bytes(clip_bytes)
    (OUT / f"{name}.poster.webp").write_bytes(poster_bytes)
    if video_bytes is not None:
        (OUT / f"{name}.mp4").write_bytes(video_bytes)
    video_field = ', "video": true' if video_bytes is not None else ""
    video_note = f" + {VIDEO_FPS} fps video {len(video_bytes) // 1024} KB" if video_bytes is not None else ""
    print(
        f'{{ "file": "{name}.webp", "caption": "", "width": {first.width}, "height": {first.height}{video_field} }}'
        f"  # {len(clip_bytes) // 1024} KB, {len(frames)} frames, {seconds:.1f} s at {fps:g} fps"
        f" + poster {len(poster_bytes) // 1024} KB{video_note}"
    )


def option(args: list[str], flag: str, default: float) -> float:
    if flag not in args:
        return default
    index = args.index(flag)
    value = float(args[index + 1])
    del args[index:index + 2]
    return value


if __name__ == "__main__":
    args = sys.argv[1:]
    OUT.mkdir(parents=True, exist_ok=True)
    if args[:1] == ["--clip"]:
        rest = args[1:]
        video = "--video" in rest
        if video:
            rest.remove("--video")
        start = option(rest, "--start", 0.0)
        duration = option(rest, "--duration", 5.0)
        fps = option(rest, "--fps", CLIP_MAX_FPS)
        if len(rest) != 2 or not 0 < duration <= CLIP_MAX_SECONDS:
            raise SystemExit(__doc__)
        prepare_clip(Path(rest[0]), rest[1], start, duration, fps, video)
    elif args[:1] == ["--cover"]:
        if len(args) != 3:
            raise SystemExit(__doc__)
        prepare_still(Path(args[1]), args[2], cover_thumbnail)
    else:
        if not args or len(args) % 2:
            raise SystemExit(__doc__)
        for source, name in zip(args[::2], args[1::2]):
            prepare_still(Path(source), name, card_thumbnail)
