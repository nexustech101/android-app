"""Builds Play Store style screenshots (1080x1920) from raw emulator captures in store/raw/.

    python store/make_screenshots.py

Raw captures are 1080x2400 screenshots of the debug build with demo data. The script cleans the
status bar (drops notification and mobile-data icons), puts each screen in a phone frame under a
headline, and writes store/screenshots/NN-name.png plus store/screenshots-overview.png.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT = ROOT / "screenshots"
FONT = ROOT.parent / "app/src/main/res/font/plus_jakarta_sans.ttf"

S = 2  # draw at 2x, downsample at the end for clean edges
W, H = 1080, 1920

THEMES = {
    "warm": dict(bg="#F5F5F3", title="#111113", sub="#6E6E73", over="#FF5B1F", bezel="#111113", ring=None),
    "ink": dict(bg="#111113", title="#FFFFFF", sub="#A1A1A6", over="#FF5B1F", bezel="#2A2A2E", ring="#3A3A3F"),
    "accent": dict(bg="#FF5B1F", title="#FFFFFF", sub="#FFE9E0", over="#111113", bezel="#111113", ring=None),
}

SLIDES = [
    ("01-jobs", "jobs", "warm", "JOBS", "Every property,\nits own folder", "Your latest job is always one tap away."),
    ("02-camera", "camera", "ink", "CAMERA", "Shoot straight into\nthe job folder", "Photos save to the right job automatically."),
    ("03-job-folder", "folder", "accent", "JOB FOLDER", "Before photos,\nsorted by day", "See what's backed up and what's waiting."),
    ("04-new-job", "new-job", "warm", "NEW JOB", "Start a job\nin seconds", "Name, address, work order. Then shoot."),
    ("05-google-drive", "drive", "ink", "GOOGLE DRIVE", "Backed up to\nyour Google Drive", "Syncs on Wi-Fi, or whenever you choose."),
    ("06-welcome", "welcome", "accent", "SITESNAP", "Proof of condition,\nbefore you start", "Works offline. Syncs when you're back."),
]

# Status bar layout of the 1080px-wide emulator captures.
SB_H = 76
SB_TIME = (20, 0, 125, SB_H)
SB_WIFI = (745, 0, 802, SB_H)
SB_BATTERY = (925, 0, 1015, SB_H)
SB_WIFI_X = 866  # wifi moves next to the battery once the mobile-data icons are gone


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def font(weight, size):
    f = ImageFont.truetype(str(FONT), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def glyph_mask(region, white):
    """Alpha mask of status-bar/overlay glyphs: white glyphs on dark, or dark glyphs on light."""
    px = region.convert("RGB").load()
    m = Image.new("L", region.size, 0)
    mp = m.load()
    for y in range(region.size[1]):
        for x in range(region.size[0]):
            r, g, b = px[x, y]
            lo, hi = min(r, g, b), max(r, g, b)
            if hi - lo > 45:
                continue
            a = (lo - 165) / 75 if white else (150 - hi) / 110
            mp[x, y] = max(0, min(255, int(a * 255)))
    return m


def clean_status_bar(img, white_glyphs=False):
    """Repaint the status bar with only the clock, Wi-Fi and battery."""
    img = img.convert("RGB")
    if white_glyphs:
        out = img  # caller already replaced the background; we only re-stamp glyphs
        src = Image.open(RAW / "camera.png").convert("RGB")
    else:
        out = img.copy()
        bg = img.getpixel((540, 40))
        ImageDraw.Draw(out).rectangle((0, 0, img.width, SB_H), fill=bg)
        src = img
    color = (255, 255, 255) if white_glyphs else (17, 17, 19)
    for box, x in ((SB_TIME, SB_TIME[0]), (SB_WIFI, SB_WIFI_X), (SB_BATTERY, SB_BATTERY[0])):
        crop = src.crop(box)
        mask = glyph_mask(crop, white_glyphs)
        out.paste(Image.new("RGB", crop.size, color), (x, box[1]), mask)
    return out


def rounded_rect_alpha(size, radius, fill_rgba):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=fill_rgba)
    return layer


def camera_screen():
    """Real camera UI from the emulator, with a job-site photo behind it instead of the emulator's test scene."""
    raw = Image.open(RAW / "camera.png").convert("RGB")
    panel_top = 1950
    photo = Image.open(RAW / "camera-preview.jpg").convert("RGB")
    scale = max(1080 / photo.width, panel_top / photo.height)
    photo = photo.resize((round(photo.width * scale), round(photo.height * scale)), Image.LANCZOS)
    left = (photo.width - 1080) // 2
    top = (photo.height - panel_top) // 2
    canvas = Image.new("RGBA", (1080, 2400))
    canvas.paste(photo.crop((left, top, left + 1080, top + panel_top)), (0, 0))

    # Same overlays CameraScreen draws: thirds grid, top scrim, glass controls.
    over = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    for f in (1 / 3, 2 / 3):
        d.line((1080 * f, 0, 1080 * f, panel_top), fill=(255, 255, 255, 64), width=2)
        d.line((0, panel_top * f, 1080, panel_top * f), fill=(255, 255, 255, 64), width=2)
    canvas = Image.alpha_composite(canvas, over)
    scrim = Image.new("RGBA", (1080, 368))
    for y in range(368):
        ImageDraw.Draw(scrim).line((0, y, 1080, y), fill=(0, 0, 0, int(0.45 * 255 * (1 - y / 368))))
    canvas.alpha_composite(scrim, (0, 0))
    glass = (255, 255, 255, int(0.16 * 255))
    for cx in (100, 980):
        canvas.alpha_composite(rounded_rect_alpha((116, 116), 58, glass), (cx - 58, 79))
    canvas.alpha_composite(rounded_rect_alpha((578, 106), 53, glass), (251, 84))

    # Icons and text from the real capture: white glyphs, plus the orange count badge and flash icon.
    for box in ((40, 79, 160, 196), (251, 84, 829, 190), (920, 79, 1040, 196)):
        crop = raw.crop(box)
        canvas.paste(Image.new("RGBA", crop.size, (255, 255, 255, 255)), box[:2], glyph_mask(crop, True))
        px = crop.load()
        orange = Image.new("L", crop.size, 0)
        op = orange.load()
        for y in range(crop.size[1]):
            for x in range(crop.size[0]):
                r, g, b = px[x, y]
                if r > 200 and g < 150 and b < 110:
                    op[x, y] = 255
        canvas.paste(crop.convert("RGBA"), box[:2], orange)

    canvas.paste(raw.crop((0, panel_top, 1080, 2400)), (0, panel_top))
    return clean_status_bar(canvas.convert("RGB"), white_glyphs=True)


