from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent / "kemtiz.ico"
S = 512

base = Image.new("RGBA", (S, S), (10, 11, 18, 0))
mask = Image.new("L", (S, S), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle((18, 18, S - 18, S - 18), radius=112, fill=255)
bg = Image.new("RGBA", (S, S), (13, 14, 23, 255))
base.alpha_composite(bg)
base.putalpha(mask)

draw = ImageDraw.Draw(base)
draw.ellipse((58, 148, 454, 360), outline=(108, 83, 206, 255), width=18)
draw.ellipse((58, 148, 454, 360), outline=(160, 137, 255, 110), width=5)
draw.ellipse((360, 115, 410, 165), fill=(180, 160, 255, 255))
draw.line((170, 116, 170, 398), fill=(231, 225, 255, 255), width=42)
draw.line((188, 272, 337, 130), fill=(184, 158, 255, 255), width=42)
draw.line((190, 264, 345, 398), fill=(143, 115, 244, 255), width=42)
base.save(OUT, format="ICO", sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
print(f"Created {OUT}")
