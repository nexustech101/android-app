"""Renders the SiteSnap logo (house with a camera-lens window) as store/icon-512.png.

    python store/make_icon.py

Geometry matches app/src/main/res/drawable/ic_launcher_foreground.xml (108-unit adaptive icon grid),
so the Play/GitHub icon and the phone's launcher icon stay identical.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
SIZE = 512
SS = 4  # supersample for smooth edges
U = SIZE * SS / 108  # px per icon unit
MARK = 1.3  # store icons are full-bleed, not masked by a launcher, so the mark can be bigger

ORANGE_TOP, ORANGE_BOTTOM = (255, 122, 61), (242, 73, 15)
WHITE = (255, 255, 255, 255)


def p(x, y):
    return (((x - 54) * MARK + 54) * U, ((y - 54) * MARK + 54) * U)


def main():
    w = SIZE * SS
    bg = Image.new("RGBA", (w, w))
    d = ImageDraw.Draw(bg)
    for y in range(w):
        t = y / (w - 1)
        d.line((0, y, w, y), fill=tuple(round(a + (b - a) * t) for a, b in zip(ORANGE_TOP, ORANGE_BOTTOM)) + (255,))

    mark = Image.new("L", (w, w), 0)
    m = ImageDraw.Draw(mark)
    stroke = round(4 * U * MARK)
    house = [p(54, 28), p(74, 49), p(74, 76), p(34, 76), p(34, 49)]
    m.polygon(house, fill=255)
    m.line(house + [house[0], house[1]], fill=255, width=stroke, joint="curve")
    for x, y in house:  # round the corners like the vector's round stroke joins
        m.ellipse((x - stroke / 2, y - stroke / 2, x + stroke / 2, y + stroke / 2), fill=255)
    chimney = [p(65, 32), p(71, 32), p(71, 46), p(65, 46)]
    m.polygon(chimney, fill=255)
    m.line(chimney + [chimney[0]], fill=255, width=round(3 * U * MARK), joint="curve")
    for x, y in chimney[:2]:
        r = 1.5 * U * MARK
        m.ellipse((x - r, y - r, x + r, y + r), fill=255)

    def circle(cx, cy, r, fill):
        x, y = p(cx, cy)
        r *= U * MARK
        m.ellipse((x - r, y - r, x + r, y + r), fill=fill)

    circle(54, 59, 10.5, 0)  # lens opening (12.5 hole minus the 2-unit stroke)
    circle(54, 59, 6.5, 255)  # lens glass
    circle(51.6, 56.6, 1.8, 0)  # glint

    bg.paste(Image.new("RGBA", (w, w), WHITE), (0, 0), mark)
    out = bg.resize((SIZE, SIZE), Image.LANCZOS)
    out.save(ROOT / "icon-512.png", optimize=True)

    # Rounded preview, roughly how launchers mask it.
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=SIZE * 0.22, fill=255)
    preview = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    preview.paste(out, (0, 0), mask)
    preview.save(ROOT / "icon-preview.png", optimize=True)
    print("wrote", ROOT / "icon-512.png")


if __name__ == "__main__":
    main()
