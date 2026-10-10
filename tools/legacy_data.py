"""Minecraft 1.20.1 resource layout and JSON encoding."""
from pathlib import Path

def convert(path: Path, obj):
    parts = list(path.parts)
    if 'data' in parts:
        namespace = parts.index('data') + 1
        if parts[namespace] == 'c': parts[namespace] = 'forge'
        i = parts.index('data') + 2
        names = {'recipe': 'recipes', 'loot_table': 'loot_tables', 'advancement': 'advancements', 'structure': 'structures'}
        if i < len(parts):
            parts[i] = names.get(parts[i], parts[i])
            if parts[i] == 'tags' and i + 1 < len(parts):
                parts[i + 1] = {'item': 'items', 'block': 'blocks', 'entity_type': 'entity_types', 'fluid': 'fluids', 'game_event': 'game_events'}.get(parts[i + 1], parts[i + 1])
    path = Path(*parts)
    def visit(value):
        if isinstance(value, list): return [visit(v) for v in value]
        if isinstance(value, str):
            value = value.replace('#c:', '#forge:')
            if value.startswith('c:'): value = 'forge:' + value[2:]
            value = value.replace('forge:gravels', 'forge:gravel').replace('forge:netherracks', 'forge:netherrack').replace('forge:obsidians', 'forge:obsidian').replace('forge:sandstone/blocks', 'forge:sandstone')
            value = value.replace('forge:cobblestones', 'forge:cobblestone').replace('forge:stones', 'forge:stone').replace('forge:sands', 'forge:sand')
            return value
        if not isinstance(value, dict): return value
        value = {k: visit(v) for k, v in value.items()}
        if value.get('type') == 'neoforge:components':
            return {'type': 'forge:partial_nbt', 'item': value['items'], 'nbt': value['components']}
        if value.get('function') == 'minecraft:copy_components':
            # Item state is copied by AbstractMachineBlock.getDrops on 1.20.1.
            return None
        if 'functions' in value: value['functions'] = [f for f in value['functions'] if f is not None]
        if 'pack_format' in value: value['pack_format'] = 15
        if 'neoforge_data' in value: value['forge_data'] = value.pop('neoforge_data')
        return value
    obj = visit(obj)
    if 'loot_tables' in parts:
        # 1.20.1's set_count clamps to the item's stack limit. Repeated rolls
        # preserve the original quantities for unstackable expedition items.
        for pool in obj.get('pools', []):
            entries = pool.get('entries', [])
            if pool.get('rolls') == 1 and len(entries) == 1:
                functions = entries[0].get('functions', [])
                count = next((f for f in functions if f.get('function') == 'minecraft:set_count'), None)
                if count is not None:
                    pool['rolls'] = count['count']
                    functions.remove(count)
    if 'recipes' in parts and obj.get('type', '').startswith('minecraft:'):
        result = obj.get('result')
        if isinstance(result, dict) and 'id' in result: result['item'] = result.pop('id')
    # 1.20.1 advancement item predicates use an array of item ids.
    if 'advancements' in parts:
        def predicates(value):
            if isinstance(value, list): return [predicates(v) for v in value]
            if not isinstance(value, dict): return value
            value = {k: predicates(v) for k, v in value.items()}
            if 'items' in value and isinstance(value['items'], str): value['items'] = [value['items']]
            if 'icon' in value and 'id' in value['icon']: value['icon']['item'] = value['icon'].pop('id')
            return value
        obj = predicates(obj)
    return path, obj
