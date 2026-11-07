"""Coeur du launcher : installe Java, Forge et les mods, prepare le profil, ouvre le Launcher officiel."""
import datetime
import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

UA = "ServerLauncher/1.0 (github.com/Sachacaca744/xyzmenu)"
MANIFEST_URL = "https://raw.githubusercontent.com/Sachacaca744/xyzmenu/launcher/modpack.json"
PROFILE_ID = "serverlauncher"
IS_WIN = os.name == "nt"


class LauncherError(Exception):
    pass


# ---------------------------------------------------------------- dossiers
def base_dir():
    if IS_WIN:
        return os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "ServerLauncher")
    return os.path.join(os.path.expanduser("~"), ".serverlauncher")


def minecraft_dir():
    if IS_WIN:
        return os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), ".minecraft")
    return os.path.join(os.path.expanduser("~"), ".minecraft")


def instance_dir(base):
    return os.path.join(base, "instance")


# ---------------------------------------------------------------- reseau
def _open(url, timeout=60, method="GET"):
    req = urllib.request.Request(url, headers={"User-Agent": UA}, method=method)
    return urllib.request.urlopen(req, timeout=timeout)


def http_json(url):
    with _open(url) as r:
        return json.loads(r.read().decode("utf-8"))


def sha1_file(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def remote_size(url):
    try:
        with _open(url, 20, "HEAD") as r:
            n = r.headers.get("Content-Length")
            return int(n) if n else None
    except Exception:
        return None


def download(url, dest, progress=None, sha1=None, retries=3):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    last = None
    for _ in range(retries):
        try:
            with _open(url, 120) as r, open(tmp, "wb") as f:
                total = int(r.headers.get("Content-Length") or 0)
                done = 0
                while True:
                    chunk = r.read(65536)
                    if not chunk:
                        break
                    f.write(chunk)
                    done += len(chunk)
                    if progress:
                        progress(done, total)
            if sha1 and sha1_file(tmp).lower() != sha1.lower():
                raise LauncherError("empreinte (sha1) incorrecte")
            os.replace(tmp, dest)
            return
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5)
    raise LauncherError("Telechargement impossible : %s (%s)" % (url, last))


# ---------------------------------------------------------------- manifeste
def cached_manifest(base):
    try:
        with open(os.path.join(base, "modpack_cache.json"), encoding="utf-8") as f:
            return json.load(f)
    except Exception:  # noqa: BLE001
        return {}


