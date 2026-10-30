"""Makes the addon's two images: the arc particle's soft glow and the mod icon (run from the project root)."""
import math
from PIL import Image, ImageDraw, ImageFilter

ROOT = "src/main/resources/"

# Arc particle: a white glow with a hot core, tinted by the particle's colour in game.
size = 16
img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
px = img.load()
for y in range(size):
    for x in range(size):
        d = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2)
        a = max(0.0, 1.0 - d) ** 1.6
        core = max(0.0, 1.0 - d * 2.2)
        v = int(255 * min(1.0, 0.75 + core))
        px[x, y] = (v, v, v, int(255 * min(1.0, a + core)))
img.save(ROOT + "assets/type95_addition/textures/particle/arc.png")

# Icon: a lightning bolt over a dark storm-blue disc.
S = 256
icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(icon)
d.ellipse((8, 8, S - 8, S - 8), fill=(14, 22, 48, 255), outline=(90, 170, 255, 255), width=6)
bolt = [(150, 24), (78, 136), (124, 136), (92, 232), (186, 104), (136, 104), (172, 24)]
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).polygon(bolt, fill=(80, 170, 255, 255))
glow = glow.filter(ImageFilter.GaussianBlur(14))
icon = Image.alpha_composite(icon, glow)
d = ImageDraw.Draw(icon)
d.polygon(bolt, fill=(235, 248, 255, 255), outline=(150, 210, 255, 255))
icon.save(ROOT + "type95_addition_icon.png")
