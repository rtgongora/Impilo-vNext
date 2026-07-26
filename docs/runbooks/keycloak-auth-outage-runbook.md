# Runbook: Keycloak down — all authentication is out

> Scope: Keycloak (preview + production realms)
> Triggers: `scripts/full-boot/verify-keycloak-endpoints.sh` FAIL · `endpoints/keycloak` empty · `/realms/*` returning 503 · "login broken" reports from any lane
> Severity: **CRITICAL always.** Keycloak has no partial-failure mode. When it is down, web login, mobile login, and every authenticated journey are down simultaneously.

---

## 0. Why this runbook exists

**2026-07-20 → 2026-07-26: six days of total authentication outage, undetected.**

Commit `0b625f727` (*"feat(keycloak): WebAuthn passwordless realm config for passkey login (L1)"*) added 11 realm fields named `webAuthnPasswordlessPolicy*`. Keycloak 25 expects **`webAuthnPolicyPasswordless*`** — Policy *before* Passwordless. Realm import rejects unknown properties, so Keycloak threw `UnrecognizedPropertyException` and died at startup on every restart.

It went unnoticed for six days because **everything else looked fine**: pods existed, the Traefik route was correct and high-priority, the public site returned 200, and no other service depends on Keycloak until a human tries to sign in. It surfaced only when a mobile-auth investigation probed `/realms/*` and got 503.

Two lessons encoded as checks:
- **`scripts/guard/check-keycloak-realm-import.sh`** (CI, path-filtered) — asks Keycloak itself whether a realm file is valid. Catches this bug class before merge.
- **`scripts/full-boot/verify-keycloak-endpoints.sh`** (deploy, hard-fail) — catches *every* way Keycloak can be down, including ones we haven't thought of.

---

## 1. Confirm the alert is real (60 seconds)

```bash
NAMESPACE=impilo-full-preview bash scripts/full-boot/verify-keycloak-endpoints.sh
```

Checks endpoints, discovery through the public edge, and issuer scheme. Exit 1 = auth is down.

Manual equivalents:
```bash
kubectl -n impilo-full-preview get endpoints keycloak      # <none> = no ready backends
kubectl -n impilo-full-preview get pods -l app=keycloak
curl -sS --resolve impilo.mohcc.gov.zw:443:10.50.1.67 \
  -o /dev/null -w '%{http_code}\n' \
  https://impilo.mohcc.gov.zw/realms/impilo/.well-known/openid-configuration
```

`503 no available server` from the edge with a correct route = Keycloak has no healthy pod. Do **not** start debugging Traefik.

---

## 2. Diagnose by pod state

```bash
kubectl -n impilo-full-preview get pods -l app=keycloak
kubectl -n impilo-full-preview logs -l app=keycloak --previous --tail=40
```

| Pod state | Cause | Fix |
|---|---|---|
| `Error` / `CrashLoopBackOff`, log shows `UnrecognizedPropertyException` | **Malformed realm JSON** (the 2026-07-20 class) | §3 |
| `CreateContainerError`, `FailedCreatePodSandBox … name is reserved for <id>` | Stale containerd sandbox reservation after an eviction | §4 |
| `Pending`, `untolerated taint node.kubernetes.io/disk-pressure` | Node disk pressure | §5 |
| `Running` but every client gets 401 | Placeholder client secrets | §6 |
| `Running`, discovery 200, but clients fail *after* correct credentials | `http://` issuer behind the TLS edge | §7 |

---

## 3. Malformed realm JSON

The log names the offending property. Validate before redeploying:

```bash
bash scripts/guard/check-keycloak-realm-import.sh
```

Fix the field name in `deploy/helm/impilo-vnext/files/realm-impilo-preview.json`, then update the live ConfigMap **data-only** so Helm's ownership labels survive:

```bash
python3 -c "import json;print(json.dumps({'data':{'realm-impilo.json':open('deploy/helm/impilo-vnext/files/realm-impilo-preview.json').read()}}))" > /tmp/kc-patch.json
kubectl -n impilo-full-preview patch configmap keycloak-realm-import --type merge --patch-file /tmp/kc-patch.json
kubectl -n impilo-full-preview delete pod -l app=keycloak
```

> **Realm files are not source of truth.** `--import-realm` **skips realms that already exist**. The live realm has ~41 users; `realm-impilo-preview.json` documents 11, and most of its passwords were never applied. Editing the file does not change existing users — that is precisely how a broken field sat there for six days doing nothing except crashing startup.

---

## 4. Stale containerd sandbox

```bash
kubectl -n impilo-full-preview delete pod <keycloak-pod> --force --grace-period=0
```

Only a fresh pod UID clears the reservation; waiting never resolves it. Note this is a **diagnostic enabler** as much as a fix — until the sandbox clears, the container never starts far enough to emit the real error.

---

## 5. Disk pressure

The lever on this estate is docker pruning, not worktrees:

```bash
docker image prune -a --filter "until=168h"    # historical preview-* per-commit tags
docker builder prune -af
```

kubelet lifts the taint automatically above ~15% free. See [[disk-pressure-lever-is-docker-prune]].

---

## 6. Placeholder client secrets (healthy pod, 401 everywhere)

