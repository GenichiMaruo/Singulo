"""One-shot recovery for Singulo 0.23.0 / Minecraft 1.21.1 NeoForge only."""
from datetime import datetime, timezone
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

from curseforge_guard import no_active_publishers, releases_for_tag

REPO = "GenichiMaruo/Singulo"
OWNER = "85728541"
BRANCH = "1.21.1"
TAG = "v0.23.0"
COMMIT = "c0175e4e1e523a7bba178302bfa0de8eb4c1f631"
PROJECT = "1734662"
CF_HOST = "minecraft.curseforge.com"
FILENAME = "Singulo-0.23.0-1.21.1N.jar"
JAR_SHA = "3587ced0dfd4ec9cf6c879a25f22994947bef97c7617b5ca0b84530317ce88bb"
NOTES_SHA = "113a80f6f201029ab16d06860f1a2c1c448c2083afe6dafccd7da24925e009b6"
SOURCE_RUN = 45643660201
SOURCE_ARTIFACT = 15036544463
SOURCE_ARTIFACT_DIGEST = "sha256:492af43fe4224dd4d37e6bc440fb8f4e50794bd15bae55ca39f93e08e291a0be"
FAILED_RUN = 45651013778
INTENT_ARTIFACT = 15041125699
LOCK = f"refs/tags/singulo-cf-upload-lock-v1/{TAG}"
RECOVERY = f"refs/tags/singulo-cf-recovery-v1/{PROJECT}/{FILENAME}"
WORKFLOW = "recover-curseforge-0230-1211.yml"
ATTESTATION = "ABSENT IN ALL STATUSES: " + FILENAME
LOG = Path("cf-recovery-receipts")