def draw_tracked(draw, xy_center, text, fnt, fill, tracking):
    """Centered single line with letter spacing (tracking in px), keeping the font's kerning."""
    widths = [fnt.getlength(text[:i]) for i in range(len(text) + 1)]
    total = widths[-1] + tracking * (len(text) - 1)
    x0 = xy_center[0] - total / 2
    for i, ch in enumerate(text):
        draw.text((x0 + widths[i] + tracking * i, xy_center[1]), ch, font=fnt, fill=fill)


def compose(name, screen, theme_key, overline, title, sub):
    t = THEMES[theme_key]
    cw, ch = W * S, H * S
    img = Image.new("RGB", (cw, ch), rgb(t["bg"]))
    d = ImageDraw.Draw(img)

    y = 104 * S
    f_over, f_title, f_sub = font(700, 30 * S), font(800, 84 * S), font(500, 36 * S)
    draw_tracked(d, (cw / 2, y), overline, f_over, rgb(t["over"]), 4.2 * S)
    y += 38 * S + 22 * S
    for line in title.split("\n"):
        draw_tracked(d, (cw / 2, y), line, f_title, rgb(t["title"]), -2.5 * S)
        y += 87 * S
    y += 36 * S
    draw_tracked(d, (cw / 2, y), sub, f_sub, rgb(t["sub"]), -0.3 * S)
    y += 47 * S

    # Phone
    sw = 583 * S
    shh = round(sw * screen.height / screen.width)
    bez = 18 * S
    pw, ph = sw + 2 * bez, shh + 2 * bez
    px, py = (cw - pw) // 2, y + 72 * S

    shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    a = 128 if theme_key == "ink" else 56
    ImageDraw.Draw(shadow).rounded_rectangle((px, py + 40 * S, px + pw, py + ph + 40 * S), 68 * S, fill=(0, 0, 0, a))
    shadow = shadow.filter(ImageFilter.GaussianBlur(45 * S))
    img = Image.alpha_composite(img.convert("RGBA"), shadow)

    d = ImageDraw.Draw(img)
    d.rounded_rectangle((px, py, px + pw, py + ph), 68 * S, fill=rgb(t["bezel"]),
                        outline=rgb(t["ring"]) if t["ring"] else None, width=3 * S if t["ring"] else 0)
    scr = screen.resize((sw, shh), Image.LANCZOS)
    mask = Image.new("L", (sw, shh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, shh - 1), 52 * S, fill=255)
    img.paste(scr, (px + bez, py + bez), mask)

    final = img.convert("RGB").resize((W, H), Image.LANCZOS)
    final.save(OUT / f"{name}.png", optimize=True)
    return final


def main():
    OUT.mkdir(exist_ok=True)
    shots = []
    for name, raw, theme, overline, title, sub in SLIDES:
        screen = camera_screen() if raw == "camera" else clean_status_bar(Image.open(RAW / f"{raw}.png"))
        shots.append(compose(name, screen, theme, overline, title, sub))
        print("wrote", OUT / f"{name}.png")

    gap, tw = 40, 360
    th = round(tw * H / W)
    sheet = Image.new("RGB", (gap + len(shots) * (tw + gap), th + 2 * gap), (232, 232, 228))
    for i, s in enumerate(shots):
        sheet.paste(s.resize((tw, th), Image.LANCZOS), (gap + i * (tw + gap), gap))
    sheet.save(ROOT / "screenshots-overview.png", optimize=True)
    print("wrote", ROOT / "screenshots-overview.png")


if __name__ == "__main__":
    main()
