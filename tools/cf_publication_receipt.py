"""Record non-secret publisher outputs; never retry or infer public approval."""
import hashlib
import json
import os
from pathlib import Path
import re
from urllib.parse import urlparse
from curseforge_guard import ref_name, GuardError


def make_receipt(env, root):
    provenance_path = root / 'release-artifacts/provenance.json'
    provenance = json.loads(provenance_path.read_text()) if provenance_path.exists() else None
    lock_path = root / 'cf-lock-receipt.json'
    lock = json.loads(lock_path.read_text()) if lock_path.exists() else None
    receipt = {'state': 'unknown', 'publisher_outcome': env.get('PUBLISH_OUTCOME', ''),
               'workflow_run': env.get('GITHUB_RUN_ID', ''), 'workflow_attempt': env.get('GITHUB_RUN_ATTEMPT', ''),
               'provenance': provenance, 'provenance_sha256': hashlib.sha256(provenance_path.read_bytes()).hexdigest() if provenance else None,
               'lock': lock, 'automatic_retry': False}
    try:
        project = env.get('CF_PROJECT', '')
        file_id = env.get('CF_VERSION', '')
        url = env.get('CF_URL', '')
        files = json.loads(env.get('CF_FILES') or '[]')
        # Keep only the documented non-secret fields, never the environment wholesale.
        receipt.update(project_id=project, file_id=file_id, url=url,
                       files=[{k:f.get(k) for k in ('id','name','url')} for f in files])
        if env.get('PUBLISH_OUTCOME') != 'success':
            return receipt, False
        if project != '1734662' or not re.fullmatch(r'[1-9][0-9]*', file_id):
            raise ValueError('Invalid accepted project/file identifier')
        parsed = urlparse(url)
        if parsed.scheme != 'https' or parsed.netloc != 'www.curseforge.com' or parsed.query or parsed.fragment or parsed.path not in (
                f'/minecraft/mc-mods/singulo/files/{file_id}', f'/minecraft/mc-mods/1734662/files/{file_id}'):
            raise ValueError('Accepted file URL does not match project/file identifier')
        expected = env.get('EXPECTED_FILE', '')
        # This workflow passes neo_name as the publisher display name and neo_name.jar
        # as the local file. mc-publish files[].name is the API display name, not fileName.
        if not expected.endswith('.jar') or Path(expected).name != expected:
            raise ValueError('Invalid expected source JAR filename')
        expected_display_name = expected[:-4]
        if len(files) != 1 or files[0].get('name') != expected_display_name or str(files[0].get('id')) != file_id:
            raise ValueError('Accepted display name/file identifier mismatch')
        jar = root / 'release-artifacts' / expected
        if not expected or Path(expected).name != expected or not provenance or not lock:
            raise ValueError('Missing immutable publication provenance/lock')
        if (not re.fullmatch(r'[0-9a-f]{40}', provenance.get('commit', ''))
                or lock != {'status': 'atomic_lock_intent', 'ref': ref_name(provenance['tag']),
                            'commit': provenance['commit']}):
            raise ValueError('Permanent lock intent does not match release tag/commit')
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        if digest != provenance['jar_sha256']['neoforge']:
            raise ValueError('Publication JAR hash mismatch')
        receipt.update(state='accepted', display_name=expected_display_name, source_filename=expected,
                       source_jar_sha256=digest, cdn_hash_verification='not_performed',
                       remote_filename_verification='not_performed', public_approval='not_verified')
        return receipt, False
    except (ValueError, TypeError, KeyError, OSError, AttributeError, GuardError) as error:
        receipt['validation_error'] = str(error)
        return receipt, env.get('PUBLISH_OUTCOME') == 'success'


def main():
    try:
        receipt, failed = make_receipt(os.environ, Path('.'))
    except (ValueError, TypeError, KeyError, OSError):
        receipt, failed = {'state':'unknown', 'automatic_retry':False, 'validation_error':'Local provenance/lock could not be read'}, True
    Path('cf-publication-receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
    if failed:
        raise SystemExit('Accepted upload receipt is uncertain; inspect actual CurseForge state. Do not retry.')

if __name__ == '__main__': main()
