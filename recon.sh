cd /tmp
curl -s -A "xyzmenu-ci/1.0" -G "https://api.modrinth.com/v2/project/cobblemon/version" --data-urlencode 'loaders=["forge"]' --data-urlencode 'game_versions=["1.20.1"]' -o v.json
URL=$(python3 -c "
import json
d=json.load(open('v.json'))
print([f['url'] for v in d if '1.5.2' in v['version_number'] for f in v['files'] if f.get('primary')][0])")
curl -sL -A "xyzmenu-ci/1.0" "$URL" -o cob.jar; ls -l cob.jar
mkdir -p x && cd x && unzip -qo ../cob.jar 'com/cobblemon/mod/common/*' 'META-INF/*' 2>/dev/null
J="javap -cp /tmp/x -public"
for c in battles.BattleRegistry battles.BattleSide battles.ActiveBattlePokemon entity.pokemon.PokemonEntity api.events.battles.BattleVictoryEvent api.events.battles.BattleFledEvent api.events.battles.BattleStartedPostEvent api.reactive.EventObservable api.reactive.Observable api.Priority client.gui.battle.BattleGUI; do echo "=== $c"; $J com.cobblemon.mod.common.$c 2>&1 | grep -v "\$default" | head -45; done
unzip -p ../cob.jar META-INF/mods.toml | head -30
ls /tmp/x/META-INF | head
