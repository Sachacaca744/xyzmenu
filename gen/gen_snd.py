import numpy as np, os, json, subprocess, wave
R = '/home/claude/udrone/src/main/resources'
A = f'{R}/assets/udrone'
D = f'{R}/data/udrone'
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

# quadricoptere : sifflement des moteurs + souffle
q = loop_multi(2.0, 230, [(1, 1), (2, .5), (3, .35), (4.03, .25), (5.9, .12)], 13, 600, 5000, 0.35)
q += 0.25 * loop_multi(2.0, 247, [(1, 1), (2, .4)], 9, 100, 200, 0.0)
save('quad_loop', q)
# gros multirotor : plus grave
h = loop_multi(2.0, 96, [(1, 1), (2, .7), (3, .5), (4.02, .3), (6, .2)], 7, 200, 2500, 0.55)
h += 0.4 * loop_multi(2.0, 104, [(1, 1), (2, .5)], 5, 80, 200, 0.0)
save('heavy_loop', h)
# moteur a helice (MQ-9) : pulsations d'echappement + souffle
dur = 2.0
t = np.arange(int(SR * dur)) / SR
fp = 36
pulse = np.zeros_like(t)
for k in range(1, 18):
    pulse += np.sin(2 * np.pi * fp * k * t + 0.3 * k) / k ** 0.8
pulse *= (1 + 0.35 * np.sin(2 * np.pi * 3 * t)) * 0.8
blade = 0.5 * np.sin(2 * np.pi * 108 * t) * (1 + 0.5 * np.sin(2 * np.pi * 36 * t))
r = pulse + blade + 0.6 * periodic_noise(dur, 80, 1800)
save('reaper_loop', r)

# arme
n = int(SR * 0.22)
t = np.arange(n) / SR
env = np.exp(-t * 38)
g = rng.normal(0, 1, n) * env + 0.9 * np.sin(2 * np.pi * (140 - 250 * t) * t) * np.exp(-t * 30)
g[:int(SR * 0.004)] *= 1.8
save('gun', g)
# explosion
n = int(SR * 2.4)
t = np.arange(n) / SR
nz = rng.normal(0, 1, n)
k = np.ones(40) / 40
low = np.convolve(nz, k, 'same')
e = low * np.exp(-t * 1.9) * 2 + rng.normal(0, 1, n) * np.exp(-t * 14) + 1.2 * np.sin(2 * np.pi * (60 - 20 * t) * t) * np.exp(-t * 2.5)
e[:int(SR * 0.003)] *= 0.3
save('boom', e)
# lancement : souffle
n = int(SR * 1.1)
t = np.arange(n) / SR
nz = rng.normal(0, 1, n)
sweep = np.zeros(n)
for lo in (400, 900, 1800):
    kk = max(2, int(SR / (lo * 2)))
    sweep += np.convolve(nz, np.ones(kk) / kk, 'same') * (t / 0.3).clip(0, 1) * np.exp(-t * 2.2)
sweep += 0.6 * np.sin(2 * np.pi * (180 + 500 * t) * t) * np.exp(-t * 3)
save('launch', sweep)
# bip
n = int(SR * 0.32)
t = np.arange(n) / SR
b = np.sin(2 * np.pi * 1300 * t) * ((t < 0.12) | ((t > 0.17) & (t < 0.29)))
save('beep', b * np.exp(-((t % 0.17)) * 6), 0.6)
# perte de signal
n = int(SR * 0.9)
t = np.arange(n) / SR
f = np.where(t < 0.3, 880, np.where(t < 0.6, 660, 440))
b = np.sign(np.sin(2 * np.pi * f * t)) * 0.4 + np.sin(2 * np.pi * f * t) * 0.6
b *= np.where((t % 0.3) < 0.26, 1, 0) * np.exp(-t * 1.5)
save('lost', b, 0.6)
# recharge
n = int(SR * 0.7)
t = np.arange(n) / SR
f = 300 + 500 * t
c = (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * 2 * f * t)) * np.exp(-t * 2.5)
c += 0.3 * rng.normal(0, 1, n) * np.exp(-t * 8) * (np.sin(2 * np.pi * 90 * t) > 0)
save('charge', c, 0.5)

sj = {n: {"sounds": [f"udrone:{n}"]} for n in ['gun', 'boom', 'launch', 'beep', 'lost', 'charge', 'quad_loop', 'heavy_loop', 'reaper_loop']}
json.dump(sj, open(f'{A}/sounds.json', 'w'), indent=1)

# ----------------------------------------------------------- lang
names = {'mini_drone': ('Mini-drone de reconnaissance', 'Reconnaissance Mini-Drone'), 'quad_drone': ('Drone quadricoptère tactique', 'Tactical Quadcopter Drone'),
         'fast_drone': ('Drone rapide', 'Fast Drone'), 'surveillance_drone': ('Drone de surveillance', 'Surveillance Drone'),
         'heavy_drone': ('Drone lourd de combat', 'Heavy Combat Drone'), 'mq9_reaper': ('MQ-9 Reaper', 'MQ-9 Reaper')}
