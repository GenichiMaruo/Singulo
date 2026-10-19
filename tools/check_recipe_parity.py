"""Ensure the port keeps the reviewed 1.21.1 recipe costs after legacy JSON normalization."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
manifest = json.loads((ROOT / 'docs/port-0.10.0-1.20.1/recipe-parity.json').read_text())
root = ROOT / 'src/generated/resources/data/singulo/recipes'
actual = {}
for path in root.rglob('*.json'):
    data = json.dumps(json.loads(path.read_text()), sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
    actual[path.relative_to(root).as_posix()] = hashlib.sha256(data).hexdigest()
if actual != manifest['recipes']:
    differing = sorted(key for key in actual.keys() | manifest['recipes'].keys() if actual.get(key) != manifest['recipes'].get(key))
    raise SystemExit(f'Recipe parity failed against {manifest["source"]}: {differing}')
print(f'{len(actual)} recipes match the reviewed source {manifest["source"]} after 1.20.1 normalization')
