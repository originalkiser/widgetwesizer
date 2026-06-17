"""
Processes WidgetWesizerAppLogo.jpg into all required Android launcher icon variants:
- ic_launcher.png at each mipmap density (white bg removed, transparent corners)
- ic_launcher_round.png at each mipmap density (circular crop)
- ic_launcher_foreground.png at each mipmap density (for adaptive icons)
- mipmap-anydpi-v26/ic_launcher.xml and ic_launcher_round.xml (adaptive icon definitions)
- res/values/colors.xml entry for ic_launcher_background
"""

import sys
import os

sys.path.insert(0, r'C:\Users\Kiser-SB\AppData\Roaming\Python\Python314\site-packages')

from PIL import Image, ImageDraw, ImageFilter
import numpy as np
from collections import deque

SRC = os.path.join(os.path.dirname(__file__), '..', 'WidgetWesizerAppLogo.jpg')
RES_BASE = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')

LEGACY_SIZES = {
    'mdpi':     48,
    'hdpi':     72,
    'xhdpi':    96,
    'xxhdpi':   144,
    'xxxhdpi':  192,
}

# Adaptive foreground: 108dp at each density (108 * scale factor)
ADAPTIVE_SIZES = {
    'mdpi':     108,
    'hdpi':     162,
    'xhdpi':    216,
    'xxhdpi':   324,
    'xxxhdpi':  432,
}


def remove_white_background(img: Image.Image, tolerance: int = 35) -> Image.Image:
    """BFS flood-fill from all four corners to remove white/near-white background."""
    rgba = img.convert('RGBA')
    arr = np.array(rgba, dtype=np.uint8)
    h, w = arr.shape[:2]

    is_near_white = (
        (arr[:, :, 0].astype(int) >= 255 - tolerance) &
        (arr[:, :, 1].astype(int) >= 255 - tolerance) &
        (arr[:, :, 2].astype(int) >= 255 - tolerance)
    )

    visited = np.zeros((h, w), dtype=bool)
    bg_mask = np.zeros((h, w), dtype=bool)
    queue = deque()

    for cy, cx in [(0, 0), (0, w - 1), (h - 1, 0), (h - 1, w - 1)]:
        if is_near_white[cy, cx] and not visited[cy, cx]:
            queue.append((cy, cx))
            visited[cy, cx] = True

    while queue:
        cy, cx = queue.popleft()
        bg_mask[cy, cx] = True
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx] and is_near_white[ny, nx]:
                visited[ny, nx] = True
                queue.append((ny, nx))

    result = arr.copy()
    result[bg_mask, 3] = 0
    return Image.fromarray(result, 'RGBA')


def make_round(img: Image.Image) -> Image.Image:
    """Crop image to a circle."""
    size = img.size
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, size[0] - 1, size[1] - 1], fill=255)
    result = Image.new('RGBA', size, (0, 0, 0, 0))
    result.paste(img, mask=mask)
    return result


def save_png(img: Image.Image, path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, 'PNG', optimize=True)
    print(f'  wrote {os.path.relpath(path)}')


def write_text(path: str, content: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'  wrote {os.path.relpath(path)}')


def main():
    print('Loading source image...')
    src = Image.open(SRC)
    print(f'  Original size: {src.size}')

    print('Removing white background...')
    no_bg = remove_white_background(src)

    print('\nGenerating legacy launcher icons (ic_launcher.png, ic_launcher_round.png)...')
    for density, size in LEGACY_SIZES.items():
        resized = no_bg.resize((size, size), Image.LANCZOS)
        save_png(resized, os.path.join(RES_BASE, f'mipmap-{density}', 'ic_launcher.png'))

        round_icon = make_round(resized)
        save_png(round_icon, os.path.join(RES_BASE, f'mipmap-{density}', 'ic_launcher_round.png'))

    print('\nGenerating adaptive icon foregrounds (ic_launcher_foreground.png)...')
    for density, size in ADAPTIVE_SIZES.items():
        # Pad to 108dp canvas with artwork in center (72dp safe zone = 2/3 of total)
        canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        inner_size = round(size * 2 / 3)
        padding = (size - inner_size) // 2
        artwork = no_bg.resize((inner_size, inner_size), Image.LANCZOS)
        canvas.paste(artwork, (padding, padding), artwork)
        save_png(canvas, os.path.join(RES_BASE, f'mipmap-{density}', 'ic_launcher_foreground.png'))

    print('\nWriting adaptive icon XML files...')
    anydpi_dir = os.path.join(RES_BASE, 'mipmap-anydpi-v26')

    adaptive_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
        '</adaptive-icon>\n'
    )
    write_text(os.path.join(anydpi_dir, 'ic_launcher.xml'), adaptive_xml)
    write_text(os.path.join(anydpi_dir, 'ic_launcher_round.xml'), adaptive_xml)

    print('\nWriting colors.xml...')
    colors_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources>\n'
        '    <color name="ic_launcher_background">#FFFFFF</color>\n'
        '</resources>\n'
    )
    write_text(os.path.join(RES_BASE, 'values', 'colors.xml'), colors_xml)

    print('\nAll icon assets generated successfully.')


if __name__ == '__main__':
    main()
