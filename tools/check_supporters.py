"""Validate the public supporter roster before publishing a build (no dependencies)."""
import argparse
import json
import re
import unicodedata
from pathlib import Path

DEFAULT = Path(__file__).resolve().parent.parent / 'src/main/resources/assets/singulo/supporters.json'


def validate(path):
    root = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(root, dict) or not isinstance(root.get('supporters'), list):
        raise ValueError('Expected a supporters array')
    supporters = root['supporters']
    if len(supporters) > 100:
        raise ValueError('Standard and rainbow supporters share a limit of 100 people')
    ids = set()
    for position, entry in enumerate(supporters, 1):
        if not isinstance(entry, dict) or any(not isinstance(entry.get(key), str) for key in ('id', 'name', 'tier')):
            raise ValueError(f'Entry {position}: id, name and tier must be strings')
        if not re.fullmatch(r'[a-z0-9_-]{1,64}', entry['id']) or entry['id'] in ids:
            raise ValueError(f'Entry {position}: invalid or duplicate id')
        ids.add(entry['id'])
        name = unicodedata.normalize('NFC', entry['name'].strip())
        if not 1 <= len(name) <= 64 or any(ord(c) <= 31 or 127 <= ord(c) <= 159 or c == '§' for c in name):
            raise ValueError(f'Entry {position}: name must contain 1–64 characters without control or formatting codes')
        if entry['tier'] not in ('standard', 'rainbow'):
            raise ValueError(f'Entry {position}: tier must be standard or rainbow')
    return len(supporters)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', nargs='?', type=Path, default=DEFAULT)
    options = parser.parse_args()
    try:
        count = validate(options.path)
    except (ValueError, OSError) as error:
        parser.exit(1, f'Invalid supporter roster: {error}\n')
    print(f'Supporter roster validated: {count} / 100')
