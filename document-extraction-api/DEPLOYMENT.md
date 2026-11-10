# Deployment guide — V1.1

This guide covers the supplied single-host Docker Compose deployment. The API and
frontend are two folders of the same repository; this Compose file lives in
`document-extraction-api/` and builds the sibling `document-extraction-front-end/` frontend folder.

## 1. Prerequisites

- Docker Engine 24+ with Compose v2
- a Linux host with at least 4 GB RAM, 2 CPUs and disk space for PostgreSQL,
  MinIO and backups
- the API repository and the frontend repository checked out side by side, or a
  custom `FRONTEND_CONTEXT`
- TLS certificates for the public hostname (recommended, required outside a
  trusted network)

Expected layout:

```text
/opt/document-extraction/
  document-extraction-api/
  document-extraction-front-end/     # frontend repository
```

## 2. Prepare configuration

```bash
cd document-extraction-api
cp .env.example .env
```

Generate independent secrets instead of reusing example values:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # POSTGRES_PASSWORD
openssl rand -base64 32   # MINIO_SECRET_KEY
openssl rand -base64 24   # BOOTSTRAP_ADMIN_PASSWORD
```

Set at least:

| Variable | Production guidance |
| --- | --- |
| `JWT_SECRET` | random, 32+ bytes, unique per environment |
| `POSTGRES_PASSWORD` | random, 16+ characters |
| `MINIO_SECRET_KEY` | random, 16+ characters; do not reuse the app password |
| `BOOTSTRAP_ADMIN_EMAIL` | real administrator address |
| `BOOTSTRAP_ADMIN_PASSWORD` | 12–128 characters; change after first login |
| `CORS_ALLOWED_ORIGIN` | exact public frontend origin, for example `https://extraction.example.dz` |
| `API_PORT` | host port for the API; keep it private behind the proxy |
| `FRONTEND_PORT` | host port for the frontend nginx container |
| `RETENTION_DAYS` | organization policy; `0` disables automatic purge |
| `OLLAMA_ENABLED` | `false` unless a local model is installed and the network path is trusted |
| `OLLAMA_BASE_URL` | `http://host.docker.internal:11434` from Compose; never a public endpoint |
| `FRONTEND_CONTEXT` | path to the frontend checkout, default `../document-extraction-front-end` |

If `OLLAMA_ENABLED=true`, pull the model first (`ollama pull llama3.2:1b`) and
verify `GET $OLLAMA_BASE_URL/api/tags` from the API container. Keep port `11434`
on localhost or a trusted private network; Ollama has no authentication by
default. When both services run in Docker and Ollama is on the host, start it with
`OLLAMA_HOST=0.0.0.0:11434` and rely on host firewall rules.

The API refuses to start with the development `JWT_SECRET`, the default bootstrap
password, or the MinIO default secret when the active profile is `postgres` or
`prod`.

### MinIO image caveat

The official MinIO images are no longer published on Docker Hub/Quay. The Compose
file defaults to the community mirror `elestio/minio:latest`, which provides the
`minio` server, `mc` and `curl`.

For production:

1. verify the mirror and its contents in your supply-chain review;
2. pin an immutable digest instead of `latest`;
3. store the pinned value in `.env`.

```bash
docker pull elestio/minio:latest
docker image inspect elestio/minio:latest \
  --format '{{index .RepoDigests 0}}'
# Example:
# MINIO_IMAGE=elestio/minio@sha256:<digest>
```

Do not expose the MinIO console publicly. If object storage is external, set
`STORAGE_TYPE=s3`, the endpoint and credentials, and remove the local MinIO
services from the deployment.

## 3. Start the stack

```bash
docker compose config -q
docker compose up --build -d
docker compose ps
docker compose logs -f document-extraction-api
```

The default endpoints are:

| Service | Default URL |
| --- | --- |
| Frontend | `http://localhost:8081` |
| API health | `http://localhost:${API_PORT:-8080}/actuator/health` |
| Swagger UI | `http://localhost:${API_PORT:-8080}/swagger-ui.html` |
| MinIO API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |

