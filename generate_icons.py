"""One-off: regenerate launcher icons + in-app logo from MiroLit_logo.png."""
import os
from collections import Counter
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "app", "src", "main", "res")
logo = Image.open(os.path.join(ROOT, "MiroLit_logo.png")).convert("RGBA")

# Dominant fully-opaque colour = the brand's brown background.
opaque = [p for p in logo.getdata() if p[3] > 250]
brown = Counter(opaque).most_common(1)[0][0]
brown_hex = "#%02X%02X%02X" % (brown[0], brown[1], brown[2])
print("brown:", brown, brown_hex)

# Adaptive foreground: logo at 64% on a transparent canvas so the whole mark — including the
# MIRROLIT wordmark at the bottom — stays inside the circular mask's safe zone (no clipping).
FG_SCALE = 0.64
FG = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
# Legacy raster: full logo on an opaque brown square.
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

def save(img, folder, name):
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name))

for dpi, s in FG.items():
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    inner = int(s * FG_SCALE)
    scaled = logo.resize((inner, inner), Image.LANCZOS)
    off = (s - inner) // 2
    canvas.alpha_composite(scaled, (off, off))
    save(canvas, f"mipmap-{dpi}", "ic_launcher_foreground.png")

for dpi, s in LEGACY.items():
    base = Image.new("RGBA", (s, s), brown)            # opaque brown square
    scaled = logo.resize((s, s), Image.LANCZOS)
    base.alpha_composite(scaled)                        # rounded logo blends into brown
    save(base, f"mipmap-{dpi}", "ic_launcher.png")
    save(base, f"mipmap-{dpi}", "ic_launcher_round.png")

# In-app logo (nav-rail wordmark): keep the original with its transparent margins.
save(logo, "drawable-nodpi", "mirrolit_logo.png")

print("done")