class Stop(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Stop(message)


def github(endpoint, body=None, *, method="POST"):
    argv = ["gh", "api", f"repos/{REPO}/{endpoint}"]
    if body is not None:
        argv += ["--method", method, "--input", "-"]
    p = subprocess.run(argv, input=None if body is None else json.dumps(body).encode(),
                       capture_output=True, check=False,
                       env={k: v for k, v in os.environ.items() if k != "CF_TOKEN"})
    require(p.returncode == 0, f"GitHub request failed: {endpoint}; no write retry")
    try:
        return json.loads(p.stdout)
    except ValueError:
        raise Stop(f"Uncertain GitHub response: {endpoint}; no write retry") from None


def verify_attestation(env, now=None):
    now = now or datetime.now(timezone.utc)
    require(env.get("GITHUB_REPOSITORY") == REPO
            and env.get("GITHUB_ACTOR_ID") == OWNER
            and env.get("GITHUB_REF") == f"refs/heads/{BRANCH}"
            and env.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and env.get("GITHUB_RUN_ATTEMPT") == "1",
            "Wrong recovery owner, branch, event, or attempt")
    require(env.get("AUTHOR_CHECK_DECLARATION") == ATTESTATION,
            "Authenticated Author Dashboard inventory assertion is missing")
    try:
        checked = datetime.strptime(env.get("AUTHOR_CHECKED_UTC", ""), "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        run_id = int(env["GITHUB_RUN_ID"])
    except (ValueError, KeyError):
        raise Stop("Malformed inventory check time or workflow identity") from None
    require(0 <= (now - checked).total_seconds() <= 1200 and run_id != FAILED_RUN and run_id > 0,
            "Dashboard verification is stale, future, or has wrong workflow ID")
    return run_id, checked.isoformat()


def original_proof(api, intent):
    refs = {r["ref"]: r for r in api("git/matching-refs/tags/singulo-cf-upload-lock-v1/v0.23.0")}
    old_lock = refs.get(LOCK, {}).get("object", {})
    require(old_lock.get("sha") == COMMIT and old_lock.get("type") == "commit",
            "Original immutable publication lock missing or mismatched")
    tag = api(f"git/ref/tags/{TAG}")
    require(tag["object"]["sha"] == COMMIT and tag["object"]["type"] == "commit",
            "Release tag no longer matches approved commit")
    require(api(f"compare/{COMMIT}...{BRANCH}")["status"] in {"ahead", "identical"},
            "Release source is no longer on maintained branch")
    run = api(f"actions/runs/{FAILED_RUN}")
    require((run["id"], run["head_sha"], run["event"], run["path"], run["status"],
             run["conclusion"], run["run_attempt"]) ==
            (FAILED_RUN, COMMIT, "workflow_dispatch", ".github/workflows/publish-curseforge.yml",
             "completed", "failure", 1), "Original failed run invalid")
    jobs = api(f"actions/runs/{FAILED_RUN}/jobs?per_page=100")
    require(jobs.get("total_count") == 1 and len(jobs["jobs"]) == 1 and
            jobs["jobs"][0]["name"] == "publish" and jobs["jobs"][0]["conclusion"] == "failure",
            "Original publisher job not uniquely identified")
    steps = jobs["jobs"][0]["steps"]
    def step(name, outcome):
        return [s["conclusion"] for s in steps if s["name"] == name] == [outcome]
    require(step("Promote the validated production JAR without rebuilding", "success") and
            step("Acquire permanent shared CurseForge upload lock", "success") and
            step("Reserve an asset-free release audit record", "failure") and
            step("Publish NeoForge to CurseForge", "skipped") and
            step("Publish release notes with CurseForge links only", "skipped"),
            "Original CF POST step was not provably skipped")
    items = api(f"actions/runs/{FAILED_RUN}/artifacts?per_page=100")
    audit = [a for a in items["artifacts"] if a["name"] == f"cf-lock-{FAILED_RUN}-1" and not a["expired"]]
    require(len(audit) == 1 and audit[0]["id"] == INTENT_ARTIFACT,
            "Original lock intent artifact missing or mismatched")
    require(intent == {"status": "atomic_lock_intent", "ref": LOCK, "commit": COMMIT},
            "Original lock intent bytes differ")


def source_artifact_proof(api):
    items = api(f"actions/runs/{SOURCE_RUN}/artifacts?per_page=100")["artifacts"]
    exact = [a for a in items if a["id"] == SOURCE_ARTIFACT and not a["expired"]]
    require(len(exact) == 1 and exact[0]["digest"] == SOURCE_ARTIFACT_DIGEST,
            "The original Actions artifact identity/digest does not match")


def exclusive(api, run_id):
    no_active_publishers(api)
    for status in ("queued", "in_progress", "waiting", "requested", "pending"):
        rows = api(f"actions/workflows/{WORKFLOW}/runs?status={status}&per_page=100")
        require(isinstance(rows.get("workflow_runs"), list) and rows["total_count"] == len(rows["workflow_runs"])
                and all(r["id"] == run_id for r in rows["workflow_runs"]),
                "Another/unknown recovery publisher is active")


def get_response(host, path, *, method="GET", body=None, headers=None, limit=2_000_000):
    # Deliberately no redirects or retries; CF token never crosses origins.
    conn = http.client.HTTPSConnection(host, timeout=40)
    try:
        conn.request(method, path, body=body, headers={"User-Agent": "Singulo-verified-release/1", **(headers or {})})
        response = conn.getresponse()
        value = response.read(limit + 1)
        require(len(value) <= limit, "HTTP response exceeded audit size limit")
        return response.status, value
    finally:
        conn.close()


def no_public_copy(request=get_response):
    index = 0
    expected_total = None
    while index < 100:
        status, value = request("www.curseforge.com", f"/api/v1/mods/{PROJECT}/files?pageIndex={index}&pageSize=50")
        require(status == 200, "Public CurseForge inventory unavailable")
        obj = json.loads(value)
        rows = obj["data"]
        pag = obj["pagination"]
        require(isinstance(rows, list) and pag["index"] == index and pag["pageSize"] == 50
                and type(pag["totalCount"]) is int and pag["totalCount"] >= 0 and
                all(type(r["id"]) is int and isinstance(r["fileName"], str) and isinstance(r["displayName"], str)
                    and r["projectId"] == int(PROJECT) for r in rows), "Unverifiable public CF listing schema")
        require(not any(r["fileName"].casefold() == FILENAME.casefold() or
                        r["displayName"].casefold() in {FILENAME.casefold(), FILENAME[:-4].casefold()} for r in rows),
                "Existing matching public CurseForge upload")
        if expected_total is None:
            expected_total = pag["totalCount"]
        require(pag["totalCount"] == expected_total and len(rows) == min(50, max(0, expected_total - index * 50)),
                "Incomplete or changing CurseForge inventory")
        index += 1
        if index * 50 >= expected_total:
            return
    raise Stop("Public listing pagination exceeds bound")


def acquire(api):
    require(not api("git/matching-refs/tags/" + RECOVERY.removeprefix("refs/tags/")),
            "One-shot recovery has already begun; never retry")
    result = api("git/refs", {"ref": RECOVERY, "sha": COMMIT})
    require(result.get("ref") == RECOVERY and result.get("object", {}).get("sha") == COMMIT
            and result.get("object", {}).get("type") == "commit", "Uncertain atomic recovery reservation; never retry")


def write_receipt(name, value, directory=LOG):
    directory.mkdir(exist_ok=True)
    with (directory / name).open("x", encoding="utf-8") as f:
        json.dump(value, f, indent=2)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())


def upload(token, notes, jar, request=get_response):
    boundary = "recover-" + secrets.token_hex(24)
    metadata = {
        "changelog": notes,
        "changelogType": "markdown",
        "displayName": FILENAME[:-4],
        "gameVersionNames": ["1.21.1", "NeoForge", "Client", "Server", "Java 21"],
        "releaseType": "beta",
        "isMarkedForManualRelease": False,
        "relations": {"projects": [{"slug": "jei", "type": "optionalDependency"},
                                  {"slug": "mekanism", "type": "optionalDependency"}]},
    }
    fields = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"metadata\"\r\n\r\n".encode() +
        json.dumps(metadata, ensure_ascii=False).encode("utf-8"),
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{FILENAME}\"\r\n"
        "Content-Type: application/java-archive\r\n\r\n".encode() + jar,
        f"--{boundary}--\r\n".encode(),
    ]
    status, value = request(CF_HOST, f"/api/projects/{PROJECT}/upload-file", method="POST",
                            body=b"\r\n".join(fields),
                            headers={"X-Api-Token": token, "Content-Type": f"multipart/form-data; boundary={boundary}"},
                            limit=32768)
    require(status in {200, 201}, f"CF upload HTTP {status}; reconcile before any further action; never retry")
    result = json.loads(value)
    require(type(result.get("id")) is int and result["id"] > 0, "Uncertain CF upload response; never retry")
    return result["id"]