Realm import ships `${env.KC_CLIENT_SECRET_*}` placeholders and does **not** substitute them on a running estate, so confidential clients hold the literal placeholder as their secret.

```bash
NAMESPACE=impilo-full-preview bash scripts/keycloak/reconcile-client-secrets.sh
```

Expect `put=204` per client. `impilo-ops-console grant=401` is expected (no service account). **Run this after every restore** — a healthy pod serving 401s reads as an application bug and burns hours.

---

## 7. `http://` issuer behind the TLS edge

Traefik terminates TLS and forwards plain HTTP. Without proxy-header handling Keycloak advertises the scheme it sees:

```
issuer: http://impilo.mohcc.gov.zw/realms/impilo     ← wrong, breaks strict validation
```

Clients fail **after** a successful credential check, which misreads as "login broken" while discovery returns 200. Fixed by `KC_PROXY_HEADERS=xforwarded` in `deploy/helm/impilo-vnext/templates/keycloak.yaml`.

This is safe for the internal mesh: Keycloak derives the issuer per request, so in-cluster callers to `keycloak:8080` still get `http://keycloak:8080/realms/impilo`, which is what services expect. Both are simultaneously correct.

---

## 8. Verify recovery

```bash
NAMESPACE=impilo-full-preview bash scripts/full-boot/verify-keycloak-endpoints.sh
NAMESPACE=impilo-full-preview bash scripts/keycloak/reconcile-client-secrets.sh
```

Then prove an actual login — a 200 on discovery is not proof that anyone can sign in:

- **Web / confidential client:** `POST /internal/v1/auth/login` with a seeded persona (`vashandi.worker` / `Vashandi@2024!`) → expect 200 with `data.attributes.token`.
- **Mobile / public PKCE client:** full authorization-code + S256 flow against `impilo-mobile-citizen`, redirect `impilo-citizen://auth/callback` → expect access + refresh + id_token with `iss: https://…`.

### Working preview credentials (verified 2026-07-26)

| Account | Password | Note |
|---|---|---|
| `citizen.moyo` | `Vashandi@2026!` | **Not the seed value** — see below |
| `vashandi.worker` | `Vashandi@2024!` | seed value, never rotated |
| `vashandi.facility` / `.national` / `.hsc` / `.reviewer` | `Vashandi@2024!` | seed value |
| `superadmin` | `Impilo@2024!` | seed value |

> **`citizen.moyo` cannot be restored to its documented seed password — ever.** It carries a `password-history` credential (Keycloak creates that only on change), and the realm enforces `passwordHistory(5)`. Attempting the documented `Vashandi@2024!` returns:
> ```
> invalidPasswordHistoryMessage: Invalid password: must not be equal to any of last 5 passwords
> ```
> which is also the proof that the seed value *was* once set and then rotated away. It was therefore reset to `Vashandi@2026!` (satisfies `length(12) upperCase lowerCase digits specialChars notUsername`). **`MAESTRO_CITIZEN_USERNAME` / `MAESTRO_CITIZEN_PASSWORD` must use the value above, not the realm file's.** Any account whose password has been rotated has the same permanent constraint — the realm file's documented password can never be reinstated on it.

---

## 9. Detection — what now exists, and what still doesn't

Three layers, each catching a different moment:

| Layer | Catches | Where |
|---|---|---|
| **CI** | malformed realm JSON before it merges | `scripts/guard/check-keycloak-realm-import.sh` (`.github/workflows/keycloak-realm-guard.yml`) |
| **Deploy** | a deploy that leaves auth down | `scripts/full-boot/verify-keycloak-endpoints.sh` (hard-fail in `full-boot-preview-deploy.sh`) |
| **Continuous** | silent death between deploys — **the 20 July case** | `estate-health-watch` CronJob, every 10 min |

The continuous watch is the one that would have caught 20 July: nothing was merged or deployed that day, Keycloak simply restarted and never came back. It checks that every service in `estateHealthWatch.requiredEndpoints` has ≥1 ready endpoint, and on failure emits:

1. loud pod logs — `kubectl -n impilo-full-preview logs -l app=estate-health-watch`
2. a Kubernetes Event — `kubectl -n impilo-full-preview get events --field-selector reason=EstateHealthCheckFailed`
3. a **failed Job** — `kubectl -n impilo-full-preview get jobs -l app=estate-health-watch`
4. a webhook POST — **only if configured** (see below)

Verified on 2026-07-26 against both paths: healthy estate → `PASS`; injected dead service → all three signals fired.

### The gap that remains: notification

Items 1–3 are **detection, not paging** — someone still has to look. There is no Alertmanager, no kube-state-metrics, and no Slack/email/webhook notifier deployed on this estate (Prometheus exists only as a dev docker-compose profile; `tools/ops/prometheus/rules/ring0-alerts.yml` is orphaned — nothing loads it).

**To turn detection into a real page**, provide a channel and set it:

```bash
kubectl -n impilo-full-preview create secret generic impilo-alert-webhook \
  --from-literal=url='https://hooks.slack.com/services/...'
```
then in `values-full-preview.yaml`:
```yaml
estateHealthWatch:
  webhookUrlSecret:
    name: impilo-alert-webhook
    key: url
```
Any Slack/Teams/generic JSON webhook works — the body is `{"text": "..."}`.
