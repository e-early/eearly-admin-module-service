# Vendored dependencies

This folder is a flat-file Maven repository, committed into this repo so the project can be
built and run **without VPN/Nexus access to `nexus.result.si`**. `pom.xml` points Maven at it
(`<repositories><repository><id>vendored-libs</id>...`) instead of the internal
`result-lib` Nexus repository.

## What's in here

Compiled `.jar`/`.pom` files (with matching `.sha1` checksums) for internal Result libraries this
project depends on, copied as-is from an internal build:

| Artifact | Version | Role |
|---|---|---|
| `si.result.lib:spring-boot-service` | `1.1.1-SNAPSHOT` | this project's Maven **parent POM** (pom-only, no jar) |
| `si.result.lib:rest-filter` | `1.4.0` | `?filter=` REST query parsing → JPA `Specification` |
| `si.result.lib:spring-boot-bricks` | `1.2.0` | base `Facade`/exception/`ResponseDTO`/JWT classes |
| `si.result.lib:spring-boot-logger` | `1.0.0` | structured/MDC request-response logging |
| `si.result.lib:logger` | `1.0.0` | lower-level MDC appenders (`spring-boot-logger`'s own dependency) |
| `si.result.lib:result-logging-lib` | `1.0.0` | Maven parent POM (pom-only, no jar) of both `logger` and `spring-boot-logger` |

## What this is — and isn't

- **This is a stopgap to unblock building this repo, not an open-sourcing of those libraries.**
  They're shared across other internal products too (not just eEarly), and whether/how to
  actually open-source them is a separate, still-open decision.
- **No license is attached to any of these artifacts.** They are not published anywhere, carry no
  `LICENSE`/`NOTICE` file, and have no `<licenses>` entry in their POMs — they remain
  all-rights-reserved. Vendoring the compiled jars here makes this project buildable; it does not
  change that legal status.
- **No source is included** — only compiled bytecode. If you need to read, audit, or modify what
  these libraries actually do, that's not possible from this folder alone.
- If you *do* have `nexus.result.si` access, prefer reverting `pom.xml`'s `<repositories>` block
  to point at the real internal repository instead of this folder — it will get you current
  (non-frozen) versions and, for `spring-boot-bricks`/`rest-filter`, sources on demand.

## Updating

There's no build step for this folder — to bump a version, replace the relevant
`<groupId-path>/<artifactId>/<version>/` directory with the new jar+pom+`.sha1`, and update
`libs-repo/README.md`'s table above to match.
