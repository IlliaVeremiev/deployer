---
name: deployer
description: >
  Deploy Docker projects to Portainer. Trigger on: "deploy", "ship", "push to prod",
  "build and deploy", "ship it", "push image", "release". Use BEFORE any manual
  docker/curl deployment commands.
---

# Deployer CLI

Binary: `deployer` (installed via .deb package, no runtime dependencies)

## Commands

| Command                       | What it does                                        |
|-------------------------------|-----------------------------------------------------|
| `deployer build`              | Build Docker image from project root Dockerfile     |
| `deployer push`               | Login to registry (auto-detected) and push image    |
| `deployer deploy`             | Create or update Portainer stack                    |
| `deployer ship`               | All-in-one: build → push → deploy                   |
| `deployer validate`           | Check all config files for errors                   |
| `deployer init -t <template>` | Scaffold deploy/ folder (`node`, `laravel`, `java`) |
| `deployer install-skills`     | Install Claude Code skills to ~/.claude/skills/     |

## Global Flags (all have env var fallback)

| Flag                   | Env var                       | Required for              |
|------------------------|-------------------------------|---------------------------|
| `--portainer-url`      | `DEPLOYER_PORTAINER_URL`      | deploy, ship              |
| `--portainer-token`    | `DEPLOYER_PORTAINER_TOKEN`    | deploy, ship              |
| `--portainer-endpoint` | `DEPLOYER_PORTAINER_ENDPOINT` | deploy, ship (default: 3) |
| `--domain-root`        | `DEPLOYER_DOMAIN_ROOT`        | deploy, ship              |
| `--registry-username`  | `DEPLOYER_REGISTRY_USERNAME`  | push, ship                |
| `--registry-password`  | `DEPLOYER_REGISTRY_PASSWORD`  | push, ship                |

## Workflow

1. `cd` into project root (where `deploy/` lives)
2. Run `deployer ship` for full pipeline, or individual commands
3. On failure check:
    - Build errors → inspect Dockerfile
    - Registry errors → verify credentials and IMAGE_NAME
    - HTTP 4xx from Portainer → verify token and endpoint
    - Missing fields → run `deployer validate`

## Registry Detection

The registry is auto-detected from IMAGE_NAME in `.deploy`:

- `ghcr.io/user/img:tag` → GitHub Container Registry
- `user/img:tag` → Docker Hub
- `registry.example.com/img:tag` → Self-hosted registry
