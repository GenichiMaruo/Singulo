# CurseForge shared upload guard protocol 1

Draft releases are audit records, not mutexes. GitHub permits multiple drafts for
the same pending tag; GET `/releases/tags/{tag}` can omit drafts. Use the authorized,
paginated `/releases` listing for preflight and `/releases/{returned_id}` for an owned draft.

Both the local publisher and the corresponding maintained Actions workflow must
use `tools/curseforge_guard.py` before any CF POST. The create-only reference is:

```
refs/tags/singulo-cf-upload-lock-v1/<canonical-release-tag>
```

It points at the already verified release commit. `POST /git/refs` creates a
unique Git ref atomically. A 422, disconnect, unexpected response or any other
uncertainty stops the caller. The caller never adopts an existing ref, retries
creation, updates it, deletes it or releases it, even after success. Revised tags
for the same output filename normalize to the same guard key.

This namespace is separate from distribution release tags; those tags are never
moved. Creating the guard requires existing Contents write access; this change
does not configure credentials or broaden permissions. If repository rules block
creation, stop for review rather than changing rules. Any future generic tag-push
workflows must exclude this namespace or be reviewed before enabling this protocol.

Actions writes a create-only lock-intent receipt before the request and retains
it with an always-run artifact step. The permanent server ref still blocks another
attempt if a runner crashes or artifact retention fails. Local publication also
records the returned draft ID and checks both the ref and draft before its POST.
No upload retry or cleanup path is added.

## Deployment and limits

Review and merge the helper and workflow change into **each** maintained MC branch.
Drain old queued, waiting and running CF workflows before enabling local uploads.
The helper checks those statuses and stops if another publisher is active; Actions
excludes its own run ID. Do not rerun historical workflows that lack this protocol.
An owner deleting a guard or a manual website upload bypassing it is outside the
coordination guarantee and must be prohibited operationally.

The local CLI exports the current maintained validation checkout as before. It
enables upload only when that checkout contains the identical reviewed helper
and the complete reviewed workflow SHA-256 for that MC branch (normalizing CRLF),
and calls it exactly once before draft creation and mc-publish. Future workflow
changes require reviewing and updating this allowlist; textual presence of a
skipped/disabled lock step alone is not accepted.
Otherwise preparation is read-only and upload fails closed. There is no force flag.
At the time this patch is prepared, neither maintained branch has deployed the
protocol, so the new local path must remain disabled until approval and deployment.

CF pending/manual-release inventory still requires a fresh complete author-UI
attestation; no automatic all-status inventory guarantee is claimed. Any unknown
outcome requires manual reconciliation. The permanent ref and receipts must not
be removed merely because a file has not appeared in the public list.

No lock ref, draft release or CF upload is created by tests or documentation.
Mocks explicitly allow multiple drafts with the same tag; atomic exclusion is
asserted only for Git ref creation, shared by both implementations.

Primary references:

- [Git references API](https://docs.github.com/en/rest/git/refs#create-a-reference)
- [GitHub CLI draft-aware release fetching](https://github.com/cli/cli/blob/trunk/pkg/cmd/release/shared/fetch.go)
- [Multiple draft release semantics](https://github.com/cli/cli/issues/2964)
