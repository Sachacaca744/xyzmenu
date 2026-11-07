"""Test de bout en bout du coeur du launcher (sur Linux, avec le vrai reseau)."""
import json
import os
import shutil
import struct
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
import core  # noqa: E402


def parse_servers_dat(path):
    with open(path, "rb") as f:
        b = f.read()
    # compound racine "" -> liste "servers" de compounds {ip, name}
    assert b[0] == 10
    pos = 3
    assert b[pos] == 9
    pos += 1
    n = struct.unpack(">H", b[pos:pos + 2])[0]
    pos += 2 + n
    assert b[pos] == 10
    count = struct.unpack(">i", b[pos + 1:pos + 5])[0]
    pos += 5
    servers = []
    for _ in range(count):
        entry = {}
        while b[pos] != 0:
            t = b[pos]
            assert t == 8
            ln = struct.unpack(">H", b[pos + 1:pos + 3])[0]
            key = b[pos + 3:pos + 3 + ln].decode()
            pos += 3 + ln
            vl = struct.unpack(">H", b[pos:pos + 2])[0]
            entry[key] = b[pos + 2:pos + 2 + vl].decode()
            pos += 2 + vl
        pos += 1
        servers.append(entry)
    return servers


def main():
    tmp = tempfile.mkdtemp()
    base = os.path.join(tmp, "base")
    mcdir = os.path.join(tmp, ".minecraft")
    os.makedirs(mcdir)
    with open(os.path.join(mcdir, "launcher_profiles.json"), "w") as f:
        json.dump({"profiles": {"autre": {"name": "autre"}}}, f)

    with open(os.path.join(ROOT, "modpack.json"), encoding="utf-8") as f:
        manifest = json.load(f)
    manifest["server"] = {"name": "Test", "address": "play.example.org:25565"}

    print("== resolution des mods")
    wanted = core.resolve_mods(manifest)
    print(json.dumps(wanted, indent=1))
    assert wanted, "aucun mod resolu"

    java = shutil.which("java")
    assert java, "java introuvable"
    print("== installation complete")
    last = [0]

    def prog(f, t):
        if f - last[0] > 0.1:
            last[0] = f
            print("   progression %d%% - %s" % (int(f * 100), t))

    name = core.run_all(base, mcdir, manifest, log=lambda m: print("   " + m[:160]), progress=prog,
                        java_exe=java, open_launcher=False)

    vid = core.forge_version_id(manifest["minecraft"], manifest["forge"])
    assert os.path.isfile(os.path.join(mcdir, "versions", vid, vid + ".json")), "version Forge absente"
    mods = os.listdir(os.path.join(base, "instance", "mods"))
    print("mods installes :", mods)
    assert len(mods) >= len(wanted)
    prof = json.load(open(os.path.join(mcdir, "launcher_profiles.json")))
    assert prof["profiles"][core.PROFILE_ID]["lastVersionId"] == vid
    assert "autre" in prof["profiles"], "profil existant perdu"
    servers = parse_servers_dat(os.path.join(base, "instance", "servers.dat"))
    assert servers == [{"ip": "play.example.org:25565", "name": "Test"}], servers

    print("== deuxieme passage (rien a retelecharger) + retrait d'un mod")
    extra = os.path.join(base, "instance", "mods", "intrus.jar")
    open(extra, "wb").write(b"x")
    core.run_all(base, mcdir, manifest, log=lambda m: None, progress=lambda f, t: None, java_exe=java,
                 open_launcher=False)
    assert not os.path.exists(extra), "le mod en trop n'a pas ete retire"
    print("TOUT EST OK :", name)


if __name__ == "__main__":
    main()
