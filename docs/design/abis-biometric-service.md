# ABIS / Biometric Service — boundary design & ownership justification (Identity Contract §11, E1)

**Status:** DESIGN + ownership justification (Wave E1). Service scaffold is a
bounded follow-on that needs port allocation + an authorized full-boot to deploy.
**Decision:** D6 (ratified) — biometric templates move behind a dedicated ABIS
boundary; VITO keeps only consent, status, and an opaque reference.

## Ownership justification (production guardrail)

> "Before introducing a new service, prove no existing service already owns that
> capability. Do not create duplicate system-of-record functionality."

Today VITO owns biometric **template custody** (`vito.biometric_template`,
BYTEA blobs) alongside identity/demographics. This conflates two different
trust concerns in one store: PII demographics and irreversible biometric
templates. D6 separates them because:

1. **Template custody is a distinct SoR** from client demographics — it has its
   own vendor SDK, key management, matching-engine lifecycle, and per-modality
   thresholds. No existing service owns population biometric matching; VITO's
   `FailClosedMatchingEngine` is a placeholder, not an ABIS.
2. **Blast-radius isolation** — a template store compromise must not be a
   demographics compromise, and vice versa. Co-residence in VITO defeats that.
3. **The SHR must never receive templates** (§11) — a dedicated boundary makes
   that structural.

This is therefore **not** duplicate SoR: template custody is being *extracted*
from VITO into its rightful owner, not duplicated. VITO stops being the
template SoR and becomes a biometric *status/consent* holder.

## Boundary

```text
VITO (registry plane)                    ABIS service (trust plane, NEW)
  · biometric_profile                       · biometric_template (encrypted blobs)
      - consent_ref                          · per-modality engines (vendor SDK seam)
      - status (ENROLLED/…)                  · thresholds per modality (config)
      - opaque template_ref  ───────────────▶ · verify (1:1) / identify (1:N)
      - quality/algorithm meta               · fail-closed default engine
  (NO template bytes)                        · governance events (enroll/verify/identify)
        │                                            │
        └───────────── TSHEPO ────────────────────────┘
                biometric-subject → HID mapping
```

- **VITO retains:** `biometric_profile`, `biometric_enrollment_event`,
  `biometric_verification_event`, `biometric_identification_event`,
  `biometric_exception_record` (the governance/consent/status ledger), plus an
  opaque `template_ref` pointing into ABIS. VITO drops `biometric_template`
  (the encrypted blob) — that column/table moves to ABIS.
- **ABIS owns:** `abis.template` (encrypted template, modality, position,
  quality, algorithm version, `subject_ref`), the `BiometricMatchingEngine`
  interface + `FailClosedMatchingEngine` default (moved from VITO), and the
  vendor-SDK bean seam. Keys via tshepo-keys custody.
- **TSHEPO** holds the biometric-subject → HID mapping (a biometric subject is
  keyed by an opaque `subject_ref`, resolved to HID only in the trust core).

## Rules (from §11, carried into the service)

- **1:1 verification** is routine (after a candidate is identified).
- **1:N identification** is restricted to enrolment / recovery / deduplication
  and returns **candidates for adjudication, never an automatic merge**.
- Thresholds are approved **per modality** (fingerprint / face / iris),
  versioned with the algorithm.
- Every operation records consent ref, device, operator, template quality,
  algorithm version; Tshepo biometric-policy evaluation precedes every op.
- **Fail-closed:** with no vendor engine configured, verify → UNAVAILABLE and
  identify → empty candidate list. Urgent care is never blocked by a biometric
  failure (care-first).
- The SHR never receives templates or biometric identifiers.

## Registry entry (to add when the scaffold lands)

```yaml
  - id: abis-service
    maven_module: abis-service
    primary_plane: trust
    plane: trust
    domain: biometric-identity
    sovereign: true
    sovereign_group: ABIS
    primary_protocol: rest
    default_http_port: <allocate — next free in docs/runbooks/port-allocation.md>
    product_names: [ABIS]
    system_of_record_for:
      - Biometric templates (encrypted)
      - Per-modality matching thresholds
    consumes_from: [tshepo-authz-service, vito-service]
    exposes_to: [vito-service]
    forbidden_responsibilities:
      - must-not-own-identity-assurance-policy
      - must-not-store-demographics-or-pii
      - must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries
```

## Migration (extraction, preview = wipe-and-reseed)

1. New `abis-service` with `abis.template` (encrypted blob custody).
2. VITO migration: drop `biometric_template.template_data` blob (keep the
   profile/event tables); add `template_ref` to `biometric_profile`.
3. VITO `BiometricService` calls ABIS over REST for enroll/verify/identify
   instead of storing bytes; the `FailClosedMatchingEngine` moves to ABIS.
4. Preview estate has no real templates (fail-closed), so no data migration —
   the reseed path simply targets the new boundary.

## Sequencing

Design + ownership justification this wave (E1). Scaffold + extraction is a
bounded follow-on wave requiring port allocation and an authorized full-boot
(new-service deploy). Until then VITO remains the interim template holder with
the fail-closed engine; no behaviour regresses.
