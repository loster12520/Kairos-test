# Security Policy

## Supported versions

The project is pre-1.0; only the latest `0.x` release line receives security fixes.

## Reporting a vulnerability

Open a private report through the repository security advisory, or email the maintainer listed in
the repository profile. Please include:

- affected version (`0.1.0`, commit hash if local);
- a minimal reproduction (Kotlin snippet or generated HTML/CSS);
- impact: what an attacker can do, and in which context (author code vs untrusted input).

Expect an acknowledgement within a few days. Once a fix is ready, the advisory and the release
notes describe the affected versions and the upgrade path.

## Scope

In scope:

- escaping and URL handling in the renderer (`kairos-core`);
- manifest / class-name handling that could produce executable content;
- Gradle plugin or compiler-plugin behaviour that could execute unexpected code;
- the CSRF/CSP recipes documented in `docs/contracts/security.md`.

Out of scope:

- vulnerabilities in sample apps that are intentionally simple (in-memory data, process-wide CSRF token);
- deployment configuration of the hosting platform;
- issues requiring a compromised build machine.

## What Kairos already promises

See [docs/contracts/security.md](docs/contracts/security.md): context-aware escaping, URL scheme
allow-list, explicit raw HTML entry point, CSP nonce support, CSRF token pattern.
