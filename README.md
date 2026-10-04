# AGIL Energy

[![CI](https://github.com/aymenayoun/energy/actions/workflows/ci.yml/badge.svg)](https://github.com/aymenayoun/energy/actions/workflows/ci.yaml)
[![codecov](https://codecov.io/github/aymenayoun/energy/graph/badge.svg?token=BC8ACWQFM1)](https://codecov.io/github/aymenayoun/energy)

## Run from Published Docker Images

The full stack is available as pre-built Docker images on GitHub Container Registry. No need to clone the repo or install Java/Node/Python — just Docker.

### 1. Authenticate to GHCR

GHCR images from private repos require authentication. You need a GitHub Personal Access Token (PAT) with `read:packages` scope.

Create one at: https://github.com/settings/tokens/new?scopes=read:packages

Then log in:

```bash
echo YOUR_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

### 2. Pull the images

```bash
docker pull ghcr.io/aymenayoun/energy-backend:latest
docker pull ghcr.io/aymenayoun/energy-frontend:latest
docker pull ghcr.io/aymenayoun/energy-ia:latest
```

### 3. Run with Docker Compose

The simplest path is to use the included `docker-compose.yml` after copying `.env.example` to `.env`:

```bash
git clone https://github.com/aymenayoun/energy.git
cd energy
cp .env.example .env
# Edit .env with your values (especially MAIL_* and JWT_SECRET)
docker compose up -d
```

The application will be available at:
- **Frontend** — http://localhost:4200
- **Backend API** — http://localhost:8082
- **IA Microservice** — http://localhost:5000
- **MySQL** — localhost:3307

### Image tags

Each image is published with two tags:
- `latest` — most recent build from `main`
- `sha-<short-commit>` — pinned to a specific commit, useful for reproducible deployments