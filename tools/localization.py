"""Offline generation and validation of the additional language catalogs."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / 'src/generated/resources/assets/singulo/lang'
LOCALES = ('zh_cn', 'zh_tw', 'ko_kr')
FORMAT = re.compile(r'%(?:(\d+)\$)?([-+#0 ]*\d*(?:\.\d+)?[sdf])|%%')


def arguments(text):
    result, index = [], 1
    for match in FORMAT.finditer(text):
        if match.group() == '%%':
            continue
        position, spec = match.groups()
        result.append((int(position) if position else index, spec))
        if not position:
            index += 1
    return sorted(result)


def generate(reference, check=False):
    for locale in LOCALES:
        catalog = json.loads((ROOT / f'tools/locales/{locale}.json').read_text(encoding='utf-8'))
        if set(catalog) != set(reference):
            raise ValueError(f'{locale}: missing or extra keys: {set(catalog) ^ set(reference)}')
        translations = {}
        for key, source in reference.items():
            entry = catalog[key]
            target = entry['translation']
            if entry['source'] != source:
                raise ValueError(f'{locale}: outdated source: {key}')
            if not target.strip() or 'SGTOKEN' in target:
                raise ValueError(f'{locale}: incomplete translation: {key}')
            if arguments(source) != arguments(target):
                raise ValueError(f'{locale}: format arguments differ: {key}')
            translations[key] = target
        path = LANG / f'{locale}.json'
        if check:
            actual = json.loads(path.read_text(encoding='utf-8'))
            if actual != translations:
                raise ValueError(f'{locale}: generated file differs from catalog')
        else:
            path.write_text(json.dumps(dict(sorted(translations.items())), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f'{locale}: {len(translations)} translations validated')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    options = parser.parse_args()
    reference = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))
    generate(reference, options.check)
