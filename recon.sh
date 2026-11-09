cd /tmp
curl -s -A "xyzmenu-ci/1.0" -G "https://api.modrinth.com/v2/project/cobblemon/version" --data-urlencode 'loaders=["forge"]' --data-urlencode 'game_versions=["1.20.1"]' -o v.json
URL=$(python3 -c "
import json
d=json.load(open('v.json'))
print([f['url'] for v in d if '1.5.2' in v['version_number'] for f in v['files'] if f.get('primary')][0])")
curl -sL -A "xyzmenu-ci/1.0" "$URL" -o cob.jar
mkdir -p x && cd x && unzip -qo ../cob.jar 'com/cobblemon/mod/common/*' 2>/dev/null
P="com.cobblemon.mod.common"
echo "=== ClientDelegate.onTrackedDataSet around scale"
javap -cp /tmp/x -p -c $P.client.entity.PokemonClientDelegate | awk '/void onTrackedDataSet/,/^$/' | awk '{print}' | grep -nE "putfield.*entityScaleModifier|getstatic|invoke" | sed -n '1,200p' | grep -B14 "entityScaleModifier" | cut -c1-200
echo "=== PokemonRenderer.render invokes"
javap -cp /tmp/x -p -c $P.client.render.pokemon.PokemonRenderer | awk '/public void render\(com.cobblemon.mod.common.entity.pokemon.PokemonEntity/,/^$/' | grep -E "invoke" | cut -c1-200
echo "=== PokemonRenderer super"
javap -cp /tmp/x -p $P.client.render.pokemon.PokemonRenderer | head -3
echo "=== PokemonRenderer.scale"
javap -cp /tmp/x -p -c $P.client.render.pokemon.PokemonRenderer | awk '/protected void scale\(/,/^$/' | grep -E "invoke|fmul|ldc" | cut -c1-200
