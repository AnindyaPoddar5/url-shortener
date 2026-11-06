# URL Shortener — Java / Spring Boot

A runnable interview assignment demonstrating engineer-led, AI-assisted delivery of a URL shortener. Java 25, Spring Boot 3.5.16, Spring MVC, Spring Security, Spring Data JPA, Flyway, H2 and Maven. No external database or cloud account is needed.

**Start here:** build and run below; then read [the engineering submission](docs/ENGINEERING_SUBMISSION.md), [AI execution record](docs/AI_EXECUTION.md), [API contract](docs/openapi.yaml), and [validation evidence](docs/VALIDATION.md).

## Run

Prerequisites: **JDK 25** (including `javac`), Maven 3.9+ (or the included Maven wrapper), and network access to Maven Central for the first build. Windows users can use `mvnw.cmd` in place of `./mvnw`.

```bash
./mvnw clean verify
# Generate a private key; keep it outside version control.
export SHORTENER_API_KEY="$(openssl rand -hex 24)"
java -jar target/url-shortener-1.0.0.jar
```

PowerShell:

```powershell
.\mvnw.cmd clean verify
$env:SHORTENER_API_KEY = [guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()
java -jar target/url-shortener-1.0.0.jar
```

The service listens on `127.0.0.1:8080`. A missing/short key deliberately prevents startup. Data is stored in `./data/shortener.mv.db`, relative to the working directory, and survives restarts. Do not run two processes against the same H2 file. Stop with Ctrl+C. To reset locally, stop the server and remove `data/` (this deletes all local links).

A prebuilt executable JAR is supplied under `dist/`; it runs with Java 25 using `java -jar dist/url-shortener-1.0.0.jar` after setting the key. Source builds produce the same application under `target/`.

## Try it

Use the same shell in which you exported `SHORTENER_API_KEY`. Commands below use Bash and curl; on Windows use Git Bash or adapt headers to PowerShell.

```bash
# Create: 201, JSON with shortUrl; aliases are case-sensitive.
curl -i http://localhost:8080/api/v1/links \
  -H "X-API-Key: $SHORTENER_API_KEY" -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/engineering?source=demo","customAlias":"demo2026"}'

# Follow one public redirect: 302; no key required. Omit -L to inspect it.
curl -i http://localhost:8080/s/demo2026

# Inspect analytics: clicks = 1. HEAD does not increment clicks.
curl -s http://localhost:8080/api/v1/links/demo2026 -H "X-API-Key: $SHORTENER_API_KEY"

# Set a future expiry (replace the date if running after this example).
curl -i -X PATCH http://localhost:8080/api/v1/links/demo2026/expiration \
  -H "X-API-Key: $SHORTENER_API_KEY" -H 'Content-Type: application/json' \
  -d '{"expiresAt":"2099-01-01T00:00:00Z"}'

# Remove expiry: null (and an omitted expiresAt in {}) means no expiration.
curl -i -X PATCH http://localhost:8080/api/v1/links/demo2026/expiration \
  -H "X-API-Key: $SHORTENER_API_KEY" -H 'Content-Type: application/json' \
  -d '{"expiresAt":null}'

# Disable: 204, repeatable. Later redirects return 410; analytics remain.
curl -i -X DELETE http://localhost:8080/api/v1/links/demo2026 -H "X-API-Key: $SHORTENER_API_KEY"
curl -i http://localhost:8080/s/demo2026
curl -s http://localhost:8080/actuator/health
```

`scripts/smoke.py` automates this flow without third-party Python packages, using an isolated random alias. With the server running and the key exported: `python3 scripts/smoke.py`. Python is only used by this optional verification utility; the application is entirely Java.

## Configuration

| Variable | Default / meaning |
|---|---|
| `SHORTENER_API_KEY` | Required, minimum 24 characters; protects all `/api/**` operations |
| `BASE_URL` | `http://localhost:8080`; public HTTP(S) origin used in returned short URLs |
| `PORT` | `8080` |
| `BIND_ADDRESS` | `127.0.0.1`; use `0.0.0.0` only when needed behind trusted ingress |
| `DB_URL` | `jdbc:h2:file:./data/shortener;DB_CLOSE_ON_EXIT=FALSE` |
| `DB_USER`, `DB_PASSWORD` | `sa`, empty; local demo only |
| `REQUESTS_PER_MINUTE` | `6000`; shared fixed-window budget for API and redirect traffic in one process |

`BASE_URL` must match the public origin, including a nondefault port. Incoming Host and forwarding headers do not determine generated links. See the security limitations before exposing the service publicly.

## Build outputs and verification

`./mvnw verify` runs compiler lint with warnings treated as errors, compiles, executes tests, packages the executable JAR and generates a JaCoCo report at `target/site/jacoco/index.html`. Surefire test results are in `target/surefire-reports/`. Test configuration uses an isolated in-memory H2 database and a synthetic test key.

The source includes two additive Flyway migrations. Hibernate validates the schema; it does not create or update it. `docs/brownfield-change.patch` captures the tracked baseline-to-disable feature diff; the V2 migration is supplied as a separate file. `docs/source-history.txt` records the development milestones. No claim of external human approval is made; review and sign-off remain a handoff step.
