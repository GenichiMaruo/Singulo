"""Check Minecraft 1.20.1 data layout, loader metadata and Java 17 artifacts."""
import argparse
import json
import struct
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODERN_DIRECTORIES = {'recipe', 'loot_table', 'advancement', 'structure'}
MODERN_TAG_DIRECTORIES = {'item', 'block', 'fluid', 'entity_type', 'game_event'}


def check_layout(names):
    for name in names:
        parts = name.split('/')
        if len(parts) > 3 and parts[0] == 'data':
            assert parts[2] not in MODERN_DIRECTORIES, f'1.21 data directory: {name}'
            if parts[2] == 'tags' and len(parts) > 4:
                assert parts[3] not in MODERN_TAG_DIRECTORIES, f'1.21 tag directory: {name}'


def validate_source():
    for root in (ROOT / 'src/main/resources', ROOT / 'src/generated/resources'):
        check_layout([p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()])
    for path in (ROOT / 'src/main/java').rglob('*.java'):
        source = path.read_text(encoding='utf-8')
        for unsupported in ('net.neoforged.', 'RegistryFriendlyByteBuf', 'net.minecraft.core.component.',
                            'SynchedEntityData.Builder', 'ResourceLocation.withDefaultNamespace'):
            assert unsupported not in source, f'1.21 API {unsupported}: {path.relative_to(ROOT)}'


def validate_jar(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        check_layout(names)
        metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode())
        mod = next(m for m in metadata['mods'] if m['modId'] == 'singulo')
        assert mod['version'] == '0.7.0', 'Incorrect mod version'
        dependencies = {d['modId']: d for d in metadata['dependencies']['singulo']}
        assert dependencies['minecraft']['versionRange'] == '[1.20.1]', 'Incorrect Minecraft dependency'
        assert 'forge' in dependencies, 'Missing 1.20.1 loader dependency'
        suffix = path.stem[-1]
        loader_version = '47.4.26' if suffix == 'F' else '47.1.106'
        assert suffix in ('F', 'N') and loader_version in dependencies['forge']['versionRange'], 'Wrong loader version or filename'
        assert 'META-INF/accesstransformer.cfg' in names, 'Missing access transformer'
        assert 'assets/singulo/supporters.json' in names, 'Missing supporter roster'
        assert not any(name.startswith(('data/singulo_port_checks/', 'data/singulo_reload_check/')) for name in names), 'Diagnostic resources shipped'
        for feature in ('android/AndroidStationBlockEntity', 'genesis/GenesisCoreBlockEntity',
                        'client/SupporterPlaque', 'network/RenameAndroidPayload',
                        'android/AndroidWorkGoal', 'android/work/WorkConsoleBlockEntity',
                        'android/work/RecipeData', 'android/AndroidAccessoryItem'):
            assert f'io/github/genichimaruo/singulo/{feature}.class' in names, f'Missing feature: {feature}'
        count = 0
        for name in names:
            if name.endswith('.class'):
                header = archive.read(name)[:8]
                assert header[:4] == b'\xca\xfe\xba\xbe' and struct.unpack('>H', header[6:8])[0] <= 61, f'Not Java 17: {name}'
                assert 'GuiReviewHarness' not in name and 'PreviewHarness' not in name and 'ReviewTests' not in name and 'PortDiagnosticTests' not in name and 'DiagnosticTests' not in name, f'Preview code shipped: {name}'
                count += 1
            if name.endswith('pack.mcmeta'):
                assert json.loads(archive.read(name))['pack']['pack_format'] == 15, f'Wrong pack format: {name}'
        print(f'{path.name}: {count} Java 17 classes, 1.20.1 layout and loader {loader_version} validated')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    validate_source()
    if args.jar:
        validate_jar(args.jar)
    else:
        print('Minecraft 1.20.1 source APIs and resource layout validated')
