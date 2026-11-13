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
from collections import Counter
from release_artifact import select_artifact

GROUPS = json.loads((Path(__file__).resolve().parent / "gametest_groups.json").read_text())
ROOT = Path(__file__).resolve().parent.parent


def properties(source):
    return dict(line.split('=', 1) for line in (source / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))


def source_identity(source, tag, loader):
    props = properties(source)
    if loader not in {'forge', 'neoforge'} or props['minecraft_version'] != '1.20.1' or props['mod_name'] != 'Singulo':
        raise RuntimeError('Expected Minecraft 1.20.1 and a supported loader')
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', props['mod_version']):
        raise RuntimeError('Expected semantic release version')
    suffix = 'F' if loader == 'forge' else 'N'
    expected = f"v{props['mod_version']}-1.20.1{suffix}"
    if not re.fullmatch(re.escape(expected) + r'(?:-r[1-9][0-9]*)?', tag):
        raise RuntimeError('Tag does not match the checked-out version and loader')
    return props


def expected_tests(source):
    # Read data and Java declarations from the verified tag, never import its code.
    from check_gametests import inventory, DEFAULT_NAMESPACES, OPTIONAL_NAMESPACES
    entries = inventory(source)
    if {e['namespace'] for e in entries} - set(DEFAULT_NAMESPACES) - OPTIONAL_NAMESPACES:
        raise RuntimeError('Unknown tagged test namespace')
    regular = [e for e in entries if e['namespace'] in DEFAULT_NAMESPACES]
    groups = json.loads((source / 'tools/gametest_groups.json').read_text())
    counts = dict(Counter(e['namespace'] for e in regular))
    if not regular or counts != groups:
        raise RuntimeError('Tagged test groups differ from exact tagged source inventory')
    return [e['name'] for e in regular]


def api(repo, endpoint):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/{endpoint}"], text=True))


def git(source, *args):
    return subprocess.check_output(["git", "-C", str(source), *args], text=True).strip()


