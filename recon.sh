cd /tmp
curl -s -A "xyzmenu-ci/1.0" -G "https://api.modrinth.com/v2/project/cobblemon/version" --data-urlencode 'loaders=["forge"]' --data-urlencode 'game_versions=["1.20.1"]' -o v.json
URL=$(python3 -c "
import json
d=json.load(open('v.json'))
print([f['url'] for v in d if '1.5.2' in v['version_number'] for f in v['files'] if f.get('primary')][0])")
curl -sL -A "xyzmenu-ci/1.0" "$URL" -o cob.jar
mkdir -p x && cd x && unzip -qo ../cob.jar 'com/cobblemon/mod/common/*' 2>/dev/null
P="com.cobblemon.mod.common"
echo "=== Pokemon scale members"; javap -cp /tmp/x -p $P.pokemon.Pokemon | grep -i scale
echo "=== setScaleModifier code"; javap -cp /tmp/x -p -c $P.pokemon.Pokemon | awk '/void setScaleModifier\(float\)/,/^$/' | head -40
echo "=== Pokemon scale-related observables"; javap -cp /tmp/x -p $P.pokemon.Pokemon | grep -iE "Observable|emit" | head
echo "=== PokemonEntity scale/dimension members"; javap -cp /tmp/x -p $P.entity.pokemon.PokemonEntity | grep -iE "scale|dimension|Size|updateScale|SCALE" | head -20
echo "=== PokemonEntity getDimensions code"; javap -cp /tmp/x -p -c $P.entity.pokemon.PokemonEntity | awk '/m_6972_|getDimensions/,/^$/' | grep -E "invoke|getstatic" | head -30
echo "=== who reads scaleModifier"; cd /tmp/x; grep -rl "getScaleModifier" . 2>/dev/null | head -20
echo "=== PokemonSideDelegate/ client"; javap -cp /tmp/x -p $P.client.entity.PokemonClientDelegate | grep -iE "scale|dimension" | head
echo "=== Scale command"; ls $(find /tmp/x -type d -name commands | head -1) | grep -i scale
javap -cp /tmp/x -p -c $P.api.pokemon.PokemonProperties 2>/dev/null | grep -i scale | head