def validate_inputs(env, *, read_only=False):
    jar = Path("release-artifacts", FILENAME).read_bytes()
    require(hashlib.sha256(jar).hexdigest() == JAR_SHA, "CI promoted JAR hash does not match exact prior artifact")
    p = json.loads(Path("release-artifacts/provenance.json").read_text(encoding="utf-8"))
    require(p.get("repository") == REPO and p.get("run_id") == SOURCE_RUN and p.get("run_attempt") == 1
            and p.get("commit") == COMMIT and p.get("tag") == TAG
            and p.get("artifacts", {}).get("neoforge", {}).get("id") == SOURCE_ARTIFACT
            and p.get("jar_sha256") == {"neoforge": JAR_SHA}, "Unexpected CI provenance")
    raw_notes = Path("release-source/docs/curseforge/changelog-0.23.0.md").read_bytes().replace(b"\r\n", b"\n")
    require(hashlib.sha256(raw_notes).hexdigest() == NOTES_SHA, "Approved spoiler-free CF notes changed")
    if not read_only:
        require(env.get("CF_PROJECT") == PROJECT and bool(env.get("CF_TOKEN")), "Unexpected CF project configuration")
    return raw_notes.decode("utf-8"), jar


def inspect():
    # No credential for CurseForge is needed; this can never reserve or upload.
    original_proof(github, json.loads(Path("original-lock/cf-lock-receipt.json").read_text(encoding="utf-8")))
    source_artifact_proof(github)
    validate_inputs(os.environ, read_only=True)
    no_active_publishers(github)
    require(not github("git/matching-refs/tags/" + RECOVERY.removeprefix("refs/tags/")),
            "Prior one-time recovery reservation exists")
    require(not releases_for_tag(github, TAG), "Existing GitHub release/draft must be inspected")
    no_public_copy()
    print("Read-only: exact artifact and original skipped upload verified; author inventory still requires attestation")


