# CP10 — Measured enforcement posture

Measured 2026-08-03 against the live `impilo-full-preview` estate. Every number below comes from an
HTTP probe of a running service. Nothing here is read from a config file, a Helm value, or
`scripts/security/audit-enforcement-posture.py`. Where I did not measure something, it says UNKNOWN.

This supersedes CP8's and CP9's enforcement numbers, which the brief already flagged as stale.

---

## 1. Method, and why it is trustworthy

**Vantage point.** All probes ran from inside the `public-website` pod in `impilo-full-preview`
(`kubectl exec`), using `curl`. That pod has `curl`; the BFF, Envoy, Keycloak and `one-ui-shell`
pods do not. Probing from inside the namespace is the correct vantage: it measures what any
workload — or anything that reaches one — can do, which is the exposure that matters given §4.

**`wget` was not used**, per the brief §7: it prints `Username/Password Authentication Failed.` on
any 401, which is wget's own text and has previously been misread as a server response.

**Discriminator.** `/actuator/health` is `permitAll` under every configuration and proves nothing
about enforcement — it is used here only as a *reachability control*. The enforcement discriminator
is a business path:

| Response | Meaning |
|---|---|
| `401` | the security filter chain refused before the application saw the request — **enforcing** |
| `404` / `405` / `400` / `500` from the app | the request **reached application code** — **open** |
| `200` | the request reached application code **and was served** — open, and proven so |

**Three synthetic path shapes** were probed on every service — `/v1/…`, `/api/v1/…`,
`/internal/v1/…` — with a path component that exists nowhere (`__enforce_probe__`). A synthetic
path is the right first screen because it tests the chain's *terminal* rule rather than a route
that might be individually exempted. Real business paths were then probed on every candidate the
screen flagged, because a synthetic path alone is not decisive (see §3).

### Instrument controls — both directions

The brief's law is that a check proven in only one direction proves nothing. The probe was
controlled both ways:

- **Negative control (the instrument can say "open")** — `public-website` returned `200` on all
  three shapes, and `opa`, `keycloak`, `matcher-engine` and `ndila-martin` returned `404`. The probe
  is not simply printing `401` everywhere.
- **Positive control (the instrument can say "enforcing", and headers do not defeat it)** — when the
  probe was re-run supplying the four v1.1 trust headers, `vito-service` and `wellness-service`
  still returned `401`. This is the decisive control: it proves the headers pass a *validation*
  filter, not an *authentication* one, so a `200` obtained with those headers is genuinely
  unauthenticated access and not an artefact of the probe authenticating itself.
- **Reachability control** — every service classified below returned `200` on `/actuator/health`, so
  a `401` is a refusal by that service and not a network failure. One service,
  `workforce-governance-service`, returned `000` (connection refused) on every path despite a
  `1/1 Ready` deployment and a populated Endpoints object; it is recorded as **UNKNOWN**, not
  assumed closed.

---

## 2. The headline numbers

**102 Spring Boot business services** answered in the namespace. (105 workloads returned
`200 /actuator/health`; three of them — `envoy`, `public-website`, `hapi-fhir` — are not Spring
business services and are handled separately in §4.)

```
Enforcing (refuse an unauthenticated business request)      91
Open      (serve an unauthenticated business request)       11
                                                           ---
                                                           102
```

- **89** refuse all three synthetic shapes with `401`.
- **1** (`product-registry-service`) refuses with `403` on all three — enforcing by a different
  mechanism.
- **1** (`experience-bff`) returns `401` on real business paths (`/internal/v1/patients`,
  `/internal/v1/work/context`, `/internal/v1/auth/oidc/session`) — enforcing.
- **11** serve unauthenticated business requests. **9 of the 11 were proven with HTTP 200 and a
  response body**, not inferred.

The brief's §2 claim — that the estate flipped from bypassed to enforcing when the fullboot dropped
`IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` — **is confirmed**. The flag is now absent from 115 of 117
deployments and present-and-`false` on the remaining two (`tshepo-audit-service`,
`tshepo-authz-service`). The measured 91/102 enforcing is consistent with that, and it remains an
unratified change that happened to survive, not a completed CP8.

---

## 3. The 11 open services — proven, not inferred

Each of these serves business requests with **no credential of any kind**. The only thing standing
in front of them is a v1.1 header-validation filter that returns `400 MISSING_REQUIRED_HEADER` and
is satisfied by four freely-forgeable headers (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`,
`X-Correlation-ID`). That filter is *validation*, not *authentication* — the positive control in §1
proves it, because enforcing services still return `401` when those same headers are supplied.

| Service | Probe | Result |
|---|---|---|
| `analytics-pipeline-service` | `GET /internal/v1/telemedicine/sla` | **200** — real data: 11 accepted events, telemedicine session counts by type |
| `nhume-service` | `GET /internal/v1/nhume/fleet` | **200** — real fleet assets with `asset_id`, `home_facility_ref` |
| `audit-ledger-service` | `GET /internal/v1/audit/records?tenantId=…` | **200** — audit record query executed |
| `audit-ledger-service` | `GET /internal/v1/audit/chain/verify?tenantId=…` | **200** `{"valid":true}` — **audit chain verification is unauthenticated** |
| `matcher-engine` | `GET /v1/engine/capabilities` | **200** `{"FINGERPRINT":true,"FACE":false,"IRIS":true}` — biometric matcher; `/extract`, `/verify`, `/identify` are POST on the same open chain |
| `connector-fhir-adapter` | `GET /internal/v1/fhir/destinations` | **200** `{"count":0,"items":[]}` |
| `iot-ingestion-service` | `GET /internal/v1/devices` | **200** `{"data":[],"meta":{…}}` |
| `offline-edge-service` | `GET /internal/v1/offline/conflicts` | **200** `{"items":[],…}` |
| `offline-sync-service` | `GET /internal/v1/sync-packs` | **200** `[]` |
| `pharmacy-elmis-adapter` | `GET /internal/v1/dispense-sync` | **200** `[]` |
| `jobs-service` | `GET /internal/v1/jobs` | **400** from the handler (Spring parameter binding) — reached application code; not proven served |
| `support-service` | `/internal/v1/**` | `400` header filter, `404` on the dispatcher — reached application code; not proven served |

`jobs-service` and `support-service` are classified open on the weaker evidence that the request
reached application code. I did not find a parameter combination that returned `200` for them, and I
did not guess one. That they *serve* data is **UNKNOWN**; that they are **not authenticating** is
measured.

### The single strongest piece of evidence

`audit-ledger-service` first returned `500` to an unauthenticated request. The pod log shows why:

```
java.lang.IllegalArgumentException: Invalid UUID string: moh-zw
    at zw.gov.mohcc.impilo.auditledger.api.AuditLedgerController.listRecords(AuditLedgerController.java:56)
```

The exception is thrown **inside the controller method body**. The unauthenticated request did not
merely reach the dispatcher — it executed business logic, and failed only because the probe supplied
a tenant string that is not a UUID. Re-probed with a syntactically valid UUID it returned `200` and
an empty result set. This is the pattern the brief demands: a *success* reaching the check, not a
denial.

---

## 4. Two findings that are not in the brief, and are worse than the eleven

Both are outside `services/*/src/main/java`, which is the only tree
`audit-enforcement-posture.py` scans. No source-level screen scoped to `services/` could ever have
seen them, and neither appears in CP8's baseline.

### 4.1 The SHR store answers unauthenticated FHIR queries

`hapi-fhir` is the stock `hapiproject/hapi:v7.4.0` image, deployed with database credentials and
**no authentication configuration at all**:

```
GET http://hapi-fhir:8090/fhir/metadata        -> 200  CapabilityStatement
GET http://hapi-fhir:8090/fhir/Patient         -> 200  Bundle, "total": 2, patient entries returned
GET http://hapi-fhir:8090/fhir/Observation     -> 200  Bundle
```

The *governed* paths in front of it are correctly closed — `butano-fhir:8289/fhir/Patient` returns
`401` and `fhir-gateway-service` returns `401`. But the store itself is directly addressable by
service name from any pod in the namespace, so the consent PEP, the trust headers and the audit
trail are all bypassable by one hop. This is the "no PII in SHR" doctrine doing its job — the
identifiers observed are CPID-shaped, and I did not retrieve or record any resource content beyond
confirming the bundle is non-empty — but an unauthenticated read of the clinical record store is a
control failure regardless of what the record contains.

**I have not fixed this and have not changed the deployment.** It needs a decision (PEP-only network
reachability, or authentication on the store, or both) rather than a reflex.

### 4.2 The PACS store answers unauthenticated queries

```
GET http://orthanc:8042/patients -> 200 []
```

Currently empty, so nothing leaked. The control is absent regardless of whether there is anything
behind it today.

For contrast, `minio:9000` correctly returned `403 AccessDenied` — so this is not a blanket
"everything infra is open"; two specific stores are.

### 4.3 There is no network containment behind any of this

```
$ kubectl get netpol -n impilo-full-preview
cohort1-workforce-governance-ingress   app=workforce-governance-service   26h
```

**One NetworkPolicy in the entire namespace**, covering one service. Every finding in §3 and §4 is
therefore reachable from every pod in the namespace, including `public-website`, which sits behind
the only Ingress (`impilo.mohcc.gov.zw`). I did not test reachability from outside the cluster and
make **no claim** about internet exposure — that is UNKNOWN and should be measured before this
document is used to size the risk.

---

## 5. Corrections to the record

### 5.1 The CP8 baseline has four false positives, not "at least one"

The brief (§6) records `wellness-service` as a known false positive in
`checkpoint-8/unconditionally-open-services.txt`. Probing the whole baseline found **four**, all
returning `401` to unauthenticated business requests:

| Baseline entry | Probe | Verdict |
|---|---|---|
| `abis-service` | `GET /v1/abis/adjudication` | **401 — enforcing.** False positive |
| `indawo-service` | `GET /internal/v1/indawo` | **401 — enforcing.** False positive |
| `ndila-service` | `GET /v1/public/ndila/tiles/0/0/0` | **401 — enforcing.** False positive |
| `wellness-service` | `GET /internal/v1/wellness` | **401 — enforcing.** Confirms the brief |

`abis-service` matters most: CP8 headlines it as *"biometric identity"* — one of the three
sensitivities used to argue the baseline's importance. It is enforcing. The argument for the
baseline still stands on `audit-ledger-service` and `matcher-engine`, both of which **are** open and
were confirmed by probe.

A fifth entry, `shared-core`, is a library, not a deployed workload. It is not probeable and should
not be in a list of open *services*.

So of the 16 baseline names: **11 confirmed open, 4 measured enforcing, 1 not a service.**

The baseline is a source-level hypothesis list that has been read as a measurement. It is still
useful as a *ratchet* — `check-enforcement-posture.sh` freezing it in both directions is sound —
but every row needs a probe before it is quoted, and the file should say so.

### 5.2 Source-level `permitAll` massively over-reports at runtime

A source screen for `requestMatchers(…).permitAll()` on non-health paths produced a long candidate
list. Probing it, most entries are in a **flag-gated test chain** (`@ConditionalOnProperty(…
disable-oauth-for-tests, havingValue = "true")`) that is not active now that the flag is gone:

| Source says `permitAll` | Runtime |
|---|---|
| `guidance-service /v1/public/guidance/**` | **401** |
| `varapi-service /v1/public/facilities/**`, `/v1/public/practitioners/**` | **401** |
| `butano-service /v1/public/**` | **401** |
| `credential-verification-service /v1/credentials/verify` | **401** |
| `ndila-service /v1/public/ndila/tiles/**` | **401** |
| `msika-service /v1/**` | **401** |

This is the same class of error as §5.1 in the opposite direction, and it is why the whole
measurement was done by probe.

### 5.3 "117 deployments, all ready" — a caveat about the instrument, not the estate

My first pass read `status.readyReplicas` from `kubectl get deploy -o json` and got `<none>` for
seven deployments (`butano-service`, `community-service`, `msika-flow-service`, `msika-service`,
`pharmacy-elmis-adapter`, `pharmacy-service`, `zibo-service`). `kubectl get deploy` reports all
seven as `1/1  READY, 1 AVAILABLE`, and all seven answered probes normally. **The brief's claim is
correct; my first reading of it was wrong**, and I could not reproduce the `<none>`. Recorded here
because the failure mode — a field absent from one read of the API and present in the next — is
exactly the kind of thing this programme keeps being bitten by, and because I would rather record an
unexplained instrument reading than quietly drop it. Root cause: **UNKNOWN**.

---

## 6. Unauthenticated paths on otherwise-enforcing services

These services enforce by default but expose specific business paths without authentication. Listed
separately because they are a narrower exposure than §3, and because several are deliberate.

**Not obviously deliberate — needs a ruling:**

| Service | Path | Result |
|---|---|---|
| `tshepo-audit-service` | `POST /v1/audit/events` | reached handler (`400 TYPE_MISMATCH` on parameter binding) — **audit ingestion is unauthenticated**, so audit records can be written by anything in the namespace |
| `tshepo-audit-service` | `GET /v1/audit/verify-chain` | reached handler (`500`) |
| `tshepo-consent-service` | `POST /v1/consent/evaluate` | reached handler (`400 MISSING_PARAMETER`) — consent decisions evaluable without a credential |
| `tshepo-authz-service` | `POST /v1/authorize` | reached the PDP (`403 DENY MISSING_HEADERS`) — by design for Envoy `ext_authz`, **but `ext_authz` is off**, so nothing legitimately needs this open right now |
| `observability-service` | `GET /v1/public/ops/status` | `200` — service status roll-up |

**Deliberate and defensible** (recorded so a future sweep does not re-raise them):
`tshepo-keys-service /v1/keys/jwks` (200 — JWKS must be public), `share-slip-service
/v1/public/share/verify/**` (200 — public share-link verification is the feature),
`tuso-service /v1/public/facilities/search` (200, returns real facility registry rows — a public
facility directory is intended, though it does expose `facilityUuid`), `scheduling-service
/v1/health` and `card-print-agent /api/status` (health aliases).

**Not investigated:** `/internal/v1/test-command` and `/internal/v1/test-federation` are declared
`permitAll` in ~48 services. In the services I sampled these are v1.1 conformance probe endpoints
that echo their input, and at least one (`ai-model-registry-service`) gates them behind
`air.probe.test-command-enabled:false`. Whether all 48 are inert is **UNKNOWN** and worth a sweep;
they are not counted as open above.

---

## 7. What this changes about the ordered work

- **§4.3 of the brief ("the 16 services with no security chain") is now 11**, plus two infrastructure
  stores that were never in scope of the count. The work is smaller than recorded in one direction
  and larger in the other, and the larger direction is the SHR.
- **The ordering should change.** The brief puts client-elective AAL2 first as "the only genuine
  security hole left". On the measurement, an unauthenticated read of the FHIR store (§4.1) is a
  larger and simpler exposure: it requires no session at all, whereas the AAL2 hole requires a
  valid first factor. Both are real; §4.1 should not queue behind §3 of the brief.
- **`audit-ledger-service` being open (§3) and `tshepo-audit-service /v1/audit/events` being open
  (§6) are the same problem seen twice**: the audit trail can be both read and written without a
  credential. An audit trail that anything can write is not evidence.

---

## Appendix — reproducing this

```bash
POD=$(kubectl get pods -n impilo-full-preview --no-headers \
        -o custom-columns=N:.metadata.name | grep '^public-website-' | head -1)

# enforcing (control):
kubectl exec -n impilo-full-preview $POD -- curl -s -o /dev/null -w '%{http_code}\n' \
  http://vito-service:8082/v1/patients                                    # 401

# enforcing, and headers do not defeat it (the decisive control):
kubectl exec -n impilo-full-preview $POD -- curl -s -o /dev/null -w '%{http_code}\n' \
  -H X-Tenant-ID:moh-zw -H X-Pod-ID:p -H X-Request-ID:r -H X-Correlation-ID:c \
  http://wellness-service:8161/internal/v1/wellness                       # 401

# open, proven by a served body:
kubectl exec -n impilo-full-preview $POD -- curl -s \
  http://analytics-pipeline-service:8365/internal/v1/telemedicine/sla     # 200 + real data

# the SHR store:
kubectl exec -n impilo-full-preview $POD -- curl -s \
  http://hapi-fhir:8090/fhir/Patient                                      # 200 + Bundle
```

All probes above are `GET` and non-mutating. No `POST` was issued to any open endpoint: reaching a
handler was demonstrated with parameter-binding failures rather than by writing data. Nothing in the
estate was changed to produce this document.
