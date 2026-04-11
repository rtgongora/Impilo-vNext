# ADR-0042 — Clinical knowledge: single deployable first, split-ready packages

## Status

Accepted (engineering baseline — 2026-04-11)

## Context

The programme requires nine bounded clinical capabilities (source, model, rules, assistant, prescribing, pathway, nudge, audit, admin console). Shipping nine independent Spring Boot services immediately multiplies CI, secrets, migrations, and SLO surface without proportional clinical content maturity.

## Decision

Implement **one** Spring Boot service `clinical-knowledge-platform-service` on port **8270** with **hexagonal-style packages** per bounded context, strict internal REST namespaces (`/internal/v1/clinical/...`), shared PostgreSQL schema `clinical`, and Kafka event contracts documented for future extraction.

## Consequences

- Positive: faster iteration on rules, seeds, and assistant orchestration; single Flyway chain; one Dockerfile for runtime pilots.
- Negative: blast radius if the JVM process fails; scaling is coarse until split.
- Follow-up: extract `clinical-rules-engine-service` and `clinical-governance-audit-service` when SRE and clinical governance require independent SLOs.

## Alternatives considered

- Nine microservices from day zero — rejected for delivery risk on empty clinical corpora.
- Embedding all logic only in `guidance-service` — rejected to keep general wellness chat separate from governed clinical evaluation.
