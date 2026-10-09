from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent / "kemtiz.ico"
S = 512


def gradient(size, start, end, direction="vertical"):
    w, h = size
    image = Image.new("RGBA", size)
    px = image.load()
    steps = max(1, (h if direction == "vertical" else w) - 1)
    for y in range(h):
        for x in range(w):
            t = (y if direction == "vertical" else x) / steps
            px[x, y] = tuple(round(start[i] * (1 - t) + end[i] * t) for i in range(4))
    return image


def polygon_mask(points):
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(points, fill=255)
    return mask.filter(ImageFilter.GaussianBlur(1.0))


# Kemtiz's new mark: a precise K-shaped portal with a violet-to-cyan spectral tail.
tile_mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(tile_mask).rounded_rectangle((18, 18, S - 18, S - 18), radius=112, fill=255)
base = gradient((S, S), (31, 35, 58, 255), (8, 13, 25, 255))
base.putalpha(tile_mask)

halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(halo).ellipse((50, 26, 455, 445), fill=(127, 94, 255, 74))
halo = halo.filter(ImageFilter.GaussianBlur(60))
base.alpha_composite(halo)

stem = Image.new("L", (S, S), 0)
ImageDraw.Draw(stem).rounded_rectangle((130, 93, 196, 419), radius=29, fill=255)
stem = stem.filter(ImageFilter.GaussianBlur(1.0))

upper_points = [
    (174, 227), (308, 93), (382, 143), (251, 265),
    (205, 306), (170, 274)
]
lower_points = [
    (216, 241), (267, 193), (407, 329), (346, 389)
]
upper_mask = polygon_mask(upper_points)
lower_mask = polygon_mask(lower_points)

stem_layer = gradient((S, S), (223, 210, 255, 255), (145, 111, 255, 255), "vertical")
stem_layer.putalpha(stem)
base.alpha_composite(stem_layer)

upper_layer = gradient((S, S), (209, 191, 255, 255), (119, 83, 230, 255), "vertical")
upper_layer.putalpha(upper_mask)
base.alpha_composite(upper_layer)

lower_layer = gradient((S, S), (157, 126, 255, 255), (65, 219, 211, 255), "vertical")
lower_layer.putalpha(lower_mask)
base.alpha_composite(lower_layer)

# Fine highlights make the silhouette read clearly at small Windows icon sizes.
accents = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ad = ImageDraw.Draw(accents)
ad.line([(151, 135), (168, 121), (177, 121)], fill=(247, 242, 255, 205), width=6)
ad.line([(285, 224), (352, 290), (376, 313)], fill=(202, 255, 248, 170), width=5)
ad.polygon([(404, 84), (414, 105), (435, 114), (414, 123), (404, 144), (395, 123), (374, 114), (395, 105)],
           fill=(160, 248, 239, 245))
accent_alpha = Image.composite(accents.getchannel("A"), Image.new("L", (S, S), 0), tile_mask)
accents.putalpha(accent_alpha)
base.alpha_composite(accents)

base = base.resize((256, 256), Image.Resampling.LANCZOS)
base.save(OUT, format="ICO", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print(f"Created {OUT}")
