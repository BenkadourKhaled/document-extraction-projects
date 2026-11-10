# Security policy and production checklist — V1.1

## Supported version

Security fixes are applied to the current V1.1 code line. V1 is a local-account
product; SSO, MFA and per-organization billing/quota controls are later-version
work.

## Implemented controls

### Authentication and sessions

- Passwords are hashed with BCrypt.
- Account creation, self-service password changes and admin password resets enforce
  a **12–128 character** password length policy.
- JWTs are signed with `JWT_SECRET`, include a token version (`ver`) and expire after
  `JWT_TTL_MINUTES` (default 480 minutes).
- `users.token_version` is incremented on self password change, logout and admin
  password reset. Existing JWT sessions are therefore revoked immediately.
- The frontend clears its token and returns to login after a successful password
  change.
- Login throttling is stored in PostgreSQL and keyed by a SHA-256 digest of the
  normalized email plus the client IP. Defaults: 5 attempts, 15-minute window,
  15-minute block. `X-Forwarded-For` is used behind a trusted proxy.
- Login failures do not reveal whether an email exists.

### Authorization

- Roles: `ADMIN`, `REVIEWER`, `USER`.
- `ADMIN` manages accounts in its organization and sees organization history and
  audit.
- `REVIEWER` sees organization history.
- `USER` is restricted to owned documents.
- An administrator cannot remove their own admin rights.
- Every document, user and audit event is scoped to an organization.

### Input, storage and data lifecycle

- Uploads are limited to 50 MB and 50 pages by default, with MIME validation and
  Tesseract/PDFBox parsing.
- Source files are stored on local disk or in a private MinIO/S3 bucket.
- Source preview requires a valid JWT and is authorized with the same document
  rules as history.
- Retention is configurable (`RETENTION_DAYS`, default 30); an hourly task removes
  expired source files and rows.
- Audit events record actor, action, IP, user agent and document id for sensitive
  operations.

### Optional local AI (Ollama)

- Ollama extraction is disabled by default (`OLLAMA_ENABLED=false`). Enabling it
  is an explicit operator decision.
- The model must run on the same host or a trusted private network. Ollama has no
  authentication by default: never expose port `11434` to the internet or to an
  untrusted network segment.
- The API sends only the already-extracted page text, bounded by
  `OLLAMA_MAX_INPUT_CHARACTERS`, and never the source file, credentials or
  secrets.
- Documents are treated as untrusted data in the prompt, and every returned value
  is re-validated against the stored text and line spans before persistence.
- Prompts, model responses and extracted values are never logged.

### Production safeguards

- Profiles `postgres` and `prod` refuse to start with the development JWT secret,
  the default bootstrap admin password, or the MinIO default secret.
- A localhost CORS origin is logged as a warning in production.
- CORS is restricted to `CORS_ALLOWED_ORIGIN`.
- Prometheus metrics are exposed on `/actuator/prometheus`.
- The supplied nginx TLS profile sets HSTS, `X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy` and a 25 MB request-body limit.

## Production checklist

- [ ] Replace every example secret; use independent random values.
- [ ] Set a 12+ character bootstrap password, then change it after first login.
- [ ] Use dedicated PostgreSQL and MinIO credentials; do not reuse app credentials.
- [ ] Pin `MINIO_IMAGE` by digest after supply-chain review.
- [ ] Terminate TLS and redirect HTTP to HTTPS.
- [ ] Restrict API, PostgreSQL, MinIO and Prometheus ports at the firewall.
- [ ] Keep Ollama on localhost or a trusted network; never expose port `11434`.
- [ ] Set `CORS_ALLOWED_ORIGIN` to the exact public frontend origin.
- [ ] Disable the MinIO console exposure or place it behind an authenticated,
  network-restricted proxy.
- [ ] Set `RETENTION_DAYS` according to the data policy.
- [ ] Schedule encrypted backups outside the host and test a restore.
- [ ] Monitor health, metrics, queue failures, disk usage and failed logins.
- [ ] Review the audit log and retention purge.
- [ ] Run `mvn -B verify`, frontend quality gates and E2E before release.
- [ ] Run the OCR benchmark on a representative, approved corpus before making
  accuracy claims to a customer.

## Known security limitations

- JWTs are stored in browser `localStorage`; an XSS vulnerability could expose a
  session. The frontend uses React escaping and a strict CSP should be added at the
  production proxy where possible.
- Local password authentication only; MFA and SSO are not implemented in V1.
- Login throttling is database-backed for a single API deployment. A distributed
  rate limiter is required for multi-region or horizontally scaled deployments.
- The `elestio/minio` image is a community mirror, not an official MinIO image.
  Pin and verify it, or use an organization-managed S3-compatible service.
- The synthetic OCR benchmark validates the pipeline, not production accuracy on
  noisy, rotated, handwritten or complex documents.
- No external vulnerability-management or penetration test has been performed.

## Reporting a vulnerability

Do not open a public issue containing credentials, customer documents or exploit
details. Send a private report to the security contact defined by the deploying
organization, with reproduction steps and affected version. If no contact is
configured yet, add one before production rollout.
