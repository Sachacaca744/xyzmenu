"""
Launcher Minecraft (Forge) avec synchronisation automatique des mods.

- Le joueur ouvre le programme, se connecte avec son compte Microsoft (une seule fois) et clique sur JOUER.
- Le launcher telecharge tout seul Minecraft, Forge et les mods listes dans modpack.json (heberge sur GitHub).
- Pour ajouter / retirer un mod : il suffit de modifier modpack.json (voir LISEZMOI-ADMIN.txt).
"""

import hashlib
import json
import os
import queue
import shutil
import subprocess
import sys
import threading
import traceback
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from pathlib import Path

# ----------------------------------------------------------------------------------------------
# REGLAGES DU LAUNCHER (modifiables avant compilation)
# ----------------------------------------------------------------------------------------------
APP_NAME = "ServeurLauncher"
APP_VERSION = "1.1.0"
MANIFEST_URL = os.environ.get(
    "LAUNCHER_MANIFEST",
    "https://raw.githubusercontent.com/Sachacaca744/xyzmenu/launcher/modpack.json",
)
# Identifiant de l'application Microsoft utilise pour la connexion (celui de la bibliotheque PortableMC).
# Pour un usage durable, enregistre ta propre application et mets son identifiant dans modpack.json ("msa_app_id").
DEFAULT_MSA_APP_ID = "708e91b5-99f8-4a1d-80ec-e746cbb24771"
ALLOW_HTTP = os.environ.get("LAUNCHER_ALLOW_HTTP") == "1"  # uniquement pour les tests

RAM_CHOICES = [2048, 3072, 4096, 6144, 8192, 12288, 16384]


# ----------------------------------------------------------------------------------------------
# Outils : dossiers, reseau, fichiers
# ----------------------------------------------------------------------------------------------
def data_dir() -> Path:
    override = os.environ.get("LAUNCHER_DATA")
    if override:
        base = Path(override)
    elif os.name == "nt":
        base = Path(os.environ.get("APPDATA", str(Path.home()))) / APP_NAME
    else:
        base = Path.home() / ".local" / "share" / APP_NAME
    base.mkdir(parents=True, exist_ok=True)
    return base


