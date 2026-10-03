#!/usr/bin/env python3
"""Generate OnAir installer icon assets (.png, .ico, .icns).

The OnAir mark is a "tally light": a red on-air lamp with a microphone glyph on a
dark rounded-square background. Everything is rendered at 4x and downsampled so the
edges stay smooth at small sizes.

Requires Pillow:  python3 -m pip install Pillow
Run from the repository root:  python3 packaging/icons/generate_icons.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

OUT_DIR = Path(__file__).resolve().parent
S = 1024
SS = 4
N = S * SS


def _lerp(a: int, b: int, t: float) -> int:
    return round(a + (b - a) * t)


def _vgradient(size: int, top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGB", (1, size))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        px[0, y] = tuple(_lerp(top[i], bottom[i], t) for i in range(3))
    return img.resize((size, size), Image.NEAREST)


def _rounded_mask(size: int, radius: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def _radial_mask(size: int, max_alpha: int) -> Image.Image:
    """Circular falloff: opaque at the centre, fading to 0 at the inscribed radius."""
    mask = Image.new("L", (size, size), 0)
    px = mask.load()
    c = (size - 1) / 2
    r = size / 2
    for y in range(size):
        dy = y - c
        for x in range(size):
            d = ((x - c) ** 2 + dy * dy) ** 0.5
            t = max(0.0, 1.0 - d / r)
            px[x, y] = round(max_alpha * t * t)
    return mask


def build_master() -> Image.Image:
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    # 1. Rounded-square background with a subtle vertical gradient.
    radius = round(N * 0.225)
    bg = _vgradient(N, (0x1F, 0x29, 0x37), (0x0B, 0x12, 0x20)).convert("RGBA")
    canvas = Image.composite(bg, canvas, _rounded_mask(N, radius))

    draw = ImageDraw.Draw(canvas, "RGBA")

    # 2. Soft inner rim to lift the tile off dark backgrounds.
    rim = round(N * 0.012)
    draw.rounded_rectangle(
        (rim, rim, N - 1 - rim, N - 1 - rim),
        radius=radius - rim,
        outline=(255, 255, 255, 26),
        width=round(N * 0.004),
    )

    # 3. On-air lamp: red circle with a lighter top and a specular highlight.
    cx, cy = N // 2, round(N * 0.50)
    r_outer = round(N * 0.315)
    draw.ellipse(
        (cx - r_outer, cy - r_outer, cx + r_outer, cy + r_outer),
        fill=(0x7F, 0x1D, 0x1D, 90),
    )
    r = round(N * 0.285)
    lamp = _vgradient(2 * r, (0xFF, 0x6B, 0x6B), (0xC8, 0x1E, 0x1E)).convert("RGBA")
    lamp_mask = Image.new("L", (2 * r, 2 * r), 0)
    ImageDraw.Draw(lamp_mask).ellipse((0, 0, 2 * r - 1, 2 * r - 1), fill=255)
    canvas.paste(lamp, (cx - r, cy - r), lamp_mask)

    # Specular highlight, upper-left: a soft circular glow with no hard bounding box.
    hx, hy = cx - round(r * 0.30), cy - round(r * 0.34)
    hr = round(r * 0.42)
    gloss = _radial_mask(2 * hr, max_alpha=115)
    white = Image.new("RGBA", (2 * hr, 2 * hr), (255, 255, 255, 255))
    canvas.paste(white, (hx - hr, hy - hr), gloss)

    draw = ImageDraw.Draw(canvas, "RGBA")

    # 4. White microphone glyph centred on the lamp.
    mic_w = round(N * 0.088)
    mic_top = cy - round(N * 0.115)
    mic_bottom = cy + round(N * 0.055)
    draw.rounded_rectangle(
        (cx - mic_w // 2, mic_top, cx + mic_w // 2, mic_bottom),
        radius=mic_w // 2,
        fill=(255, 255, 255, 235),
    )

    cradle_r = round(N * 0.105)
    cradle_w = max(round(N * 0.016), 1)
    draw.arc(
        (cx - cradle_r, cy - cradle_r + round(N * 0.02), cx + cradle_r, cy + cradle_r + round(N * 0.02)),
        start=20,
        end=160,
        fill=(255, 255, 255, 235),
        width=cradle_w,
    )
    stem_bottom = cy + cradle_r + round(N * 0.06)
    draw.line((cx, cy + cradle_r + round(N * 0.02), cx, stem_bottom), fill=(255, 255, 255, 235), width=cradle_w)
    draw.line(
        (cx - round(N * 0.052), stem_bottom, cx + round(N * 0.052), stem_bottom),
        fill=(255, 255, 255, 235),
        width=cradle_w,
    )

    return canvas.resize((S, S), Image.LANCZOS)


def main() -> None:
    master = build_master()
    master.save(OUT_DIR / "onair.png", format="PNG")

    master.resize((256, 256), Image.LANCZOS).save(
        OUT_DIR / "onair.ico",
        format="ICO",
        sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )

    master.save(OUT_DIR / "onair.icns", format="ICNS")

    for name in ("onair.png", "onair.ico", "onair.icns"):
        path = OUT_DIR / name
        print(f"wrote {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
