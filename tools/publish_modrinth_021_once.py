"""One-off prereview Modrinth publisher for the four verified Singulo 0.21.0 JARs.

Default: authenticated preflight only. Run only in the repository's Modrinth
secret environment, under its exclusive publication lock. Never rerun an
uncertain submission before reconciling the durable receipt and Modrinth.
This program registers versions, never changes the project or its moderation.
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re

from publish_modrinth import PROJECT, matching_version, multipart, request

PROJECT_ROUTE = '/project/' + PROJECT
VERSION_ROUTE = PROJECT_ROUTE + '/version'
VERSIONS_021 = re.compile(r'^v?0\.21\.0(?:$|[^0-9a-zA-Z])', re.I)
ID = re.compile(r'[0-9a-zA-Z]{8}')
SHA512 = re.compile(r'[0-9a-f]{128}')


@dataclass(frozen=True)
class Build:
    game: str
    loader: str
    sha512: str

    @property
    def number(self):
        return '0.21.0-' + self.game + ('F' if self.loader == 'forge' else 'N')

    @property
    def name(self):
        return 'Singulo-' + self.number + '.jar'


# Digests from the checked CI artifact that was sent to CurseForge, per loader.
BUILDS = (
    Build('1.21.1', 'neoforge', 'c73bba10979ac2e9ff63484b04f1ec966a129b36deee02a764a6f59af53eea590801c036882656a43e4f5173b8146de475683919e25db8abdab896cb1a081330'),
    Build('1.20.1', 'forge', '2ff33935d7a83c87d2ac2557066bb84f667a281c745ac39c4619028b34d8b49a261c776463f4ef911f4519e47a863617dfae5d4172bb9c6767f4df69bdc96b6f'),
    Build('1.20.1', 'neoforge', 'ccc6a76d4f4f0712f3fbfa55d8578adecc21c429193f804aa9a902082998fc7b209da00128c42b59c14ffcca3ea4e5f120a3481e8b4a1b50d34cd69d688457c5'),
    Build('26.1.2', 'neoforge', 'e5366e2ee7ee7fd32f19d5fa25c0af50bde4ce60a35c45932544ed8c48aed4ac582b02ac687aa890550fa07794f7eb49e7c7f39c5b8232f1601c3d7277e8be5d'),
)


@dataclass(frozen=True)
class StagedJar:
    name: str
    data: bytes

    def read_bytes(self):
        return self.data


def payload(build, changelog):
    return {
        'project_id': PROJECT, 'name': build.name.removesuffix('.jar'),
        'version_number': build.number, 'version_type': 'beta',
        'loaders': [build.loader], 'game_versions': [build.game],
        'environment': 'client_and_server', 'changelog': changelog,
        'dependencies': [{'project_id': p, 'dependency_type': 'optional'}
                         for p in ('u6dRKJwZ', 'Ce6I4WUE')],
        'file_parts': ['file'], 'primary_file': 'file', 'status': 'listed',
    }


def stage(artifact_directory):
    # Keep exactly the verified bytes in memory, rather than reopening mutable
    # paths after any subsequent API call.
    staged = {}
    for build in BUILDS:
        data = (artifact_directory / build.name).read_bytes()
        if hashlib.sha512(data).hexdigest() != build.sha512:
            raise RuntimeError(f'CI artifact SHA-512 mismatch: {build.name}')
        staged[build.number] = StagedJar(build.name, data)
    return staged


def assert_anonymously_hidden(route):
    # Existing shared HTTPS client rejects redirects; it reports only HTTP
    # status numbers, never response bodies or the request's Authorization.
    try:
        request(route)
    except RuntimeError as exc:
        if str(exc).startswith('Modrinth HTTP 404;'):
            return
        raise
    raise RuntimeError('Project is not hidden; use the ordinary approved workflow')


def inventory(auth):
    # A successful read on a demonstrably private project proves the endpoint
    # did not silently fall back to anonymous access on an invalid token.
    p = request(PROJECT_ROUTE, headers=auth)
    if (p.get('id'), p.get('slug'), p.get('project_type'), p.get('status')) != (
            PROJECT, 'singulo', 'mod', 'processing'):
        raise RuntimeError('Expected the exact Singulo mod in processing status')
    versions = request(VERSION_ROUTE, headers=auth)
    assert_anonymously_hidden(PROJECT_ROUTE)
    assert_anonymously_hidden(VERSION_ROUTE)
    expected_ids = p.get('versions')
    if not isinstance(expected_ids, list) or not isinstance(versions, list):
        raise RuntimeError('Incomplete project version inventory')
    found_ids = []
    for v in versions:
        if (v['project_id'] != PROJECT or not ID.fullmatch(v['id'])
                or not isinstance(v['status'], str)
                or not isinstance(v['version_number'], str)
                or not isinstance(v['files'], list)):
            raise RuntimeError('Invalid entry in authenticated project inventory')
        for f in v['files']:
            if not isinstance(f['filename'], str) or not SHA512.fullmatch(f['hashes']['sha512']):
                raise RuntimeError('Invalid file entry in authenticated project inventory')
        found_ids.append(v['id'])
    if (len(expected_ids) != len(set(expected_ids))
            or len(found_ids) != len(set(found_ids))
            or set(found_ids) != set(expected_ids)):
        raise RuntimeError('Project and version-list inventories disagree; no submission')
    return versions


def classify(versions, payloads):
    if any(VERSIONS_021.match(v['version_number']) and v['version_number'] not in payloads
           for v in versions):
        raise RuntimeError('Unexpected existing 0.21.0 version number; reconcile manually')
    return {b.number: matching_version(versions, payloads[b.number], b.name, b.sha512) for b in BUILDS}


def record(stream, row):
    print(json.dumps(row), file=stream, flush=True)
    os.fsync(stream.fileno())
    print(json.dumps(row), flush=True)


def run(staged, changelog, receipt_path, execute=False):
    if os.environ.get('MODRINTH_PROJECT_ID') != PROJECT:
        raise RuntimeError('Missing or unexpected Singulo Modrinth project ID')
    token = os.environ.get('MODRINTH_TOKEN')
    if not token:
        raise RuntimeError('Missing Modrinth credential')
    # Separate official read token is supported if already provisioned; absent
    # one, require the existing upload token to be authorized for these reads.
    auth = {'Authorization': os.environ.get('MODRINTH_READ_TOKEN') or token}
    payloads = {b.number: payload(b, changelog) for b in BUILDS}
    matches = classify(inventory(auth), payloads)
    if len([row for row in matches.values() if row]) not in (0, len(BUILDS)):
        raise RuntimeError('Partial 0.21.0 batch; reconcile existing submissions manually')
    if not all(matches.values()):
        games = {row['version'] for row in request('/tag/game_version')}
        loaders = {row['name'] for row in request('/tag/loader')}
        if any(b.game not in games or b.loader not in loaders for b in BUILDS):
            raise RuntimeError('A target is not yet in the Modrinth game/loader catalog')
    # Exclusive creation prevents local accidental overwrite/retry. On Actions,
    # retain this through the protected release path even when the job fails.
    with receipt_path.open('x', encoding='utf-8') as receipt:
        known_ids = {k: row['id'] for k, row in matches.items() if row}
        for b in BUILDS:
            state = {'project': PROJECT, 'version': b.number, 'file': b.name,
                     'sha512': b.sha512, 'public_visibility': 'not_verified'}
            if b.number in known_ids:
                record(receipt, dict(state, state='already_listed', id=known_ids[b.number]))
                continue
            if not execute:
                record(receipt, dict(state, state='dry_run_not_submitted'))
                continue
            now = classify(inventory(auth), payloads)
            if {k: v['id'] for k, v in now.items() if v} != known_ids:
                raise RuntimeError('Version inventory changed during the batch; no further submission')
            data, boundary = multipart(payloads[b.number], staged[b.number])
            record(receipt, dict(state, state='submission_started_outcome_uncertain'))
            result = request('/version', data=data, headers={
                'Authorization': token, 'Content-Type': 'multipart/form-data; boundary=' + boundary,
            })
            if not matching_version([result], payloads[b.number], b.name, b.sha512):
                raise RuntimeError('Unexpected submission response; reconcile before any retry')
            if not ID.fullmatch(result['id']):
                raise RuntimeError('Unexpected submission ID; reconcile before any retry')
            known_ids[b.number] = result['id']
            record(receipt, dict(state, state='accepted_listed', id=result['id']))
        if execute:
            final = classify(inventory(auth), payloads)
            if {k: v['id'] for k, v in final.items() if v} != known_ids:
                raise RuntimeError('Final authenticated readback differs; reconcile manually')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-dir', required=True, type=Path)
    parser.add_argument('--changelog', required=True, type=Path,
                        help='Reviewed 0.21.0 changelog from release provenance')
    parser.add_argument('--receipt', required=True, type=Path)
    parser.add_argument('--execute-0-21-0', action='store_true',
                        help='Send only after the authorized preflight and exclusive release lock')
    args = parser.parse_args()
    if (os.environ.get('GITHUB_ACTIONS') != 'true'
            or os.environ.get('GITHUB_REPOSITORY') != 'GenichiMaruo/Singulo'):
        raise RuntimeError('Only run in the reviewed Singulo GitHub Actions secret environment')
    changelog = args.changelog.read_text(encoding='utf-8').strip()
    if not changelog or len(changelog.encode('utf-8')) > 65_536:
        raise RuntimeError('Missing changelog, or exceeds release metadata size bound')
    run(stage(args.artifact_dir), changelog, args.receipt, args.execute_0_21_0)


if __name__ == '__main__':
    main()