def inspect(repo, run_id, tag, source, loader):
    props = source_identity(source, tag, loader)
    if git(source, 'status', '--porcelain', '--untracked-files=all', '--ignored=matching'):
        raise RuntimeError('Verified tagged source checkout must be clean')
    run = api(repo, f"actions/runs/{run_id}")
    if not (run["conclusion"] == "success" and run["status"] == "completed"
            and (run["event"] == "pull_request" or (run["event"] in ("push", "workflow_dispatch") and run["head_branch"] == "1.20.1"))
            and run["path"] == ".github/workflows/pr-validation.yml"
            and run["head_repository"]["full_name"] == repo):
        raise RuntimeError("Source must be a successful PR or 1.20.1 branch validation in this repository")
    jobs = api(repo, f"actions/runs/{run_id}/jobs?per_page=100")["jobs"]
    expected_jobs = {'build-and-gametest (forge)', 'build-and-gametest (neoforge)',
                     'optional-mekanism', 'renderer-lighting'}
    if len(jobs) != len(expected_jobs) or {j["name"] for j in jobs} != expected_jobs or any(j["conclusion"] != "success" for j in jobs):
        raise RuntimeError("All required validation jobs must have succeeded")
    ref = api(repo, f"git/ref/tags/{tag}")["object"]
    while ref["type"] == "tag":
        ref = api(repo, f"git/tags/{ref['sha']}")["object"]
    if ref["type"] != "commit" or ref["sha"] != git(source, "rev-parse", "HEAD"):
        raise RuntimeError("Checkout does not match the immutable release tag")
    if api(repo, f"compare/{ref['sha']}...1.20.1")['status'] not in {'ahead', 'identical'}:
        raise RuntimeError('Release tag is not on the maintained 1.20.1 branch')
    tree = git(source, "rev-parse", "HEAD^{tree}")
    if api(repo, f"git/commits/{run['head_sha']}")["tree"]["sha"] != tree:
        raise RuntimeError("Reviewed source tree differs from the release tag")
    artifacts = api(repo, f"actions/runs/{run_id}/artifacts?per_page=100")
    producer = next(job for job in jobs if job['name'] == f'build-and-gametest ({loader})')
    artifact, tested_commit = select_artifact(
        artifacts, producer, run['run_attempt'],
        rf'pr-validation-(?P<tested>[0-9a-f]{{40}})-{loader}-full(?:-attempt-(?P<attempt>[1-9][0-9]*))?')
    selected = {'full': artifact}
    if api(repo, f"git/commits/{tested_commit}")["tree"]["sha"] != tree:
        raise RuntimeError("The actual CI merge tree differs from the release tag")
    return {"loader": loader, "repository": repo, "run_id": run_id, "run_attempt": run["run_attempt"],
            "version": props['mod_version'], "mod_name": props['mod_name'],
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


def promote(metadata, artifact_dir, output, jar_name, source):
    props = properties(source)
    suffix = 'F' if metadata['loader'] == 'forge' else 'N'
    if metadata['loader'] not in {'forge', 'neoforge'} or props['minecraft_version'] != '1.20.1' or props['mod_name'] != 'Singulo':
        raise RuntimeError('Unsupported publication target')
    if jar_name != f"{props['mod_name']}-{props['mod_version']}-1.20.1{suffix}.jar" or output.name != jar_name:
        raise RuntimeError('Output filename differs from verified tagged version/loader')
    root = artifact_dir / metadata["artifacts"]["full"]["name"]
    jar = one(root.rglob(jar_name), "production JAR")
    log = one((p for p in root.rglob("latest.log") if "gametest" in p.parts), "GameTest log")
    expected = expected_tests(source)
    expected_count = len(expected)
    if re.findall(r"All ([0-9]+) required tests passed", log.read_text()) != [str(expected_count)]:
        raise RuntimeError("Missing successful full-suite completion")
    from check_gametests import validate_report
    report = one(root.rglob("reports/gametest/results.json"), "per-test report")
    validate_report(json.loads(report.read_text()), expected)
    gui_log = one((p for p in root.rglob("latest.log") if "gui-review" in p.parts), "GUI completion log")
    if "SINGULO_GUI_COMPLETE 22" not in gui_log.read_text() or len(list((gui_log.parents[1] / "screenshots").glob("*.png"))) != 272:
        raise RuntimeError("Missing complete 272-screen GUI validation")
    gui = gui_log.read_text()
    for marker in ("SINGULO_SEARCH_COMPLETE", "SINGULO_JEI_SYNC_COMPLETE", "SINGULO_HANDBOOK_SEARCH_COMPLETE"):
        if marker not in gui:
            raise RuntimeError("Missing GUI interaction validation: " + marker)
    pixels = one(root.rglob("inventory-pixels.json"), "inventory pixel verification")
    pixel_results = json.loads(pixels.read_text())
    if len(pixel_results) != 3 or sum(row["checked_empty_slot_centers"] for row in pixel_results) != 105:
        raise RuntimeError("Missing 105-point inventory verification")
    if "SINGULO_MACHINE_GUI_COMPLETE 250" not in gui:
        raise RuntimeError("Missing machine GUI validation")
    machine = json.loads(one(root.rglob("machine-pixels.json"), "machine pixel verification").read_text())
    if (set(machine["languages"]) != {"ja_jp", "en_us", "zh_cn", "zh_tw", "ko_kr"}
            or len(machine["languages"]) != 5 or machine["machine_captures"] != 250
            or machine.get("supply_port_inventory_slots") != 350 or machine.get("supply_port_tooltips") != 5
            or machine["headers_without_title_status_overlap"] != 80 or machine["generator_font_probes"] != 5 or machine.get("jet_forge_inventory_slots") != 700):
        raise RuntimeError("Incomplete machine GUI pixel verification")
    from check_port_1201 import validate_jar
    validate_jar(jar, source=source)
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jar, output)
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
        promote(metadata, args.artifacts, args.output, args.output.name, args.source)
    else:
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a") as output:
                output.write(f"pattern=pr-validation-{metadata['tested_commit']}-{loader}-*\n")
        print(f"Verified {len(expected_tests(args.source))} tests + Mekanism + GUI at tree {metadata['tree']}")
    args.metadata.write_text(json.dumps(metadata, indent=2) + "\n")


if __name__ == "__main__":
    main()
