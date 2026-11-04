"""Publish one promoted CI JAR. Public reads + VERSION_CREATE are sufficient.

Never retry a POST: on an uncertain response inspect Modrinth and the receipt first.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request
import uuid

API = 'https://api.modrinth.com/v2'
PROJECT = '8KO6x1Ol'
HEADERS = {'User-Agent': 'GenichiMaruo/Singulo release workflow (github.com/GenichiMaruo/Singulo)'}


def request(path, data=None, headers=None):
    req = urllib.request.Request(API + path, data=data, headers=HEADERS | (headers or {}))
    # No redirects with the upload credential, and no automatic POST retry.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, hdrs, newurl):
            return None
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=120) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'Modrinth HTTP {error.code}; inspect the release before retrying') from None


def matching_version(versions, payload, filename, digest):
    """Skip only an exact existing release; stop on collisions or changed bytes."""
    matches = []
    for version in versions:
        same_number = version['version_number'] == payload['version_number']
        files = version.get('files', [])
        collision = any(f['filename'] == filename or f.get('hashes', {}).get('sha512') == digest for f in files)
        if not (same_number or collision):
            continue
        valid = (same_number and version['project_id'] == payload['project_id']
                 and version['loaders'] == payload['loaders']
                 and version['game_versions'] == payload['game_versions']
                 and version['version_type'] == payload['version_type']
                 and version.get('status') == 'listed' and len(files) == 1
                 and files[0]['filename'] == filename
                 and files[0].get('primary') is True
                 and files[0].get('hashes', {}).get('sha512') == digest)
        if not valid:
            raise RuntimeError('Existing Modrinth version/file conflicts; inspect manually, do not overwrite')
        matches.append(version)
    if len(matches) > 1:
        raise RuntimeError('Ambiguous duplicate versions')
    return matches[0] if matches else None


def multipart(payload, jar):
    boundary = 'singulo-' + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            'Content-Type: application/json\r\n\r\n').encode() + json.dumps(payload).encode()
    body += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; '
             f'filename="{jar.name}"\r\nContent-Type: application/java-archive\r\n\r\n').encode()
    return body + jar.read_bytes() + f'\r\n--{boundary}--\r\n'.encode(), boundary


def publish(payload, jar, receipt, dry_run=False):
    if payload['project_id'] != PROJECT:
        raise RuntimeError('MODRINTH_PROJECT_ID must identify Singulo (8KO6x1Ol)')
    project = request('/project/' + PROJECT)
    if project['id'] != PROJECT or project['status'] != 'approved' or project['project_type'] != 'mod':
        raise RuntimeError('Singulo must be approved and publicly readable before workflow publication')
    digest = hashlib.sha512(jar.read_bytes()).hexdigest()
    existing = matching_version(request('/project/' + PROJECT + '/version'), payload, jar.name, digest)
    state = {'version_number': payload['version_number'], 'file': jar.name, 'sha512': digest}
    if existing:
        state.update(status='already_exists', id=existing['id'])
    elif dry_run:
        state.update(status='dry_run')
    else:
        token = os.environ.get('MODRINTH_TOKEN')
        if not token:
            raise RuntimeError('Missing MODRINTH_TOKEN environment secret')
        state.update(status='upload_started_outcome_uncertain')
        receipt.write_text(json.dumps(state, indent=2) + '\n')
        data, boundary = multipart(payload, jar)
        result = request('/version', data, {'Authorization': token,
                         'Content-Type': 'multipart/form-data; boundary=' + boundary})
        if not matching_version([result], payload, jar.name, digest):
            raise RuntimeError('Unexpected upload response; inspect Modrinth before retrying')
        state.update(status='uploaded', id=result['id'])
    receipt.write_text(json.dumps(state, indent=2) + '\n')
    print(json.dumps(state))
    if state.get('id'):
        print('https://modrinth.com/mod/singulo/version/' + state['id'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--loader', choices=['forge', 'neoforge'], required=True)
    parser.add_argument('--type', choices=['auto', 'release', 'beta', 'alpha'], default='auto')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    props = dict(line.split('=', 1) for line in (args.source / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    version, minecraft = props['mod_version'], props['minecraft_version']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise RuntimeError('Expected semantic release version')
    if minecraft not in ('1.20.1', '1.21.1') or (minecraft == '1.21.1' and args.loader != 'neoforge'):
        raise RuntimeError('Unsupported Minecraft/loader combination')
    suffix = 'F' if args.loader == 'forge' else 'N'
    number = f'{version}-{minecraft}{suffix}'
    if args.jar.name != f'Singulo-{number}.jar':
        raise RuntimeError('Promoted JAR filename must match source version and loader')
    release_type = args.type if args.type != 'auto' else ('beta' if version.split('.')[0] == '0' else 'release')
    payload = {'project_id': os.environ.get('MODRINTH_PROJECT_ID'), 'name': args.jar.stem,
               'version_number': number, 'version_type': release_type, 'loaders': [args.loader],
               'game_versions': [minecraft], 'environment': 'client_and_server',
               'changelog': (args.source / f'docs/curseforge/changelog-{version}.md').read_text(encoding='utf-8'),
               'dependencies': [{'project_id': project, 'dependency_type': 'optional'} for project in ('u6dRKJwZ', 'Ce6I4WUE')],
               'file_parts': ['file'], 'primary_file': 'file', 'status': 'listed'}
    publish(payload, args.jar, Path('modrinth-receipt.json'), args.dry_run)


if __name__ == '__main__':
    main()
