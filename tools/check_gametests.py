"""Check exact GameTest membership and completion, independently of the success count."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
OPTIONAL_NAMESPACES = {'singulo_mekanism'}
DEFAULT_NAMESPACES = ('singulo', 'singulo_prototype', 'singulo_progression',
                      'singulo_cable', 'singulo_rayleigh', 'singulo_panel_review', 'singulo_scaling', 'singulo_automation', 'singulo_reactor', 'singulo_lab')


def inventory(root=ROOT):
    """Read @GameTest methods, refusing unfamiliar declarations instead of silently skipping them."""
    entries = []
    for source_dir in ('src/main/java', 'src/gameTest/java'):
        for path in sorted((root / source_dir).rglob('*.java')):
            text = path.read_text(encoding='utf-8')
            # Preserve strings and newlines while ignoring comments containing annotation examples.
            text = re.sub(r'"(?:\\.|[^"\\])*"|/\*.*?\*/|//[^\n]*',
                          lambda m: m[0] if m[0].startswith('"') else '\n' * m[0].count('\n'), text, flags=re.S)
            text = text.replace('@net.neoforged.neoforge.gametest.', '@').replace('@net.minecraftforge.gametest.', '@')
            if '@GameTest(' not in text:
                continue
            if not path.name.endswith('GameTests.java'):
                raise RuntimeError('Test file must belong to the gameTest source set: ' + str(path.relative_to(root)))
            holder = re.search(r'@GameTestHolder\(([^)]+)\)', text)
            if not holder or '@PrefixGameTestTemplate(false)' not in text:
                raise RuntimeError(f'Unsupported test holder/prefix: {path.relative_to(root)}')
            constants = dict(re.findall(r'(?:static\s+final\s+String)\s+(\w+)\s*=\s*"([^"]*)"', text))
            constants['Singulo.MODID'] = 'singulo'

            def string(value):
                value = value.strip()
                if value.startswith('"') and value.endswith('"'):
                    return value[1:-1]
                if value in constants:
                    return constants[value]
                raise RuntimeError(f'Unknown test constant in {path.relative_to(root)}: {value}')

            namespace = string(holder[1])
            methods = list(re.finditer(r'@GameTest\(([^)]*)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*public\s+static\s+void\s+(\w+)\s*\(GameTestHelper\s+\w+\)', text))
            if len(methods) != text.count('@GameTest('):
                raise RuntimeError(f'Unparsed test declaration: {path.relative_to(root)}')
            for m in methods:
                attrs = dict(re.findall(r'(\w+)\s*=\s*([^,]+)', m[1]))
                if attrs.get('required', 'true').strip() != 'true':
                    raise RuntimeError('Optional test would hide a regression: ' + m[2])
                if attrs.get('attempts', '1').strip() != '1' or attrs.get('requiredSuccesses', '1').strip() != '1':
                    raise RuntimeError('Retries must not hide unstable tests: ' + m[2])
                ns = string(attrs['templateNamespace']) if 'templateNamespace' in attrs else namespace
                entries.append({'name': m[2].lower(), 'namespace': ns,
                                'source': str(path.relative_to(root))})
    if not entries:
        raise RuntimeError('No GameTests found')
    duplicates = [name for name, n in Counter(e['name'] for e in entries).items() if n != 1]
    if duplicates:
        raise RuntimeError('Duplicate GameTest names: ' + ', '.join(duplicates))
    return entries


def validate_report(report, expected):
    expected = set(expected)
    if not expected:
        raise RuntimeError('Empty expected test selection')
    if report.get('schema') != 1 or report.get('complete') is not True:
        raise RuntimeError('Missing completed GameTest report')
    registered = report.get('expected', [])
    results = report.get('results', [])
    names = [r['name'] for r in results]
    if len(registered) != len(set(registered)) or set(registered) != expected:
        raise RuntimeError('Registered tests differ from source inventory')
    if len(names) != len(set(names)) or set(names) != expected:
        raise RuntimeError('Missing, unexpected or duplicate GameTest results')
    if any(r.get('status') != 'passed' or r.get('required') is not True for r in results):
        raise RuntimeError('Failed or optional GameTest result')
    if any(not isinstance(r.get('millis'), int) or r['millis'] < 0 for r in results):
        raise RuntimeError('Invalid GameTest timing')
    return len(names)


def check(root, namespaces=DEFAULT_NAMESPACES, source=ROOT, classes=()):
    entries = inventory(source)
    unknown = {e['namespace'] for e in entries} - set(DEFAULT_NAMESPACES) - OPTIONAL_NAMESPACES
    if unknown:
        raise RuntimeError('Test namespace is absent from the full suite configuration')
    namespaces = set(namespaces)
    if namespaces - {e['namespace'] for e in entries}:
        raise RuntimeError('Unknown/empty test namespace')
    if set(classes) - {Path(e['source']).stem for e in entries}:
        raise RuntimeError('Unknown test class')
    expected = [e['name'] for e in entries if e['namespace'] in namespaces
                and (not classes or Path(e['source']).stem in classes)]
    report = json.loads((root / 'reports/gametest/results.json').read_text())
    total = validate_report(report, expected)
    print(f'Exact GameTest membership verified: {total}; suite {report["elapsedMillis"] / 1000:.3f}s')
    return total


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, default=Path('build'))
    parser.add_argument('--namespaces', default=','.join(DEFAULT_NAMESPACES))
    parser.add_argument('--inventory', action='store_true')
    parser.add_argument('--classes', default='')
    args = parser.parse_args()
    if args.inventory:
        entries = inventory()
        print(json.dumps({'total': len(entries), 'namespaces': dict(Counter(e['namespace'] for e in entries))}, indent=2))
    else:
        check(args.build_dir, args.namespaces.split(','), classes=tuple(filter(None, args.classes.split(','))))
