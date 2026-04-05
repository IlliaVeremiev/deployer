---
name: deployer-setup
description: >
  Project setup and validation for the deployer CLI. Trigger on: "make deployable",
  "add deploy config", "setup deployment", "fix deploy config", "why won't it deploy",
  "scaffold deploy", "init deploy". Use when creating or debugging deploy configurations.
---

# Deployer Project Setup

## Required File Structure

```
my-project/
├── Dockerfile              ← builds the app image
├── deploy/
│   ├── .deploy             ← project identity (required)
│   ├── docker-compose.deploy.yml  ← stack definition (required)
│   └── .env.production     ← runtime env vars (optional)
```

## .deploy Format

```env
STACK_NAME=my-app           # Portainer stack name (unique per stack)
IMAGE_NAME=ghcr.io/user/my-app:latest   # Full registry path with tag
APP_NAME=my-app             # Subdomain slug → https://my-app.DOMAIN_ROOT
BUILD_ARGS=KEY=val,KEY2=val2  # Optional, comma-separated
```

Rules:

- `STACK_NAME` must be unique across all Portainer stacks
- `IMAGE_NAME` must include the full registry path for push/pull to work
- `APP_NAME` becomes the subdomain: `APP_NAME.DOMAIN_ROOT`
- Registry is auto-detected from `IMAGE_NAME` (ghcr.io, docker.io, or custom host)

## docker-compose.deploy.yml Rules

- `image:` must use `${IMAGE_NAME}` variable (injected by Portainer env)
- Must include Traefik labels for routing
- Must join the `proxy` external network
- Use `${APP_NAME}` and `${DOMAIN_ROOT}` in Traefik Host rule

Example:

```yaml
services:
  my-app:
    image: ${IMAGE_NAME}
    restart: unless-stopped
    networks:
      - proxy
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.my-app.rule=Host(`${APP_NAME}.${DOMAIN_ROOT}`)"
      - "traefik.http.routers.my-app.entrypoints=websecure"
      - "traefik.http.routers.my-app.tls.certresolver=letsencrypt"
      - "traefik.http.services.my-app.loadbalancer.server.port=3000"

networks:
  proxy:
    external: true
```

## .env.production

Optional key-value file. All vars are injected into the Portainer stack environment.
Deployer automatically adds `IMAGE_NAME`, `APP_NAME`, `DOMAIN_ROOT` — do not duplicate them.

## Scaffolding

```bash
deployer init -t node       # Node.js (port 3000)
deployer init -t laravel    # Laravel/PHP (port 80)
deployer init -t java       # Java/Gradle (port 8080)
deployer init -t list       # Show available templates
deployer init -n my-slug    # Override app name (default: directory name)
```

## Validation

Run `deployer validate` to check:

- deploy/ directory exists
- .deploy has STACK_NAME, IMAGE_NAME, APP_NAME
- docker-compose.deploy.yml exists and references correct image
- Traefik labels present
- proxy network declared
- Dockerfile exists in project root
- .env.production readable (if present)
