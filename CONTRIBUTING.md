# Contributing to Kairos

Thanks for taking a look. This project optimizes for **small, explainable changes**: the framework
is a compiler plugin plus a tiny runtime, and every public behaviour has a contract document.

## Development setup

```bash
# JDK 21, Node 22+ (Kotlin/JS), Gradle wrapper included
./gradlew -PframeworkOnly=true publishToMavenLocal   # framework must be in mavenLocal first
./gradlew build
```

Sample apps and the docs site consume the framework as *published coordinates*, so after changing
framework code run `publishToMavenLocal` again (or add `--refresh-dependencies` when a build still
sees the old artifact).

## Before opening a pull request

Run the same gates CI runs:

```bash
./gradlew build apiCheck
node tools/verify.mjs --rebuild
node tools/browser-check.mjs
node tools/style-browser-check.mjs
node tools/runtime-check.mjs
node tools/consumer-check.mjs
node tools/library-check.mjs
node tools/starter-check.mjs
node tools/docs-check.mjs
node tools/a11y-check.mjs
bash tools/e2e-ci.sh
```

A PR is expected to:

- keep every gate green;
- update the relevant contract when it changes public behaviour (`docs/contracts/`, `docs/adr/`);
- regenerate `api/*.api` with `./gradlew apiDump` when a public signature changes on purpose;
- prefer a smaller change with a test over a bigger change without one.

## Code style

- Kotlin official style; comments explain *why*, not *what*.
- Compiler plugin changes need a golden or negative test in `kairos-gradle-plugin`.
- Runtime/browser changes need a unit test or a Playwright check.
- No new dependency without a note in the PR description explaining why the stdlib is not enough.

## Commit messages

`area: short imperative summary`, for example `compiler: reject shorthand/longhand overlaps`.

## Security

Please do not open a public issue for a vulnerability; see [SECURITY.md](SECURITY.md).
