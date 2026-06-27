# OROS External Interoperability Adapters — Runbook

The OROS diagnostics journey ships five standards-based interop adapters. All are **flag-gated and
OFF by default** — `/admin/integrations` (via `IntegrationStatusService`) reports each honestly as
configured / NOT_LIVE. This runbook covers enabling + integration-testing each against a
self-hosted counterparty.

## Counterparties

| Counterparty | Source | Used by |
|---|---|---|
| `hapi-fhir` (in `docker-compose.yml`) | already running | FHIR ServiceRequest-inbound, ImagingStudy-outbound |
| `orthanc` (in `docker-compose.yml`, 8042/4242) | already running | lightweight DICOMweb / worklist |
| `dcm4chee-arc` (in `docker-compose.interop.yml`, profile `interop`) | dcm4che images | DICOM MWL-outbound (REST + DIMSE) |
| HAPI HL7 MLLP sender/receiver | `ca.uhn.hapi` test tooling / `hl7-mllp` CLI | HL7 ORM-inbound, ORU-outbound |

Bring up the DICOM archive:

```bash
docker compose -f docker-compose.yml -f docker-compose.interop.yml --profile interop up -d dcm4chee-arc
# arc HTTP 8088, DICOM DIMSE 11112
```

## Adapter A — FHIR ImagingStudy-outbound
- Enable: `OROS_FHIR_IMAGINGSTUDY_OUTBOUND_ENABLED=true`, `OROS_BUTANO_BASE_URL=http://hapi-fhir:8080` (or the running hapi-fhir).
- Drive: link a study to an order (`POST /v1/orders/{id}/link-study`).
- Verify: `GET {fhir}/ImagingStudy?identifier=urn:oid:<studyUid>` returns the resource.

## Adapter B — FHIR ServiceRequest-inbound
- Enable: `OROS_FHIR_SERVICEREQUEST_INBOUND_ENABLED=true`.
- Drive: `POST /v1/fhir/ServiceRequest` with an R4 ServiceRequest (subject `Patient/<CPID>`).
- Verify: the response returns the created `orderId` (PLACED); re-POST with the same
  `identifier` is idempotent (same order).

## Adapters C/D — HL7 v2 ORM-inbound / ORU-outbound
- Inbound enable: `OROS_HL7_ORM_INBOUND_ENABLED=true`, `OROS_HL7_ORM_INBOUND_PORT=2575`,
  `OROS_HL7_ORM_INBOUND_TENANT_ID=<uuid>`, `OROS_HL7_ORM_INBOUND_FACILITY_ID=<uuid>`.
  Send an `ORM^O01` over MLLP to `:2575`; OROS creates the order and returns an ACK.
- Outbound enable: `OROS_HL7_ORU_OUTBOUND_ENABLED=true`, `OROS_HL7_ORU_OUTBOUND_HOST/PORT` →
  your receiver. Authoring a final report sends an `ORU^R01` (best-effort) and logs the ACK.
- Ports 2575 (inbound) / 2576 (outbound) — see `port-allocation.md`.

## Adapter E — DICOM MWL-outbound (REST or DIMSE)
- Mode: `OROS_DICOM_MWL_MODE=REST` or `DIMSE` (default `OFF`).
- REST: `OROS_DICOM_MWL_REST_BASE_URL=http://localhost:8088`,
  `OROS_DICOM_MWL_REST_PATH=/dcm4chee-arc/aets/DCM4CHEE/rs/mwlitems` (dcm4chee-arc; Orthanc has an
  equivalent worklist endpoint).
- DIMSE: `OROS_DICOM_MWL_DIMSE_HOST=localhost`, `OROS_DICOM_MWL_DIMSE_PORT=11112`,
  `OROS_DICOM_MWL_CALLED_AET=DCM4CHEE` — sends a UPS-Push N-CREATE.
- Drive: schedule an imaging order (`POST /v1/orders/{id}/schedule`).
- Verify: query the archive (arc UI / MWL C-FIND from a DICOM tool) for the scheduled item.

## Notes
- `dcm4che` is not on Maven Central — the `oros-service` pom adds the `https://maven.dcm4che.org`
  repository. First build of the module fetches it online (don't use `mvn -o` the first time).
- MWL DIMSE publishes UPS via N-CREATE (the modern worklist); a classic MWL C-FIND SCP is served by
  the archive once items exist. The REST path is the simplest to operate.
