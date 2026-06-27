# Tshepo GDHCN Trust & Certification — Design Note

**Status:** foundations in progress (Wave B). **Not** a claim of GDHCN conformance.
**Owner:** Tshepo (the Trust Plane). **Last updated:** 2026-06-24.

## Purpose

This note records how Impilo approaches the WHO **Global Digital Health Certification
Network (GDHCN)** trust model, what Wave B actually delivers, and — importantly — what is
**deliberately deferred**. It exists so that "GDHCN-ready" is never mistaken for
"GDHCN-operational".

## Doctrine

- **Tshepo IS the Trust Plane.** GDHCN participation is a Tshepo trust-network *use case*,
  implemented as a Tshepo capability. There is **no separate "Simbiso" or parallel trust
  service** — that would duplicate system-of-record trust ownership, which the architecture
  guardrails forbid.
- **Tshepo owns trust; other services provide data.** Butano/FHIR provides clinical content;
  Zibo provides terminology; Vito provides identity attributes. Tshepo verifies signatures,
  resolves keys, evaluates trust, and records the trust registry. It does not own clinical or
  demographic truth.
- **Wave B makes Tshepo GDHCN-*ready*. Later waves make GDHCN *operational*.**

## What Wave B delivers (foundations)

- **B4 — `libs/tshepo-trust-crypto`:** the trust-plane signature primitive — JWS verification
  with kid resolution, an algorithm allowlist, and a canonical `TrustError` model. The single
  building block all trust consumers verify against.
- **B5 — Trust Authority registry (tshepo-authz, V014):** `trust_authority`, authorised
  `trust_issuer_system`, and `trust_document_signer_cert` **metadata** (no private keys), with
  a status lifecycle and **honest readiness enums** (`NOT_STARTED` … `PRODUCTION_NOT_READY` —
  the top state is explicitly *not* "ready"). Mutations are dual-gated (admin role + step-up)
  and audited.
- **B6 — GDHCN readiness cockpit:** a real readiness-assessment surface (backend → BFF → UI)
  over the registry, with conservative maturity language, so the programme can see — honestly
  — how far from operational each trust domain is.

## Deliberately deferred (NOT built in Wave B)

These are **out of scope** for the foundations and must not be implied as present:

- **VDHC issuance** — Verifiable Digital Health Certificate generation/signing.
- **IPS / PHR packaging** — International Patient Summary / personal-health-record bundle
  assembly for cross-border exchange.
- **DSC operational workflows** — certificate issuance, rotation, distribution, and **revocation
  operations** (the registry holds DSC *metadata* and a status field only).
- **QR / signed-payload generation** and a **verifier app**.
- **WHO Trust Network Gateway adapter** — onboarding to the live GDHCN gateway, trust-list
  ingestion, and mutual onboarding.
- **Audit/monitoring dashboard** and **evidence-pack generation** for certification.

## Dependencies before GDHCN can become operational

GDHCN operation is gated on work owned by later waves, not Tshepo alone:

- **Wave C — identity assurance** (real risk/attestation; assurance levels) — VDHC binding needs it.
- **Wave D — multi-tenant isolation** — trust data must be strictly tenant-bound.
- **Wave F — Butano/FHIR/IPS** — clinical content + IPS packaging.
- **Wave G — privacy/governance** — consent + legal basis for cross-border exchange.
- **Zibo — terminology** — code-system alignment for IPS.
- **Production hardening** — TLS, DR, backup/restore, key custody (HSM/KMS), incident response.

## Readiness language (honesty contract)

Every readiness value in the registry/cockpit uses the conservative `TrustReadiness` ladder.
No entry may be marked beyond `PRODUCTION_NOT_READY` until the deferred items above are built
and a real certification assessment is passed. "Ready" in this codebase means *foundations
exist and are proven*, never *certified*.
