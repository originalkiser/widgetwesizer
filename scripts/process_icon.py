"""
Builds the Android launcher icons from WidgetWesizerAppLogo.jpg.

The logo is a blue rounded square on white with "WidgetWesizer" text underneath.
For the app icon we keep only the artwork inside the dotted border (arrows, grid,
corner brackets, dotted line), drop the text and the white margin, and place it
on a full-bleed blue gradient that continues the logo's own background.

Outputs (under app/src/main/res):
- mipmap-*/ic_launcher_foreground.png   adaptive icon layer (108dp, full bleed)
- mipmap-*/ic_launcher.png              legacy rounded-square icon (48dp)
- mipmap-*/ic_launcher_round.png        legacy circular icon (48dp)
- mipmap-anydpi-v26/ic_launcher*.xml    adaptive icon definitions
- values/colors.xml                     ic_launcher_background fallback color

Run: python3 scripts/process_icon.py   (needs Pillow and numpy)
"""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = os.path.join(ROOT, 'WidgetWesizerAppLogo.jpg')
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')

# Dotted border bounds in the source image (pixels), measured from the logo.
DOT_L, DOT_T, DOT_R, DOT_B = 187, 155, 833, 768
# Edge of the blue rounded square in the source image.
BLUE_L, BLUE_T, BLUE_R, BLUE_B = 100, 104, 923, 939
# Text starts below this row; never sample artwork past it.
TEXT_TOP = 805

# Width of the dotted border inside the 108dp adaptive canvas. Sized so the rounded
# dotted corners stay inside the 66dp circle that circular launcher masks show.
ART_DP = 54
CANVAS_DP = 108

ADAPTIVE_PX = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
LEGACY_PX = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def _terms(x, y):
    # Quadratic surface in normalised coordinates.
    x = (x - 512) / 512
    y = (y - 512) / 512
    return np.stack([np.ones_like(x), x, y, x * x, y * y, x * y], axis=-1)


def fit_background(src: np.ndarray):
    """Fit a smooth colour gradient (per channel) to the plain blue background."""
    h, w, _ = src.shape
    yy, xx = np.mgrid[0:h, 0:w]
    inset = 45  # stay clear of the anti-aliased rounded corners of the blue square
    in_blue = (xx > BLUE_L + inset) & (xx < BLUE_R - inset) & (yy > BLUE_T + inset) & (yy < TEXT_TOP)
    outside_art = (xx < DOT_L - 15) | (xx > DOT_R + 15) | (yy < DOT_T - 15) | (yy > DOT_B + 15)
    # Plain background is dark and saturated; skip anything light (dots, text halos).
    dark = src.max(axis=2) < 215
    m = in_blue & outside_art & dark
    X = _terms(xx[m].astype(float), yy[m].astype(float))
    coefs = [np.linalg.lstsq(X, src[..., c][m].astype(float), rcond=None)[0] for c in range(3)]
    return coefs


def render_background(coefs, x0, y0, size):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    # Clamp to the logo's blue square so the gradient never goes beyond its real colours.
    xx = np.clip(xx + x0, BLUE_L, BLUE_R)
    yy = np.clip(yy + y0, BLUE_T, BLUE_B)
    T = _terms(xx, yy)
    chans = [np.clip(T @ c, 0, 255) for c in coefs]
    return Image.fromarray(np.stack(chans, axis=2).astype(np.uint8), 'RGB')


def build_master(src_img: Image.Image) -> Image.Image:
    """Full-bleed square icon in source-pixel scale, artwork centred."""
    src = np.array(src_img.convert('RGB'))
    coefs = fit_background(src)

    art_w = DOT_R - DOT_L
    cx, cy = (DOT_L + DOT_R) / 2, (DOT_T + DOT_B) / 2
    size = int(round(art_w * CANVAS_DP / ART_DP))
    x0, y0 = int(round(cx - size / 2)), int(round(cy - size / 2))

    canvas = render_background(coefs, x0, y0, size)

    # Artwork: dotted border plus a small margin, below which the text begins.
    margin = 32
    box = (DOT_L - margin, DOT_T - margin, DOT_R + margin, DOT_B + margin)
    art = src_img.convert('RGB').crop(box)

    # Feathered rounded-rect mask so the crop blends into the generated gradient.
    mask = Image.new('L', art.size, 0)
    feather = 14
    ImageDraw.Draw(mask).rounded_rectangle(
        (feather, feather, art.size[0] - 1 - feather, art.size[1] - 1 - feather),
        radius=80, fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(feather / 1.5))
    canvas.paste(art, (box[0] - x0, box[1] - y0), mask)
    return canvas


def save(img: Image.Image, path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, 'PNG', optimize=True)
    print('  wrote', os.path.relpath(path, ROOT))


def masked(img: Image.Image, shape: str) -> Image.Image:
    s = img.size[0]
    scale = 4  # supersample the mask for smooth edges
    m = Image.new('L', (s * scale, s * scale), 0)
    d = ImageDraw.Draw(m)
    if shape == 'circle':
        d.ellipse((0, 0, s * scale - 1, s * scale - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, s * scale - 1, s * scale - 1), radius=int(s * scale * 0.22), fill=255)
    m = m.resize((s, s), Image.LANCZOS)
    out = img.convert('RGBA')
    out.putalpha(m)
    return out


def main():
    master = build_master(Image.open(SRC))
    print('Master icon:', master.size)

    for density, px in ADAPTIVE_PX.items():
        save(master.resize((px, px), Image.LANCZOS),
             os.path.join(RES, f'mipmap-{density}', 'ic_launcher_foreground.png'))

    # Legacy icons show the 72dp safe zone of the adaptive canvas.
    inner = int(round(master.size[0] * 72 / CANVAS_DP))
    off = (master.size[0] - inner) // 2
    legacy = master.crop((off, off, off + inner, off + inner))
    for density, px in LEGACY_PX.items():
        base = legacy.resize((px, px), Image.LANCZOS)
        save(masked(base, 'square'), os.path.join(RES, f'mipmap-{density}', 'ic_launcher.png'))
        save(masked(base, 'circle'), os.path.join(RES, f'mipmap-{density}', 'ic_launcher_round.png'))

    adaptive_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
        '</adaptive-icon>\n'
    )
    for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        path = os.path.join(RES, 'mipmap-anydpi-v26', name)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(adaptive_xml)
        print('  wrote', os.path.relpath(path, ROOT))

    # Fallback colour behind the (opaque) foreground: the gradient's middle.
    mid = master.resize((1, 1), Image.BOX).getpixel((0, 0))
    color = '#{:02X}{:02X}{:02X}'.format(*mid)
    path = os.path.join(RES, 'values', 'colors.xml')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'    <color name="ic_launcher_background">{color}</color>\n</resources>\n')
    print('  wrote', os.path.relpath(path, ROOT), color)


if __name__ == '__main__':
    main()
