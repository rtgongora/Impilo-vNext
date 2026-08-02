# Checkpoint 3 — Governed Synthetic Preview Test Identity

Date: 2026-08-02 · Branch: `claude/tshepo-trust-cp1-truth-audit`  
Authority: Product Owner authorization to create a dedicated synthetic, preview-only
CITIZEN test identity and store its credential in the approved preview secret mechanism.

## Non-secret metadata

| Field | Value |
|---|---|
| Username | `preview.test.citizen` |
| Keycloak user id | `a964a589-3681-4e58-aab9-b8407e903aa4` |
| Realm | `impilo` |
| Enabled | true |
| Realm roles | `CITIZEN`, `default-roles-impilo` (Keycloak-mandatory default only) |
| Email | `preview.test.citizen@preview.impilo.local` (synthetic; not a real mailbox) |
| Kind attribute | `SYNTHETIC_PREVIEW_TEST` |
| Purpose attribute | `authenticated-runtime-proof` |
| Environment attribute | `preview` |
| No real person | `true` |
| No clinical record | `true` |
| Facility / workforce / clinical / regulatory / finance / admin / platform authority | **none** |
| Existing users modified | **none** (`citizen.moyo` id `4a6c7696-…` unchanged) |

## Secret mechanism

| Item | Value |
|---|---|
| Store | Dedicated Kubernetes Opaque Secret (not shared app crypto keys) |
| Name | `impilo-preview-test-identity` |
| Namespace | `impilo-full-preview` |
| Provisioner | `scripts/operator/provision-preview-test-citizen.sh` (idempotent) |
| Keys | `username`, `password`, `realm`, `client_id_web`, `client_id_mobile_citizen`, `purpose`, `environment`, `identity_kind` |
| Storage rule | Written with `kubectl create` / `kubectl replace` against base64 `data` — **never** `kubectl apply` (avoids plaintext in `last-applied-configuration`) |
| Workload mount | **None** — annotated `impilo.mohcc.gov.zw/no-workload-mount=true`; no Deployment `secretKeyRef` or volume references this Secret |

### RBAC proof (2026-08-02)

```
kubectl auth can-i get secret/impilo-preview-test-identity \
  -n impilo-full-preview \
  --as=system:serviceaccount:impilo-full-preview:default
→ no

kubectl auth can-i get secret/impilo-preview-test-identity \
  -n impilo-full-preview \
  --as=system:serviceaccount:impilo-full-preview:estate-health-watch
→ no

Workload secretKeyRef / volume mounts of impilo-preview-test-identity
→ NONE across all Deployments in impilo-full-preview
```

Application workloads therefore cannot read the test secret via the Kubernetes API
or via injection. Human cluster-admin kubeconfigs remain out of scope of app SA RBAC.

## Ownership and procedures

| Item | Value |
|---|---|
| Owners | Product Owner and Security Owner (joint) |
| Purpose | Authenticated browser (Playwright) and Redroid/Maestro runtime proof in preview only |
| Environment restriction | Preview (`impilo-full-preview` / `https://impilo.mohcc.gov.zw`) only. Must never be seeded into staging or production. |
| Rotation | Re-run with a deleted Secret, or an explicit operator rotation that regenerates the password, updates Keycloak via `reset-password`, and rewrites the Secret without `kubectl apply`. Record rotation in the trust-audit evidence trail. Do not print the new value. |
| Revocation | Disable the Keycloak user (`enabled=false`) and delete the Secret after Product Owner + Security Owner written approval. Do not delete while authenticated proof still depends on it. |
| Credential handling | Never commit, never log, never embed in Helm values, never echo to the terminal. Consumers load via `kubectl get secret … -o jsonpath` into process environment for a single test run. |

## Consumers

| Consumer | Env vars | Secret keys |
|---|---|---|
| Playwright authenticated preview | `PREVIEW_TEST_USERNAME` / `PREVIEW_TEST_PASSWORD` | `username` / `password` |
| Maestro / Redroid authenticated smoke | `MAESTRO_CITIZEN_USERNAME` / `MAESTRO_CITIZEN_PASSWORD` | `username` / `password` |

## Explicit non-goals

- Does not replace or reset `citizen.moyo` or any other existing user.
- Does not grant AAL2, workforce MFA, clinical, regulatory, finance or admin authority.
- Does not create a VITO person / clinical record — login proves the auth session path only.