def read_json(path: Path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    os.replace(tmp, path)


def check_url(url: str) -> str:
    low = url.lower()
    if low.startswith("https://") or (ALLOW_HTTP and low.startswith("http://")):
        return url
    raise ValueError("Adresse non autorisee (https obligatoire) : " + url)


def http_get(url: str, timeout: int = 30) -> bytes:
    req = urllib.request.Request(check_url(url), headers={"User-Agent": APP_NAME + "/" + APP_VERSION})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def download_file(url: str, dest: Path, on_bytes=None) -> str:
    """Telecharge vers dest (via un fichier .part). Renvoie le sha256 du fichier."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    req = urllib.request.Request(check_url(url), headers={"User-Agent": APP_NAME + "/" + APP_VERSION})
    h = hashlib.sha256()
    with urllib.request.urlopen(req, timeout=60) as r, open(part, "wb") as f:
        while True:
            chunk = r.read(1024 * 256)
            if not chunk:
                break
            f.write(chunk)
            h.update(chunk)
            if on_bytes:
                on_bytes(len(chunk))
    os.replace(part, dest)
    return h.hexdigest()


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


# ----------------------------------------------------------------------------------------------
# Manifeste (modpack.json) et synchronisation des mods
# ----------------------------------------------------------------------------------------------
def load_manifest(location: str, cache_file: Path) -> tuple[dict, bool]:
    """Renvoie (manifeste, hors_ligne). Utilise la copie locale si internet est coupe."""
    try:
        if location.startswith(("http://", "https://")):
            raw = http_get(location)
        else:
            raw = Path(location).read_bytes()
        manifest = json.loads(raw.decode("utf-8-sig"))
        write_json(cache_file, manifest)
        return manifest, False
    except Exception as e:
        cached = read_json(cache_file, None)
        if cached is None:
            raise RuntimeError("Impossible de lire la liste des mods (" + str(e) + ")") from e
        return cached, True


def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def modrinth_resolve(project: str, wanted_version: str, mc: str) -> tuple:
    """Interroge Modrinth : renvoie (nom_fichier, url, sha1, numero_de_version)."""
    q = urllib.parse.urlencode({"loaders": json.dumps(["forge"]), "game_versions": json.dumps([mc])})
    versions = json.loads(http_get("https://api.modrinth.com/v2/project/%s/version?%s"
                                   % (urllib.parse.quote(str(project)), q)).decode("utf-8"))
    if not versions:
        raise RuntimeError("Modrinth : aucune version Forge %s pour '%s'" % (mc, project))
    chosen = None
    w = str(wanted_version or "").strip()
    if w:
        for v in versions:
            if v.get("version_number") == w:
                chosen = v
                break
        if chosen is None:
            for v in versions:
                num = str(v.get("version_number", ""))
                fn = " ".join(str(f.get("filename", "")) for f in v.get("files", []))
                if w in num or w in fn:
                    chosen = v
                    break
        if chosen is None:
            avail = ", ".join(str(v.get("version_number", "?")) for v in versions[:8])
            raise RuntimeError("Modrinth : version '%s' introuvable pour '%s' (disponibles : %s)" % (w, project, avail))
    else:
        chosen = versions[0]
    files = chosen.get("files") or []
    if not files:
        raise RuntimeError("Modrinth : version sans fichier pour '%s'" % project)
    f = next((x for x in files if x.get("primary")), files[0])
    name = _safe_jar_name(f["filename"])
    return name, f["url"], str(f.get("hashes", {}).get("sha1", "")).lower(), str(chosen.get("version_number", ""))


def _safe_jar_name(name: str) -> str:
    n = Path(str(name)).name
    if n != str(name) or not n.lower().endswith(".jar"):
        raise ValueError("Nom de mod invalide dans modpack.json : " + str(name))
    return n


def _safe_rel_path(rel: str) -> Path:
    p = Path(str(rel))
    if p.is_absolute() or ".." in p.parts or not p.parts:
        raise ValueError("Chemin invalide dans modpack.json : " + str(rel))
    return p


def sync_files(mc_dir: Path, manifest: dict, status=lambda s: None, progress=lambda f: None) -> dict:
    """
    Aligne le dossier du jeu sur modpack.json :
      - telecharge les mods manquants ou mis a jour ;
      - SUPPRIME les .jar de mods qui ne sont plus dans la liste ;
      - gere les fichiers supplementaires ("files") : configs, resourcepacks...
    """
    mods_dir = mc_dir / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    state_file = mc_dir / "launcher_state.json"
    state = read_json(state_file, {"mods": {}, "files": {}})
    state.setdefault("mods", {})
    state.setdefault("files", {})
    base_url = str(manifest.get("mods_base_url", "")).strip()
    if base_url and not base_url.endswith("/"):
        base_url += "/"

    # --- liste voulue ---
    wanted = {}  # nom -> (url, sha256, version)
    sha1s = {}   # nom -> sha1 (mods Modrinth)
    state.setdefault("modrinth", {})
    mc_version = str(manifest.get("minecraft", "1.20.1"))
    for m in manifest.get("mods", []):
        if not isinstance(m, dict) or m.get("enabled", True) is False:
            continue
        if str(m.get("source", "")).lower() == "modrinth":
            ident = str(m.get("project", "")) + "@" + str(m.get("version", ""))
            status("Recherche du mod : %s" % m.get("project"))
            try:
                name, url, sha1, num = modrinth_resolve(m.get("project", ""), m.get("version", ""), mc_version)
                wanted[name] = (url, "", "modrinth:" + sha1)
                sha1s[name] = sha1
                state["modrinth"][ident] = name
            except Exception as e:
                old_name = state["modrinth"].get(ident)
                if old_name and (mods_dir / old_name).exists():
                    # pas d'internet ou Modrinth en panne : on garde la copie deja telechargee
                    wanted[old_name] = ("", "", state["mods"].get(old_name, ""))
                elif m.get("optional"):
                    status("Mod ignore (introuvable) : %s" % m.get("project"))
                else:
                    raise RuntimeError("Mod '%s' impossible a telecharger : %s" % (m.get("project"), e)) from e
            continue
        name = _safe_jar_name(m.get("file", ""))
        url = m.get("url") or (base_url + urllib.parse.quote(name) if base_url else "")
        if not url:
            raise ValueError("Pas d'adresse pour le mod " + name + " (ajoute 'url' ou 'mods_base_url')")
        wanted[name] = (url, str(m.get("sha256", "")).lower().strip(), str(m.get("version", "")))

    extra = {}  # chemin relatif -> (url, sha256, version)
    for f in manifest.get("files", []):
        if not isinstance(f, dict) or f.get("enabled", True) is False:
            continue
        rel = _safe_rel_path(f.get("path", ""))
        url = f.get("url")
        if not url:
            raise ValueError("Pas d'adresse pour le fichier " + str(rel))
        extra[rel.as_posix()] = (url, str(f.get("sha256", "")).lower().strip(), str(f.get("version", "")))

    summary = {"added": [], "updated": [], "removed": [], "kept": 0}

    # --- suppression des mods retires ---
    for p in list(mods_dir.iterdir()):
        if p.is_file() and p.name not in wanted:
            if p.suffix.lower() in (".jar", ".disabled", ".part"):
                p.unlink()
                summary["removed"].append(p.name)
                state["mods"].pop(p.name, None)

    # --- telechargements ---
    todo = []
    for name, (url, sha, ver) in wanted.items():
        path = mods_dir / name
        key = sha or ver
        ok = path.exists() and path.stat().st_size > 0
        if ok and sha:
            ok = sha256_of(path) == sha
        if ok and name in sha1s:
            ok = sha1_of(path) == sha1s[name]
        if ok and key and state["mods"].get(name) != key:
            ok = False
        if ok:
            summary["kept"] += 1
        else:
            todo.append(("mod", name, path, url, sha, key, path.exists()))
    for rel, (url, sha, ver) in extra.items():
        path = mc_dir / rel
        key = sha or ver
        ok = path.exists() and path.stat().st_size > 0
        if ok and sha:
            ok = sha256_of(path) == sha
        if ok and key and state["files"].get(rel) != key:
            ok = False
        if not ok:
            todo.append(("file", rel, path, url, sha, key, path.exists()))

    total = len(todo)
    for i, (kind, name, path, url, sha, key, existed) in enumerate(todo):
        status("Telechargement : %s (%d/%d)" % (Path(name).name, i + 1, total))
        progress(i / max(total, 1))
        got = download_file(url, path)
        if sha and got != sha:
            try:
                path.unlink()
            except OSError:
                pass
            raise RuntimeError("Fichier corrompu ou modifie : " + str(name))
        if kind == "mod" and name in sha1s and sha1_of(path) != sha1s[name]:
            try:
                path.unlink()
            except OSError:
                pass
            raise RuntimeError("Fichier corrompu ou modifie : " + str(name))
        state["mods" if kind == "mod" else "files"][name] = key
        (summary["updated"] if existed else summary["added"]).append(str(name))
    progress(1.0)

    write_json(state_file, state)
    return summary


# ----------------------------------------------------------------------------------------------
# Connexion Microsoft + installation + lancement (bibliotheque portablemc)
# ----------------------------------------------------------------------------------------------
def get_account(db_file: Path, app_id: str, on_code):
    """Renvoie un compte Microsoft : reutilise le compte memorise, sinon connexion par code."""
    from portablemc import msa

    db = msa.Database(db_file)
    try:
        for acc in db.load_iter():
            try:
                acc.request_refresh()
                db.store(acc)
                return acc
            except msa.AuthError:
                db.remove_from_uuid(acc.uuid)
    except msa.DatabaseError:
        pass

    flow = msa.Auth(app_id).request_device_code()
    on_code(flow.user_code, flow.verification_uri)
    acc = flow.wait()
    db.store(acc)
    return acc


def forget_account(db_file: Path) -> None:
    try:
        db_file.unlink()
    except OSError:
        pass


def make_installer(manifest: dict, game_dir: Path):
    from portablemc import forge, mojang

    mc_version = str(manifest.get("minecraft", "1.20.1"))
    forge_ver = str(manifest.get("forge", "stable"))
    if forge_ver in ("", "stable", "latest"):
        version = forge.Version.Stable(mc_version)
    else:
        version = forge.Version.Name(forge_ver if "-" in forge_ver else mc_version + "-" + forge_ver)
    inst = forge.Installer(forge.Loader.Forge, version)
    inst.set_main_dir(game_dir)
    inst.launcher_name = APP_NAME
    inst.launcher_version = APP_VERSION
    server = str(manifest.get("server", "")).strip()
    if server:
        host, _, port = server.partition(":")
        try:
            inst.quick_play = mojang.QuickPlay.Multiplayer(host, int(port) if port else 25565)
        except Exception:
            pass
    return inst


def make_handler(status, progress):
    from portablemc import forge

    class UiHandler(forge.Handler):
        def load_version(self, version, file):
            status("Preparation de la version " + str(version) + "...")

        def fetch_installer(self, version):
            status("Telechargement de Forge " + str(version) + "...")

        def installing_game(self):
            status("Installation de Forge (patienter, premiere fois seulement)...")

        def load_libraries(self):
            status("Verification des bibliotheques...")

        def load_assets(self, id):
            status("Verification des ressources du jeu...")

        def load_jvm(self, major_version):
            status("Verification de Java " + str(major_version) + "...")

        def download_progress(self, count, total_count, size, total_size):
            if total_size:
                progress(size / total_size)
                status("Telechargement du jeu : %d / %d Mo" % (size // 1048576, total_size // 1048576))

    return UiHandler()


# ----------------------------------------------------------------------------------------------
# Interface
# ----------------------------------------------------------------------------------------------
BG = "#0e1522"
PANEL = "#16233a"
ACCENT = "#22b8e8"
ACCENT_HOVER = "#55ceF5"
ACCENT_DARK = "#0f7fa8"
TEXT = "#f2f6fc"
MUTED = "#a9b8d0"
ERROR = "#ff7b7b"
OK = "#6cf0a8"
WIN_W, WIN_H = 960, 600
PANEL_X = 610  # le panneau des boutons commence ici (image a gauche)


def asset_path(name: str) -> Path:
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return base / name


def rounded_points(x1, y1, x2, y2, r):
    return [x1 + r, y1, x1 + r, y1, x2 - r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y1 + r, x2, y2 - r, x2, y2 - r,
            x2, y2, x2 - r, y2, x2 - r, y2, x1 + r, y2, x1 + r, y2, x1, y2, x1, y2 - r, x1, y2 - r, x1, y1 + r,
            x1, y1 + r, x1, y1]


class _Txt:
    """Texte pose sur le canvas (se pilote comme un Label)."""

    def __init__(self, canvas, item):
        self.c, self.item = canvas, item

    def configure(self, text=None, fg=None):
        if text is not None:
            self.c.itemconfigure(self.item, text=text)
        if fg is not None:
            self.c.itemconfigure(self.item, fill=fg)


class _Var:
    def __init__(self, on_change):
        self._v, self._cb = "", on_change

    def set(self, v):
        self._v = v
        self._cb(v)

    def get(self):
        return self._v


class _Bar:
    """Barre de progression arrondie."""

    def __init__(self, canvas, x, y, w, h):
        self.c, self.x, self.y, self.w, self.h = canvas, x, y, w, h
        canvas.create_polygon(rounded_points(x, y, x + w, y + h, h // 2), smooth=True, fill="#0a1220",
                              outline="#2b4468")
        self.fill = canvas.create_polygon(0, 0, 0, 0, 0, 0, smooth=True, fill=ACCENT, outline="")
        self.configure(value=0)

    def configure(self, value=0):
        frac = max(0.0, min(1.0, value / 1000.0))
        wid = int((self.w - 4) * frac)
        if wid < self.h - 4:
            self.c.itemconfigure(self.fill, state="hidden" if wid <= 0 else "normal")
            wid = max(wid, self.h - 4) if wid > 0 else 0
        else:
            self.c.itemconfigure(self.fill, state="normal")
        if wid > 0:
            self.c.coords(self.fill, *rounded_points(self.x + 2, self.y + 2, self.x + 2 + wid,
                                                     self.y + self.h - 2, (self.h - 4) // 2))


class _PillButton:
    """Bouton arrondi, style Cobblemon : pastille avec ombre en dessous. primary=bouton principal."""

    def __init__(self, canvas, x, y, w, h, text, command, primary=False, size=12):
        self.c, self.cmd, self.primary = canvas, command, primary
        self.state, self.text, self.hover = "normal", text, False
        r = h // 2
        self.shadow = canvas.create_polygon(rounded_points(x, y + 4, x + w, y + h + 4, r), smooth=True, fill="")
        self.body = canvas.create_polygon(rounded_points(x, y, x + w, y + h, r), smooth=True, fill="", width=2)
        self.label = canvas.create_text(x + w // 2, y + h // 2, text=text, fill=TEXT,
                                        font=("Segoe UI", size, "bold"))
        for item in (self.shadow, self.body, self.label):
            canvas.tag_bind(item, "<Enter>", self._enter)
            canvas.tag_bind(item, "<Leave>", self._leave)
            canvas.tag_bind(item, "<Button-1>", self._click)
        self._paint()

    def _enter(self, _e):
        self.hover = True
        self._paint()

    def _leave(self, _e):
        self.hover = False
        self._paint()

    def _click(self, _e):
        if self.state == "normal" and self.cmd:
            self.cmd()

    def _paint(self):
        c = self.c
        if self.state == "disabled":
            fill, edge, shadow, fg, cur = "#2a3850", "#3a4c68", "#1a2538", "#7d8da8", "arrow"
        elif self.primary:
            fill = ACCENT_HOVER if self.hover else ACCENT
            edge, shadow, fg, cur = "#aeeaff", ACCENT_DARK, "#04202c", "hand2"
        else:
            fill = "#2b4468" if self.hover else "#1d3050"
            edge, shadow, fg, cur = "#5b7ba8", "#0c1626", TEXT, "hand2"
        c.itemconfigure(self.shadow, fill=shadow)
        c.itemconfigure(self.body, fill=fill, outline=edge)
        c.itemconfigure(self.label, fill=fg, text=self.text)
        for item in (self.shadow, self.body, self.label):
            c.itemconfigure(item, state="normal")
        try:
            c.configure(cursor=cur if self.hover else "")
        except Exception:
            pass

    def configure(self, state=None, text=None):
        if state is not None:
            self.state = state
        if text is not None:
            self.text = text
        self._paint()

    def cget(self, key):
        return self.text if key == "text" else self.state


class App:
    def __init__(self, demo: bool = False):
        import tkinter as tk
        from tkinter import ttk

        self.tk = tk
        self.ttk = ttk
        self.demo = demo
        self.data = data_dir()
        self.game_dir = self.data / "game"
        self.settings_file = self.data / "settings.json"
        self.settings = read_json(self.settings_file, {})
        self.manifest = {}
        self.ready = False
        self.busy = False
        self.q = queue.Queue()

        root = tk.Tk()
        self.root = root
        root.title(APP_NAME)
        sw, sh = root.winfo_screenwidth(), root.winfo_screenheight()
        root.geometry("%dx%d+%d+%d" % (WIN_W, WIN_H, max(0, (sw - WIN_W) // 2), max(0, (sh - WIN_H) // 3)))
        root.resizable(False, False)
        root.configure(bg=BG)

        cv = tk.Canvas(root, width=WIN_W, height=WIN_H, bg=BG, highlightthickness=0, bd=0)
        cv.pack(fill="both", expand=True)
        self.canvas = cv
        self.bg_img = None
        try:
            self.bg_img = tk.PhotoImage(file=str(asset_path("bg.png")))
            cv.create_image(0, 0, image=self.bg_img, anchor="nw")
        except Exception:
            cv.create_rectangle(0, 0, PANEL_X, WIN_H, fill=PANEL, outline="")

        px = PANEL_X + 34
        pw = WIN_W - PANEL_X - 68
        # titre + sous-titre (avec ombre pour rester lisibles)
        cv.create_text(px + 2, 62, text=APP_NAME, anchor="w", fill="#000000", width=pw,
                       font=("Segoe UI", 22, "bold"))
        self.title_lbl = _Txt(cv, cv.create_text(px, 60, text=APP_NAME, anchor="w", fill=TEXT, width=pw,
                                                 font=("Segoe UI", 22, "bold")))
        self.sub_lbl = _Txt(cv, cv.create_text(px, 112, text="Minecraft + Forge + mods, tout est automatique.",
                                               anchor="w", fill=MUTED, width=pw, font=("Segoe UI", 10)))

        self.play_btn = _PillButton(cv, px, 190, pw, 64, "JOUER", self.on_play, primary=True, size=20)
        self.play_btn.configure(state="disabled")

        self.status_lbl = _Txt(cv, cv.create_text(px, 300, text="Demarrage...", anchor="nw", fill=TEXT, width=pw,
                                                  font=("Segoe UI", 11)))
        self.bar = _Bar(cv, px, 372, pw, 16)

        ram_default = int(self.settings.get("ram_mb", 4096))
        self.ram_btn = _PillButton(cv, px, 430, pw, 40, "", self.cycle_ram, size=11)
        self.ram_var = _Var(lambda v: self.ram_btn.configure(text="Mémoire : %s   ▸" % v))
        self.ram_var.set(self._ram_label(ram_default))
        half = (pw - 12) // 2
        self.folder_btn = _PillButton(cv, px, 484, half, 36, "Dossier du jeu", self.open_folder, size=10)
        self.logout_btn = _PillButton(cv, px + half + 12, 484, half, 36, "Changer de compte", self.on_logout,
                                      size=10)

        root.after(100, self.pump)
        threading.Thread(target=self.prepare, daemon=True).start()

    # ---- petits utilitaires UI ----
    @staticmethod
    def _ram_label(mb: int) -> str:
        return "%d Go" % (mb // 1024)

    def cycle_ram(self):
        cur = self._ram_value()
        i = RAM_CHOICES.index(cur) if cur in RAM_CHOICES else 2
        self.ram_var.set(self._ram_label(RAM_CHOICES[(i + 1) % len(RAM_CHOICES)]))

    def _ram_value(self) -> int:
        for r in RAM_CHOICES:
            if self._ram_label(r) == self.ram_var.get():
                return r
        return 4096

    def post(self, fn, *args):
        self.q.put((fn, args))

    def pump(self):
        try:
            while True:
                fn, args = self.q.get_nowait()
                fn(*args)
        except queue.Empty:
            pass
        self.root.after(100, self.pump)

    def set_status(self, text, color=TEXT):
        self.post(lambda: self.status_lbl.configure(text=text, fg=color))

    def set_progress(self, frac):
        self.post(lambda: self.bar.configure(value=int(max(0.0, min(1.0, frac)) * 1000)))

    def set_ready(self, ready, label="JOUER"):
        def f():
            self.ready = ready
            self.play_btn.configure(state="normal" if ready else "disabled", text=label)
        self.post(f)

    def open_folder(self):
        try:
            self.game_dir.mkdir(parents=True, exist_ok=True)
            if os.name == "nt":
                os.startfile(str(self.game_dir))  # type: ignore[attr-defined]
            else:
                webbrowser.open(self.game_dir.as_uri())
        except Exception:
            pass

    def on_logout(self):
        forget_account(self.data / "comptes.json")
        self.set_status("Compte oublie : tu devras te reconnecter au prochain lancement.", MUTED)

    # ---- etape 1 : preparation (liste des mods + installation du jeu) ----
    def prepare(self):
        try:
            self.set_status("Recherche des mises a jour...")
            manifest, offline = load_manifest(MANIFEST_URL, self.data / "modpack_cache.json")
            self.manifest = manifest
            name = str(manifest.get("name", APP_NAME))
            self.post(lambda: (self.title_lbl.configure(text=name), self.root.title(name)))
            if manifest.get("description"):
                self.post(lambda: self.sub_lbl.configure(text=str(manifest["description"])))
            if "ram_mb" not in self.settings and manifest.get("ram_mb"):
                self.post(lambda: self.ram_var.set(self._ram_label(int(manifest["ram_mb"]))))

            if self.demo:
                self.set_progress(1.0)
                self.set_status("Tout est a jour. Pret a jouer !", OK)
                self.set_ready(True)
                return

            if offline:
                self.set_status("Pas d'internet : utilisation de la derniere version telechargee.", MUTED)
            else:
                sync_files(self.game_dir, manifest, self.set_status, self.set_progress)

            self.set_progress(0.0)
            inst = make_installer(manifest, self.game_dir)
            inst.install(make_handler(self.set_status, self.set_progress))
            self.set_progress(1.0)
            self.set_status("Tout est a jour. Pret a jouer !", OK)
            self.set_ready(True)
        except Exception as e:
            traceback.print_exc()
            self.set_status("Erreur : %s" % e, ERROR)
            self.set_ready(True, "REESSAYER")

    # ---- etape 2 : clic sur JOUER ----
    def on_play(self):
        if self.busy or not self.ready:
            return
        if self.play_btn.cget("text") == "REESSAYER":
            self.set_ready(False)
            threading.Thread(target=self.prepare, daemon=True).start()
            return
        self.busy = True
        self.set_ready(False, "...")
        self.settings["ram_mb"] = self._ram_value()
        write_json(self.settings_file, self.settings)
        threading.Thread(target=self.play, daemon=True).start()

    def show_code(self, code, url):
        def f():
            tk = self.tk
            win = tk.Toplevel(self.root)
            win.title("Connexion Microsoft")
            win.geometry("420x250")
            win.configure(bg=BG)
            win.resizable(False, False)
            tk.Label(win, text="Connexion a ton compte Microsoft", font=("Segoe UI", 13, "bold"),
                     fg=TEXT, bg=BG).pack(pady=(20, 6))
            tk.Label(win, text="1. Clique sur le bouton ci-dessous\n2. Entre ce code sur la page :",
                     font=("Segoe UI", 10), fg=MUTED, bg=BG, justify="center").pack()
            tk.Label(win, text=code, font=("Consolas", 26, "bold"), fg=ACCENT_HOVER, bg=BG).pack(pady=8)
            tk.Button(win, text="Ouvrir la page Microsoft", font=("Segoe UI", 11, "bold"), fg="white", bg=ACCENT,
                      activebackground=ACCENT_HOVER, relief="flat", bd=0, cursor="hand2",
                      command=lambda: webbrowser.open(url)).pack(pady=8, ipadx=14, ipady=6)
            self.code_win = win
            try:
                self.root.clipboard_clear()
                self.root.clipboard_append(code)
            except Exception:
                pass
            webbrowser.open(url)
        self.post(f)

    def close_code(self):
        def f():
            win = getattr(self, "code_win", None)
            if win is not None:
                win.destroy()
                self.code_win = None
        self.post(f)

    def play(self):
        try:
            app_id = str(self.manifest.get("msa_app_id") or DEFAULT_MSA_APP_ID)
            self.set_status("Connexion au compte Microsoft...")
            acc = get_account(self.data / "comptes.json", app_id, self.show_code)
            self.close_code()

            self.set_status("Preparation du lancement...")
            inst = make_installer(self.manifest, self.game_dir)
            inst.set_auth_msa(acc)
            game = inst.install(make_handler(self.set_status, self.set_progress))

            ram = self._ram_value()
            game.jvm_args = list(game.jvm_args) + ["-Xmx%dM" % ram, "-Xms512M"]

            logs = self.game_dir / "logs"
            logs.mkdir(parents=True, exist_ok=True)
            logfile = open(logs / "launcher-jeu.log", "ab")
            kwargs = {"stdout": logfile, "stderr": subprocess.STDOUT}
            if os.name == "nt":
                kwargs["creationflags"] = 0x08000000  # pas de fenetre console noire
            game.command()(**kwargs)

            self.set_status("Minecraft se lance... bon jeu, %s !" % acc.username, OK)
            self.post(lambda: self.root.after(3500, self.root.destroy))
        except Exception as e:
            traceback.print_exc()
            self.close_code()
            self.busy = False
            self.set_status("Erreur : %s" % e, ERROR)
            self.set_ready(True)

    def run(self):
        self.root.mainloop()


def selftest() -> None:
    """Verifie que tout est bien present dans le .exe (bibliotheques, interface). Ecrit le resultat dans un fichier."""
    out = data_dir() / "selftest.txt"
    try:
        import tkinter  # noqa: F401
        from portablemc import forge, msa, mojang  # noqa: F401
        inst = make_installer({"minecraft": "1.20.1", "forge": "47.4.28"}, data_dir() / "game")
        make_handler(lambda s: None, lambda f: None)
        msa.Auth(DEFAULT_MSA_APP_ID)
        if not asset_path("bg.png").exists():
            raise RuntimeError("bg.png absent du .exe")
        out.write_text("OK " + str(inst), encoding="utf-8")
    except Exception:
        out.write_text("ERREUR\n" + traceback.format_exc(), encoding="utf-8")
        sys.exit(1)


def main():
    args = sys.argv[1:]
    if "--selftest" in args:
        selftest()
        return
    if "--sync-only" in args:
        # Mode sans interface : utile pour tester la liste des mods.
        manifest, offline = load_manifest(MANIFEST_URL, data_dir() / "modpack_cache.json")
        result = sync_files(data_dir() / "game", manifest, print)
        print(json.dumps(result, ensure_ascii=False))
        return
    App(demo="--demo" in args).run()


if __name__ == "__main__":
    main()
