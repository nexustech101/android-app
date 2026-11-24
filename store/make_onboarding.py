"""Renders the phone mockups shown on the onboarding carousel (WelcomeScreen).

    python store/make_onboarding.py

Uses the same raw emulator captures and phone frame as make_screenshots.py, but draws only the phone
(with its shadow) on a transparent background. The coloured card and the headline are drawn by the
app itself, so the text stays sharp and translatable. Output:
app/src/main/res/drawable-nodpi/onboarding_<name>.webp
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

from make_screenshots import RAW, camera_screen, clean_status_bar

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / "app/src/main/res/drawable-nodpi"

SCREENS = ["jobs", "camera", "folder", "drive"]

SW = 640          # screen width in px
BEZEL = 20
RADIUS_OUTER = 74
RADIUS_INNER = 57
PAD = 64          # room for the shadow on each side


def phone(screen, shadow_alpha):
    sh = round(SW * screen.height / screen.width)
    pw, ph = SW + 2 * BEZEL, sh + 2 * BEZEL
    w, h = pw + 2 * PAD, ph + 2 * PAD
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((PAD, PAD + 24, PAD + pw, PAD + ph + 24), RADIUS_OUTER, fill=(0, 0, 0, shadow_alpha))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(30)))

    d = ImageDraw.Draw(img)
    d.rounded_rectangle((PAD, PAD, PAD + pw, PAD + ph), RADIUS_OUTER, fill=(17, 17, 19, 255), outline=(58, 58, 63, 255), width=3)
    scr = screen.convert("RGB").resize((SW, sh), Image.LANCZOS)
    mask = Image.new("L", (SW, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SW - 1, sh - 1), RADIUS_INNER, fill=255)
    img.paste(scr, (PAD + BEZEL, PAD + BEZEL), mask)
    return img


def main():
    for name in SCREENS:
        screen = camera_screen() if name == "camera" else clean_status_bar(Image.open(RAW / f"{name}.png"))
        out = OUT / f"onboarding_{name}.webp"
        phone(screen, 90).save(out, "WEBP", quality=86, method=6)
        print("wrote", out, out.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
