"""Validate a production 26.1.2 JAR and its exact nonempty GameTest completion."""
import argparse
from pathlib import Path
import re
import struct
try:
    import tomllib
except ModuleNotFoundError:  # Local Python 3.10 validation; CI uses Python 3.12.
    import tomli as tomllib
import zipfile
from check_gametests import check, inventory, DEFAULT_NAMESPACES

# 26.1.2 is NeoForge-only, without Mekanism integration.
COUNTS = {'neoforge': 1037}


def validate(root, loader, name=None, source=None):
    # Parse only the verified tag's Java declarations; never execute its Python
    # validation code. Ordinary local/PR builds retain the current count guard.
    expected = COUNTS[loader] if source is None else sum(
        e['namespace'] in DEFAULT_NAMESPACES for e in inventory(source))
    source = Path(__file__).resolve().parent.parent if source is None else source
    if expected <= 0:
        raise RuntimeError('Empty tagged GameTest inventory')
    log = (root / 'gametest/logs/latest.log').read_text()
    validate_completion(log, expected)
    if check(root, source=source) != expected:
        raise RuntimeError('Source test inventory differs from release count')
    if name is None:
        properties = dict(line.split('=', 1) for line in (source / 'gradle.properties').read_text().splitlines()
                          if '=' in line and not line.startswith('#'))
        name = f"{properties['mod_name']}-{properties['mod_version']}-26.1.2N.jar"
    path = root / 'libs' / name
    validate_distribution(path, loader)
    print(f'{loader}: {expected} tests and Java 25 distribution validated: {path}')


def validate_completion(log, expected):
    counts = re.findall(r'All ([0-9]+) required tests passed', log)
    if counts != [str(expected + 1)]:
        raise RuntimeError(f'expected {expected} custom + 1 vanilla successful tests, got {counts}')
    # Official GameTestInstances.bootstrap uses TestData(..., required=false).
    # We still require its successful execution explicitly; custom tests remain required.
    builtins = re.findall(r'SINGULO_VANILLA_TEST (\S+) (\S+) required=(\S+)', log)
    if builtins != [('minecraft:always_pass', 'passed', 'false')]:
        raise RuntimeError('Missing, failed or duplicate vanilla always_pass evidence')


def validate_distribution(path, loader="neoforge"):
    """Inspect packaging independently; this alone never certifies release readiness."""
    name = path.name
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)) or any('ReviewHarness' in n or 'ReviewScreenshot' in n or 'ReviewLightmap' in n or 'ReviewEnvironment' in n
                or re.search(r'(?:GameTests(?:\$[^/]*)?|TestBuild)\.class$', n)
                or '/test/harness/' in n or '/singulo/review/' in n
                or n == 'singulo-test-suites.list' or '/compat/mekanism/' in n for n in names):
            raise RuntimeError('Duplicate entries or development GUI code in production JAR')
        for n in names:
            if n.endswith('.class') and struct.unpack('>H', jar.read(n)[6:8])[0] > 69:
                raise RuntimeError('Production class requires newer than Java 25: ' + n)
        meta = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        mod = next(m for m in meta['mods'] if m['modId'] == 'singulo')
        if name != f"Singulo-{mod['version']}-26.1.2N.jar":
            raise RuntimeError('Version/loader in filename does not match JAR metadata')
        dependencies = meta['dependencies']['singulo']
        deps = {d['modId']: d for d in dependencies}
        if len(deps) != len(dependencies):
            raise RuntimeError('Duplicate mod dependency')
        if 'mekanism' in deps or 'forge' in deps:
            raise RuntimeError('Unsupported dependency in the NeoForge-only non-Mekanism port')
        if loader not in deps or deps['minecraft']['versionRange'] != '[26.1.2]' or deps[loader].get('type') != 'required' or deps['minecraft'].get('type') != 'required':
            raise RuntimeError('Wrong loader or Minecraft dependency')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--loader', choices=tuple(COUNTS), required=True)
    p.add_argument('--build-dir', type=Path, required=True)
    p.add_argument('--source', type=Path, help='Verified source checkout used for exact test membership')
    a = p.parse_args()
    validate(a.build_dir, a.loader, source=a.source)
