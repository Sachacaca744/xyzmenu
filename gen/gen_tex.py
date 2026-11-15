import numpy as np, os, random, json
from PIL import Image, ImageDraw, ImageFilter
R = '/home/claude/uwar/src/main/resources/assets/uwar'
for d in ['textures/entity','textures/item','models/item','lang','sounds']:
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


def flame():
    im = Image.new('RGB', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            k = y / 31.0
            px[x, y] = (255, int(150 + 90 * (1 - k)), int(30 + 150 * (1 - k) ** 2))
    return im

PAL = {
 'attack_heli': dict(ha=(70, 78, 54), hb=(50, 56, 42), acc=(200, 40, 30), met=(46, 48, 52), lens=(30, 70, 100),
                     camo=[(70, 78, 54), (52, 60, 40), (98, 84, 56), (38, 44, 34)]),
 'fighter_jet': dict(ha=(132, 138, 148), hb=(96, 102, 112), acc=(200, 40, 30), met=(50, 52, 58), lens=(70, 100, 130),
                     camo=[(132, 138, 148), (104, 112, 124), (84, 92, 104), (150, 156, 164)]),
 'weapons': dict(ha=(120, 124, 120), hb=(84, 90, 84), acc=(230, 120, 30), met=(60, 64, 60), lens=(40, 70, 130),
                 camo=[(120, 124, 120), (96, 100, 96), (84, 90, 84), (140, 144, 140)]),
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
    cells[8] = cell(p['hb'], 4)
    cells[9] = cell((176, 182, 190), 5, True, False, True, -40)
    cells[10] = glass((18, 30, 46))
    cells[11] = cell((236, 238, 240), 3, True, True, False, -18)
    cells[12] = blur((205, 210, 215))
    cells[13] = camo(p['camo'])
    cells[14] = led((255, 60, 50))
    cells[15] = flame()
    for i, c in enumerate(cells):
        c = c.convert('RGBA')
        im.paste(c, ((i % 4) * 32, (i // 4) * 32))
    im.save(f'{R}/textures/entity/{name}.png')

for n, p in PAL.items():
    atlas(n, p)

# ---------------------------------------------------------------- icones (dessinees en grand puis reduites)
def mk(draw, name, S=32):
    big = Image.new('RGBA', (S * 8, S * 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    draw(d, S * 8)
    big.resize((S, S), Image.LANCZOS).save(f'{R}/textures/item/{name}.png')

def P(d, pts, S, fill, outline=(20, 22, 26)):
    d.polygon([(x * S, y * S) for x, y in pts], fill=fill, outline=outline)

def ic_heli(d, S):
    # vue de dessus : fuselage, queue, rotor
    P(d, [(.44, .22), (.56, .22), (.6, .55), (.53, .62), (.53, .88), (.47, .88), (.47, .62), (.4, .55)], S, (74, 84, 58))
    P(d, [(.43, .08), (.57, .08), (.57, .12), (.43, .12)], S, (60, 66, 50))
    P(d, [(.455, .14), (.545, .14), (.55, .3), (.45, .3)], S, (60, 120, 160))
    P(d, [(.25, .45), (.75, .45), (.75, .5), (.25, .5)], S, (56, 62, 46))
    d.line([(.1 * S, .1 * S), (.9 * S, .78 * S)], fill=(28, 30, 34), width=int(S * .035))
    d.line([(.9 * S, .1 * S), (.1 * S, .78 * S)], fill=(28, 30, 34), width=int(S * .035))
    d.ellipse([(.46 * S, .42 * S), (.54 * S, .5 * S)], fill=(200, 200, 205))
    d.ellipse([(.1 * S, .1 * S), (.9 * S, .78 * S)], outline=(255, 255, 255, 70), width=int(S * .01))

def ic_jet(d, S):
    P(d, [(.5, .06), (.56, .3), (.58, .5), (.95, .78), (.95, .86), (.58, .76), (.56, .9), (.7, .96), (.7, 1.0), (.3, 1.0), (.3, .96), (.44, .9), (.42, .76), (.05, .86), (.05, .78), (.42, .5), (.44, .3)], S, (140, 146, 156))
    P(d, [(.46, .24), (.54, .24), (.55, .4), (.45, .4)], S, (60, 110, 150))
    P(d, [(.48, .6), (.52, .6), (.52, .95), (.48, .95)], S, (110, 116, 126))

def ic_ammo(color, kind):
    def f(d, S):
        if kind == 'belt':
            for i in range(5):
                x = .12 + i * .17
                P(d, [(x, .3), (x + .12, .3), (x + .12, .72), (x, .72)], S, (200, 160, 50))
                P(d, [(x, .3), (x + .12, .3), (x + .09, .18), (x + .03, .18)], S, (150, 80, 40))
            d.line([(.05 * S, .78 * S), (.95 * S, .78 * S)], fill=(70, 70, 76), width=int(S * .05))
        elif kind == 'pod':
            P(d, [(.2, .2), (.8, .2), (.8, .8), (.2, .8)], S, (90, 96, 90))
            for i in range(3):
                for j in range(2):
                    cx, cy = .32 + i * .18, .38 + j * .26
                    d.ellipse([((cx - .07) * S, (cy - .07) * S), ((cx + .07) * S, (cy + .07) * S)], fill=(230, 120, 30), outline=(20, 20, 20))
        elif kind == 'missile':
            P(d, [(.1, .46), (.7, .46), (.9, .5), (.7, .54), (.1, .54)], S, (210, 214, 218))
            P(d, [(.1, .46), (.2, .46), (.12, .3), (.08, .3)], S, (160, 60, 50))
            P(d, [(.1, .54), (.2, .54), (.12, .7), (.08, .7)], S, (160, 60, 50))
            P(d, [(.7, .46), (.9, .5), (.7, .54)], S, (200, 50, 40))
        elif kind == 'bomb':
            d.ellipse([(.2 * S, .35 * S), (.85 * S, .65 * S)], fill=(90, 96, 90), outline=(20, 20, 20))
            P(d, [(.1, .42), (.25, .42), (.2, .28), (.1, .3)], S, (140, 140, 140))
            P(d, [(.1, .58), (.25, .58), (.2, .72), (.1, .7)], S, (140, 140, 140))
            d.rectangle([(.5 * S, .35 * S), (.58 * S, .65 * S)], fill=(230, 190, 40))
        elif kind == 'fuel':
            P(d, [(.25, .3), (.75, .3), (.75, .85), (.25, .85)], S, (190, 50, 40))
            P(d, [(.3, .18), (.5, .18), (.5, .3), (.3, .3)], S, (60, 60, 64))
            P(d, [(.5, .22), (.68, .22), (.68, .3), (.5, .3)], S, (60, 60, 64))
            P(d, [(.4, .5), (.6, .5), (.6, .66), (.4, .66)], S, (240, 220, 80))
        elif kind == 'repair':
            P(d, [(.15, .3), (.85, .3), (.85, .78), (.15, .78)], S, (60, 130, 70))
            P(d, [(.35, .2), (.65, .2), (.65, .3), (.35, .3)], S, (40, 90, 50))
            P(d, [(.44, .42), (.56, .42), (.56, .66), (.44, .66)], S, (240, 240, 240))
            P(d, [(.32, .5), (.68, .5), (.68, .58), (.32, .58)], S, (240, 240, 240))
    return f

mk(ic_heli, 'attack_heli')
mk(ic_jet, 'fighter_jet')
mk(ic_ammo(0, 'belt'), 'cannon_ammo')
mk(ic_ammo(0, 'pod'), 'rocket_pod')
mk(ic_ammo(0, 'missile'), 'air_missile')
mk(ic_ammo(0, 'bomb'), 'aerial_bomb')
mk(ic_ammo(0, 'fuel'), 'fuel_can')
mk(ic_ammo(0, 'repair'), 'repair_kit')

ITEMS = ['attack_heli', 'fighter_jet', 'cannon_ammo', 'rocket_pod', 'air_missile', 'aerial_bomb', 'fuel_can', 'repair_kit']
for n in ITEMS:
    json.dump({"parent": "item/generated", "textures": {"layer0": f"uwar:item/{n}"}}, open(f'{R}/models/item/{n}.json', 'w'), indent=1)
print('textures ok')
