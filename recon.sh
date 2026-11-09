cd /tmp
curl -s -A "xyzmenu-ci/1.0" -G "https://api.modrinth.com/v2/project/cobblemon/version" --data-urlencode 'loaders=["forge"]' --data-urlencode 'game_versions=["1.20.1"]' -o v.json
URL=$(python3 -c "
import json
d=json.load(open('v.json'))
print([f['url'] for v in d if '1.5.2' in v['version_number'] for f in v['files'] if f.get('primary')][0])")
curl -sL -A "xyzmenu-ci/1.0" "$URL" -o cob.jar
mkdir -p x && cd x && unzip -qo ../cob.jar 'com/cobblemon/mod/common/*' 2>/dev/null
P="com.cobblemon.mod.common"
for c in entity.PokemonSideDelegate entity.pokemon.PokemonServerDelegate client.entity.PokemonClientDelegate entity.pokemon.PokemonEntity client.render.pokemon.PokemonRenderer; do
 echo "=== $c : scale refs"; javap -cp /tmp/x -p -c $P.$c 2>/dev/null | grep -iE "^  [a-z].*\(|scale" | grep -iE -B1 "scale" | cut -c1-220 | head -40
done
echo "=== PokemonServerDelegate members"; javap -cp /tmp/x -p $P.entity.pokemon.PokemonServerDelegate | cut -c1-200 | head -60
echo "=== PokemonEntity data accessors"; javap -cp /tmp/x -p $P.entity.pokemon.PokemonEntity | grep -iE "EntityDataAccessor|static.*(SCALE|DIMENS)" | head -30
echo "=== effects"; javap -cp /tmp/x -p $P.entity.pokemon.effects.EffectTracker | head -30
ls /tmp/x/com/cobblemon/mod/common/entity/pokemon/effects/ | head -20
javap -cp /tmp/x -p $P.api.entity.pokemon.MocKEffect
