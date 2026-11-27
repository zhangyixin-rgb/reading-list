# Branching model

This repository uses short-lived feature branches merged into `main` through pull
requests. `main` is the only long-lived branch and always builds.

## Branches

| Branch | Purpose | Lifetime |
| --- | --- | --- |
| `main` | Always releasable; every commit builds and passes the tests | permanent |
| `feature/<topic>` | One coherent change: a command, an index, a quality gate | days, then deleted |
| `fix/<topic>` | A defect found after a merge | days, then deleted |
| `docs/<topic>` | README, report and diagrams only | days, then deleted |

Names are lower-case and hyphenated: `feature/cli-argument-parsing`, not
`Feature/A` and not `my-branch`.

## Flow

1. Branch from an up-to-date `main`:
   `git switch main && git pull && git switch -c feature/<topic>`
2. Commit in small steps that each build. A commit message says what changed and
   why, for example `Add author index so list --author does not sort` rather than
   `update`.
3. Push the branch and open a pull request into `main`. The description states
   what the change does, how it was tested, and anything left out on purpose.
4. At least one review comment or self-review note is resolved before merging.
   For this assignment the author reviews their own pull request against the
   checklist below and records the outcome in the PR.
5. Merge with a merge commit (`--no-ff`) so the branch remains visible in the
   history, then delete the branch.

## Pull request checklist

- [ ] `./gradlew build` passes locally from a clean clone
- [ ] New behaviour has a test that fails without the change
- [ ] `./gradlew detekt` reports no findings
- [ ] README or the branch model is updated if the change affects them
- [ ] No build output, data files or credentials are added to the commit

## Commit message convention

```
<imperative summary, max 72 characters>

<why the change was needed, and anything a reviewer should know>
```

The body is optional for trivial commits and expected for anything that changes
behaviour, so that the history can be read as a narrative of the work rather
than a list of file names.
