import numpy as np, os, json, subprocess, wave
R = '/home/claude/uwar/src/main/resources'
A = f'{R}/assets/uwar'
D = f'{R}/data/uwar'
os.makedirs(f'{A}/sounds', exist_ok=True)
SR = 44100
rng = np.random.default_rng(11)

def periodic_noise(dur, lo, hi):
    n = int(SR * dur)
    spec = np.zeros(n // 2 + 1, complex)
    f = np.fft.rfftfreq(n, 1 / SR)
    m = (f >= lo) & (f <= hi)
    spec[m] = np.exp(1j * rng.uniform(0, 2 * np.pi, m.sum())) * (1.0 / np.sqrt(np.maximum(f[m], 1) / lo))
    x = np.fft.irfft(spec, n)
    return x / (np.abs(x).max() + 1e-9)

def save(name, x, peak=0.8):
    x = x / (np.abs(x).max() + 1e-9) * peak
    wav = f'/tmp/{name}.wav'
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', '-ac', '1', f'{A}/sounds/{name}.ogg'], check=True)

def loop_multi(dur, fund, harm, flutter, nlo, nhi, nmix):
    t = np.arange(int(SR * dur)) / SR
    x = np.zeros_like(t)
    for k, (mult, amp) in enumerate(harm):
        f = round(fund * mult * dur) / dur  # frequence a nombre entier de periodes -> boucle parfaite
        x += amp * np.sin(2 * np.pi * f * t + k)
    fl = round(flutter * dur) / dur
    x *= (1 + 0.15 * np.sin(2 * np.pi * fl * t))
    x = x / np.abs(x).max()
    return x + nmix * periodic_noise(dur, nlo, nhi)

dur = 2.0
t = np.arange(int(SR * dur)) / SR
# helicoptere : claquement des pales + turbine
fp = 22
chop = (0.5 + 0.5 * np.cos(2 * np.pi * fp * t)) ** 2.5
body = np.zeros_like(t)
for k in range(1, 12):
    body += np.sin(2 * np.pi * fp * k * t + 0.4 * k) / k ** 0.7
x = body * (0.4 + chop) + 0.9 * chop * periodic_noise(dur, 60, 700)
x += 0.25 * np.sin(2 * np.pi * 900 * t) * (1 + 0.2 * np.sin(2 * np.pi * 4 * t)) + 0.18 * np.sin(2 * np.pi * 1802 * t)
x += 0.35 * periodic_noise(dur, 200, 3500)
save('heli_loop', x)
# avion : grondement + sifflement turbine
j = 1.0 * periodic_noise(dur, 70, 900) + 0.9 * periodic_noise(dur, 400, 6000)
j += 0.35 * np.sin(2 * np.pi * 1200 * t) + 0.25 * np.sin(2 * np.pi * 2400 * t + 1) + 0.5 * np.sin(2 * np.pi * 62 * t)
j *= (1 + 0.12 * np.sin(2 * np.pi * 3 * t))
save('jet_loop', j)
# canon
n = int(SR * 0.16)
tt = np.arange(n) / SR
env = np.exp(-tt * 45)
g = rng.normal(0, 1, n) * env + 0.9 * np.sin(2 * np.pi * (180 - 400 * tt) * tt) * np.exp(-tt * 36)
g[:int(SR * 0.003)] *= 1.8
save('gun', g)
# explosion
n = int(SR * 2.6)
tt = np.arange(n) / SR
nz = rng.normal(0, 1, n)
low = np.convolve(nz, np.ones(40) / 40, 'same')
e = low * np.exp(-tt * 1.8) * 2 + rng.normal(0, 1, n) * np.exp(-tt * 14) + 1.2 * np.sin(2 * np.pi * (60 - 20 * tt) * tt) * np.exp(-tt * 2.5)
e[:int(SR * 0.003)] *= 0.3
save('boom', e)
# lancement de roquette
n = int(SR * 1.2)
tt = np.arange(n) / SR
nz = rng.normal(0, 1, n)
sw = np.zeros(n)
for lo in (400, 900, 1800):
    kk = max(2, int(SR / (lo * 2)))
    sw += np.convolve(nz, np.ones(kk) / kk, 'same') * (tt / 0.25).clip(0, 1) * np.exp(-tt * 2.0)
sw += 0.6 * np.sin(2 * np.pi * (160 + 500 * tt) * tt) * np.exp(-tt * 3)
save('launch', sw)
# bip de verrouillage
n = int(SR * 0.3)
tt = np.arange(n) / SR
b = np.sin(2 * np.pi * 1400 * tt) * ((tt < 0.1) | ((tt > 0.15) & (tt < 0.25)))
save('beep', b * np.exp(-(tt % 0.15) * 6), 0.6)
# alarme
n = int(SR * 0.8)
tt = np.arange(n) / SR
f = np.where((tt % 0.4) < 0.2, 900, 600)
w = np.sign(np.sin(2 * np.pi * f * tt)) * 0.4 + np.sin(2 * np.pi * f * tt) * 0.6
save('warn', w * 0.7, 0.6)

sj = {n: {"sounds": [f"uwar:{n}"]} for n in ['gun', 'boom', 'launch', 'beep', 'warn', 'heli_loop', 'jet_loop']}
json.dump(sj, open(f'{A}/sounds.json', 'w'), indent=1)

# ---------------------------------------------------------------- lang
items = {
 'attack_heli': ("Hélicoptère d'attaque AH-64", 'AH-64 Attack Helicopter'),
 'fighter_jet': ('Avion de chasse F-16', 'F-16 Fighter Jet'),
 'mini_fighter': ('Mini chasseur', 'Mini Fighter'),
 'cannon_ammo': ('Munitions canon 20 mm', '20 mm Cannon Ammo'),
 'rocket_pod': ('Nacelle de roquettes', 'Rocket Pod'),
 'air_missile': ('Missile guidé', 'Guided Missile'),
 'aerial_bomb': ('Bombe aérienne', 'Aerial Bomb'),
 'fuel_can': ('Bidon de carburant aviation', 'Aviation Fuel Can'),
 'repair_kit': ('Kit de réparation', 'Repair Kit'),
 'ir_missile': ('Missile infrarouge', 'Infrared Missile'),
 'flak_missile': ('Missile à fragmentation (200 m)', 'Fragmentation Missile (200 m)'),
 'cruise_missile': ('Missile de croisière', 'Cruise Missile'),
 'ballistic_missile': ('Missile balistique', 'Ballistic Missile'),
 'buster_missile': ('Missile anti-bunker', 'Bunker Buster Missile'),
 'heavy_bomb': ('Bombe lourde (soute)', 'Heavy Bomb (bay)'),
 'nuclear_bomb': ('Bombe nucléaire', 'Nuclear Bomb'),
 'b17': ('Bombardier B-17 Forteresse volante', 'B-17 Flying Fortress'),
 'flak500_missile': ('Missile à fragmentation (500 m)', 'Fragmentation Missile (500 m)'),
 'longrange_missile': ('Missile longue portée (900 m)', 'Long-Range Missile (900 m)'),
 'incendiary_bomb': ('Bombe incendiaire', 'Incendiary Bomb'),
 'acid_bomb': ('Bombe à acide', 'Acid Bomb'),
 'cluster_bomb': ('Bombe à sous-munitions', 'Cluster Bomb'),
 'thermobaric_bomb': ('Bombe thermobarique', 'Thermobaric Bomb'),
 'gas_bomb': ('Bombe à gaz toxique', 'Toxic Gas Bomb'),
 'seismic_bomb': ('Bombe sismique', 'Seismic Bomb'),
 'lightning_bomb': ('Bombe foudre', 'Lightning Bomb'),
}
for idx, code in enumerate(['fr_fr', 'en_us']):
    L = {}
    for k, v in items.items():
        L[f'item.uwar.{k}'] = v[idx]
    L['entity.uwar.attack_heli'] = items['attack_heli'][idx]
    L['entity.uwar.fighter_jet'] = items['fighter_jet'][idx]
    L['entity.uwar.mini_fighter'] = items['mini_fighter'][idx]
    L['entity.uwar.b17'] = items['b17'][idx]
    L['entity.uwar.shell'] = ['Munition aérienne', 'Aircraft ordnance'][idx]
    L['key.categories.uwar'] = 'UWar - Aviation'
    L['key.uwar.weapon'] = ['Arme suivante', 'Next weapon'][idx]
    L['key.uwar.loadout'] = ['Interface d\'armement', 'Weapon loadout screen'][idx]
    L['key.uwar.reload'] = ['Recharger depuis l\'inventaire', 'Reload from inventory'][idx]
    json.dump(L, open(f'{A}/lang/{code}.json', 'w'), indent=1, ensure_ascii=False)

# ---------------------------------------------------------------- recettes
os.makedirs(f'{D}/recipes', exist_ok=True)
def it(i): return {"item": i}
def shapeless(name, ing, out, n=1):
    json.dump({"type": "minecraft:crafting_shapeless", "ingredients": [it(i) for i in ing], "result": {"item": f"uwar:{out}", "count": n}},
              open(f'{D}/recipes/{name}.json', 'w'), indent=1)
def shaped(name, pat, key, out):
    json.dump({"type": "minecraft:crafting_shaped", "pattern": pat, "key": {k: it(v) for k, v in key.items()}, "result": {"item": f"uwar:{out}", "count": 1}},
              open(f'{D}/recipes/{name}.json', 'w'), indent=1)
shapeless('cannon_ammo', ['minecraft:gunpowder', 'minecraft:gunpowder', 'minecraft:iron_ingot', 'minecraft:copper_ingot'], 'cannon_ammo', 2)
shapeless('rocket_pod', ['minecraft:firework_rocket', 'minecraft:firework_rocket', 'minecraft:gunpowder', 'minecraft:iron_ingot'], 'rocket_pod')
shapeless('air_missile', ['minecraft:firework_rocket', 'minecraft:iron_ingot', 'minecraft:iron_ingot', 'minecraft:gunpowder', 'minecraft:gunpowder', 'minecraft:redstone'], 'air_missile')
shapeless('aerial_bomb', ['minecraft:tnt', 'minecraft:iron_ingot', 'minecraft:iron_ingot'], 'aerial_bomb')
shapeless('fuel_can', ['minecraft:iron_ingot', 'minecraft:coal', 'minecraft:coal', 'minecraft:coal'], 'fuel_can')
shapeless('repair_kit', ['minecraft:iron_ingot', 'minecraft:iron_ingot', 'minecraft:redstone', 'minecraft:copper_ingot'], 'repair_kit')
shaped('attack_heli', ['BBB', 'IFI', 'GRG'], {'B': 'minecraft:iron_block', 'I': 'minecraft:iron_ingot', 'F': 'minecraft:blast_furnace', 'G': 'minecraft:glass', 'R': 'minecraft:redstone'}, 'attack_heli')
shaped('fighter_jet', ['IBI', 'BFB', 'GRG'], {'B': 'minecraft:iron_block', 'I': 'minecraft:iron_ingot', 'F': 'minecraft:blast_furnace', 'G': 'minecraft:glass', 'R': 'minecraft:redstone'}, 'fighter_jet')
shapeless('ir_missile', ['uwar:air_missile', 'minecraft:blaze_powder'], 'ir_missile')
shapeless('flak_missile', ['uwar:air_missile', 'minecraft:gunpowder', 'minecraft:gunpowder', 'minecraft:flint'], 'flak_missile')
shapeless('cruise_missile', ['uwar:air_missile', 'uwar:fuel_can', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:iron_ingot'], 'cruise_missile')
shapeless('ballistic_missile', ['uwar:fuel_can', 'uwar:fuel_can', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:iron_block', 'minecraft:redstone'], 'ballistic_missile')
shapeless('buster_missile', ['uwar:air_missile', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:iron_block', 'minecraft:gunpowder'], 'buster_missile')
shapeless('heavy_bomb', ['uwar:aerial_bomb', 'minecraft:tnt', 'minecraft:iron_ingot', 'minecraft:gunpowder'], 'heavy_bomb')
shapeless('nuclear_bomb', ['uwar:heavy_bomb', 'minecraft:nether_star', 'minecraft:netherite_ingot', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:tnt'], 'nuclear_bomb')
shaped('mini_fighter', ['IBI', 'GFG', ' R '], {'B': 'minecraft:iron_block', 'I': 'minecraft:iron_ingot', 'F': 'minecraft:furnace', 'G': 'minecraft:glass', 'R': 'minecraft:redstone'}, 'mini_fighter')
shaped('b17', ['BBB', 'IFI', 'GRG'], {'B': 'minecraft:iron_block', 'I': 'minecraft:iron_ingot', 'F': 'minecraft:blast_furnace', 'G': 'minecraft:glass', 'R': 'minecraft:redstone'}, 'b17')
shapeless('flak500_missile', ['uwar:flak_missile', 'uwar:fuel_can', 'minecraft:gunpowder'], 'flak500_missile')
shapeless('longrange_missile', ['uwar:air_missile', 'uwar:fuel_can', 'uwar:fuel_can', 'minecraft:tnt', 'minecraft:iron_block'], 'longrange_missile')
shapeless('incendiary_bomb', ['uwar:aerial_bomb', 'minecraft:blaze_powder', 'minecraft:blaze_powder', 'minecraft:coal'], 'incendiary_bomb')
shapeless('acid_bomb', ['uwar:aerial_bomb', 'minecraft:spider_eye', 'minecraft:slime_ball', 'minecraft:fermented_spider_eye'], 'acid_bomb')
shapeless('cluster_bomb', ['uwar:aerial_bomb', 'minecraft:tnt', 'minecraft:tnt', 'minecraft:gunpowder'], 'cluster_bomb')
shapeless('thermobaric_bomb', ['uwar:heavy_bomb', 'minecraft:blaze_powder', 'uwar:fuel_can'], 'thermobaric_bomb')
shapeless('gas_bomb', ['uwar:aerial_bomb', 'minecraft:poisonous_potato', 'minecraft:spider_eye', 'minecraft:fermented_spider_eye'], 'gas_bomb')
shapeless('seismic_bomb', ['uwar:heavy_bomb', 'minecraft:piston', 'minecraft:piston', 'minecraft:tnt'], 'seismic_bomb')
shapeless('lightning_bomb', ['uwar:aerial_bomb', 'minecraft:lightning_rod', 'minecraft:copper_ingot', 'minecraft:redstone'], 'lightning_bomb')
print('ok')