for idx, code in enumerate(['fr_fr', 'en_us']):
    L = {}
    for k, v in names.items():
        L[f'item.udrone.{k}'] = v[idx]
        L[f'entity.udrone.{k}'] = v[idx]
    L['entity.udrone.shell'] = ['Munition de drone', 'Drone ordnance'][idx]
    L['item.udrone.controller'] = ['Télécommande de drone', 'Drone Controller'][idx]
    L['block.udrone.control_station'] = ['Station de contrôle', 'Control Station'][idx]
    L['block.udrone.charging_pad'] = ["Plateforme de recharge", 'Charging Pad'][idx]
    L['key.categories.udrone'] = 'Advanced Drone System'
    L['key.udrone.exit'] = ['Quitter le pilotage', 'Stop piloting'][idx]
    L['key.udrone.weapon'] = ["Arme suivante", 'Next weapon'][idx]
    L['key.udrone.rtb'] = ['Retour à la base (on/off)', 'Return to base (toggle)'][idx]
    L['key.udrone.next'] = ['Drone suivant', 'Next drone'][idx]
    L['key.udrone.zoom'] = ['Zoom caméra', 'Camera zoom'][idx]
    json.dump(L, open(f'{A}/lang/{code}.json', 'w'), indent=1, ensure_ascii=False)

# ----------------------------------------------------------- donnees
os.makedirs(f'{D}/recipes', exist_ok=True)
os.makedirs(f'{D}/loot_tables/blocks', exist_ok=True)
os.makedirs(f'{R}/data/minecraft/tags/blocks/mineable', exist_ok=True)
def it(i): return {"item": i}
def shaped(name, pat, key, out):
    json.dump({"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pat, "key": {k: it(v) for k, v in key.items()},
               "result": {"item": f"udrone:{out}"}}, open(f'{D}/recipes/{name}.json', 'w'), indent=1)
def shapeless(name, ings, out):
    json.dump({"type": "minecraft:crafting_shapeless", "category": "equipment", "ingredients": [it(i) for i in ings],
               "result": {"item": f"udrone:{out}"}}, open(f'{D}/recipes/{name}.json', 'w'), indent=1)
mc = lambda s: 'minecraft:' + s
shaped('mini_drone', ["N N", "IRI", " G "], {"N": mc('iron_nugget'), "I": mc('iron_ingot'), "R": mc('redstone'), "G": mc('glass_pane')}, 'mini_drone')
shaped('controller', [" T ", "ICI", "IBI"], {"T": mc('redstone_torch'), "I": mc('iron_ingot'), "C": mc('repeater'), "B": mc('stone_button')}, 'controller')
shaped('charging_pad', ["III", "GRG", "SSS"], {"I": mc('iron_ingot'), "G": mc('gold_ingot'), "R": mc('redstone_block'), "S": mc('smooth_stone_slab')}, 'charging_pad')
shaped('control_station', ["PPP", "ICI", "III"], {"P": mc('glass_pane'), "I": mc('iron_ingot'), "C": mc('comparator')}, 'control_station')
shapeless('quad_drone', ['udrone:mini_drone', mc('iron_ingot'), mc('iron_ingot'), mc('iron_ingot'), mc('redstone_block')], 'quad_drone')
shapeless('fast_drone', ['udrone:mini_drone', mc('gunpowder'), mc('gunpowder'), mc('redstone'), mc('redstone'), mc('gold_ingot')], 'fast_drone')
shapeless('surveillance_drone', ['udrone:mini_drone', mc('spyglass'), mc('daylight_detector'), mc('glass_pane'), mc('glass_pane'), mc('iron_ingot')], 'surveillance_drone')
shapeless('heavy_drone', ['udrone:quad_drone', mc('iron_block'), mc('iron_block'), mc('tnt'), mc('dispenser'), mc('iron_ingot'), mc('iron_ingot')], 'heavy_drone')
shapeless('mq9_reaper', ['udrone:heavy_drone', mc('elytra'), mc('diamond'), mc('diamond'), mc('diamond'), mc('iron_block'), mc('iron_block'), mc('tnt'), mc('tnt')], 'mq9_reaper')
for b in ['control_station', 'charging_pad']:
    json.dump({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"udrone:{b}"}],
               "conditions": [{"condition": "minecraft:survives_explosion"}]}]}, open(f'{D}/loot_tables/blocks/{b}.json', 'w'), indent=1)
json.dump({"replace": False, "values": ["udrone:control_station", "udrone:charging_pad"]}, open(f'{R}/data/minecraft/tags/blocks/mineable/pickaxe.json', 'w'))
print('sounds+data ok')
