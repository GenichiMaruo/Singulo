"""Promote the 1.21.1 NeoForge artifact from the exact tested/tagged source tree (Forge ended with 0.8.1)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from check_release_1211 import validate


def api(repo, endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/{endpoint}'], text=True))


def git(source, *args):
    return subprocess.check_output(['git', '-C', str(source), *args], text=True).strip()


def inspect(repo, run_id, tag, source):
    props = dict(line.split('=', 1) for line in (source / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    if tag != 'v' + props['mod_version'] or props['minecraft_version'] != '1.21.1':
        raise RuntimeError('Tag must match the checked-out 1.21.1 version')
    run = api(repo, f'actions/runs/{run_id}')
    if not (run['conclusion'] == 'success' and run['status'] == 'completed'
            and run['event'] == 'pull_request' and run['path'] == '.github/workflows/pr-validation.yml'
            and run['head_repository']['full_name'] == repo):
        raise RuntimeError('Expected successful PR validation in this repository')
    jobs = api(repo, f'actions/runs/{run_id}/jobs?per_page=100')['jobs']
    if len(jobs) != 2 or {j['name'] for j in jobs} != {'build-and-gametest', 'optional-mekanism'} or any(j['conclusion'] != 'success' for j in jobs):
        raise RuntimeError('The NeoForge/GUI job and real Mekanism validation must succeed')
    ref = api(repo, f'git/ref/tags/{tag}')['object']
    while ref['type'] == 'tag':
        ref = api(repo, 'git/tags/' + ref['sha'])['object']
    if ref['type'] != 'commit' or ref['sha'] != git(source, 'rev-parse', 'HEAD'):
        raise RuntimeError('Checkout does not match immutable tag')
    tree = git(source, 'rev-parse', 'HEAD^{tree}')
    if api(repo, f"git/commits/{run['head_sha']}")['tree']['sha'] != tree:
        raise RuntimeError('PR source differs from tagged tree')
    artifacts = api(repo, f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    selected = {}
    commits = set()
    for a in artifacts:
        m = re.fullmatch(r'pr-validation-([0-9a-f]{40})', a['name'])
        if not m:
            continue
        loader = 'neoforge'
        if a['expired'] or loader in selected:
            raise RuntimeError('Expired or ambiguous artifacts')
        commits.add(m[1])
        selected[loader] = {'id': a['id'], 'name': a['name']}
    if set(selected) != {'neoforge'} or len(commits) != 1:
        raise RuntimeError('Expected the NeoForge artifact from one actual tested commit')
    tested = commits.pop()
    if api(repo, 'git/commits/' + tested)['tree']['sha'] != tree:
        raise RuntimeError('Actual tested merge tree differs from tagged tree')
    return {'repository': repo, 'run_id': run_id, 'run_attempt': run['run_attempt'],
            'tag': tag, 'commit': ref['sha'], 'tree': tree, 'tested_commit': tested,
            'version': props['mod_version'], 'mod_name': props['mod_name'], 'artifacts': selected}


def promote(metadata, artifacts, output):
    output.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for loader, suffix in [('neoforge', 'N')]:
        root = artifacts / metadata['artifacts'][loader]['name']
        name = f"{metadata['mod_name']}-{metadata['version']}-1.21.1{suffix}.jar"
        validate(root, loader, name)
        gui = (root / 'gui-review/logs/latest.log').read_text()
        pixels = json.loads((root / 'gui-review/inventory-pixels.json').read_text())
        if 'SINGULO_GUI_COMPLETE 22' not in gui or 'SINGULO_SEARCH_COMPLETE' not in gui or 'SINGULO_HANDBOOK_SEARCH_COMPLETE' not in gui or len(list((root / 'gui-review/screenshots').glob('*.png'))) != 22:
            raise RuntimeError('Missing real GUI/search validation for ' + loader)
        if 'SINGULO_JEI_SYNC_COMPLETE' not in gui:
            raise RuntimeError('Missing bidirectional JEI search validation')
        if len(pixels) != 3 or sum(p['checked_empty_slot_centers'] for p in pixels) != 105:
            raise RuntimeError('Missing inventory PNG verification for ' + loader)
        target = output / name
        shutil.copyfile(root / 'libs' / name, target)
        hashes[loader] = hashlib.sha256(target.read_bytes()).hexdigest()
        print(f'Promoted {name}: {hashes[loader]}')
    return hashes


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('check', 'promote'))
    p.add_argument('--repo', required=True)
    p.add_argument('--run', type=int, required=True)
    p.add_argument('--tag', required=True)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--metadata', type=Path, default=Path('verified-release.json'))
    p.add_argument('--artifacts', type=Path, default=Path('verified-artifacts'))
    p.add_argument('--output', type=Path, default=Path('release-artifacts'))
    a = p.parse_args()
    if a.run <= 0 or not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', a.tag):
        raise RuntimeError('Invalid release tag/run')
    metadata = inspect(a.repo, a.run, a.tag, a.source)
    if a.mode == 'promote':
        if metadata != json.loads(a.metadata.read_text()):
            raise RuntimeError('Provenance changed during artifact download')
        hashes = promote(metadata, a.artifacts, a.output)
        (a.output / 'provenance.json').write_text(json.dumps({**metadata, 'jar_sha256': hashes}, indent=2) + '\n')
    else:
        a.metadata.write_text(json.dumps(metadata, indent=2) + '\n')
        if os.environ.get('GITHUB_OUTPUT'):
            with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
                for loader in ('neoforge',):
                    output.write(f"{loader}_artifact={metadata['artifacts'][loader]['name']}\n")
        print('Verified the NeoForge CI jobs, immutable tag and actual tested source tree')


if __name__ == '__main__':
    main()
