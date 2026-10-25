"""Promote the exact artifact from a successful, tree-identical PR validation run."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

GROUPS = json.loads((Path(__file__).resolve().parent / "gametest_groups.json").read_text())


def api(repo, endpoint):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/{endpoint}"], text=True))


def git(source, *args):
    return subprocess.check_output(["git", "-C", str(source), *args], text=True).strip()


def inspect(repo, run_id, tag, source, loader):
    run = api(repo, f"actions/runs/{run_id}")
    if not (run["conclusion"] == "success" and run["status"] == "completed"
            and (run["event"] == "pull_request" or (run["event"] in ("push", "workflow_dispatch") and run["head_branch"] == "1.20.1"))
            and run["path"] == ".github/workflows/pr-validation.yml"
            and run["head_repository"]["full_name"] == repo):
        raise RuntimeError("Source must be a successful PR or 1.20.1 branch validation in this repository")
    jobs = api(repo, f"actions/runs/{run_id}/jobs?per_page=100")["jobs"]
    expected_jobs = {f"build-and-gametest ({variant}, {ns})" for variant in ("forge", "neoforge") for ns in GROUPS} | {f"optional-mekanism ({variant})" for variant in ("forge", "neoforge")} | {f"renderer-lighting ({variant})" for variant in ("forge", "neoforge")}
    if len(jobs) != len(expected_jobs) or {j["name"] for j in jobs} != expected_jobs or any(j["conclusion"] != "success" for j in jobs):
        raise RuntimeError("All required validation jobs must have succeeded")
    ref = api(repo, f"git/ref/tags/{tag}")["object"]
    while ref["type"] == "tag":
        ref = api(repo, f"git/tags/{ref['sha']}")["object"]
    if ref["type"] != "commit" or ref["sha"] != git(source, "rev-parse", "HEAD"):
        raise RuntimeError("Checkout does not match the immutable release tag")
    tree = git(source, "rev-parse", "HEAD^{tree}")
    if api(repo, f"git/commits/{run['head_sha']}")["tree"]["sha"] != tree:
        raise RuntimeError("Reviewed source tree differs from the release tag")
    artifacts = api(repo, f"actions/runs/{run_id}/artifacts?per_page=100")["artifacts"]
    selected = {}
    tested_commits = set()
    for artifact in artifacts:
        match = re.fullmatch(rf"pr-validation-([0-9a-f]{{40}})-{loader}-(singulo(?:_[a-z]+)*)", artifact["name"])
        if match:
            ns = match[2]
            if artifact["expired"] or ns in selected:
                raise RuntimeError("Expired or ambiguous validation artifacts")
            tested_commits.add(match[1])
            selected[ns] = {"id": artifact["id"], "name": artifact["name"]}
    if set(selected) != set(GROUPS) or len(tested_commits) != 1:
        raise RuntimeError("Expected all required namespace artifacts from one tested merge commit")
    tested_commit = tested_commits.pop()
    if api(repo, f"git/commits/{tested_commit}")["tree"]["sha"] != tree:
        raise RuntimeError("The actual CI merge tree differs from the release tag")
    return {"loader": loader, "repository": repo, "run_id": run_id, "run_attempt": run["run_attempt"],
            "tag": tag, "commit": ref["sha"], "tree": tree, "tested_commit": tested_commit,
            "artifacts": selected}


def one(paths, description):
    paths = list(paths)
    if len(paths) != 1:
        raise RuntimeError(f"Expected one {description}, found {len(paths)}")
    return paths[0]


def content_hash(path):
    # ZIP timestamps may differ; the tested classes, resources and manifest must not.
    digest = hashlib.sha256()
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("Duplicate JAR entries")
        for name in sorted(names):
            digest.update(name.encode("utf-8") + b"\0")
            digest.update(hashlib.sha256(jar.read(name)).digest())
    return digest.hexdigest()


def promote(metadata, artifact_dir, output, jar_name):
    jars = {}
    for ns, expected in GROUPS.items():
        root = artifact_dir / metadata["artifacts"][ns]["name"]
        jars[ns] = one(root.rglob(jar_name), f"{ns} production JAR")
        log = one((p for p in root.rglob("latest.log") if "gametest" in p.parts), f"{ns} GameTest log")
        found = re.search(r"All ([1-9][0-9]*) required tests passed", log.read_text())
        if not found or int(found[1]) != expected:
            raise RuntimeError(f"Missing successful {expected}-test completion for {ns}")
    if len({content_hash(jar) for jar in jars.values()}) != 1:
        raise RuntimeError("The tested production JARs have different contents")
    root = artifact_dir / metadata["artifacts"]["singulo"]["name"]
    gui_log = one((p for p in root.rglob("latest.log") if "gui-review" in p.parts), "GUI completion log")
    if "SINGULO_GUI_COMPLETE 22" not in gui_log.read_text() or len(list((gui_log.parents[1] / "screenshots").glob("*.png"))) != 212:
        raise RuntimeError("Missing complete 212-screen GUI validation")
    gui = gui_log.read_text()
    for marker in ("SINGULO_SEARCH_COMPLETE", "SINGULO_JEI_SYNC_COMPLETE", "SINGULO_HANDBOOK_SEARCH_COMPLETE"):
        if marker not in gui:
            raise RuntimeError("Missing GUI interaction validation: " + marker)
    pixels = one(root.rglob("inventory-pixels.json"), "inventory pixel verification")
    pixel_results = json.loads(pixels.read_text())
    if len(pixel_results) != 3 or sum(row["checked_empty_slot_centers"] for row in pixel_results) != 105:
        raise RuntimeError("Missing 105-point inventory verification")
    if "SINGULO_MACHINE_GUI_COMPLETE 190" not in gui:
        raise RuntimeError("Missing machine GUI validation")
    machine = json.loads(one(root.rglob("machine-pixels.json"), "machine pixel verification").read_text())
    if (set(machine["languages"]) != {"ja_jp", "en_us", "zh_cn", "zh_tw", "ko_kr"}
            or len(machine["languages"]) != 5 or machine["machine_captures"] != 190
            or machine["headers_without_title_status_overlap"] != 80 or machine["generator_font_probes"] != 5):
        raise RuntimeError("Incomplete machine GUI pixel verification")
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jars["singulo"], output)
    metadata["jar_sha256"] = hashlib.sha256(output.read_bytes()).hexdigest()
    metadata["jar_content_sha256"] = content_hash(output)
    print(f"Promoted {output.name}: {metadata['jar_sha256']}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["check", "promote"])
    parser.add_argument("--repo", required=True)
    parser.add_argument("--run", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, default=Path("verified-release.json"))
    parser.add_argument("--artifacts", type=Path, default=Path("verified-artifacts"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+-1\.20\.1[FN](?:-r[1-9][0-9]*)?", args.tag) or args.run <= 0:
        raise RuntimeError("Invalid 1.20.1 release tag or validation run")
    properties = dict(line.split("=", 1) for line in (args.source / "gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    suffix = re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+-1\.20\.1([FN])(?:-r[1-9][0-9]*)?", args.tag)[1]
    loader = "forge" if suffix == "F" else "neoforge"
    expected_tag = f"v{properties['mod_version']}-{properties['minecraft_version']}{suffix}"
    if args.tag != expected_tag and not re.fullmatch(re.escape(expected_tag) + r"-r[1-9][0-9]*", args.tag):
        raise RuntimeError("Tag does not match the checked-out version and loader")
    if properties['minecraft_version'] != '1.20.1':
        raise RuntimeError("Expected Minecraft 1.20.1 sources")
    if args.mode == "promote" and (args.output is None or args.output.name != f"{properties['mod_name']}-{properties['mod_version']}-1.20.1{suffix}.jar"):
        raise RuntimeError("Output filename must match the tagged loader and version")
    metadata = inspect(args.repo, args.run, args.tag, args.source, loader)
    if args.mode == "promote":
        if metadata != json.loads(args.metadata.read_text()):
            raise RuntimeError("Validation provenance changed during download")
        if args.output is None:
            raise RuntimeError("An output path is required")
        promote(metadata, args.artifacts, args.output, args.output.name)
    else:
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a") as output:
                output.write(f"pattern=pr-validation-{metadata['tested_commit']}-{loader}-*\n")
        print(f"Verified {sum(GROUPS.values())} tests + Mekanism + GUI at tree {metadata['tree']}")
    args.metadata.write_text(json.dumps(metadata, indent=2) + "\n")


if __name__ == "__main__":
    main()
