set -x
cd /tmp
curl -s "https://api.modrinth.com/v2/project/cobblemon/version/1.5.2%2B1.20.1" -o v.json || true
URL=$(python3 -c "import json;d=json.load(open('v.json'));print([f['url'] for f in d['files'] if f.get('primary')][0])")
curl -sL "$URL" -o cob.jar; ls -l cob.jar
mkdir -p x && cd x && unzip -qo ../cob.jar 'com/cobblemon/mod/common/*' 'META-INF/*' 'fabric.mod.json' 2>/dev/null
ls /tmp/x | head
find . -name '*.class' | grep -iE "BattleGUI|BattleRegistry|/PokemonBattle\.class|BattleActor|PokemonBattleActor|PlayerBattleActor|BattlePokemon|CobblemonEvents|BattleEndedEvent|BattleVictoryEvent|BattleStartedPostEvent|BattleFledEvent|BattleFaintedEvent" | head -40
J="javap -cp /tmp/x -public"
for c in api.battles.model.PokemonBattle api.battles.model.BattleRegistry api.battles.model.actor.BattleActor battles.actor.PlayerBattleActor battles.pokemon.BattlePokemon pokemon.Pokemon api.events.CobblemonEvents; do echo "=== $c"; $J com.cobblemon.mod.common.$c 2>&1 | grep -iE "class |Battle|scale|entity|getActors|getPlayers|getPokemon|getEffectedPokemon|getActive|BATTLE_|isPvP|ended|getOriginalPokemon|getUuid|Player" | head -60; done
find . -name '*.class' | grep -iE "BattleGUI|BattleGui" | head
