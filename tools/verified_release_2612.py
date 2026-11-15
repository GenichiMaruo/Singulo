"""Promote only paired, digest-verified 26.1.2 NeoForge PR artifacts; never rebuild or upload here."""
import argparse
import io
import zipfile
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from check_release_2612 import validate
from release_artifact import select_artifact


def api(repo, endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/{endpoint}'], text=True))


def git(source, *args):
    return subprocess.check_output(['git', '-C', str(source), *args], text=True).strip()


def verify_merged_pr(repo, run, tree):
    candidates = run.get('pull_requests') or api(repo, f"commits/{run['head_sha']}/pulls?per_page=100")
    if not isinstance(candidates, list) or not candidates or len(candidates) >= 100:
        raise RuntimeError('Missing or potentially incomplete PR association listing')
    numbers = [p.get('number') for p in candidates]
    if any(type(n) is not int or n <= 0 for n in numbers) or len(set(numbers)) != len(numbers):
        raise RuntimeError('Invalid PR association listing')
    matches = []
    for number in numbers:
        pr = api(repo, f'pulls/{number}')
        if not (pr.get('merged') is True and pr.get('base', {}).get('ref') == '26.1.2'
                and pr.get('head', {}).get('sha') == run['head_sha']
                and pr.get('head', {}).get('repo', {}).get('full_name') == repo):
            continue
        merge = pr.get('merge_commit_sha', '')
        if not re.fullmatch('[0-9a-f]{40}', merge):
            continue
        if api(repo, f'git/commits/{merge}')['tree']['sha'] != tree:
            continue
        if api(repo, f'compare/{merge}...26.1.2')['status'] not in {'ahead', 'identical'}:
            continue
        matches.append({'number': number, 'merge_commit': merge})
    if len(matches) != 1:
        raise RuntimeError('Expected exactly one merged same-repository PR with the tested head and tagged tree')
    return matches[0]


def inspect(repo, run_id, tag, source):
    props = dict(line.split('=', 1) for line in (source / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    if tag != 'v' + props['mod_version'] + '-26.1.2N' or props['minecraft_version'] != '26.1.2':
        raise RuntimeError('Tag must match the checked-out 26.1.2 version')
    if git(source, 'status', '--porcelain', '--untracked-files=all', '--ignored=matching'):
        raise RuntimeError('Verified tagged source checkout must be clean')
    run = api(repo, f'actions/runs/{run_id}')
    if not (run['conclusion'] == 'success' and run['status'] == 'completed'
            and run['event'] == 'pull_request' and run['path'] == '.github/workflows/pr-validation.yml'
            and run['head_repository']['full_name'] == repo):
        raise RuntimeError('Expected successful PR validation in this repository')
    jobs_response = api(repo, f'actions/runs/{run_id}/jobs?per_page=100')
    jobs = jobs_response['jobs']
    if jobs_response.get('total_count', len(jobs)) != len(jobs):
        raise RuntimeError('Incomplete validation job listing')
    if len(jobs) != 2 or {j['name'] for j in jobs} != {'build-and-gametest', 'sodium-lighting'} or any(j['conclusion'] != 'success' for j in jobs):
        raise RuntimeError('Exactly the build-and-gametest and sodium-lighting jobs must succeed')
    ref = api(repo, f'git/ref/tags/{tag}')['object']
    while ref['type'] == 'tag':
        ref = api(repo, 'git/tags/' + ref['sha'])['object']
    if ref['type'] != 'commit' or ref['sha'] != git(source, 'rev-parse', 'HEAD'):
        raise RuntimeError('Checkout does not match immutable tag')
    if api(repo, f"compare/{ref['sha']}...26.1.2")['status'] not in {'ahead', 'identical'}:
        raise RuntimeError('Release tag is not on the maintained 26.1.2 branch')
    tree = git(source, 'rev-parse', 'HEAD^{tree}')
    if api(repo, f"git/commits/{run['head_sha']}")['tree']['sha'] != tree:
        raise RuntimeError('PR source differs from tagged tree')
    merged_pr = verify_merged_pr(repo, run, tree)
    artifacts = api(repo, f'actions/runs/{run_id}/artifacts?per_page=100')
    selected = {}
    tested_commits = {}
    for loader, job_name, prefix in [('neoforge', 'build-and-gametest', 'pr-validation'),
                                     ('sodium', 'sodium-lighting', 'sodium-lighting')]:
        producer = next(job for job in jobs if job['name'] == job_name)
        artifact, tested = select_artifact(artifacts, producer, run['run_attempt'],
            re.escape(prefix) + r'-(?P<tested>[0-9a-f]{40})-attempt-(?P<attempt>[1-9][0-9]*)')
        original = next(a for a in artifacts['artifacts'] if a['id'] == artifact['id'])
        digest = original.get('digest', '')
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
            raise RuntimeError('Missing immutable artifact SHA-256 digest')
        artifact['digest'] = digest
        if api(repo, 'git/commits/' + tested)['tree']['sha'] != tree:
            raise RuntimeError('Actual tested merge tree differs from tagged tree')
        selected[loader] = artifact
        tested_commits[loader] = tested
    if len(set(tested_commits.values())) != 1:
        raise RuntimeError('Validation jobs tested different commits')
    tested = tested_commits['neoforge']
    return {'repository': repo, 'run_id': run_id, 'run_attempt': run['run_attempt'],
            'tag': tag, 'commit': ref['sha'], 'tree': tree, 'tested_commit': tested,
            'merged_pr': merged_pr, 'version': props['mod_version'], 'mod_name': props['mod_name'], 'artifacts': selected}


def promote(metadata, artifacts, output, source):
    validate_visual_evidence(metadata, artifacts)
    output.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for loader, suffix in [('neoforge', 'N')]:
        root = artifacts / metadata['artifacts'][loader]['name']
        name = f"{metadata['mod_name']}-{metadata['version']}-26.1.2{suffix}.jar"
        validate(root, loader, name, source=source)
        gui = (root / 'gui-review/logs/latest.log').read_text()
        pixels = json.loads((root / 'gui-review/inventory-pixels.json').read_text())
        if 'SINGULO_GUI_COMPLETE 22' not in gui or 'SINGULO_SEARCH_COMPLETE' not in gui or 'SINGULO_HANDBOOK_SEARCH_COMPLETE' not in gui or 'SINGULO_MACHINE_GUI_COMPLETE 250' not in gui or len(list((root / 'gui-review/screenshots').glob('*.png'))) != 272:
            raise RuntimeError('Missing real GUI/search validation for ' + loader)
        if 'SINGULO_JEI_SYNC_COMPLETE' not in gui:
            raise RuntimeError('Missing bidirectional JEI search validation')
        if len(pixels) != 3 or sum(p['checked_empty_slot_centers'] for p in pixels) != 105:
            raise RuntimeError('Missing inventory PNG verification for ' + loader)
        machine = json.loads((root / 'gui-review/machine-pixels.json').read_text())
        if (machine.get('machine_captures') != 250 or machine.get('headers_without_title_status_overlap') != 80
                or machine.get('generator_font_probes') != 5 or machine.get('jet_forge_inventory_slots') != 700
                or machine.get('supply_port_inventory_slots') != 350 or machine.get('supply_port_tooltips') != 5
                or set(machine.get('languages', [])) != {'ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr'}):
            raise RuntimeError('Missing machine PNG verification for ' + loader)
        target = output / name
        shutil.copyfile(root / 'libs' / name, target)
        hashes[loader] = hashlib.sha256(target.read_bytes()).hexdigest()
        print(f'Promoted {name}: {hashes[loader]}')
    return hashes


def download(metadata, destination):
    for artifact in metadata['artifacts'].values():
        archive = subprocess.check_output(['gh', 'api',
            f"repos/{metadata['repository']}/actions/artifacts/{artifact['id']}/zip"])
        digest = 'sha256:' + hashlib.sha256(archive).hexdigest()
        if digest != artifact['digest']:
            raise RuntimeError('Downloaded artifact SHA-256 mismatch')
        root = destination / artifact['name']
        if root.exists():
            raise RuntimeError('Artifact destination already exists; refusing mixed evidence')
        root.mkdir(parents=True)
        with zipfile.ZipFile(io.BytesIO(archive)) as jar:
            names = jar.namelist()
            if len(names) != len(set(names)):
                raise RuntimeError('Duplicate ZIP entries')
            for item in jar.infolist():
                name = item.filename
                if (name.startswith('/') or '\\' in name or ':' in name or '..' in Path(name).parts
                        or (item.external_attr >> 16) & 0o170000 == 0o120000):
                    raise RuntimeError('Unsafe artifact ZIP path')
            jar.extractall(root)
        manifest = {str(f.relative_to(root)).replace('\\', '/'): hashlib.sha256(f.read_bytes()).hexdigest()
                    for f in root.rglob('*') if f.is_file()}
        (destination / (artifact['name'] + '.receipt.json')).write_text(json.dumps(
            {'artifact': artifact, 'files': manifest}, sort_keys=True))


def verify_download_receipts(metadata, destination):
    for artifact in metadata['artifacts'].values():
        receipt = json.loads((destination / (artifact['name'] + '.receipt.json')).read_text())
        root = destination / artifact['name']
        actual = {str(f.relative_to(root)).replace('\\', '/'): hashlib.sha256(f.read_bytes()).hexdigest()
                  for f in root.rglob('*') if f.is_file()}
        if receipt != {'artifact': artifact, 'files': actual} or not actual:
            raise RuntimeError('Downloaded artifact files changed after digest verification')


def require_markers(root, relative, markers):
    log = (root / relative).read_text(encoding='utf-8', errors='replace')
    for marker in markers:
        if log.count(marker) != 1:
            raise RuntimeError('Missing or duplicate validation marker: ' + marker)
    return log


def validate_pngs(directory, expected):
    from PIL import Image
    files = list(directory.glob('*.png'))
    if len(files) != expected:
        raise RuntimeError('Missing visual PNG evidence: ' + str(directory))
    for path in files:
        with Image.open(path) as image:
            if image.format != 'PNG' or image.width < 1 or image.height < 1:
                raise RuntimeError('Invalid visual evidence PNG')
            image.verify()


def validate_outline(root):
    from PIL import Image
    log = require_markers(root, 'outline-review/logs/latest.log', ['SINGULO_OUTLINE_COMPLETE 4 5'])
    pairs = re.findall(r'SINGULO_OUTLINE_ENTITY phase=(\d+) index=(\d+) redPixels=\d+ darkPixels=\d+', log)
    if len(pairs) != 20 or set(pairs) != {(str(p),str(i)) for p in range(5) for i in range(4)}:
        raise RuntimeError('Missing exact twenty outline entity validations')
    directory = root / 'outline-review/screenshots'
    if {p.name for p in directory.glob('*.png')} != {f'outline-{i}.png' for i in range(5)}:
        raise RuntimeError('Missing exact outline evidence images')
    validate_pngs(directory, 5)
    for path in directory.glob('*.png'):
        with Image.open(path) as image:
            if image.size != (1280, 800):
                raise RuntimeError('Invalid outline evidence dimensions')


# Shared GuiReviewHarness fixtures precede either machine inventory.
GUI_BASE_NAMES = {
    'android-status', 'android-gear', 'android-decor', 'android-log-en-854x480', 'android-log-ja-854x480',
    'console-detail', 'console-detail-scrolled', 'console-order-confirmation', 'console-orders',
    'console-record-crafting', 'console-record-procedure', 'console-record-processing', 'delivery-port',
    'handbook-item-lookup-en-854x480', 'handbook-search-en-1280x800', 'recipe-rack',
    'station', 'station-en-854x480', 'station-ja-854x480', 'station-patrol-en-854x480',
    'station-patrol-ja-854x480', 'station-rules-ja-854x480',
}

def validate_gui_images(directory, machines_per_language):
    from PIL import Image
    base = {name + '.png' for name in GUI_BASE_NAMES}
    machines = {f'machine-{language}-{index:02d}.png' for language in
                ('en_us','ja_jp','ko_kr','zh_cn','zh_tw') for index in range(machines_per_language)}
    if {p.name for p in directory.glob('*.png')} != base | machines:
        raise RuntimeError('GUI fixture image set differs from exact shared and machine inventory')
    validate_pngs(directory, len(base) + len(machines))
    for path in directory.glob('*.png'):
        expected = (854,480) if path.name in machines or '-854x480' in path.name else (1280,800)
        with Image.open(path) as image:
            if image.size != expected:
                raise RuntimeError('Wrong GUI fixture dimensions: ' + path.name)


def validate_rejoin(root):
    """Require all five visits in order; only the terminal stop notification is idempotent."""
    log = (root / 'rayleigh-review/logs/latest.log').read_text(encoding='utf-8', errors='replace')
    events = []
    for line in log.splitlines():
        if 'SINGULO_REJOIN_' not in line:
            continue
        match = re.search(r'SINGULO_REJOIN_(ENTER|TITLE|COMPLETE) ([1-5])$', line)
        if not match or line.count('SINGULO_REJOIN_') != 1:
            raise RuntimeError('Malformed rejoin validation event')
        events.append((match.group(1), int(match.group(2))))
    expected = [(kind, visit) for visit in range(1, 5) for kind in ('ENTER', 'TITLE')] + [('ENTER', 5)]
    if events[:9] != expected or len(events) < 10 or any(event != ('COMPLETE', 5) for event in events[9:]):
        raise RuntimeError('Missing, reordered or repeated rejoin cycle evidence')


def validate_visual_evidence(metadata, artifacts):
    roots = {key: artifacts / value['name'] for key, value in metadata['artifacts'].items()}
    tools = Path(__file__).resolve().parent
    for key, root in roots.items():
        lighting = 'rayleigh-review/logs/lighting.log' if key == 'sodium' else 'rayleigh-review/logs/latest.log'
        log = require_markers(root, lighting, ['SINGULO_RAYLEIGH_COMPLETE 256 4'])
        if key == 'sodium':
            require_markers(root, lighting, ['SINGULO_SODIUM_NOON_VERTEX_COMPLETE 241', 'SINGULO_SODIUM_NOON_EMISSIVE_COMPLETE'])
            if log.count('SINGULO_DH_LIGHTMAP_COMPLETE 256') != 4:
                raise RuntimeError('Missing exact four DH lightmap validations')
            validate_rejoin(root)
        subprocess.run([os.sys.executable, str(tools / 'review/check_rayleigh_png.py'),
                        str(root / 'rayleigh-review/screenshots')], check=True)
        require_markers(root, 'cosmic-review/logs/latest.log', [
            'SINGULO_COSMIC_COMPLETE 18', 'SINGULO_COSMIC_GEOMETRY_COMPLETE 144 outward quads',
            'SINGULO_PIPE_ARROW_GEOMETRY_COMPLETE 24 external-surface cases',
            'SINGULO_JET_GLOW_DEPTH_COMPLETE 15 view-depth/quad cases',
            'SINGULO_COSMIC_SHADER_RELOAD_COMPLETE 2 replaced and released programs'])
        validate_pngs(root / 'cosmic-review/screenshots', 18)
        if len(list((root / 'cosmic-review/screenshots').glob('cosmic-*.png'))) != 18:
            raise RuntimeError('Missing cosmic screenshots')
    root = roots['neoforge']
    validate_outline(root)
    subprocess.run([os.sys.executable, str(tools / 'review/check_gui_png.py'), '--build-dir', str(root)], check=True)
    cosmic_gui = require_markers(root, 'cosmic-gui-review/logs/latest.log', ['SINGULO_MACHINE_GUI_COMPLETE 35'])
    if cosmic_gui.count('SINGULO_COSMIC_GUI_REGIONS ') != 35:
        raise RuntimeError('Missing cosmic GUI region validations')
    if len(list((root / 'cosmic-gui-review/screenshots').glob('machine-*.png'))) != 35:
        raise RuntimeError('Missing cosmic GUI screenshots')
    validate_pngs(root / 'occlusion-review/screenshots', 18)
    validate_pngs(root / 'lensing-review/screenshots', 1)
    validate_gui_images(root / 'cosmic-gui-review/screenshots', 7)
    validate_gui_images(root / 'gui-review/screenshots', 50)
    require_markers(root, 'occlusion-review/logs/latest.log', ['SINGULO_OCCLUSION_COMPLETE 9'])
    require_markers(root, 'lensing-review/logs/latest.log', ['SINGULO_LENSING_MANY_COMPLETE traced=15', 'SINGULO_LENSING_FRAME_TIME holes=15'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('check', 'download', 'promote'))
    p.add_argument('--repo', required=True)
    p.add_argument('--run', type=int, required=True)
    p.add_argument('--tag', required=True)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--metadata', type=Path, default=Path('verified-release.json'))
    p.add_argument('--artifacts', type=Path, default=Path('verified-artifacts'))
    p.add_argument('--output', type=Path, default=Path('release-artifacts'))
    a = p.parse_args()
    if a.run <= 0 or not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+-26\.1\.2N', a.tag):
        raise RuntimeError('Invalid release tag/run')
    metadata = inspect(a.repo, a.run, a.tag, a.source)
    if a.mode in ('download', 'promote'):
        if metadata != json.loads(a.metadata.read_text()):
            raise RuntimeError('Provenance changed during artifact download')
        if a.mode == 'download':
            download(metadata, a.artifacts)
            return
        verify_download_receipts(metadata, a.artifacts)
        hashes = promote(metadata, a.artifacts, a.output, a.source)
        (a.output / 'provenance.json').write_text(json.dumps({**metadata, 'jar_sha256': hashes}, indent=2) + '\n')
    else:
        a.metadata.write_text(json.dumps(metadata, indent=2) + '\n')
        if os.environ.get('GITHUB_OUTPUT'):
            with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
                for loader in ('neoforge', 'sodium'):
                    output.write(f"{loader}_artifact={metadata['artifacts'][loader]['name']}\n")
        print('Verified the NeoForge CI jobs, immutable tag and actual tested source tree')


if __name__ == '__main__':
    main()
