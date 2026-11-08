"""Select immutable CI output without confusing retained failed rerun artifacts."""
from datetime import datetime
import re


def timestamp(value):
    try:
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        if parsed.utcoffset() is None:
            raise ValueError('missing timezone')
        return parsed
    except (AttributeError, TypeError, ValueError) as error:
        raise RuntimeError('Missing or invalid artifact/job timestamp') from error


def select_artifact(response, job, run_attempt, pattern):
    """Bind one unique artifact name to the effective successful producer job.

    GitHub clones retained successful jobs when another job is rerun, updating
    their run_attempt but retaining their original execution timestamps. Therefore
    the execution interval, not the cloned attempt field, identifies the producer.
    Old unsuffixed names remain supported only when globally unambiguous.
    """
    artifacts = response['artifacts']
    if response.get('total_count') != len(artifacts):
        raise RuntimeError('Incomplete artifact listing; cannot prove name uniqueness')
    if not isinstance(run_attempt, int) or isinstance(run_attempt, bool) or run_attempt < 1:
        raise RuntimeError('Invalid workflow run attempt')
    if job.get('conclusion') != 'success':
        raise RuntimeError('Artifact producer job must succeed')
    start, end = timestamp(job.get('started_at')), timestamp(job.get('completed_at'))
    if start > end:
        raise RuntimeError('Invalid artifact producer job interval')
    selected = []
    for artifact in artifacts:
        match = re.fullmatch(pattern, artifact['name'])
        if not match:
            continue
        attempt = match['attempt']
        if attempt is not None and not 1 <= int(attempt) <= run_attempt:
            raise RuntimeError('Artifact names an invalid or future run attempt')
        created = timestamp(artifact.get('created_at'))
        if not start <= created <= end:
            continue
        if artifact['expired']:
            raise RuntimeError('Expired current validation artifact')
        if sum(a['name'] == artifact['name'] for a in artifacts) != 1:
            raise RuntimeError('Ambiguous artifact name across run attempts')
        selected.append((artifact, match))
    if len(selected) != 1:
        raise RuntimeError('Expected exactly one artifact from the successful producer job')
    artifact, match = selected[0]
    return ({'id': artifact['id'], 'name': artifact['name'],
             'created_at': artifact['created_at'], 'producer_job_id': job['id'],
             'producer_started_at': job['started_at'], 'producer_completed_at': job['completed_at']},
            match['tested'])