If port 8080 is already used locally, set `API_PORT=18080`. The internal Compose
network still uses `document-extraction-api:8080`.

Verify login and OCR without the frontend:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.dz","password":"<password>"}'
```

## 4. TLS

The optional `tls` profile adds an nginx reverse proxy:

```bash
mkdir -p certs
cp /path/to/fullchain.pem certs/server.crt
cp /path/to/privkey.pem certs/server.key
chmod 600 certs/server.key

# In .env:
PROXY_HTTP_PORT=80
PROXY_HTTPS_PORT=443

docker compose --profile tls up -d
```

`deploy/nginx-tls.conf` redirects HTTP to HTTPS, terminates TLS 1.2/1.3, adds
security headers and forwards the client IP in `X-Forwarded-For`. It does not
obtain or renew certificates; use your organization's certificate process or a
reverse proxy such as Caddy, Traefik or an external load balancer.

Restrict the API and MinIO ports at the firewall. Only the TLS proxy ports should
be reachable from untrusted networks. Set `CORS_ALLOWED_ORIGIN` to the public
HTTPS origin.

## 5. Optional monitoring

```bash
docker compose --profile monitoring up -d
```

Prometheus scrapes `document-extraction-api:8080/actuator/prometheus`. The API
exposes JVM, HTTP and application metrics. The Prometheus port is controlled by
`PROMETHEUS_PORT` (default `9090`) and should also be restricted.

Monitor at minimum:

- `/actuator/health` and container restarts;
- queue depth and failed/stuck jobs;
- OCR duration and error rate;
- PostgreSQL and MinIO disk usage;
- backup completion and restore tests.

## 6. Backup

`scripts/backup.sh` stops the API, dumps PostgreSQL with `pg_dump -Fc`, mirrors the
MinIO bucket and restarts the API. Stopping writes prevents a database/object
mismatch.

```bash
cd document-extraction-api
./scripts/backup.sh
# default destination: ./backups
```

Use an absolute or mounted path for production retention:

```bash
BACKUP_DIR=/srv/backups/document-extraction ./scripts/backup.sh
```

Keep backups encrypted, access-controlled and outside the Docker host. Schedule
them with cron or systemd timers, and define a retention policy. A backup is not
validated until it has been restored in a non-production environment.

## 7. Restore

> Restore is destructive. Take a fresh backup first and stop user traffic.

```bash
cd document-extraction-api
./scripts/restore.sh <stamp> [backup-directory]
# Example:
./scripts/restore.sh 20261027T093000Z /srv/backups/document-extraction
```

The script:

1. stops the API;
2. restores PostgreSQL with `pg_restore --clean --if-exists --no-owner`;
3. mirrors the MinIO backup back into the bucket;
4. starts the API.

After restore, verify login, an existing history entry, source-file preview and a
new FR/EN/AR OCR upload. Keep the API stopped if any step fails and investigate
before accepting writes.

## 8. Upgrade

```bash
cd document-extraction-api
./scripts/backup.sh
docker compose pull
docker compose build
docker compose up -d
docker compose ps
```

Flyway applies new migrations at API startup. Read the release notes before
upgrading and test the upgrade against a restored copy of production data.

## 9. Operations notes

- Flyway migrations run automatically; do not edit an applied migration.
- Data volumes are named `postgres-data`, `minio-data` and `prometheus-data`.
  Removing volumes deletes production data.
- The `document-extraction-api` container has a 2 GB memory limit and 2 CPU
  limit in Compose. Adjust them for the document volume and host size.
- source files are retained according to `RETENTION_DAYS`; exports in `.txt` and
  `.json` are generated on demand.
- TIFF preview is a browser fallback: the original file can be downloaded, but
  some browsers cannot render TIFF inline. OCR itself supports TIFF.
- See [SECURITY.md](SECURITY.md) for the production security checklist.
