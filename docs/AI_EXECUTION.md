# AI-assisted execution and review record

This is a truthful summary of the current AI-assisted session, not a verbatim external Copilot/Claude export. The user supplied the assignment and requested Java/Spring Boot after the assistant initially proposed Python. No subagents were used. The candidate has not yet performed or signed off an independent human code review. The prompts below express the task specifications used to guide implementation; they are reproducible prompt templates, not fabricated quotations of messages sent to a second AI tool.

## Task specifications and disposition

| Task | Intent, constraints and acceptance criteria | Output and disposition |
|---|---|---|
| Requirements | Interpret URL shortener assignment; identify undefined analytics/reliability; produce bounded deliverable scope | Accepted prototype scope, assumption table and three scenario write-ups; stakeholder confirmation remains outstanding |
| Stack | Implement in Java/Spring Boot with Maven, JPA and local persistence | User rejected initial Python proposal; Java 21/Spring Boot implementation accepted as the working direction |
| Allocation | Generate 8-char Base62 codes; custom alias must conflict, never overwrite; retry collisions at most five times | Revised initial JPA approach to explicit persist + flush through a repository writer and fresh retry transactions |
| Analytics | Count successful GET resolutions atomically; HEAD and inactive links must not count | Accepted conditional SQL increment; rejected read/increment/save pattern due to lost updates |
| Security | No committed real secret; protect all management paths; bound input and state | Required key at startup, stateless filter, constant-time key comparison, bounded global limiter and body cap |
| Body limit | Enforce 16KiB even when Content-Length is absent | Added readNBytes(16385) and wrapped body; Content-Length-only check rejected as incomplete |
| Brownfield | Add link disabling without deleting analytics or permitting code reuse | Additive V2 migration, enabled response field, DELETE operation and lifecycle regression tests |
| Tests | Exercise real Spring HTTP mappings and actual JPA database behavior; deterministic time; verify races | Spring integration tests, parallel service calls, URL policy and guard unit tests; packaged HTTP checks |
| Build | Compile and execute tests before claiming success | Runtime-only Java installation identified; separate JDK obtained; Maven proxy configured only in temporary tooling, excluded from package |
| Documentation | Explain architecture, decisions, setup, all scenarios, evidence and limitations | Accepted Markdown/OpenAPI artifacts; no invented human sign-off, multi-day timeline or production-readiness certification |

## Example reproducible task prompt

“Implement creation of a shortened URL in Java 21/Spring Boot. Accept an absolute HTTP(S) URL up to 2048 characters, optional case-sensitive alias and optional future UTC expiry. Use a database uniqueness constraint, not a check-before-insert assumption. Generated collisions must retry no more than five times in independent transactions; custom alias conflicts return 409 without changing the existing row. Use constructor injection and tests that force collisions. Explain transaction rollback behavior and do not log destination credentials or API keys.”

A refinement prompt would ask: “Review the insert path for assigned JPA IDs: could save/merge overwrite an existing alias? Prove duplicate protection and concurrent claim behavior with tests.” The final implementation uses persist rather than merge.

Validation refinement: integration checks caught a servletPath-dependent guard bypass under MockMvc; request URI/context-path resolution replaced it. Mockito is explicitly attached as a test JVM agent. All 36 tests pass after these refinements.

## Secure AI boundaries and human ownership

Synthetic example.com targets and disposable test keys only. No account credentials, production data or internal service access were required. Review generated changes as untrusted until compilation, tests and engineering review establish confidence. High-impact release decisions (external exposure, real secrets, production migration, data deletion) remain subject to explicit engineer approval. Automated checks are evidence, not human approval. The final package records validation actually executed in VALIDATION.md and distinguishes deferred production gates.

## Completed follow-up: custom-alias validation regression tests

Recorded November 6, 2026 (America/Chicago). This entry supplements the earlier 36-test baseline above.

### User request and implementation

