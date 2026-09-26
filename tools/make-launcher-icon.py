#!/usr/bin/env python3
"""Generate MaterialReader launcher icons from design sources.

Reads design/iconbackground.png (full-bleed artwork) and
design/iconcenter.png (glyph with transparency), following the adaptive-icon
keylines (108dp viewport, critical content inside the central ~66dp circle):

- drawable-nodpi/launcher_{background,foreground,monochrome}.png (432px,
  referenced by mipmap-anydpi-v26 adaptive icons; monochrome is a white
  silhouette so themed launchers can tint it dynamically)
- mipmap-{mdpi..xxxhdpi}/ic_launcher{,_round}.png (legacy pre-26 fallbacks)

The foreground glyph is scaled so its alpha bounding box fits ~61dp: bold on
squircle masks, with only extreme corners potentially rounded by strict
circle masks. Re-run after changing the sources:
    python3 tools/make-launcher-icon.py
Requires: Pillow (pip install pillow).
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_BG = os.path.join(ROOT, "design", "iconbackground.png")
SRC_FG = os.path.join(ROOT, "design", "iconcenter.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

LAYER_PX = 432
# Foreground glyph target width in layer px (61dp of 108dp viewport).
GLYPH_TARGET_W = 264

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def main():
    bg_src = Image.open(SRC_BG).convert("RGB")
    fg_src = Image.open(SRC_FG).convert("RGBA")

    # --- Adaptive layers (nodpi, high resolution) ---
    bg_layer = bg_src.resize((LAYER_PX, LAYER_PX), Image.LANCZOS)

    bbox = fg_src.getbbox()
    assert bbox, "foreground has no opaque pixels"
    glyph = fg_src.crop(bbox)
    scale = GLYPH_TARGET_W / glyph.width
    glyph = glyph.resize(
        (GLYPH_TARGET_W, round(glyph.height * scale)), Image.LANCZOS
    )
    fg_layer = Image.new("RGBA", (LAYER_PX, LAYER_PX), (0, 0, 0, 0))
    fg_layer.alpha_composite(
        glyph, ((LAYER_PX - glyph.width) // 2, (LAYER_PX - glyph.height) // 2)
    )

    white = Image.new("RGBA", fg_layer.size, (255, 255, 255, 255))
    _, _, _, alpha = fg_layer.split()
    mono_layer = Image.merge("RGBA", (white.split()[0], white.split()[1],
                                      white.split()[2], alpha))

    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    bg_layer.convert("RGB").save(os.path.join(nodpi, "launcher_background.png"))
    fg_layer.save(os.path.join(nodpi, "launcher_foreground.png"))
    mono_layer.save(os.path.join(nodpi, "launcher_monochrome.png"))

    # --- Legacy raster icons (pre-26): background + centered glyph ---
    for density, size in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        bg = bg_src.resize((size, size), Image.LANCZOS).convert("RGBA")
        fg = fg_layer.resize((size, size), Image.LANCZOS)
        full = bg_src.resize((size, size), Image.LANCZOS).convert("RGBA")
        full.alpha_composite(fg, (0, 0))
        full.convert("RGB").save(os.path.join(d, "ic_launcher.png"))

        mask = Image.new("L", (size, size), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
        rounded = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rounded.paste(full, (0, 0), mask)
        # Transparent corners: launchers apply their own circle mask.
        rounded.save(os.path.join(d, "ic_launcher_round.png"))
    print("launcher icons generated")


if __name__ == "__main__":
    main()
