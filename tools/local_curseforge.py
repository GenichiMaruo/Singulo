"""Singulo local CF release: verified CI bytes, permanent Git-ref lock, one POST.

prepare is read-only remotely. upload requires a fresh human all-status inventory,
interactive authorization and an in-memory token. Never retries or clears guards.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import getpass
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request
import uuid
import warnings
import zipfile
import curseforge_guard as guard

REPO = 'GenichiMaruo/Singulo'
PROJECT = 1734662
API = 'https://minecraft.curseforge.com/api'
MAX_JAR = 128 * 1024 * 1024
MAX_ARCHIVE = 1024 * 1024 * 1024


class Stop(RuntimeError):
    """A deliberately sanitized error, safe to show without traceback."""


class Parser(argparse.ArgumentParser):
    def error(self, message):
        # argparse normally echoes unknown values, which could be a misplaced
        # credential. Never reflect user arguments in diagnostics.
        raise Stop('Invalid command/options; use --help. Never put credentials in arguments.')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return (json.dumps(value, sort_keys=True, indent=2) + '\n').encode()


def now():
    return datetime.now(timezone.utc)


def positive(value):
    return type(value) is int and value > 0


def target(tag, channel='auto'):
    match = re.fullmatch(r'v([0-9]+\.[0-9]+\.[0-9]+)(?:-1\.20\.1([FN]))?', tag)
    if not match or channel not in {'auto', 'alpha', 'beta', 'release'}:
        raise Stop('Invalid canonical Singulo release tag or channel')
    version, suffix = match.groups()
    mc = '1.20.1' if suffix else '1.21.1'
    loader = 'forge' if suffix == 'F' else 'neoforge'
    kind = ('beta' if int(version.split('.')[0]) == 0 else 'release') if channel == 'auto' else channel
    name = f'Singulo-{version}-{mc}{suffix or "N"}.jar'
    return {'version': version, 'minecraft': mc, 'loader': loader,
            'java': 17 if suffix else 21, 'filename': name, 'channel': kind}


def gh(endpoint, payload=None, missing=False):
    """Use existing gh authorization; never read, print or extract its secret."""
    args = ['gh', 'api', '--include', f'repos/{REPO}/{endpoint}']
    if payload is not None:
        args += ['--method', 'POST', '--input', '-']
    proc = subprocess.run(args, input=None if payload is None else canonical(payload),
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    chunks = re.split(rb'\r?\n\r?\n', proc.stdout, maxsplit=1)
    status = re.match(rb'HTTP/[\d.]+ (\d{3})', chunks[0])
    if status and status[1] == b'404' and missing:
        return None
    if proc.returncode or not status or not 200 <= int(status[1]) < 300 or len(chunks) != 2:
        raise Stop('GitHub request failed; inspect reservation state before another write')
    try:
        return json.loads(chunks[1])
    except (ValueError, UnicodeError):
        raise Stop('Malformed GitHub response; inspect state') from None


def command(args, cwd=None):
    proc = subprocess.run(args, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    if proc.returncode:
        raise Stop('Local provenance command failed; no upload allowed')
    return proc.stdout


def safe_name(name):
    p = PurePosixPath(name)
    if (not name or p.is_absolute() or '..' in p.parts or ':' in name or '\\' in name
            or any(part.casefold() == '.git' for part in p.parts)):
        raise Stop('Unsafe archive path')
    return str(p).rstrip('/')


def unpack_zip(data, destination):
    """Never execute artifact code; reject traversal, links, ambiguity and bombs."""
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        seen, size = set(), 0
        for entry in archive.infolist():
            key = safe_name(entry.filename).casefold()
            size += entry.file_size
            if (key in seen or size > MAX_ARCHIVE or entry.flag_bits & 1
                    or (entry.external_attr >> 16) & 0o170000 == 0o120000):
                raise Stop('Unsafe or oversized CI archive')
            seen.add(key)
        archive.extractall(destination)


def validator_snapshot(repo, commit, output):
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise Stop('Invalid maintained validator commit')
    data = command(['git', '-C', str(repo), 'archive', commit])
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for member in archive.getmembers():
            safe_name(member.name)
            if not (member.isfile() or member.isdir()):
                raise Stop('Nonregular trusted validator snapshot entry')
        archive.extractall(output, filter='data')


def run_validator(snapshot, args):
    # Isolated Python: only the immutable maintained snapshot can supply imports.
    bootstrap = ('import sys,runpy; from pathlib import Path; p=sys.argv.pop(1); '
                 'sys.path.insert(0,str(Path(p).parent)); runpy.run_path(p,run_name="__main__")')
    script = snapshot / 'tools' / args[0]
    return command([sys.executable, '-I', '-X', 'utf8', '-c', bootstrap, str(script), *args[1:]], snapshot)


def validate_inputs(inputs):
    if set(inputs) != {'tag', 'run', 'source', 'validator_repo', 'channel', 'archive_cache'}:
        raise Stop('Unexpected plan inputs')
    spec = target(inputs['tag'], inputs['channel'])
    if not positive(inputs['run']):
        raise Stop('A successful CI run ID is required')
    for key in ('source', 'validator_repo', 'archive_cache'):
        if not isinstance(inputs[key], str) or not Path(inputs[key]).is_absolute():
            raise Stop('Plan paths must be absolute local paths')
    return spec


def prepare(inputs, bundle):
    """Export current maintained validation code and run its original check/promote."""
    spec = validate_inputs(inputs)
    source = Path(inputs['source']).resolve()
    if bundle.resolve().is_relative_to(source):
        raise Stop('Keep release bundles outside the pristine tagged source checkout')
    bundle.mkdir(exist_ok=False)
    branch = spec['minecraft']
    maintained = gh('git/ref/heads/' + branch)['object']['sha']
    snapshot = bundle / 'validator'
    snapshot.mkdir()
    validator_snapshot(Path(inputs['validator_repo']), maintained, snapshot)
    script = 'verified_release_1211.py' if branch == '1.21.1' else 'verified_release.py'
    metadata = bundle / 'verified.json'
    common = ['--repo', REPO, '--run', str(inputs['run']), '--tag', inputs['tag'],
              '--source', str(source), '--metadata', str(metadata)]
    log = run_validator(snapshot, [script, 'check', *common])
    proof = json.loads(metadata.read_text())
    artifacts = bundle / 'artifacts'
    artifacts.mkdir()
    cache = Path(inputs['archive_cache'])
    cache.mkdir(parents=True, exist_ok=True)
    for item in proof['artifacts'].values():
        info = gh(f"actions/artifacts/{item['id']}")
        if (info['id'] != item['id'] or info['name'] != item['name'] or info['expired']
                or info['workflow_run']['id'] != inputs['run']
                or not positive(info.get('size_in_bytes')) or info['size_in_bytes'] > MAX_ARCHIVE
                or not re.fullmatch('sha256:[0-9a-f]{64}', info.get('digest', ''))):
            raise Stop('CI archive identity/digest unavailable or inconsistent')
        archive = cache / f"{item['id']}.zip"
        if archive.exists():
            if archive.stat().st_size > MAX_ARCHIVE:
                raise Stop('Oversized cached CI archive')
            data = archive.read_bytes()
        else:
            data = command(['gh', 'api', f"repos/{REPO}/actions/artifacts/{item['id']}/zip"])
        if len(data) > MAX_ARCHIVE or 'sha256:' + digest(data) != info['digest']:
            raise Stop('Downloaded CI archive differs from GitHub artifact digest')
        if not archive.exists():
            with archive.open('xb') as stream:
                stream.write(data)
        name = safe_name(item['name'])
        if '/' in name:
            raise Stop('Invalid artifact name')
        destination = artifacts / name
        destination.mkdir()
        unpack_zip(data, destination)
    promoted = bundle / 'promoted'
    promoted.mkdir()
    output = promoted if branch == '1.21.1' else promoted / spec['filename']
    log += run_validator(snapshot, [script, 'promote', *common, '--artifacts', str(artifacts), '--output', str(output)])
    jar = (promoted / spec['filename']).read_bytes()
    if not 0 < len(jar) <= MAX_JAR:
        raise Stop('Unexpected distribution size')
    changelog = (source / f"docs/curseforge/changelog-{spec['version']}.md").read_text(encoding='utf-8')
    if not changelog.strip() or len(changelog.encode()) > 100_000:
        raise Stop('Missing or oversized verified changelog')
    payload = {'displayName': spec['filename'][:-4], 'changelog': changelog, 'changelogType': 'markdown',
               'releaseType': spec['channel'], 'isMarkedForManualRelease': False,
               'relations': {'projects': [{'slug': slug, 'type': 'optionalDependency'} for slug in ('jei', 'mekanism')]}}
    plan = {'schema': 1, 'project_id': PROJECT, 'inputs': inputs, 'target': spec,
            'upload_protocol_ready': guard.workflow_ready(snapshot),
            'validator_commit': maintained, 'provenance': proof, 'payload': payload,
            'jar_sha256': digest(jar), 'jar_size': len(jar)}
    (bundle / 'validation.log').write_bytes(log)
    (bundle / 'plan.json').write_bytes(canonical(plan))
    return plan, jar


def read_inventory(path, filename, sha256, clock=None):
    """Human author-UI attestation; not an undocumented/private API scraper."""
    if path.stat().st_size > 10 * 1024 * 1024:
        raise Stop('Oversized author inventory')
    data = json.loads(path.read_text(encoding='utf-8'))
    fields = {'schema', 'project_id', 'checked_at', 'all_statuses_accessible',
              'all_pages_checked', 'no_other_publishers', 'files'}
    if (set(data) != fields or type(data['schema']) is not int or data['schema'] != 1 or data['project_id'] != PROJECT
            or any(data[k] is not True for k in ('all_statuses_accessible', 'all_pages_checked', 'no_other_publishers'))):
        raise Stop('A complete author-UI inventory including pending/manual files is required')
    try:
        stamp = datetime.fromisoformat(data['checked_at'].replace('Z', '+00:00'))
        if stamp.tzinfo is None:
            raise ValueError()
        age = ((clock or now()) - stamp).total_seconds()
    except (TypeError, ValueError, AttributeError):
        raise Stop('Invalid author inventory timestamp') from None
    if not -30 <= age <= 300 or not isinstance(data['files'], list):
        raise Stop('Author inventory is stale, future-dated or malformed; inspect all states again')
    ids = set()
    for row in data['files']:
        if (not isinstance(row, dict) or set(row) - {'id', 'filename', 'status', 'sha256'}
                or not {'id', 'filename', 'status'} <= set(row) or not positive(row['id'])
                or row['id'] in ids or not isinstance(row['filename'], str)
                or row['status'] not in {'published', 'pending', 'manual_release', 'processing', 'rejected', 'archived'}
                or ('sha256' in row and not re.fullmatch('[0-9a-f]{64}', row['sha256']))):
            raise Stop('Malformed or ambiguous author inventory entry')
        ids.add(row['id'])
        if row['filename'].casefold() == filename.casefold() or row.get('sha256') == sha256:
            raise Stop('Existing or pending file collides; reconcile it, do not upload again')
    return data


@contextmanager
def lock(directory):
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (directory / 'publisher.lock').open('a+b') as stream:
        try:
            if not os.fstat(stream.fileno()).st_size:
                stream.write(b'0')
                stream.flush()
            stream.seek(0)
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise Stop('Another local publisher holds the project lock') from None
        try:
            yield
        finally:
            stream.seek(0)
            if os.name == 'nt':
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


def save(path, value):
    """Durable intent before writes; never truncate the previous receipt in place."""
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    with temporary.open('xb') as stream:
        stream.write(canonical(value))
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)
    if os.name != 'nt':
        fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)


class CF:
    def __init__(self, token):
        if not isinstance(token, str) or not 1 <= len(token) <= 4096 or any(not 33 <= ord(c) <= 126 for c in token):
            raise Stop('Invalid credential format')
        self.token = token

    def request(self, path, data=None, content_type=None):
        # Destination fixed in code. No redirects, auth negotiation or retry loop.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        headers = {'X-Api-Token': self.token, 'User-Agent': 'Singulo-local-release/1'}
        if content_type:
            headers['Content-Type'] = content_type
        req = urllib.request.Request(API + path, data=data, headers=headers)
        try:
            with urllib.request.build_opener(NoRedirect).open(req, timeout=120) as response:
                raw = response.read(1024 * 1024 + 1)
                if len(raw) > 1024 * 1024:
                    raise Stop('Oversized CurseForge response')
                return json.loads(raw)
        except Exception:
            # Never include request headers, response bodies or exceptions in logs.
            raise Stop('CurseForge request failed; do not retry an uncertain upload') from None


def metadata_for(cf, plan):
    versions, types = cf.request('/game/versions'), cf.request('/game/version-types')
    spec = plan['target']
    names = [('minecraft', spec['minecraft']), ('modloader', 'Forge' if spec['loader'] == 'forge' else 'NeoForge'),
             ('java', f"Java {spec['java']}"), ('environment', 'Client'), ('environment', 'Server')]
    ids = []
    for kind, name in names:
        type_ids = {t['id'] for t in types if t['slug'].startswith(kind)}
        matches = [v['id'] for v in versions if v['gameVersionTypeID'] in type_ids and v['name'].casefold() == name.casefold()]
        if len(matches) != 1 or not positive(matches[0]) or matches[0] in ids:
            raise Stop('CurseForge game/loader/Java/environment catalog is ambiguous or incomplete')
        ids += matches
    return dict(plan['payload'], gameVersions=ids)


def multipart(payload, filename, jar):
    boundary = 'singulo-' + uuid.uuid4().hex
    if boundary.encode() in jar:
        raise Stop('Multipart boundary collision')
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
            'Content-Type: application/json\r\n\r\n').encode() + canonical(payload)
    body += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
             'Content-Type: application/java-archive\r\n\r\n').encode() + jar
    return body + f'\r\n--{boundary}--\r\n'.encode(), 'multipart/form-data; boundary=' + boundary


def check_remote_plan(plan, reservation_id):
    """Fail if immutable inputs or the shared guard changed during interaction."""
    proof, tag = plan['provenance'], plan['inputs']['tag']
    ref = gh('git/ref/tags/' + tag)['object']
    for _ in range(20):
        if ref['type'] != 'tag':
            break
        ref = gh('git/tags/' + ref['sha'])['object']
    run = gh(f"actions/runs/{plan['inputs']['run']}")
    branch = gh('git/ref/heads/' + plan['target']['minecraft'])['object']
    try:
        guard.check_owned(gh, tag, proof['commit'], reservation_id)
    except guard.GuardError:
        raise Stop('Owned release or permanent lock changed; stop') from None
    if (ref['type'] != 'commit' or ref['sha'] != proof['commit']
            or branch['sha'] != plan['validator_commit']
            or run['status'] != 'completed' or run['conclusion'] != 'success'
            or run['run_attempt'] != proof['run_attempt']):
        raise Stop('Tag, CI, maintained validator or shared reservation changed; stop')


def upload_verified(plan, jar, inventory, state_dir, confirm, token_prompt, cf_factory=CF):
    """Internal only: CLI rederives this plan through official CI validators first."""
    tag, filename = plan['inputs']['tag'], plan['target']['filename']
    if (plan.get('project_id') != PROJECT or validate_inputs(plan['inputs']) != plan['target']):
        raise Stop('Inconsistent publication target')
    if plan.get('upload_protocol_ready') is not True:
        raise Stop('Upload disabled: maintained Actions has not adopted the reviewed atomic-lock protocol')
    if digest(jar) != plan['jar_sha256'] or len(jar) != plan['jar_size']:
        raise Stop('Bytes to send differ from promoted CI bytes')
    with lock(state_dir):
        receipt_path = state_dir / (tag + '.json')
        if receipt_path.exists():
            raise Stop('Receipt already exists; reconcile it, never automatically resend')
        read_inventory(inventory, filename, plan['jar_sha256'])
        try:
            guard.no_active_publishers(gh)
            existing = guard.releases_for_tag(gh, tag)
        except guard.GuardError:
            raise Stop('Shared publication protocol preflight failed; no upload') from None
        if existing:
            raise Stop('Shared release reservation already exists; inspect before any upload')
        phrase = f'UPLOAD {filename} TO {PROJECT}'
        if confirm(phrase) != phrase:
            raise Stop('Upload not confirmed')
        cf = cf_factory(token_prompt())
        payload = metadata_for(cf, plan)
        body, content_type = multipart(payload, filename, jar)
        read_inventory(inventory, filename, plan['jar_sha256'])
        receipt = {'schema': 1, 'project_id': PROJECT, 'tag': tag, 'filename': filename,
                   'jar_sha256': plan['jar_sha256'], 'plan_sha256': digest(canonical(plan)),
                   'operation': uuid.uuid4().hex, 'created_at': now().isoformat(),
                   'status': 'atomic_lock_intent', 'metadata': payload,
                   'atomic_lock_ref': guard.ref_name(tag)}
        save(receipt_path, receipt)
        try:
            guard.acquire(gh, tag, plan['provenance']['commit'])
            receipt.update(status='atomic_lock_acquired')
            save(receipt_path, receipt)
            reservation = gh('releases', {'tag_name': tag, 'target_commitish': plan['provenance']['commit'],
                'draft': True, 'prerelease': plan['target']['channel'] != 'release',
                'name': 'Singulo ' + plan['target']['version'],
                'body': f"Local CF reservation {receipt['operation']}\nPlan SHA-256: {receipt['plan_sha256']}\n"
                        'Do not delete or retry while outcome is uncertain. No distribution assets.\n'})
            if (not positive(reservation.get('id')) or reservation.get('draft') is not True
                    or reservation.get('tag_name') != tag or reservation.get('assets') != []):
                raise Stop('Unexpected shared reservation response')
            receipt.update(status='reserved', reservation_id=reservation['id'])
            save(receipt_path, receipt)
            check_remote_plan(plan, reservation['id'])
            read_inventory(inventory, filename, plan['jar_sha256'])
            # Once this is durable, every interruption is uncertain, even before
            # urllib sends its first byte. The permanent server ref survives disk loss.
            receipt.update(status='upload_started_outcome_uncertain', request_sha256=digest(body))
            save(receipt_path, receipt)
            result = cf.request(f'/projects/{PROJECT}/upload-file', body, content_type)
            if not isinstance(result, dict) or not positive(result.get('id')):
                raise Stop('Unexpected upload response')
            receipt.update(status='submitted_awaiting_moderation', file_id=result['id'],
                           file_url=f"https://www.curseforge.com/minecraft/mc-mods/singulo/files/{result['id']}")
            save(receipt_path, receipt)
            return receipt
        except BaseException:
            # Leave the last durable intent and shared reservation. No cleanup,
            # retry, exception text, credential or raw server response is stored.
            raise Stop('Stopped with a persistent reservation/receipt; reconcile before any further action') from None


def load_plan(path, expected):
    raw = path.read_bytes()
    if not re.fullmatch('[0-9a-f]{64}', expected) or digest(raw) != expected:
        raise Stop('Plan hash mismatch')
    plan = json.loads(raw)
    if plan.get('schema') != 1 or plan.get('project_id') != PROJECT:
        raise Stop('Unsupported plan')
    validate_inputs(plan['inputs'])
    return plan


def reconcile(receipt_path, inventory):
    # Read-only externally. Absence never becomes permission to POST again.
    receipt = json.loads(receipt_path.read_text())
    if (receipt.get('project_id') != PROJECT or target(receipt['tag'])['filename'] != receipt['filename']
            or not re.fullmatch('[0-9a-f]{64}', receipt.get('jar_sha256', ''))):
        raise Stop('Invalid receipt identity')
    data = read_inventory(inventory, '__reconcile_only__', 'not-a-digest')
    rows = [r for r in data['files'] if r['filename'].casefold() == receipt['filename'].casefold()]
    observation = {'checked_at': data['checked_at'], 'matching_file_ids': [r['id'] for r in rows],
                   'states': [r['status'] for r in rows], 'resend_allowed': False}
    with lock(receipt_path.parent):
        # Keep original receipt immutable; reconciliation is a separate record.
        output = receipt_path.with_name(receipt_path.name + '.' + uuid.uuid4().hex + '.observation.json')
        save(output, observation)
    return observation


def interactive_token():
    if not sys.stdin.isatty():
        raise Stop('Interactive hidden token entry is required; no environment/file/argument token input')
    with warnings.catch_warnings():
        warnings.simplefilter('error', getpass.GetPassWarning)
        return getpass.getpass('CurseForge upload token (hidden, not saved): ')


def main():
    parser = Parser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    p = sub.add_parser('prepare', help='Read-only remote verification; no token required')
    p.add_argument('--tag', required=True)
    p.add_argument('--run', required=True, type=int)
    p.add_argument('--source', required=True, type=Path)
    p.add_argument('--validator-repo', required=True, type=Path)
    p.add_argument('--archive-cache', required=True, type=Path)
    p.add_argument('--bundle', required=True, type=Path)
    p.add_argument('--channel', choices=['auto', 'alpha', 'beta', 'release'], default='auto')
    p = sub.add_parser('upload', help='Explicit interactive submission; never used by prepare/tests')
    p.add_argument('--plan', required=True, type=Path)
    p.add_argument('--plan-sha256', required=True)
    p.add_argument('--inventory', required=True, type=Path)
    p.add_argument('--state-dir', required=True, type=Path)
    p = sub.add_parser('reconcile', help='Record manual all-status observation; never retry')
    p.add_argument('--receipt', required=True, type=Path)
    p.add_argument('--inventory', required=True, type=Path)
    args = parser.parse_args()
    if args.mode == 'prepare':
        inputs = {'tag': args.tag, 'run': args.run, 'channel': args.channel,
                  'source': str(args.source.resolve()), 'validator_repo': str(args.validator_repo.resolve()),
                  'archive_cache': str(args.archive_cache.resolve())}
        plan, _ = prepare(inputs, args.bundle.resolve())
        print(json.dumps({'plan': str(args.bundle.resolve() / 'plan.json'),
                          'plan_sha256': digest(canonical(plan)), 'target': plan['target'],
                          'upload_protocol_ready': plan['upload_protocol_ready'],
                          'jar_sha256': plan['jar_sha256'], 'remote_writes': False}, indent=2))
    elif args.mode == 'upload':
        if not sys.stdin.isatty():
            raise Stop('Interactive operator authorization required')
        plan = load_plan(args.plan, args.plan_sha256)
        with tempfile.TemporaryDirectory(prefix='singulo-verify-') as folder:
            fresh, jar = prepare(plan['inputs'], Path(folder) / 'bundle')
            if fresh != plan:
                raise Stop('Provenance, validator or payload changed since plan; prepare and review a new plan')
            print(json.dumps({'project': PROJECT, 'tag': plan['inputs']['tag'], 'target': plan['target'],
                              'jar_sha256': plan['jar_sha256']}, indent=2))
            result = upload_verified(fresh, jar, args.inventory, args.state_dir,
                lambda phrase: input('Confirm all public/pending/manual files checked; type ' + phrase + ': '), interactive_token)
            print(json.dumps(result, indent=2))
    else:
        print(json.dumps(reconcile(args.receipt, args.inventory), indent=2))


if __name__ == '__main__':
    try:
        main()
    except Stop as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except SystemExit:
        raise
    except BaseException:
        # No traceback/locals or raw transport error can reveal a token.
        print('Stopped. Inspect the receipt and shared reservation; no automatic retry.', file=sys.stderr)
        sys.exit(1)