def load_manifest(base, url=MANIFEST_URL):
    try:
        data = http_json(url)
        os.makedirs(base, exist_ok=True)
        with open(os.path.join(base, "modpack_cache.json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        return data
    except Exception as e:  # noqa: BLE001
        cached = cached_manifest(base)
        if cached:
            return cached
        raise LauncherError("Impossible de joindre le serveur de mises a jour. Verifie ta connexion internet. (%s)" % e)


# ---------------------------------------------------------------- mods
def _modrinth_versions(project, mc):
    q = urllib.parse.urlencode({"loaders": json.dumps(["forge"]), "game_versions": json.dumps([mc])})
    return http_json("https://api.modrinth.com/v2/project/%s/version?%s" % (urllib.parse.quote(project), q))


def _resolve_modrinth(project, wanted, mc, out, seen, log):
    if project in seen:
        return
    seen.add(project)
    versions = _modrinth_versions(project, mc)
    chosen = None
    if wanted:
        for v in versions:
            if v.get("version_number") == wanted:
                chosen = v
                break
        if chosen is None:
            avail = ", ".join(v.get("version_number", "?") for v in versions[:8])
            raise LauncherError("Mod '%s' : version %s introuvable (disponibles : %s)" % (project, wanted, avail))
    elif versions:
        chosen = versions[0]
    if chosen is None:
        raise LauncherError("Mod '%s' : aucune version pour Minecraft %s / Forge" % (project, mc))
    files = chosen["files"]
    f = next((x for x in files if x.get("primary")), files[0])
    out[f["filename"]] = {"url": f["url"], "sha1": f.get("hashes", {}).get("sha1", "")}
    log("Mod : %s (%s)" % (f["filename"], chosen.get("version_number", "")))
    for dep in chosen.get("dependencies", []):
        if dep.get("dependency_type") == "required" and dep.get("project_id"):
            _resolve_modrinth(dep["project_id"], None, mc, out, seen, log)


def resolve_mods(manifest, log=print):
    """Liste des fichiers a avoir cote joueur : {nom_fichier: {url, sha1}}."""
    mc = manifest.get("minecraft", "1.20.1")
    out = {}
    seen = set()
    for m in manifest.get("mods", []):
        if m.get("disabled") or m.get("side", "both") == "server":
            continue
        if m.get("source", "url") == "modrinth":
            _resolve_modrinth(m["project"], m.get("version") or None, mc, out, seen, log)
        else:
            url = m["url"]
            fn = m.get("filename") or urllib.parse.unquote(url.split("?")[0].rstrip("/").split("/")[-1])
            out[fn] = {"url": url, "sha1": m.get("sha1", "")}
            log("Mod : %s" % fn)
    return out


def sync_mods(instance, wanted, log, progress):
    mods_dir = os.path.join(instance, "mods")
    os.makedirs(mods_dir, exist_ok=True)
    state_path = os.path.join(instance, ".launcher_state.json")
    try:
        with open(state_path, encoding="utf-8") as f:
            state = json.load(f)
    except Exception:  # noqa: BLE001
        state = {}

    for fn in os.listdir(mods_dir):
        if fn.lower().endswith(".jar") and fn not in wanted:
            log("Mod retire : %s" % fn)
            os.remove(os.path.join(mods_dir, fn))
            state.pop(fn, None)

    names = sorted(wanted)
    for i, fn in enumerate(names):
        info = wanted[fn]
        path = os.path.join(mods_dir, fn)
        ok = False
        if os.path.isfile(path):
            if info["sha1"]:
                ok = sha1_file(path).lower() == info["sha1"].lower()
            else:
                size = remote_size(info["url"])
                if size is not None:
                    ok = size == os.path.getsize(path)
                else:
                    ok = state.get(fn) == info["url"]
        if not ok:
            log("Telechargement : %s" % fn)
            download(info["url"], path,
                     progress=lambda d, t, i=i: progress((i + (d / t if t else 0.5)) / len(names), fn),
                     sha1=info["sha1"] or None)
            state[fn] = info["url"]
        progress((i + 1) / len(names), fn)

    with open(state_path, "w", encoding="utf-8") as f:
        json.dump(state, f)


# ---------------------------------------------------------------- Java
def ensure_java(base, log, progress):
    jre = os.path.join(base, "jre17")
    exe = "java.exe" if IS_WIN else "java"

    def find():
        for root, _dirs, files in os.walk(jre):
            if exe in files and os.path.basename(root) == "bin":
                return os.path.join(root, exe)
        return None

    found = find() if os.path.isdir(jre) else None
    if found:
        return found
    osname = "windows" if IS_WIN else ("mac" if sys.platform == "darwin" else "linux")
    url = "https://api.adoptium.net/v3/binary/latest/17/ga/%s/x64/jre/hotspot/normal/eclipse" % osname
    log("Telechargement de Java 17...")
    archive = os.path.join(base, "cache", "jre17.archive")
    download(url, archive, progress=lambda d, t: progress(d / t if t else 0.5))
    shutil.rmtree(jre, ignore_errors=True)
    os.makedirs(jre, exist_ok=True)
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as z:
            z.extractall(jre)
    else:
        with tarfile.open(archive) as t:
            t.extractall(jre)
    os.remove(archive)
    found = find()
    if not found:
        raise LauncherError("Java 17 telecharge mais introuvable.")
    if not IS_WIN:
        os.chmod(found, 0o755)
    return found


# ---------------------------------------------------------------- Forge
def forge_version_id(mc, forge):
    return "%s-forge-%s" % (mc, forge)


def ensure_forge(base, mcdir, java, mc, forge, log, progress):
    vid = forge_version_id(mc, forge)
    if os.path.isfile(os.path.join(mcdir, "versions", vid, vid + ".json")):
        return vid
    url = "https://maven.minecraftforge.net/net/minecraftforge/forge/%s-%s/forge-%s-%s-installer.jar" % (mc, forge, mc, forge)
    installer = os.path.join(base, "cache", "forge-%s-%s-installer.jar" % (mc, forge))
    log("Telechargement de Forge %s..." % forge)
    download(url, installer, progress=lambda d, t: progress(0.2 * (d / t if t else 0.5)))
    log("Installation de Forge (quelques minutes, ne ferme pas la fenetre)...")
    flags = subprocess.CREATE_NO_WINDOW if IS_WIN else 0
    proc = subprocess.Popen([java, "-jar", installer, "--installClient", mcdir], cwd=base,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                            encoding="utf-8", errors="replace", creationflags=flags)
    lines = 0
    for line in proc.stdout:
        line = line.rstrip()
        if line:
            lines += 1
            log(line)
            progress(min(0.2 + lines / 1500.0, 0.98))
    if proc.wait() != 0:
        raise LauncherError("L'installation de Forge a echoue (voir le fichier launcher.log).")
    if not os.path.isfile(os.path.join(mcdir, "versions", vid, vid + ".json")):
        raise LauncherError("Forge installe mais la version %s est introuvable." % vid)
    return vid


# ---------------------------------------------------------------- profil + serveur
def write_profile(mcdir, instance, vid, name, ram_mb):
    path = os.path.join(mcdir, "launcher_profiles.json")
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception:  # noqa: BLE001
        data = {}
    if os.path.isfile(path):
        shutil.copyfile(path, path + ".bak")
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    profiles = data.setdefault("profiles", {})
    old = profiles.get(PROFILE_ID, {})
    profiles[PROFILE_ID] = {
        "name": name,
        "type": "custom",
        "created": old.get("created", now),
        "lastUsed": now,
        "icon": "Furnace",
        "lastVersionId": vid,
        "gameDir": instance,
        "javaArgs": "-Xmx%dM -Xms512M -XX:+UseG1GC" % int(ram_mb),
    }
    data["selectedProfile"] = PROFILE_ID
    data.setdefault("version", 3)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def _nbt_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def write_servers_dat(instance, name, address):
    """Ajoute le serveur a la liste (seulement si le joueur n'a pas encore de servers.dat)."""
    path = os.path.join(instance, "servers.dat")
    if os.path.isfile(path) or not address:
        return
    os.makedirs(instance, exist_ok=True)
    out = bytearray()
    out += b"\x0a" + _nbt_str("")                       # compound racine
    out += b"\x09" + _nbt_str("servers") + b"\x0a" + struct.pack(">i", 1)   # liste de compounds
    out += b"\x08" + _nbt_str("ip") + _nbt_str(address)
    out += b"\x08" + _nbt_str("name") + _nbt_str(name)
    out += b"\x00"                                       # fin du compound serveur
    out += b"\x00"                                       # fin du compound racine
    with open(path, "wb") as f:
        f.write(bytes(out))


def open_official_launcher():
    if not IS_WIN:
        return False
    pf86 = os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")
    pf = os.environ.get("ProgramFiles", r"C:\Program Files")
    for p in (os.path.join(pf86, "Minecraft Launcher", "MinecraftLauncher.exe"),
              os.path.join(pf, "Minecraft Launcher", "MinecraftLauncher.exe")):
        if os.path.isfile(p):
            subprocess.Popen([p])
            return True
    try:
        os.startfile(r"shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft")  # noqa: S606
        return True
    except Exception:  # noqa: BLE001
        return False


# ---------------------------------------------------------------- tout
def run_all(base, mcdir, manifest, log, progress, java_exe=None, open_launcher=True):
    """progress(fraction 0..1, texte). Renvoie le nom du profil."""
    mc = manifest.get("minecraft", "1.20.1")
    forge = manifest.get("forge", "47.4.28")
    name = manifest.get("name", "Mon Serveur")
    os.makedirs(base, exist_ok=True)

    progress(0.01, "Verification...")
    if not os.path.isfile(os.path.join(mcdir, "launcher_profiles.json")):
        raise LauncherError("Le Launcher Minecraft officiel n'est pas pret. Installe-le (minecraft.net/download), "
                            "ouvre-le une fois et connecte-toi, puis relance ce launcher.")

    if not java_exe:
        progress(0.02, "Java...")
        java_exe = ensure_java(base, log, lambda f: progress(0.02 + 0.13 * f, "Telechargement de Java..."))

    progress(0.15, "Forge...")
    vid = ensure_forge(base, mcdir, java_exe, mc, forge, log,
                       lambda f: progress(0.15 + 0.30 * f, "Installation de Forge..."))

    progress(0.45, "Mods...")
    wanted = resolve_mods(manifest, log)
    inst = instance_dir(base)
    sync_mods(inst, wanted, log, lambda f, fn: progress(0.45 + 0.50 * f, "Mods : " + fn))

    progress(0.96, "Finalisation...")
    srv = manifest.get("server", {})
    write_servers_dat(inst, srv.get("name", name), srv.get("address", ""))
    write_profile(mcdir, inst, vid, name, manifest.get("ram_mb", 4096))

    progress(1.0, "Pret !")
    if open_launcher:
        open_official_launcher()
    return name
