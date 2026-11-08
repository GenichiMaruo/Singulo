"""Shared local/Actions protocol 1: permanent, create-only Git-ref upload guard.

Draft releases are audit records, NOT mutexes. Never adopt/delete/update a lock.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = 'GenichiMaruo/Singulo'
PROTOCOL = 1
PREFIX = 'refs/tags/singulo-cf-upload-lock-v1/'
WORKFLOW_DIGESTS = {
    '1.21.1': 'e67f8aa097c46de06d2af5e0979118f1b9d9f246c68bb68873ac60f8fa649f42',
    '1.20.1': '4c6990fa1579c7050a7a14043b5ae547d59672ad72228bfd5d02d3ebcb1009e7',
}
WORKFLOW_COMMAND = 'python3 tools/curseforge_guard.py --repo "$GITHUB_REPOSITORY" --tag "$RELEASE_TAG" --commit "$(git -C release-source rev-parse HEAD)" --run-id "$GITHUB_RUN_ID" --receipt cf-lock-receipt.json'


class GuardError(RuntimeError):
    pass


def ref_name(tag):
    match = re.fullmatch(r'(v[0-9]+\.[0-9]+\.[0-9]+(?:-1\.20\.1[FN])?)(?:-r[1-9][0-9]*)?', tag)
    if not match:
        raise GuardError('Invalid release tag')
    # Revised tags must not evade the lock for the same release filename.
    return PREFIX + match[1]


def releases_for_tag(api, tag):
    """Authorized paginated listing includes drafts; by-tag GET does not."""
    found, seen = [], set()
    for page in range(1, 501):
        rows = api(f'releases?per_page=100&page={page}')
        if not isinstance(rows, list) or len(rows) > 100:
            raise GuardError('Incomplete/malformed authenticated release listing')
        for row in rows:
            if type(row.get('id')) is not int or row['id'] <= 0 or row['id'] in seen or not isinstance(row.get('tag_name'), str):
                raise GuardError('Ambiguous release listing')
            seen.add(row['id'])
            if row['tag_name'] == tag:
                found.append(row)
        if len(rows) < 100:
            return found
    raise GuardError('Release listing pagination bound reached; fail closed')


def acquire(api, tag, commit):
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise GuardError('Invalid release commit')
    ref = ref_name(tag)
    # POST creates a unique Git ref atomically. No check-then-adopt or PATCH.
    result = api('git/refs', {'ref': ref, 'sha': commit})
    obj = result.get('object', {})
    if result.get('ref') != ref or obj.get('type') != 'commit' or obj.get('sha') != commit:
        raise GuardError('Uncertain atomic lock response; inspect ref, never retry')
    return ref


def check_owned(api, tag, commit, release_id):
    if type(release_id) is not int or release_id <= 0:
        raise GuardError('Invalid owned release ID')
    release = api(f'releases/{release_id}')
    ref = ref_name(tag)
    lock = api('git/ref/' + ref.removeprefix('refs/'))
    if (release.get('id') != release_id or release.get('draft') is not True
            or release.get('tag_name') != tag or release.get('assets') != []
            or lock.get('ref') != ref or lock.get('object', {}).get('sha') != commit
            or lock.get('object', {}).get('type') != 'commit'):
        raise GuardError('Owned reservation or permanent lock changed')


def workflow_ready(snapshot):
    helper = snapshot / 'tools/curseforge_guard.py'
    path = snapshot / '.github/workflows/publish-curseforge.yml'
    properties = snapshot / 'gradle.properties'
    if not helper.is_file() or not path.is_file() or not properties.is_file():
        return False
    if hashlib.sha256(helper.read_bytes().replace(b'\r\n', b'\n')).digest() != hashlib.sha256(Path(__file__).read_bytes().replace(b'\r\n', b'\n')).digest():
        return False
    versions = re.findall(r'^minecraft_version=(.*)$', properties.read_text(), re.M)
    if len(versions) != 1 or hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest() != WORKFLOW_DIGESTS.get(versions[0]):
        return False
    text = path.read_text(encoding='utf-8')
    if text.count(WORKFLOW_COMMAND) != 1 or 'gh release create ' not in text or 'uses: Kira-NT/mc-publish@' not in text:
        return False
    return text.index(WORKFLOW_COMMAND) < text.index('gh release create ') < text.index('uses: Kira-NT/mc-publish@')


def no_active_publishers(api, own_run_id=None):
    # In particular, stop for pre-migration queued/running workflows that lack
    # this protocol. Historical unsafe workflow reruns must not be requested.
    for status in ('queued', 'in_progress', 'waiting', 'requested', 'pending'):
        data = api(f'actions/workflows/publish-curseforge.yml/runs?status={status}&per_page=100')
        rows = data.get('workflow_runs') if isinstance(data, dict) else None
        if (not isinstance(rows, list) or len(rows) >= 100
                or any(not isinstance(r, dict) or type(r.get('id')) is not int or r['id'] <= 0
                       or r['id'] != own_run_id for r in rows)):
            raise GuardError('Other/unknown CF workflow active; drain old publishers before proceeding')


def api(endpoint, payload=None):
    args = ['gh', 'api', f'repos/{REPO}/{endpoint}']
    if payload is not None:
        args += ['--method', 'POST', '--input', '-']
    p = subprocess.run(args, input=None if payload is None else json.dumps(payload).encode(),
                       stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    if p.returncode:
        raise GuardError('GitHub lock request failed/uncertain; inspect permanent ref; no retry')
    return json.loads(p.stdout)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', required=True, choices=[REPO])
    p.add_argument('--tag', required=True)
    p.add_argument('--commit', required=True)
    p.add_argument('--receipt', type=Path, required=True)
    p.add_argument('--run-id', type=int, required=True)
    a = p.parse_args()
    if a.run_id <= 0:
        raise GuardError('Invalid Actions run ID')
    ref = ref_name(a.tag)
    no_active_publishers(api, a.run_id)
    if releases_for_tag(api, a.tag):
        raise GuardError('Existing draft/published release; inspect before any upload')
    # Actions' receipt is create-only. The permanent server ref also survives
    # runner/disk loss or failure before artifact retention.
    with a.receipt.open('xb') as stream:
        stream.write((json.dumps({'status': 'atomic_lock_intent', 'ref': ref, 'commit': a.commit}) + '\n').encode())
        stream.flush()
        os.fsync(stream.fileno())
    acquire(api, a.tag, a.commit)
    print('Acquired permanent CF upload lock; never automatically delete or retry')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('CF lock stopped; inspect releases and permanent ref; no retry.', file=sys.stderr)
        sys.exit(1)
