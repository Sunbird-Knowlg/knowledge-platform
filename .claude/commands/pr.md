---
description: Open a GitHub PR against a base branch using the repo PR template
argument-hint: "[base-branch]"
allowed-tools: Bash(git *), Bash(gh *), Bash(cat:*)
---

Open a pull request for **knowledge-platform**. Base branch = `$1` (default **develop** if empty).

## Context

Current branch:
!`git branch --show-current`

Recent commits on this branch:
!`git log --oneline -15`

PR template:
!`cat .github/pull_request_template.md`

## Steps

1. Resolve the base ref: use `${1:-develop}` if it exists locally, else `origin/${1:-develop}` (run `git fetch origin ${1:-develop}` first if needed). Then list the branch's commits and changed files vs that base: `git log <base>..HEAD --oneline` and `git diff --stat <base>...HEAD`. If there are no commits ahead, stop and say so.
2. Draft the PR **title** as a Conventional Commits summary of the branch.
3. Fill the PR template sections from the commits/diff: summary + issue reference, tick the correct **Type of change** box, describe **How Has This Been Tested?**, and complete the checklist honestly.
4. **Confirm the title and body with the user**, then create it:
   `gh pr create --base ${1:-develop} --head <current-branch> --title "<title>" --body "<body>"`
5. Report the PR URL.

Push the branch first if `gh` reports the head branch isn't on the remote (ask before pushing).

## Examples

```
/pr               # open a PR against develop (default)
/pr release-1.0   # open a PR against release-1.0
```
