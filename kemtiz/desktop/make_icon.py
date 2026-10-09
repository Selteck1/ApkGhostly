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


# Rounded dark glass tile.
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle((18, 18, S - 18, S - 18), radius=112, fill=255)
base = gradient((S, S), (31, 32, 56, 255), (10, 17, 29, 255))
base.putalpha(mask)

# Add a soft violet halo behind the flowing ribbon mark.
halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
hd = ImageDraw.Draw(halo)
hd.ellipse((68, 38, 440, 408), fill=(128, 91, 255, 70))
halo = halo.filter(ImageFilter.GaussianBlur(54))
base.alpha_composite(halo)

# Draw the custom Kemtiz ribbon in a high-resolution icon surface.
ribbon_mask = Image.new("L", (S, S), 0)
d = ImageDraw.Draw(ribbon_mask)
# Upper folded ribbon.
d.polygon(
    [(115, 230), (123, 169), (156, 119), (224, 89), (351, 58),
     (327, 107), (287, 147), (240, 175), (183, 199), (151, 231),
     (141, 294), (115, 320)],
    fill=255,
)
# Curving lower tail.
d.polygon(
    [(143, 257), (186, 220), (230, 198), (282, 172), (328, 142),
     (366, 101), (364, 157), (344, 204), (308, 244), (265, 274),
     (230, 306), (205, 354), (177, 399), (154, 382), (142, 339)],
    fill=255,
)
ribbon_mask = ribbon_mask.filter(ImageFilter.GaussianBlur(0.7))
ribbon_top = gradient((S, S), (219, 199, 255, 255), (130, 88, 255, 255), "vertical")
ribbon_lower = gradient((S, S), (151, 107, 255, 255), (62, 220, 210, 255), "vertical")

# Compose the upper and lower ribbons with their own masks.
upper_mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(upper_mask).polygon(
    [(115, 230), (123, 169), (156, 119), (224, 89), (351, 58),
     (327, 107), (287, 147), (240, 175), (183, 199), (151, 231),
     (141, 294), (115, 320)], fill=255
)
upper_mask = upper_mask.filter(ImageFilter.GaussianBlur(0.7))
lower_mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(lower_mask).polygon(
    [(143, 257), (186, 220), (230, 198), (282, 172), (328, 142),
     (366, 101), (364, 157), (344, 204), (308, 244), (265, 274),
     (230, 306), (205, 354), (177, 399), (154, 382), (142, 339)], fill=255
)
lower_mask = lower_mask.filter(ImageFilter.GaussianBlur(0.7))

# A subtle blurred shadow adds depth without changing the silhouette.
shadow = Image.new("RGBA", (S, S), (80, 57, 183, 0))
shadow.putalpha(ribbon_mask.filter(ImageFilter.GaussianBlur(18)))
base.alpha_composite(shadow)

upper = ribbon_top
upper.putalpha(upper_mask)
base.alpha_composite(upper)

lower = ribbon_lower
lower.putalpha(lower_mask)
base.alpha_composite(lower)

# Tiny light-catching line accents.
accents = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ad = ImageDraw.Draw(accents)
ad.line([(154, 182), (203, 148), (263, 127), (319, 90)], fill=(245, 237, 255, 200), width=7)
ad.line([(181, 292), (221, 253), (269, 223), (316, 181)], fill=(192, 255, 249, 155), width=5)
ad.ellipse((384, 345, 398, 359), fill=(112, 232, 224, 255))
ad.ellipse((93, 150, 105, 162), fill=(201, 183, 255, 255))
accents.putalpha(Image.composite(accents.getchannel("A"), Image.new("L", (S,S), 0), mask))
base.alpha_composite(accents)

base = base.resize((256, 256), Image.Resampling.LANCZOS)
base.save(OUT, format="ICO", sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
print(f"Created {OUT}")
