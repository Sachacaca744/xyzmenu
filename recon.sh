mkdir -p /tmp/srv && cd /tmp/srv
M=$(curl -s -A "xyzmenu-ci/1.0" https://launchermeta.mojang.com/mc/game/version_manifest_v2.json | python3 -c "import sys,json;print([v['url'] for v in json.load(sys.stdin)['versions'] if v['id']=='1.20.1'][0])")
SU=$(curl -s -A "xyzmenu-ci/1.0" "$M" | python3 -c "import sys,json;print(json.load(sys.stdin)['downloads']['server']['url'])")
curl -sL -A "xyzmenu-ci/1.0" "$SU" -o server.jar; ls -l server.jar
echo "eula=true" > eula.txt
printf 'level-name=world\nonline-mode=false\nspawn-protection=0\nmax-tick-time=-1\nview-distance=4\nsimulation-distance=4\n' > server.properties
tar xzf "$OLDPWD/world.tgz" -C /tmp/srv && mv /tmp/srv/GTA_City /tmp/srv/world && ls -l world world/region | head
cp "$OLDPWD/cmds.txt" /tmp/srv/cmds.txt
echo "SRVLOG_START"
( sleep 45; while IFS= read -r l; do echo "$l"; sleep 0.4; done < cmds.txt; sleep 8; echo "stop" ) | timeout 400 java -Xmx2G -jar server.jar nogui 2>&1 | tail -200
echo "SRVLOG_END"
