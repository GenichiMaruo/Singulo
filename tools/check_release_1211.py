"""Validate a production 1.21.1 JAR and its exact nonempty GameTest completion."""
import argparse
from pathlib import Path
import re
import struct
import tomllib
import zipfile

COUNTS = {'neoforge': 359, 'forge': 361}


def validate(root, loader, name=None):
    log = (root / 'gametest/logs/latest.log').read_text()
    counts = re.findall(r'All ([0-9]+) required tests passed', log)
    if counts != [str(COUNTS[loader])]:
        raise RuntimeError(f'{loader}: expected {COUNTS[loader]} successful tests, got {counts}')
    if name is None:
        properties = dict(line.split('=', 1) for line in (Path(__file__).resolve().parent.parent / 'gradle.properties').read_text().splitlines()
                          if '=' in line and not line.startswith('#'))
        suffix = 'N' if loader == 'neoforge' else 'F'
        name = f"{properties['mod_name']}-{properties['mod_version']}-1.21.1{suffix}.jar"
    path = root / 'libs' / name
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)) or any('ReviewHarness' in n for n in names):
            raise RuntimeError('Duplicate entries or development GUI code in production JAR')
        for n in names:
            if n.endswith('.class') and struct.unpack('>H', jar.read(n)[6:8])[0] > 65:
                raise RuntimeError('Production class requires newer than Java 21: ' + n)
        meta_path = 'META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml'
        meta = tomllib.loads(jar.read(meta_path).decode())
        mod = next(m for m in meta['mods'] if m['modId'] == 'singulo')
        suffix = 'N' if loader == 'neoforge' else 'F'
        if name != f"Singulo-{mod['version']}-1.21.1{suffix}.jar":
            raise RuntimeError('Version/loader in filename does not match JAR metadata')
        deps = {d['modId']: d for d in meta['dependencies']['singulo']}
        if loader not in deps or deps['minecraft']['versionRange'] != '[1.21.1]':
            raise RuntimeError('Wrong loader or Minecraft dependency')
        if loader == 'forge' and any('/compat/mekanism/' in n for n in names):
            raise RuntimeError('NeoForge-only Mekanism code leaked into Forge')
    print(f'{loader}: {COUNTS[loader]} tests and Java 21 distribution validated: {path}')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--loader', choices=tuple(COUNTS), required=True)
    p.add_argument('--build-dir', type=Path, required=True)
    a = p.parse_args()
    validate(a.build_dir, a.loader)
