# Scenario A — Frontline Clinical Journey (Runbook)

Login → work context → shift check-in → patient → queue → encounter → lab →
imaging → teleconsult with media token. Proven end-to-end on the live preview
estate (24 checks).

## Proof script

```bash
bash scripts/e2e/scenario-a-clinical-journey.sh          # phases 1-9
bash scripts/test/run-scenario-a-smoke.sh                # gate-wrapper form (post-deploy lane)
```

Runs from the preview VM (`kubectl` context for namespace `impilo-full-preview`).
Transport is `kubectl exec deploy/experience-bff -- curl` with full trust headers.

## Preconditions

- Full preview estate serving HEAD (`bash scripts/preview/full-boot.sh` lane).
- Seeds: `bash scripts/operator/seed-scenario-a-estate.sh` (idempotent) — TUSO
  facility Harare Central (`f1000000-0000-0000-0000-000000000001`), golden
  patient, VARAPI providers, workforce-governance assignments, and chain
  verification (login → session contract has non-empty `workAssignments`).
- Keycloak users reconciled: `bash scripts/operator/reconcile-keycloak-realm-users.sh`
  (unmanagedAttributePolicy ADMIN_EDIT + attribute sync + `basic` scope — without
  these the BFF mints a random person anchor per login).
- Personas: `dr.mapfumo` (PROV-ZW-00001, anchor `c0000000-…-0001`),
  `nurse.chienda` (PROV-ZW-00007, anchor `c0000000-…-0007`), password `ImpiloTest123!`.
- Tenant: `00000000-0000-4000-8000-000000000001` (canonical UUID — string
  tenants null-out in TUSO/msika-flow).

## What each phase asserts

1. **Login** — BFF `POST /internal/v1/auth/login`, health_id claim → linked IDs → provider ID.
2. **Work context** — session contract `workAssignments` non-empty (Work tab renders).
3. **Shift** — vashandi ad-hoc check-in via BFF proxy; `X-Shift-ID` then flows on all calls.
4. **Patient** — VITO register/search via `/registry/clients`.
5. **Queue** — WAITING → CALLED → IN_CONSULTATION.
6. **Encounter + lab** — PCT encounter carries `shift_id` (V029); OROS order → RESULT_AVAILABLE; timeline shows the result.
7. **Imaging** — order → minimal DICOM upload → Orthanc → pacs register/forward/sync → radiology report-link → Kafka → OROS RESULT_AVAILABLE.
8. **Teleconsult** — VITO-guarded create (422/503 fail-closed), SPECIALTY_POOL routing, consent, submit, accept.
9. **Media** — LiveKit token issued and validated in-cluster; `/end` deletes the room.

## Event loops this journey depends on (preview opt-ins)

Kafka listeners default OFF estate-wide; `values-full-preview.yaml`
`fullBootServices.<svc>.env.SPRING_KAFKA_LISTENER_AUTO_STARTUP: "true"` opts in
oros + pacs-adapter (imaging loop). Rollback = remove the env line.

## Known limits

- **Browser video join** needs firewall ports 7880/7881 TCP + 7882/UDP opened to
  the VM (NAT 41.57.127.235 → 10.50.1.67 + ufw allows). Until then media is
  proven to token/room level only; Playwright TrackSubscribed assert is pending.
- **No real DICOM modality** — imaging ingestion is proven via adapter-registered
  studies forwarded to Orthanc, not device acquisition.
- LAN-side browsers hit hairpin NAT on the public IP; use the LAN address.

## Playwright

`ui/one-ui-shell/e2e/scenario-a-clinical-journey.spec.ts` (2 specs) drives the
windowed shell with consent-gate handling against `http://41.57.127.235`.