The user requested improved custom-alias validation while preserving the existing rule of 4-32 letters, digits, underscores or hyphens and existing API behavior. They specifically requested meaningful tests for too-short aliases, invalid characters and case-sensitive aliases, an explanation of the plan before implementation, and execution of `./mvnw clean verify`. They subsequently requested a read-only review of correctness, unintended behavior and remaining gaps.

Inspection found that `LinkService.create()` already enforced `[A-Za-z0-9_-]{4,32}`, with an additional maximum-length constraint in `LinkController.CreateLink`. No production validation change was necessary. The implementation changed only `src/test/java/com/example/shortener/ShortenerIntegrationTest.java`, adding parameterized-test imports and 11 test invocations:

- Four invalid-length cases: empty, one character, three characters and 33 characters. Each asserts HTTP 400 and no stored link.
- Four invalid-character cases: space, dot, slash and an accented letter. Each asserts HTTP 400 and no stored link.
- Two valid boundary cases: four and 32 characters, including letters, digits, underscores and hyphens. Each asserts HTTP 201, preservation of the alias in the returned code and short URL, and a successful redirect to the expected destination.
- One case-sensitivity scenario: `Demo123` and `demo123` are created with separate destinations, each redirects correctly, an uncreated `DEMO123` returns 404, and two rows remain stored.

Application code, schema, dependencies and API behavior were unchanged by this task. The earlier Windows build-tool task had separately fixed null handling in `mvnw.cmd`; that was not part of the alias-test change. This record is an additional documentation-only change.

### Automated validation actually performed

File inspection used PowerShell. Full verification ran through Git Bash on Windows with Microsoft OpenJDK 21.0.13.0.2 and Maven Wrapper-provided Maven 3.9.11.

The first build attempt stopped before compilation because the sandbox resolved Maven's local repository to `C:\.m2\repository` and could not create it. The retry outside the sandbox reached the tests. That run exposed an error in the new test fixture: the intended 32-character valid alias was actually 34 characters, and the application correctly rejected it. The fixture was corrected to 32 characters, then `./mvnw clean verify` was rerun in full.

The final run returned exit code 0 and `BUILD SUCCESS` in 32.547 seconds. Compilation, tests, executable JAR packaging and JaCoCo report generation completed.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ShortenerIntegrationTest | 31 | 0 | 0 | 0 |
| UrlPolicyTest | 12 | 0 | 0 | 0 |
| RequestGuardTest | 3 | 0 | 0 | 0 |
| MigrationTest | 1 | 0 | 0 | 0 |
| **Total** | **47** | **0** | **0** | **0** |

Results were checked in `target/surefire-reports/`; the executable JAR and `target/site/jacoco/index.html` were also verified to exist. No new coverage percentage was calculated. No packaged-server smoke test, deployment or alternative-database validation was performed for this follow-up.

### Assistant review, human review and remaining limitations

At the user's request, the assistant performed a read-only review of the added tests against the implementation and saved test reports. The assistant found no correctness issues in the changes and made no edits during that review. The suite was not rerun during the review itself. This was an AI-assisted review, not independent human review. The user requested the review and this record; the conversation does not establish that the user personally reviewed the code, approved the changes or signed off a release.

Remaining coverage limits identified during review:

- Explicit JSON `customAlias: null` is not tested; omission is covered by the existing automatic-code test.
- Case-sensitive analytics lookup and independent click totals for aliases differing only by case are not directly asserted.
- The new rejection tests assert status and absence of stored rows, not exact validation-error wording or all problem-response fields.
- JSON bodies are assembled by string concatenation. Current fixtures are safe; future quotes, backslashes or control characters should be serialized correctly to avoid testing malformed JSON instead of alias validation.
- Case-sensitive storage and lookup were verified with H2. A different database or collation requires separate verification.

These tests add regression evidence for the existing ASCII alias rule; they do not establish production readiness or constitute human approval.
