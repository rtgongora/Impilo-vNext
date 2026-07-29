# matcher-engine — Internal Only

> **Classification:** Q — internal-only; no actor-facing UI required.

## Purpose

Matcher Engine is the biometric matching compute component of the ABIS group under the **trust**
plane. It performs modality comparison only — face (`OnnxFaceEmbedder`, `FaceMatcherImpl`),
fingerprint (`FingerprintMatcherImpl`, SourceAFIS) and iris (`IrisMatcherImpl`) — dispatched by
`MatchDispatcher`. It holds no person record, no template store and no clinical state.

## Why no user-facing UI

- Its only caller is `abis-service`, via `BmeClientMatchingEngine` over the `abis.matcher-engine`
  base URL. Nothing else in the estate calls `/v1/engine/*`.
- Citizens and providers reach biometric identity through `abis-service`
  (`ui/one-ui-shell/src/hooks/queries/useAbisBiometric.ts`); ABIS owns enrolment, dedup and the
  audited decision. A direct experience surface onto raw match scores would bypass that ownership.
- It is stateless compute: no entities, no repositories and therefore no migrations. The absence of
  a database is correct for this service, not a gap.

## Exposure

| Surface | Status |
|---------|--------|
| REST API | `/v1/engine/capabilities`, `/extract`, `/verify`, `/identify` — service-to-service only |
| Experience BFF | **Not required** — abis-service is the sovereign surface |
| one-ui-shell | **Not required** |
| Mobile | **Not required** |

## Tests required

- Per-modality matcher unit tests (`FaceMatcherImplTest`, `FingerprintMatcherImplTest`,
  `IrisMatcherImplTest`) — present.
- Client-side contract coverage lives with the caller in
  `abis-service/…/engine/BmeClientMatchingEngineTest`, which pins the `/v1/engine/*` request shapes.

## Registration history

The service existed in the tree but was absent from `docs/registry/services-registry.yaml` until
`4ae10ccfd` (2026-07-20) registered it so `build-full-vnext-images.sh --full-estate` would stop
shipping stale images. Registration made it visible to the product-truth scanner for the first time,
which surfaced its (pre-existing) B/C/D/I/N gaps. Those gaps were measured against user-facing
expectations that never applied to it; this document records the internal-only classification that
the scanner reads.

## Related services

See [product-truth-backend-ui-traceability.md](../product-truth-backend-ui-traceability.md).
