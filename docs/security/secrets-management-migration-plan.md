# Secrets-management migration — epic plan

Move the platform off **committed placeholder secrets** to **out-of-band,
strong, rotatable secrets** referenced by `secretKeyRef` — ahead of exposing
`impilo.mohcc.gov.zw` to the public internet.

## Why

The preview/full-boot deployment commits `…-change-me-…` placeholder secrets by
convention. That's tolerable for a closed demo, but the estate is now going
public (telemedicine media), so predictable, git-committed signing keys and
admin passwords become a real compromise vector (anyone reading the repo can mint
LiveKit room tokens, DAGS permit tokens, forge VITO pseudonyms, or log into
Keycloak/MinIO admin). Doing this per-service (as nearly happened for LiveKit) is
inconsistent and misses the shared risk — hence one epic.

Interim state: **LiveKit's secret was already rotated live** (strong random,
consistent across livekit-config/egress/rtc-gateway) but only in-cluster — a
`helm sync` reverts it until this epic lands. Every other secret below is still
the committed placeholder.

## Inventory (grounded)

| # | Secret | Source (git) | Consumer(s) | Injection | Sev |
|---|--------|--------------|-------------|-----------|-----|
| 1 | `KEYCLOAK_ADMIN_PASSWORD` `preview-keycloak-change-me` | `values-full-preview.yaml:36` | Keycloak | Secret (`keycloak-preview-credentials`) from committed value | 🔴 Critical |
| 2 | `VITO_HMAC_PEPPER` | `generate-full-preview-runtime-values.mjs:80` | vito-service (PII pseudonymization) | literal env | 🔴 Critical |
| 3 | `DAGS_ENFORCEMENT_SIGNING_KEY` | generator `:110` | data-access-governance (permit tokens) | literal env | 🔴 High |
| 4 | `LIVEKIT_API_SECRET` | generator `:98`, `values:146` | livekit (file), egress (file), rtc-gateway (env) | mixed | 🔴 High *(rotated live)* |
| 5 | `MINIO_ROOT_PASSWORD` `preview-minio-change-me` | `values-full-preview.yaml:38` | MinIO, egress S3 | Secret + literal (egress) | 🟠 High |
| 6 | Keycloak client secrets `CHANGE_ME_*`, `impilo-backend-secret` | `files/realm-impilo-preview.json`, `infra/keycloak/realm-impilo-production.json` | OIDC clients (bff/ops/ehr/portal) | realm import | 🟠 High |
| 7 | tshepo-keys signing `change-me-in-production-use-vault` | `helm/tshepo-keys/values.yaml:17` | tshepo-keys-service | value | 🟠 High |
| 8 | nhume webhook `dev-webhook-secret-change-me` | `services/nhume-service/.../application.yml:46` | nhume-service | value | 🟡 Med |
| 9 | mushex `change-me-in-production` | `services/mushex-service/helm/mushex/values.yaml:19` | mushex-service | value | 🟡 Med |
| 10 | `values.yaml:28` `preview-change-me` | base chart | — | value | 🟡 Med |

`.env*.example` files are illustrative and out of scope (no live consumer).

Two injection shapes matter for the design:
- **env-based** (2,3,4-rtc-gateway) → trivial `secretKeyRef`.
- **config-file-based** (4-livekit `keys:`, 4-egress `api_secret`, 6 realm) → need
  env override (`LIVEKIT_KEYS`), an initContainer `envsubst`, or a rendered Secret.

Neither External-Secrets-Operator nor Sealed-Secrets is installed today.

## Target architecture

