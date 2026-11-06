# Validation evidence

Executed November 6, 2026 (America/Chicago), in the task workspace. These are local prototype checks, not a production certification.

## Automated verification

A clean Maven `verify` completed successfully with Java 25, including compiler lint (`-Xlint:all,-processing,-serial`, warnings fail the build), packaging, JUnit and JaCoCo. Processing and serial warnings are excluded deliberately; no separate style linter is configured. Mockito is provided as an explicit JVM test agent, avoiding environment-dependent self-attachment.

| Suite | Executed cases | Result |
|---|---:|---|
| URL policy (parameterized) | 12 | Passed |
| Flyway V1 → V2 upgrade | 1 | Passed |
| Spring integration, lifecycle and concurrency | 20 | Passed |
| Request guard | 3 | Passed |
| **Total** | **36** | **0 failures, 0 errors, 0 skipped** |

JaCoCo aggregate: **93.6% line coverage** (131 covered / 140 total), **77.6% branch coverage** (76 / 98), **92.8% instruction coverage** (856 / 922). Coverage shows exercised code, not absence of defects. Uncovered paths include some defensive configuration, storage failure and validation branches. HTML report and raw test summaries are included under `evidence/`.

Important assertions:

- Duplicate alias insertion returns 409 and cannot replace the target.
- Forced generated-code collision recovers in a fresh transaction; retry exhaustion is bounded.
- Eight concurrent alias claimants produce exactly one winner.
- One hundred concurrent service redirect calls produce exactly 100 recorded clicks.
- Expiration equality is expired, returns 410 and adds no click.
- HEAD does not count; disabled GET/HEAD both return 410.
- Repeated DELETE returns 204, preserves analytics and prevents code reuse.
- Missing/wrong API key fails; body limits include a streamed-body case without Content-Length.
- Rate limit rejects the next request and resets after a clock-controlled window.
- V2 preserves an existing V1 row, its destination and its seven historical clicks, and enables it by default.

## Packaged application checks

Started the executable Spring Boot JAR using a generated, temporary API key, an isolated file-based H2 database and localhost port 18081. The optional smoke utility exercised real HTTP creation, redirection, HEAD, analytics, authentication failure, duplicate conflict, expiry modification, disable and missing-link behavior: **passed**.

Stopped the process, restarted it against the same file, and read a previously created link: **passed persistence restart check**.

Started without a key and required a nonzero exit plus the expected key-validation diagnostic: **passed fail-fast configuration check**.

The acceptance harness used for these checks is included as `scripts/packaged_acceptance.py`. Run from the project root after a build. It creates a temporary isolated database, launches/stops local server processes and invokes smoke/load scripts. It requires Python 3 and Java 25; no external Python packages are used. It leaves only the printed result and no test data in the project.

## Small local concurrency sample

`load.py` sent 200 HTTP redirects with 8 workers to one newly created link in the file-backed packaged server:

| Metric | Observed |
|---|---:|
| Requests / recorded clicks | 200 / 200 |
| Errors | 0 |
| Elapsed | 0.625 s |
| Throughput | 319.9 requests/s |
| Median latency | 18.98 ms |
| p95 latency | 42.84 ms |

This is one short localhost sample after the smoke checks, not a sustained benchmark, capacity claim or SLA. No warm-up strategy, controlled hardware, GC analysis, overload test or cross-network latency model was applied. The key correctness observation is no lost counts during this sample. Run `LOAD_REQUESTS=200 python3 scripts/load.py` against a running local instance to reproduce the procedure; timings will vary.

## Findings corrected during validation

1. Initial build tooling had only a Java runtime; a full JDK was supplied for compilation.
2. Maven needed the environment proxy/trust store for downloads; those private settings are excluded from the submission.
3. Mockito self-attachment was unsuitable in this environment; the test JVM now receives an explicit agent.
4. The first integration run found that relying on servletPath skipped the guard with MockMvc's empty servlet path. The guard now derives the application path from request URI and context path; both authentication and oversize-body regression checks pass.

## Deferred gates

No external deployment, Docker run, penetration test, third-party vulnerability-feed scan, production database failover/restore exercise or sustained production load test is claimed. The included CI workflow is a proposed reproducible gate; no remote GitHub Actions run occurred. Before public deployment, complete independent review, dependency/license scanning, TLS and identity integration, abuse handling, backup/restore and production performance testing. See ENGINEERING_SUBMISSION.md for the risk register and release decision boundaries.
