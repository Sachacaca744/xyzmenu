import numpy as np, os, random, json
from PIL import Image, ImageDraw, ImageFilter
R = '/home/claude/udrone/src/main/resources/assets/udrone'
for d in ['textures/entity','textures/item','textures/block','models/item','models/block','blockstates','lang','sounds']:
    os.makedirs(f'{R}/{d}', exist_ok=True)
rng = np.random.default_rng(7)

def noise(h, w, s=1.0):
    return rng.normal(0, s, (h, w))

def cell(base, var=7, lines=True, rivets=True, brushed=False, edge=-28):
    h = w = 32
    a = np.zeros((h, w, 3), float) + np.array(base, float)
    n = noise(h, w, var)
    a += n[..., None]
    if brushed:
        b = np.repeat(noise(h, 1, var * 1.2), w, axis=1)
        a += b[..., None]
    # gentle gradient
    g = np.linspace(6, -6, h)[:, None, None]
    a += g
    im = Image.fromarray(np.clip(a, 0, 255).astype('uint8'))
    d = ImageDraw.Draw(im)
    dark = tuple(int(max(0, c + edge)) for c in base)
    light = tuple(int(min(255, c + 22)) for c in base)
    if lines:
        d.rectangle([1, 1, 30, 30], outline=dark)
        d.line([2, 2, 29, 2], fill=light)
        d.line([16, 2, 16, 29], fill=tuple(int(max(0, c - 14)) for c in base))
    if rivets:
        for x, y in [(4, 4), (27, 4), (4, 27), (27, 27), (16, 5), (16, 26)]:
            d.point((x, y), fill=dark)
            d.point((x + 1, y + 1), fill=light)
    # scratches / weathering
    for _ in range(5):
        x = random.randint(2, 28); y = random.randint(2, 28); l = random.randint(2, 7)
        d.line([x, y, x + l, y + random.randint(-1, 1)], fill=tuple(int(min(255, c + 25)) for c in base))
    return im