1. **Provisioning** — secrets created **out-of-band**, never in git. Recommended
   phased mechanism:
   - **Now (single k3s node, fastest):** a `scripts/secrets/bootstrap-secrets.sh`
     that generates strong randoms (`openssl rand`) and `kubectl apply`s a
     `Secret/impilo-app-secrets` (+ the existing `*-preview-credentials`),
     idempotently (won't overwrite existing keys). Values live only in-cluster +
     the team's password manager.
   - **Next (GitOps-friendly):** **Sealed Secrets** (encrypted secrets committable
     to git) or **External Secrets Operator** backed by Vault/cloud SM. The chart
     changes below are identical either way — only the Secret's origin changes.
2. **Consumption** — chart references Secrets via `secretKeyRef`; **zero plaintext
   in values/generator**. A rendered `helm template` must contain no secret material.

## Chart primitives to build (reusable, land first)

- **`secretEnv` hook in `templates/microservice.yaml`** — after the `$svc.env`
  loop, render `$svc.secretEnv` as a map of `envName -> {name, key}` into
  `valueFrom.secretKeyRef`. Lets the generator declare secret refs instead of
  literals (covers 2, 3, 4-rtc-gateway, 8, 9 — every Spring service).
- **`Secret/impilo-app-secrets`** — one Opaque Secret holding the app signing
  secrets (vito pepper, dags key, livekit `api-secret` + `keys`, nhume, mushex …).
  Extend `templates/preview-credentials.yaml`'s pattern but sourced out-of-band.
- **`scripts/secrets/bootstrap-secrets.sh`** — idempotent provisioner (generate +
  apply; skip existing keys). Documented as a prerequisite before `helm … sync`.
- **LiveKit** — server reads keys via `LIVEKIT_KEYS` env (`secretKeyRef`), drop the
  `keys:` block from `livekit-config`. Egress gets an **initContainer** that
  `envsubst`s `api_secret`/`s3.secret` from env (`secretKeyRef`) into a rendered
  `egress.yaml` on an `emptyDir`. (Removes the last file-inlined secrets.)

## Phasing

- **P0 — Guardrail first. ✅ DONE (baseline guard).** The hollow "Check for
  secrets in code" step in the `security-scan` CI job (a `|| true` one-file grep)
  is replaced by `scripts/guard/check-committed-secrets.sh`, which fails on any
  NEW committed placeholder/secret token across tracked helm values, the
  runtime-values generator, realm JSON, service config and env files. The 33
  existing occurrences are recorded in `scripts/guard/committed-secrets-baseline.txt`
  (green now, red on anything new); prune with `--update-baseline` as each phase
  removes a secret. Proven: green on the tree, red on a planted token.
  - **Remaining (gitleaks high-entropy net):** `.gitleaks.toml` is committed as the
    starting config, but a raw scan of the tree reports **594 findings** (mostly
    vendored/test-fixture noise) and runs ~21 min locally. Adopting it as a gate
    needs (a) a committed findings-baseline (`--baseline-path`) so only NEW
    high-entropy secrets fail, and (b) scoping to tracked files for speed. Do this
    within P1 — the baseline guard already blocks the `*-change-me-*` convention.
- **P1 — env-based. ✅ DONE (main-chart generator secrets).** Built the reusable
  `secretEnv` hook in `templates/microservice.yaml` (ENV → `secretKeyRef`), the
  out-of-band `Secret/impilo-app-secrets`, and idempotent
  `scripts/secrets/bootstrap-secrets.sh`. Migrated **VITO_HMAC_PEPPER**,
  **DAGS_ENFORCEMENT_SIGNING_KEY**, and **LIVEKIT_API_SECRET (rtc-gateway)** off
  committed literals in the generator + generated values → `secretKeyRef`
  (verified via `helm template`). Secret provisioned live seeded with **current**
  values (livekit = the rotated strong value matching the server key; vito/dags
  preserved) so next deploy is behavior-identical. Guard baseline 33 → 27.
  - **Remaining P1:** `nhume-service` (`application.yml`) and `mushex-service`
    (`helm/mushex/values.yaml`) live in their own service charts, not the main
    generator — migrate them the same way (own Secret/secretKeyRef).
  - **Value-strengthening (separate op, now trivial):** vito/dags still hold the
    weak placeholder *value* (just no longer in git). Rotating is now a
    `kubectl` Secret update + rollout — **but vito-hmac-pepper is data-affecting**
    (needs a migration); dags is safe. LiveKit is already strong.
  - **Coupling note:** livekit-api-secret must equal the LiveKit *server* key until
    **P2** moves livekit-config/egress onto the same Secret.
- **P2 — file-based (medium):** LiveKit server `LIVEKIT_KEYS` env + egress
  initContainer; drop inline `keys:`/`api_secret`. Blank `values.livekit.apiSecret`.
- **P3 — infra creds:** move `keycloakAdminPassword`, `minioRootPassword`,
  tshepo-keys off committed values to `impilo-app-secrets`/dedicated Secrets.
- **P4 — Keycloak client secrets:** provision real client secrets out-of-band;
  update realm import to reference env/`${...}` placeholders; rotate the
  `CHANGE_ME_*` clients. Update any consumers reading those client secrets.
- **P5 — Rotation runbook:** document rotation per secret (which consumers must
  roll together — e.g. LiveKit's three), and a `certbot`-style redeploy/roll step.

## Verification

- `helm template deploy/helm/impilo-vnext -f values-full-preview.yaml | grep -iE
  'change-me|secret:|password'` → no secret material (only `secretKeyRef` names).
- Bootstrap script is idempotent (re-run doesn't rotate existing keys).
- Smoke deploy to a scratch namespace: pods boot, LiveKit token round-trip works,
  Keycloak login works, VITO/DAGS behave (signing keys valid).
- P0 gitleaks gate fails a deliberately-planted test secret.

## Rollback / interim safety

- Each phase is independent; a phase that misbehaves reverts by restoring the prior
  literal for that one secret (git revert) — no big-bang cutover.
- Until P1–P2 land, LiveKit stays protected by the **live rotation**; do not `helm
  sync` livekit/rtc-gateway/egress without first re-applying the rotated secret (or
  landing P1–P2), or it reverts to the public placeholder.

## Follow-ups referenced elsewhere

- Public LiveKit **media** (TURN + firewall) — `deploy/tls/mohcc-gov/PUBLIC-MEDIA-PLAN.md`.
- This epic is the "Step 1 (secret management)" of that plan, generalized.