def main():
    env = os.environ
    run, prior_check = verify_attestation(env)
    run_data = github(f"actions/runs/{run}")
    require(run_data["head_branch"] == BRANCH and run_data["path"] == ".github/workflows/" + WORKFLOW
            and run_data["run_attempt"] == 1 and run_data["actor"]["id"] == int(OWNER)
            and run_data["triggering_actor"]["id"] == int(OWNER), "Unexpected recovery workflow identity")
    original_proof(github, json.loads(Path("original-lock/cf-lock-receipt.json").read_text(encoding="utf-8")))
    source_artifact_proof(github)
    notes, jar = validate_inputs(env)
    exclusive(github, run)
    require(not github("git/matching-refs/tags/" + RECOVERY.removeprefix("refs/tags/")),
            "Prior one-time recovery reservation already exists")
    require(not releases_for_tag(github, TAG), "Existing release draft; inspect before recovery")
    no_public_copy()
    write_receipt("preflight.json", {
        "state": "preflight-complete", "recovery_run": run, "approved_owner_id": OWNER,
        "author_dashboard_attested_at": prior_check, "old_failed_run": FAILED_RUN,
        "old_lock": LOCK, "commit": COMMIT, "original_ci_run": SOURCE_RUN,
        "original_ci_artifact": SOURCE_ARTIFACT, "original_ci_artifact_digest": SOURCE_ARTIFACT_DIGEST,
        "project_id": int(PROJECT), "file_name": FILENAME, "jar_sha256": JAR_SHA,
        "recovery_ref": RECOVERY, "retry": False,
    })
    # The original publisher is already locked. A draft alone never grants upload.
    draft = github("releases", {"tag_name": TAG, "target_commitish": COMMIT, "name": "Singulo 0.23.0",
        "body": notes + "\n\nDownload the NeoForge JAR from CurseForge: https://www.curseforge.com/minecraft/mc-mods/singulo/files\n",
        "draft": True, "prerelease": True, "make_latest": "false"})
    release_id = draft.get("id")
    require(type(release_id) is int and release_id > 0 and draft.get("draft") is True
            and draft.get("tag_name") == TAG and draft.get("assets") == [], "Could not verify fresh asset-free draft")
    write_receipt("draft.json", {"release_id": release_id, "tag": TAG, "draft": True})
    acquire(github)
    # Re-read environment and reservations immediately before the irreversible step.
    verify_attestation(env)
    exclusive(github, run)
    for expected in (RECOVERY, LOCK, f"refs/tags/{TAG}"):
        current = github("git/ref/" + expected.removeprefix("refs/"))
        require(current.get("ref") == expected and current.get("object", {}).get("sha") == COMMIT
                and current.get("object", {}).get("type") == "commit",
                "One of the immutable release refs changed before upload")
    drafts = releases_for_tag(github, TAG)
    require(len(drafts) == 1 and drafts[0].get("id") == release_id
            and drafts[0].get("draft") is True and drafts[0].get("assets") == [],
            "Draft reservation no longer uniquely owned and asset-free")
    detail = github(f"releases/{release_id}")
    require(detail.get("id") == release_id and detail.get("draft") is True
            and detail.get("tag_name") == TAG and detail.get("assets") == [],
            "Detailed draft lookup did not confirm the reservation")
    no_public_copy()
    write_receipt("upload-intent.json", {"state": "once-only-post-intent", "recovery_run": run,
        "project_id": int(PROJECT), "file_name": FILENAME, "sha256": JAR_SHA})
    file_id = upload(env["CF_TOKEN"], notes, jar)
    write_receipt("cf-response.json", {"state": "accepted-not-yet-public", "recovery_run": run,
        "project_id": int(PROJECT), "file_id": file_id, "file_name": FILENAME, "sha256": JAR_SHA})
    print(f"CurseForge accepted file {file_id}; general availability must be verified independently")
    result = github(f"releases/{release_id}", {"draft": False, "prerelease": True, "make_latest": "false",
        "body": notes + f"\n\nCurseForge file: https://www.curseforge.com/minecraft/mc-mods/singulo/files/{file_id}\n"}, method="PATCH")
    require(result.get("draft") is False and result.get("tag_name") == TAG and result.get("assets") == [],
            "CF received the file, but the GitHub audit publication must be reconciled")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["--inspect"]:
            inspect()
        else:
            require(not sys.argv[1:], "Unexpected recovery arguments")
            main()
    except Stop as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)
    except Exception:
        # Network, response parsing and unknown errors fail without echoing credentials or response bodies.
        print("Unknown recovery failure; inspect the retained receipts; no automatic retry", file=sys.stderr)
        sys.exit(1)