def glass(base):
    im = Image.new('RGB', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            r = ((x - 12) ** 2 + (y - 12) ** 2) ** 0.5 / 26.0
            k = max(0.15, 1.0 - r)
            px[x, y] = tuple(int(c * (0.35 + 0.9 * k)) for c in base)
    d = ImageDraw.Draw(im)
    d.line([6, 24, 24, 6], fill=tuple(min(255, int(c * 2.3 + 30)) for c in base))
    d.line([9, 27, 27, 9], fill=tuple(min(255, int(c * 1.6 + 20)) for c in base))
    d.rectangle([1, 1, 30, 30], outline=(10, 12, 16))
    return im

def tread():
    im = cell((22, 22, 24), 4, False, False)
    d = ImageDraw.Draw(im)
    for x in range(2, 31, 4):
        d.line([x, 2, x, 29], fill=(10, 10, 12))
    return im

def hazard(base):
    im = Image.new('RGB', (32, 32), base)
    d = ImageDraw.Draw(im)
    for i in range(-32, 64, 8):
        d.polygon([(i, 0), (i + 4, 0), (i + 4 - 32, 32), (i - 32, 32)], fill=(20, 20, 20))
    d.rectangle([1, 1, 30, 30], outline=tuple(int(c * .6) for c in base))
    return im

def prop():
    im = cell((26, 27, 30), 3, False, False)
    d = ImageDraw.Draw(im)
    for y in range(0, 32, 2):
        d.line([0, y, 31, y], fill=(32, 34, 38))
    d.rectangle([24, 0, 31, 31], fill=(190, 40, 30))
    d.rectangle([0, 0, 3, 31], fill=(50, 52, 58))
    return im

def led(c):
    im = Image.new('RGB', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            r = ((x - 16) ** 2 + (y - 16) ** 2) ** 0.5 / 20
            k = max(0.5, 1 - r * 0.6)
            px[x, y] = tuple(min(255, int(v * k + 70 * (1 - r) ** 2)) for v in c)
    return im

def solar():
    im = Image.new('RGB', (32, 32), (14, 24, 60))
    d = ImageDraw.Draw(im)
    for x in range(0, 32, 8):
        d.line([x, 0, x, 31], fill=(110, 120, 140))
    for y in range(0, 32, 8):
        d.line([0, y, 31, y], fill=(110, 120, 140))
    for x in range(1, 32, 8):
        for y in range(1, 32, 8):
            d.rectangle([x, y, x + 6, y + 6], fill=(18 + (x * y) % 14, 34 + (x + y) % 12, 86 + (x * 3 + y) % 24))
    return im

def camo(pal):
    im = Image.new('RGB', (32, 32), pal[0])
    px = im.load()
    for k in range(1, len(pal)):
        for _ in range(7):
            cx, cy, r = random.randint(0, 31), random.randint(0, 31), random.randint(3, 8)
            for y in range(32):
                for x in range(32):
                    if (x - cx) ** 2 + (y - cy) ** 2 < r * r * (0.7 + 0.3 * random.random()):
                        px[x, y] = pal[k]
    return im

def blur(c):
    im = Image.new('RGBA', (32, 32), c + (200,))
    return im

PAL = {
 'mini_drone':   dict(ha=(205, 208, 212), hb=(150, 155, 162), acc=(235, 120, 30), met=(50, 52, 58), lens=(40, 80, 150)),
 'quad_drone':   dict(ha=(58, 62, 70), hb=(36, 38, 44), acc=(240, 130, 20), met=(40, 42, 48), lens=(40, 100, 170)),
 'fast_drone':   dict(ha=(26, 26, 30), hb=(160, 24, 24), acc=(230, 40, 30), met=(52, 54, 60), lens=(60, 60, 150)),
 'surveillance_drone': dict(ha=(226, 230, 236), hb=(120, 135, 160), acc=(40, 150, 220), met=(60, 64, 72), lens=(30, 90, 160)),
 'heavy_drone':  dict(ha=(76, 84, 58), hb=(52, 58, 40), acc=(235, 190, 30), met=(44, 46, 42), lens=(150, 60, 40)),
 'mq9_reaper':   dict(ha=(142, 148, 154), hb=(104, 112, 122), acc=(190, 40, 30), met=(56, 60, 66), lens=(40, 70, 130)),
 'weapons':      dict(ha=(120, 124, 120), hb=(84, 90, 84), acc=(220, 70, 30), met=(50, 54, 52), lens=(40, 70, 130)),
}

def atlas(name, p):
    random.seed(name)
    im = Image.new('RGBA', (128, 128), (0, 0, 0, 255))
    cells = [None] * 16
    cells[0] = cell(p['ha'], 6)
    cells[1] = cell(p['hb'], 6)
    cells[2] = cell(p['met'], 5, True, True, True)
    cells[3] = glass(p['lens'])
    cells[4] = tread()
    cells[5] = hazard(p['acc'])
    cells[6] = prop()
    cells[7] = led((70, 255, 110))
    cells[8] = solar()
    cells[9] = cell((176, 182, 190), 5, True, False, True, -40)
    cells[10] = glass((18, 30, 46))
    cells[11] = cell((236, 238, 240), 3, True, True, False, -18)
    cells[12] = blur((205, 210, 215))
    cells[13] = camo([p['ha'], p['hb'], tuple(int(c * .75) for c in p['ha']), tuple(int(c * .55) for c in p['hb'])])
    cells[14] = led((255, 60, 50))
    cells[15] = cell(p['hb'], 8)
    for i, c in enumerate(cells):
        c = c.convert('RGBA')
        im.paste(c, ((i % 4) * 32, (i // 4) * 32))
    im.save(f'{R}/textures/entity/{name}.png')

for n, p in PAL.items():
    atlas(n, p)

# ---------------------------------------------------------------- icones (vue de dessus)
def icon(name, p):
    S = 8
    im = Image.new('RGBA', (32 * S, 32 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    def c(x, y): return (x * S, y * S)
    ha, hb, met, acc = p['ha'], p['hb'], p['met'], p['acc']
    if name == 'mq9_reaper':
        d.polygon([c(16, 3), c(18, 8), c(18.5, 14), c(30, 15), c(30, 17.5), c(18, 18), c(17.5, 25), c(23, 27), c(23, 28.5), c(16, 28), c(9, 28.5), c(9, 27), c(14.5, 25), c(14, 18), c(2, 17.5), c(2, 15), c(13.5, 14), c(14, 8)], fill=ha + (255,), outline=met + (255,))
        d.ellipse([c(14.6, 9)[0], c(14.6, 9)[1], c(17.4, 13)[0], c(17.4, 13)[1]], fill=(230, 232, 235, 255))
        d.polygon([c(15, 4), c(17, 4), c(16.6, 8), c(15.4, 8)], fill=hb + (255,))
        d.rectangle([c(1.8, 15.4)[0], c(1.8, 15.4)[1], c(2.8, 16.4)[0], c(2.8, 16.4)[1]], fill=(255, 60, 50, 255))
        d.rectangle([c(29.2, 15.4)[0], c(29.2, 15.4)[1], c(30.2, 16.4)[0], c(30.2, 16.4)[1]], fill=(70, 255, 110, 255))
    else:
        n = {'mini_drone': 4, 'quad_drone': 4, 'fast_drone': 4, 'surveillance_drone': 6, 'heavy_drone': 8}[name]
        import math
        R_ = 10.5
        for i in range(n):
            a = math.radians((i + .5) * 360 / n if name != 'fast_drone' else [30, -30, 150, -150][i])
            ex, ey = 16 + math.sin(a) * R_, 16 - math.cos(a) * R_
            d.line([c(16, 16), c(ex, ey)], fill=hb + (255,), width=int(1.6 * S))
            r = 4.2 if n <= 4 else 3.4
            d.ellipse([c(ex - r, ey - r)[0], c(ex - r, ey - r)[1], c(ex + r, ey + r)[0], c(ex + r, ey + r)[1]], fill=(150, 160, 170, 90), outline=(220, 225, 230, 200), width=S // 2)
            d.ellipse([c(ex - 1, ey - 1)[0], c(ex - 1, ey - 1)[1], c(ex + 1, ey + 1)[0], c(ex + 1, ey + 1)[1]], fill=met + (255,))
        d.ellipse([c(11.5, 10)[0], c(11.5, 10)[1], c(20.5, 22)[0], c(20.5, 22)[1]], fill=ha + (255,), outline=met + (255,), width=S // 2)
        d.rectangle([c(13.5, 11)[0], c(13.5, 11)[1], c(18.5, 15)[0], c(18.5, 15)[1]], fill=acc + (255,))
        d.ellipse([c(14.5, 19)[0], c(14.5, 19)[1], c(17.5, 22.5)[0], c(17.5, 22.5)[1]], fill=p['lens'] + (255,), outline=(10, 10, 14, 255))
    im = im.resize((32, 32), Image.LANCZOS)
    im.save(f'{R}/textures/item/{name}.png')
    json.dump({"parent": "item/generated", "textures": {"layer0": f"udrone:item/{name}"}}, open(f'{R}/models/item/{name}.json', 'w'))

for n, p in PAL.items():
    if n != 'weapons':
        icon(n, p)

# telecommande
im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rectangle([3, 5, 12, 14], fill=(46, 48, 54, 255), outline=(20, 20, 24, 255))
d.rectangle([4, 6, 11, 9], fill=(30, 150, 80, 255))
d.point((5, 7), fill=(150, 255, 190, 255))
d.ellipse([4, 11, 6, 13], fill=(200, 40, 40, 255)); d.ellipse([9, 11, 11, 13], fill=(60, 120, 230, 255))
d.line([8, 1, 8, 5], fill=(190, 195, 200, 255)); d.point((8, 0), fill=(255, 70, 60, 255))
im = im.resize((32, 32), Image.NEAREST)
im.save(f'{R}/textures/item/controller.png')
json.dump({"parent": "item/handheld", "textures": {"layer0": "udrone:item/controller"}}, open(f'{R}/models/item/controller.json', 'w'))

# ---------------------------------------------------------------- blocs
def metal_tex(size=16, base=(70, 76, 84)):
    random.seed(3)
    a = np.zeros((size, size, 3)) + np.array(base, float) + noise(size, size, 5)[..., None]
    im = Image.fromarray(np.clip(a, 0, 255).astype('uint8'))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, size - 1, size - 1], outline=(36, 40, 46))
    for x, y in [(2, 2), (size - 3, 2), (2, size - 3), (size - 3, size - 3)]:
        d.point((x, y), fill=(150, 156, 164))
    return im

metal_tex().save(f'{R}/textures/block/station_metal.png')
# ecran
sc = Image.new('RGB', (16, 16), (6, 22, 14)); d = ImageDraw.Draw(sc)
d.rectangle([0, 0, 15, 15], outline=(30, 40, 36))
d.rectangle([1, 1, 14, 14], outline=(14, 60, 36))
for x in range(2, 14, 3): d.line([x, 12, x, 12 - (x * 7) % 7 - 2], fill=(88, 255, 122))
d.line([2, 4, 13, 4], fill=(88, 255, 122)); d.line([2, 6, 8, 6], fill=(60, 180, 90))
d.ellipse([9, 5, 12, 8], outline=(255, 190, 64))
sc.save(f'{R}/textures/block/station_screen.png')
kb = Image.new('RGB', (16, 16), (30, 32, 36)); d = ImageDraw.Draw(kb)
for y in range(2, 14, 3):
    for x in range(1, 15, 3): d.rectangle([x, y, x + 1, y + 1], fill=(120, 126, 134))
kb.save(f'{R}/textures/block/station_kb.png')
top = metal_tex(16, (52, 58, 66)); d = ImageDraw.Draw(top)
d.rectangle([2, 2, 13, 13], outline=(30, 90, 56)); top.save(f'{R}/textures/block/station_top.png')
# pad
pt = metal_tex(16, (58, 62, 68)); d = ImageDraw.Draw(pt)
d.ellipse([2, 2, 13, 13], outline=(240, 200, 40))
d.line([5, 5, 5, 10], fill=(240, 200, 40)); d.line([10, 5, 10, 10], fill=(240, 200, 40)); d.line([5, 8, 10, 8], fill=(240, 200, 40))
for x, y in [(0, 0), (14, 0), (0, 14), (14, 14)]: d.rectangle([x, y, x + 1, y + 1], fill=(70, 255, 110))
pt.save(f'{R}/textures/block/pad_top.png')
metal_tex(16, (44, 48, 54)).save(f'{R}/textures/block/pad_side.png')

def face(tex, uv=None):
    f = {"texture": tex}
    if uv: f["uv"] = uv
    return f

def el(a, b, faces=None, tex='#metal', rot=None, screen=None):
    f = {k: {"texture": tex} for k in ['north', 'south', 'east', 'west', 'up', 'down']}
    if screen: f['south'] = {"texture": "#screen", "uv": [0, 0, 16, 16]}
    e = {"from": a, "to": b, "faces": f}
    if rot: e["rotation"] = rot
    return e

els = [
    el([0, 0, 0], [16, 8, 16]),
    el([0, 8, 0], [16, 10, 16], tex='#top'),
    el([0.5, 10, 10], [5.5, 15.5, 11.2], rot={"origin": [3, 10, 10], "axis": "x", "angle": -22.5}, screen=True),
    el([5.5, 10, 10], [10.5, 16.5, 11.2], rot={"origin": [8, 10, 10], "axis": "x", "angle": -22.5}, screen=True),
    el([10.5, 10, 10], [15.5, 15.5, 11.2], rot={"origin": [13, 10, 10], "axis": "x", "angle": -22.5}, screen=True),
    el([3, 10, 10.6], [13, 11, 12.2]),
    el([4, 10, 3], [12, 10.6, 6.5], tex='#kb'),
    el([13, 10, 4], [14.2, 13, 5.2]),
    el([1.5, 10, 13], [2.1, 18, 13.6]),
]
json.dump({"parent": "block/block", "textures": {"metal": "udrone:block/station_metal", "screen": "udrone:block/station_screen",
          "kb": "udrone:block/station_kb", "top": "udrone:block/station_top", "particle": "udrone:block/station_metal"}, "elements": els},
          open(f'{R}/models/block/control_station.json', 'w'))
pad = {"parent": "block/block", "textures": {"top": "udrone:block/pad_top", "side": "udrone:block/pad_side", "particle": "udrone:block/pad_side"},
       "elements": [{"from": [0, 0, 0], "to": [16, 3, 16], "faces": {
           "up": {"texture": "#top", "uv": [0, 0, 16, 16]}, "down": {"texture": "#side"},
           "north": {"texture": "#side", "uv": [0, 13, 16, 16]}, "south": {"texture": "#side", "uv": [0, 13, 16, 16]},
           "east": {"texture": "#side", "uv": [0, 13, 16, 16]}, "west": {"texture": "#side", "uv": [0, 13, 16, 16]}}}]}
json.dump(pad, open(f'{R}/models/block/charging_pad.json', 'w'))
json.dump({"variants": {"facing=south": {"model": "udrone:block/control_station"},
                        "facing=west": {"model": "udrone:block/control_station", "y": 90},
                        "facing=north": {"model": "udrone:block/control_station", "y": 180},
                        "facing=east": {"model": "udrone:block/control_station", "y": 270}}}, open(f'{R}/blockstates/control_station.json', 'w'))
json.dump({"variants": {"": {"model": "udrone:block/charging_pad"}}}, open(f'{R}/blockstates/charging_pad.json', 'w'))
disp = {"gui": {"rotation": [30, 225, 0], "scale": 0.6}, "ground": {"scale": 0.4, "translation": [0, 3, 0]},
        "fixed": {"scale": 0.5}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": 0.4},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": 0.4}}
for n in ['control_station', 'charging_pad']:
    m = {"parent": f"udrone:block/{n}", "display": disp}
    json.dump(m, open(f'{R}/models/item/{n}.json', 'w'))
print('textures ok')
