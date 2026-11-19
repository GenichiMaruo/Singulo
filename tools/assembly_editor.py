"""精密組立台のローカル編集画面。uv run --no-project --python 3.12 --with pillow tools/assembly_editor.py"""
import base64
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
from pathlib import Path
import secrets
from tempfile import NamedTemporaryFile
from threading import Lock
import webbrowser

import gen_data as gen
import recipe_images as images
from assembly_shapes import apply_edit, assembly_id, assembly_rows
from gen_docs import DESC, recipe_uses
from localization import LOCALES
import vanilla_catalog


def json_bytes(obj):
    return (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode()


def png_bytes(image):
    buffer = BytesIO()
    image.save(buffer, format='PNG')
    return buffer.getvalue()


def make_edit(cells, palette, original):
    if not isinstance(cells, list) or len(cells) != 25:
        raise ValueError('盤面は5×5で指定してください。')
    if any(not isinstance(m, str) or (m and m not in palette) for m in cells):
        raise ValueError('材料一覧にある素材を選んでください。')
    used = set(cells) - {''}
    names = [m for m in dict.fromkeys([*original, *cells]) if m in used]
    symbols = {m: chr(65+i) for i, m in enumerate(names)}
    return {'rows': [''.join(symbols.get(m, '.') for m in cells[i:i+5]) for i in range(0, 25, 5)],
            'key': {ch: m for m, ch in symbols.items()}}


def replace_outputs(outputs):
    """Stage all bytes before publishing. The authoritative edit is replaced last."""
    staged, replaced = {}, []
    previous = {p: p.read_bytes() if p.exists() else None for p in outputs}
    try:
        for path, data in outputs.items():
            if previous[path] == data:
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            with NamedTemporaryFile(dir=path.parent, delete=False) as tmp:
                staged[path] = Path(tmp.name)
                tmp.write(data)
        for path, temp in staged.items():
            temp.replace(path)
            replaced.append(path)
    except OSError:
        for path in reversed(replaced):
            if previous[path] is None:
                path.unlink()
            else:
                path.write_bytes(previous[path])
        raise
    finally:
        for temp in staged.values():
            temp.unlink(missing_ok=True)


def usage_outputs(root, materials):
    """Keep the generated 'used in' lists and translation sources consistent on material changes."""
    affected = materials & DESC.keys() & gen.MOD.keys()
    if not affected:
        return {}
    uses = recipe_uses(gen.RECIPES, gen.RESTORED)
    lang = root / 'src/generated/resources/assets/singulo/lang'
    formats = {'ja_jp': ('使い道: ', '、', ' ほか{}件'), 'en_us': ('Used in: ', ', ', ' and {} more'),
               'zh_cn': ('用于：', '、', ' 等 {} 个'), 'zh_tw': ('用於：', '、', ' 等 {} 個'),
               'ko_kr': ('사용처: ', ', ', ' 외 {}개')}
    # These two alternate recipes have no block, item or multiblock name key.
    variants = {'cooling_tower_extension': ('冷却塔扩建', '冷卻水塔擴建', '냉각탑 확장'),
                'compressed_block_2_mixed': ('压缩块Lv2（混合）', '壓縮塊Lv2（混合）', '압축 블록 Lv2(혼합)')}
    outputs, english = {}, {}
    for locale, (prefix, separator, suffix) in formats.items():
        path = lang / f'{locale}.json'
        strings = json.loads(path.read_bytes())
        for material in affected:
            key = 'uses.singulo.' + gen.MOD[material][0]
            products = [n for n in uses.get(material, []) if n in gen.MOD and gen.MOD[n][2] != 'fluid']
            shown = []
            for name in products[:5]:
                iid, en, kind = gen.MOD[name]
                if locale == 'ja_jp':
                    shown.append(name)
                elif locale == 'en_us':
                    shown.append(en)
                elif iid in variants:
                    shown.append(variants[iid][LOCALES.index(locale)])
                elif kind == 'structure':
                    shown.append(strings.get(f'multiblock.singulo.{iid}') or strings[f'station.singulo.{iid}'])
                else:
                    group = 'block' if kind in gen.BLOCK_KINDS else 'item'
                    shown.append(strings[f'{group}.singulo.{iid}'])
            if shown:
                strings[key] = prefix + separator.join(shown) + (suffix.format(len(products)-5) if len(products)>5 else '')
            else:
                strings.pop(key, None)
            if locale == 'en_us':
                english[key] = strings.get(key)
        if locale in LOCALES:
            source = root / 'tools/locales' / f'{locale}.json'
            catalog = json.loads(source.read_bytes())
            for key, en in sorted(english.items()):
                if en is None:
                    catalog.pop(key, None)
                elif catalog.get(key, {}).get('source') == en:
                    strings[key] = catalog[key]['translation']
                else:
                    catalog[key] = {'source': en, 'translation': strings[key]}
            outputs[source] = json_bytes(catalog)
        outputs[path] = json_bytes(dict(sorted(strings.items())))
    return outputs


class RecipeEditor:
    def __init__(self, root=gen.ROOT):
        self.root = root
        self.edits = root / 'tools/assembly_edits'
        self.data = root / 'src/generated/resources/data/singulo/recipe/precision_assembler'
        self.pictures = root / 'docs/recipes'
        self.names = {assembly_id(n): n for n in gen.ORDER if gen.RECIPES[n]['station'] == '精密組立台'}
        # Ordinary materials already used by the mod, including optional integrations.
        dependencies = {}
        for r in gen.RECIPES.values():
            for m in r['inputs']:
                dependencies.setdefault(m, set()).add(r.get('mod'))
        existing = [m for m in dependencies if gen.resolve(m)[0] in {'item', 'tag'} and m not in gen.CATALYST_TIERS]
        # Keep established recipe keys, including tags. An exact vanilla ingredient only needs one picker entry.
        exact = {gen.resolve(m)[1] for m in existing if gen.resolve(m)[0] == 'item'}
        self.palette = sorted([*existing, *(vanilla_catalog.ITEM_NAMES.keys() - exact)],
                              key=lambda m: (vanilla_catalog.ITEM_NAMES.get(m, m), m in vanilla_catalog.ITEM_NAMES, m))
        self.material_ids = {}
        self.optional = {}
        for m in self.palette:
            kind, identifier = gen.resolve(m)
            self.material_ids[m] = '#' + identifier if kind == 'tag' else identifier
            mod = gen.RECIPES.get(m, {}).get('mod')
            if kind == 'item' and identifier.split(':')[0] not in ('minecraft', 'singulo'):
                mod = identifier.split(':')[0]
            if kind == 'tag' and len(dependencies[m]) == 1:
                mod = next(iter(dependencies[m]))
            if mod:
                self.optional[m] = mod
        self.entries = {rid: self.entry(rid) for rid in self.names}
        names = {m for m in self.palette if self.material_ids[m] not in vanilla_catalog.ITEM_NAMES}
        names.update(self.names.values())
        self.icons = {n: 'data:image/png;base64,' + base64.b64encode(png_bytes(images.icon(n))).decode()
                      for n in names}

    def revision(self, rid):
        digest = sha256()
        for path in (self.edits / (rid + '.json'), self.data / (rid + '.json')):
            digest.update(sha256(path.read_bytes() if path.exists() else b'').digest())
        return digest.hexdigest()

    def entry(self, rid):
        name = self.names[rid]
        recipe = gen.RECIPES[name]
        items, _, _ = gen.item_inputs(recipe['inputs'], name)
        names = [m for m in recipe['inputs'] if gen.resolve(m)[0] in {'item', 'tag'}]
        rows = assembly_rows(rid, items, recipe.get('assembly_diagram'))
        board = [names[ord(ch)-65] if ch != ' ' else '' for row in rows for ch in row]
        tools = [m + ' ×' + str(recipe['inputs'][m]) for m, i in zip(names, items, strict=True) if i.get('uses')]
        return dict(id=rid, name=name, board=board, parts=[m for m in names if m in board], catalysts=tools,
                    stage=recipe['stage'], time=recipe['t'], energy=recipe['fe'], mod=recipe.get('mod'),
                    revision=self.revision(rid))

    def catalog(self):
        return dict(recipes=list(self.entries.values()), materials=self.palette, materialIds=self.material_ids,
                    vanillaNames=vanilla_catalog.ITEM_NAMES, optional=self.optional, icons=self.icons)

    def save(self, rid, cells, revision):
        current = self.entries[rid]
        if revision != current['revision'] or revision != self.revision(rid):
            raise ValueError('別の編集が保存されています。編集ツールを開き直してください。')
        allowed = {m for m in self.palette if m not in self.optional or self.optional[m] == current['mod']}
        edit = make_edit(cells, allowed, current['parts'])
        name = self.names[rid]
        original = gen.RECIPES[name]
        updated = apply_edit(name, original, edit)
        if cells == current['board']:
            return current
        gen.RECIPES[name] = updated
        try:
            errors = gen.validate()
            if errors:
                raise ValueError('\n'.join(errors))
            data = gen.recipe_files(write=False)[0]['precision_assembler/' + rid]
            sub = f'段階{updated["stage"]} · 精密組立台'
            image = images.render_assembly(name, updated, sub, data).convert('RGB')
            picture = images.file_id(name, gen.ORDER.index(name)) + '.png'
            usages = usage_outputs(self.root, original['inputs'].keys() ^ updated['inputs'].keys())
            replace_outputs({**usages, self.data / (rid + '.json'): json_bytes(data),
                             self.pictures / picture: png_bytes(image),
                             self.edits / (rid + '.json'): json_bytes(edit)})
        except Exception:
            gen.RECIPES[name] = original
            raise
        self.entries[rid] = self.entry(rid)
        return self.entries[rid]


def serve(editor=None, port=8764):
    editor = editor or RecipeEditor()
    token, lock = secrets.token_urlsafe(32), Lock()
    page = Path(__file__).with_suffix('.html').read_text(encoding='utf-8').replace('__SESSION_TOKEN__', token)

    class Handler(BaseHTTPRequestHandler):
        def reply(self, status, mime, body):
            self.send_response(status)
            self.send_header('Content-Type', mime)
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.headers.get('Host') != address:
                return self.send_error(403)
            if self.path == '/':
                return self.reply(200, 'text/html; charset=utf-8', page.encode())
            if self.path == '/vanilla-icons.png':
                return self.reply(200, 'image/png', (vanilla_catalog.DIRECTORY / 'icons.png').read_bytes())
            if self.path == '/catalog':
                with lock:
                    return self.reply(200, 'application/json', json_bytes(editor.catalog()))
            self.send_error(404)

        def do_POST(self):
            if self.path != '/save':
                return self.send_error(404)
            if (self.headers.get('Host') != address or self.headers.get('Origin') != 'http://' + address
                    or self.headers.get('X-Editor-Token') != token):
                return self.send_error(403)
            try:
                size = int(self.headers.get('Content-Length', 0))
                if not 0 < size <= 32768:
                    raise ValueError('編集データのサイズが不正です。')
                payload = json.loads(self.rfile.read(size))
                with lock:
                    entry = editor.save(payload['id'], payload['board'], payload['revision'])
                self.reply(200, 'application/json', json_bytes(entry))
            except (KeyError, TypeError, ValueError, OSError) as exc:
                self.reply(400, 'application/json', json_bytes({'error': str(exc)}))

    # A fixed loopback port also prevents two editor processes writing the workspace at once.
    with ThreadingHTTPServer(('127.0.0.1', port), Handler) as server:
        address = f'127.0.0.1:{server.server_port}'
        print('精密組立台レシピエディター  http://' + address + '  (Ctrl+C で終了)', flush=True)
        webbrowser.open('http://' + address)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    serve()
