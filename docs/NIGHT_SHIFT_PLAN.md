# Pary Night Shift — Engineering Plan

## Operating mode

Combined development: deliver product improvements while protecting reliability, privacy, and build stability.

## Safety rules

- Never push directly to `main`.
- Work on isolated feature branches and submit pull requests.
- Do not commit API keys, passwords, signing material, personal data, or production credentials.
- Do not rewrite Git history or force-push.
- Do not delete user data or change persistence formats without migration tests.
- Every behavior change must include focused tests where practical.
- Run the relevant Gradle checks before requesting review.
- If a check cannot run in the available environment, report it explicitly; do not claim it passed.
- Keep changes small enough to review and revert.

## Initial repository findings

- Android application using Kotlin, Jetpack Compose, and Gradle.
- Main source areas include data, notifications, UI screens, dialogs, and app entry points.
- Existing unit-test files include `LogicTest.kt` and `PlatonusRouteFallbackTest.kt`.
- Existing CI workflows: `.github/workflows/build.yml`, `secret-scan.yml`, and `purge-history.yml`.
- At audit time, open PR #15 proposes seven-day attendance analytics.
- At audit time, open draft PR #1 proposes a Compose compilation fix. Its base commit is older than the current main tip; re-check whether it is still relevant before acting.
- Do not duplicate work already present in open PRs or newer commits.

## Work queue

### P0 — Establish a trustworthy baseline
1. Inspect the current `main` tree and recent commits.
2. Inspect all open pull requests and their changed files.
3. Review CI workflow triggers, permissions, caching, test execution, and artifact publication.
4. Run unit tests and debug build in CI or a supported Android build environment.
5. Record actual failures separately from unverified assumptions.

### P0 — Privacy and security review
1. Review AndroidManifest permissions and exported components.
2. Review `Secure.kt` and secret/key storage lifecycle.
3. Check logs and error messages for credentials, tokens, student identifiers, and personal data.
4. Review Platonus networking, redirects, TLS assumptions, and credential handling.
5. Add regression tests for any confirmed issue before changing behavior.

### P1 — Data and synchronization reliability
1. Review `Store.kt` persistence and migration behavior.
2. Review `PlatonusSyncWorker.kt` retry/backoff and duplicate execution handling.
3. Verify cancellation, offline behavior, and partial-sync recovery.
4. Add tests for malformed legacy data and failure paths.

### P1 — Core logic and maintainability
1. Map responsibilities in data and UI layers.
2. Identify high-complexity functions using evidence from source.
3. Extract pure business logic only where it improves testability without broad rewrites.
4. Add focused unit tests for scheduling, attendance, grades, and task logic.

### P2 — Student-facing improvements
1. Review existing schedule, study, tasks, focus, and today screens for incomplete workflows.
2. Prioritize improvements that solve a clear student problem.
3. Avoid introducing paid services or mandatory external accounts.
4. Keep accessibility, localization, and offline usability in scope.

### P2 — Release quality
1. Verify debug and release build paths.
2. Verify signing secrets are optional for debug and never exposed in logs.
3. Verify CI artifacts and version-code behavior.
4. Document reproducible build and test commands.

## Definition of done

A task is complete only when:
- the implementation is committed on a non-main branch;
- relevant tests pass, or the missing test capability is clearly reported;
- the diff is reviewed for accidental secrets and unrelated changes;
- the PR describes behavior, risk, and verification;
- no unverified claim of build success is made.

## Nightly report format

- Commit / branch:
- Tasks completed:
- Files changed:
- Tests executed and results:
- Build executed and result:
- Security/privacy observations:
- Blockers and unverified items:
- Next recommended tasks:
