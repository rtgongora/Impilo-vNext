# The FHIR split, measured

**Date:** 2026-08-08 · **Branch:** `phase0/g-fhir-split` (from `origin/main` `00c0a7100`)
**Environment measured:** `impilo-full-preview`

The architecture instruction was: *repoint `FhirPublisher` and the gateway default target at
`butano-service`; migrate and retire `butano-fhir` and stock `hapi-fhir`.* This records what was
actually there before anything was changed, because two of the three services turned out not to
hold what the instruction assumes.

---

## 1. Three FHIR stores, not two

| | `butano-service` | `hapi-fhir` | `butano-fhir` |
|---|---|---|---|
| Database | `butano` | `hapi` | `butano_fhir` |
| What it is | HAPI FHIR R4 JPA server | stock `hapiproject/hapi:v7.4.0` | JSONB blob store, **not a FHIR server** |
| Service port | 8090 | 8090 (pod 8080) | 8289 |
| API | `/fhir/*` (full R4) | `/fhir/*` (full R4) | `/internal/v1/fhir/resources` (proprietary envelope) |
| Governance on write | header validation, tenant enforcement, PII prevention, provenance stamping, terminology validation | **none** | Spring Security JWT only |
| Unauthenticated `GET /fhir/Patient` | **403** | **200 + Bundle** | n/a |
| Rows held | **4** | **5** | **0** |
| Fed by | Kafka (`ButanoEventConsumer`) | fhir-gateway default target | nothing — see §5 |

`butano-fhir` is not a smaller BUTANO. It stores `{tenantId, resourceType, resourceId, fhirVersion,
payload}` rows in `butano_fhir.fhir_resource` and has no FHIR engine, no search parameters, no
references and no interceptors. It shares a name prefix with the SHR and nothing else.

### Rows, counted — not tables

```
butano       (butano-service)   Observation 3, Patient 1                        =  4
hapi         (hapi-fhir)        Patient 2, DiagnosticReport 1, Encounter 1,
                                Composition 1                                   =  5
butano_fhir  (butano-fhir)      fhir_resource 0, event_outbox 0                 =  0
fhir_gateway                    fhir_route 0                                    =  0
```

**`fhir_route` holding zero rows is the single most consequential number here.** Every forward the
gateway serves falls through to `FHIR_GATEWAY_DEFAULT_TARGET_BASE`, so that one environment
variable was not a fallback — it was the delivery target for the entire governed write path.

### What the two populated stores actually hold

`butano` holds real governed SHR content: a CPID-only `Patient` (identifier system
`https://impilo.gov.zw/cpid`) and three PCT vital-sign `Observation`s referencing `Patient/1`.

`hapi` holds five teleconsultation proof artefacts written 2026-07-22 — `"Break-glass FHIR delivery
proof"`, subjects `cpid-bg-1784688186` and `cpid-doc-1784693596`, identifier system
`http://impilo.mohcc.gov.zw/fhir/cpid`. They are synthetic, they are not in VITO's CPID space, and
their identifier system is not the one the SHR uses.

---

## 2. What targeted what, before

```
experience-bff  FhirPublisher ──FHIR_BASE_URL──────────────────► hapi-fhir   (dead: nothing injects it)
experience-bff  TeleconsultController ──forward──► fhir-gateway ─┐
telemonitoring  ObservationShrWriter  ──forward──► fhir-gateway ─┤
                                                                 └─DEFAULT_TARGET_BASE──► hapi-fhir
inpatient       ButanoProcedureClient ────────────────────────► butano-fhir  (inert: see §5)
                butano-service ◄──────────────────────────────── Kafka only
```

Nothing reached `butano-service` over HTTP at all. The SHR's entire ingest was Kafka; the entire
HTTP write path, including everything the consent PEP had just approved, went to the server with no
authentication.

### The consent PEP was real and the delivery was not governed

`fhir-gateway-service` runs consent enforcement and then calls `FhirForwarder.send(...)`. The
resource it had just decided on was handed, unaltered, to a store that answers any pod in the
namespace with no credential. `deploy/networkpolicy/shr-hapi-fhir-ingress.yaml` (2026-08-03)
contains who can *reach* that server; it does not authenticate anyone, and the file says so.

**Live probes, from a port-forward:**

```
GET http://hapi-fhir:8090/fhir/Patient        no headers, no credential  → 200, Bundle total=2
GET http://butano-service:8090/fhir/Patient   no headers, no credential  → 403
  "Missing mandatory trust headers: X-Tenant-Id, X-Correlation-Id, X-Actor-Id,
   X-Actor-Type, X-Purpose-Of-Use"
GET http://butano-service:8090/fhir/Patient   with the five headers      → 200, Bundle total=1
```

---

## 3. `FhirPublisher` is dead code, and the repoint is still worth doing

A search for the type outside its own file returns nothing; `ServiceClientConfig.ServiceEndpoints
.fhirBaseUrl()` is never read; all six `publish*` methods are HTTP PUT with no read path. So
repointing it changes nothing observable today — that is stated on the class rather than implied.

It is repointed anyway because the cost of the value being wrong is paid entirely at the moment
someone injects it, and at that moment it would have PUT clinical resources into the ungoverned
server with no consent check and no audit. Two further things are now written on the class for
whoever does wire it up: it bypasses the gateway where the consent PEP lives, and its subject
references would be rejected for the reason in §4.

Its source default (`http://localhost:8090/fhir`) was already correct — 8090 is butano-service's
port in the local port map. Only the estate value named the stock server.

---

## 4. The blocker the repoint had to clear first

Both live gateway callers named their subject `"Patient/" + cpid`. That resolved against `hapi-fhir`
only because a proof script had created Patients there with the CPID as their *logical id*. BUTANO
assigns its own ids and keeps the CPID in `Patient.identifier`, so the same string is a dangling
reference — and BUTANO enforces referential integrity on write.

**Probes (none of which persisted anything — a rejected write stores nothing):**

```
POST /fhir/Observation  subject = Patient/cpid-phase0g-does-not-exist
  → 400  HAPI-1094: Resource Patient/... not found, specified in path: Observation.subject

POST /fhir/Observation  subject = Patient?identifier=https://impilo.gov.zw/cpid|<unknown>
  → 404  HAPI-1091: Invalid match URL "..." - No resources match this search
         (the match URL IS evaluated at write time — the feature is on)

GET  /fhir/Patient?identifier=https://impilo.gov.zw/cpid|c08ba747-...   → 200 total=1, Patient/1
GET  /fhir/Patient?identifier=https://impilo.gov.zw/cpid|<unknown>      → 200 total=0
```

The last two are the positive and negative control on the same match URL: it resolves for a subject
the SHR knows and returns nothing for one it does not. So the callers now emit
`Patient?identifier=https://impilo.gov.zw/cpid|<cpid>`. Referential integrity stays on, no BUTANO
configuration changed, and a CPID with no Patient in the SHR is a non-delivery both callers already
refuse to report as SUCCESS — rather than a clinical record filed against a subject the record
cannot identify.

**SHR row count after every probe in this document: `Observation 3, Patient 1` — unchanged.**

---

## 5. `butano-fhir`: there is no migration, and it still cannot be retired

`butano_fhir.fhir_resource` holds **0 rows** and `butano_fhir.event_outbox` holds **0 rows**. There
is nothing to migrate. The workstream's stated danger — that "migrate" could destroy clinical
records — does not materialise here, and no `DELETE`, `DROP` or `TRUNCATE` was issued against any
database in the course of this work.

Zero rows is itself the finding. `ButanoProcedureClient` (live-wired into `TheatreService` and
`SpecimenCustodyService`) writes the signed operative note, theatre specimens and pathology
references to `butano-fhir`. The inpatient deployment sets **no base-url override**, so the client
falls back to `http://localhost:8289` — inside the inpatient pod, where nothing listens. Every
write fails transport and is swallowed by a best-effort catch. No operative note, specimen or
pathology reference has ever been recorded anywhere by this client.

**Retirement is blocked, and not on plumbing.** `Procedure.encounter` and
`DocumentReference.context.encounter` name an inpatient encounter id, and BUTANO holds no Encounter
for an inpatient case — nothing in `ButanoEventConsumer` creates one, and `Encounter` appears only
in read registries. Repointing at the gateway would therefore either write a dangling reference
(rejected with HAPI-1094) or drop the encounter link to make the write succeed. The second is data
loss dressed as a fix.

**Decision needed before `butano-fhir` can be retired:** how a theatre case anchors in the SHR —
an `Encounter` written from the PCT journey, or the encounter id carried as a business identifier
rather than a resolvable reference. That is a clinical-record decision, not a repoint.

`config/full-boot-service-classification.yml:341`, `config/full-boot-waves.yml:44` and the
`services/pom.xml` module entry are therefore **left in place**.

---

## 6. Stock `hapi-fhir`: repointed away from, not retired

Nothing in the repository now targets `hapi-fhir` as a FHIR write destination. The deployment,
its `hapi` database and its five rows are untouched — **nothing in `impilo-full-preview` was
scaled down, deleted or restarted.** Retiring the deployment is a separate authorised estate step,
and it must preserve the `hapi` database rather than drop it.

Two follow-ups that belong to that step, not to this branch:

- `deploy/networkpolicy/shr-hapi-fhir-ingress.yaml` still allows `fhir-gateway-service` to reach
  `hapi-fhir`. That allowance is now unused. Narrowing it is a containment change that needs its
  own authorised apply and its own positive controls — the file's own history records what
  happens when a policy is applied on an unverified port assumption.
- The five rows in `hapi` are **not migrated**. Their `Patient` identifier system
  (`http://impilo.mohcc.gov.zw/fhir/cpid`) is not the SHR's, so BUTANO's PII interceptor would
  reject them, and their subjects have no Patient in the SHR. Copying them in would mean either
  relaxing a guard or inventing a Patient. They stay where they are, and nothing is deleted.

---

## 7. A correctness defect found on the way

`RECONCILABLE_RESOURCES` in `ReconciliationService` drives the O-CPID → CPID merge walk. Three
types BUTANO already ingests were missing from it — `ImagingStudy` (PACS), `ClinicalImpression`
(examinations) and `DetectedIssue` (multimorbidity). Reconciliation never searched them, so after a
merge each stayed bound to the merged-away Patient and dropped out of the surviving patient's
chart, while the merge logged success and a `resourcesUpdated` count that excluded them.

`rewriteSubjectReference` is a second, independent way to lose the same records: a type listed in
the map but absent from that `instanceof` chain is re-saved with a reconciliation provenance tag
and counted as updated while still pointing at the old Patient — worse than the omission, because
the audit trail then asserts the rewrite happened.

Both halves are fixed and both are pinned by `ReconcilableResourceCoverageTest`, which recovers the
ingest set from `ButanoEventConsumer`'s own source rather than a second hand-kept list. `Composition`
was added separately: it arrives over HTTP from the teleconsult projection, so the source scan is
blind to it by construction, and a second test pins the four types the gateway's live callers send.

---

## 8. What landed

| Change | Proof |
|---|---|
| `ImagingStudy`, `ClinicalImpression`, `DetectedIssue` reconcilable | red-proved 4 ways (map entry, rewrite branch, scanner regex, scanner path) |
| Subject references become resolvable match URLs | red-proved on both callers by reverting to the literal form |
| `FHIR_GATEWAY_DEFAULT_TARGET_BASE` → `butano-service` | `check-shr-write-target.sh`, red-proved 4 ways |
| `FHIR_BASE_URL` (BFF + gateway) → `butano-service` | same guard |
| `HAPI_FHIR_URL` (butano-service self-advertisement) → own address | same guard; noted inert (binds `hapi.fhir.url`, servlet reads `hapi.fhir.server-address`) |
| `Composition` reconcilable | `http_ingested_types_are_reconcilable_too` |

**Not done, deliberately:** `butano-fhir` retirement (§5), `hapi-fhir` deployment retirement (§6),
NetworkPolicy narrowing (§6), the `hapi.fhir.server-address` property-name correction (changes every
link BUTANO advertises).
