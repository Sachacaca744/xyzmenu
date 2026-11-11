set -x
mkdir -p /tmp/srv && cd /tmp/srv
M=$(curl -s -A "xyzmenu-ci/1.0" https://launchermeta.mojang.com/mc/game/version_manifest_v2.json | python3 -c "import sys,json;print([v['url'] for v in json.load(sys.stdin)['versions'] if v['id']=='1.20.1'][0])")
SU=$(curl -s -A "xyzmenu-ci/1.0" "$M" | python3 -c "import sys,json;print(json.load(sys.stdin)['downloads']['server']['url'])")
curl -sL -A "xyzmenu-ci/1.0" "$SU" -o server.jar; ls -l server.jar
echo "eula=true" > eula.txt
printf 'level-type=minecraft\\:flat\nlevel-name=world\nonline-mode=false\nspawn-protection=0\nmax-tick-time=-1\n' > server.properties
(sleep 40; echo "stop") | java -jar server.jar nogui 2>&1 | tail -15
ls -l world
echo "LEVELDAT_B64_START"
base64 -w0 world/level.dat
echo
echo "LEVELDAT_B64_END"
