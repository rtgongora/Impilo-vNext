# Experience Doctrine — Impilo ID (Health ID) as primary human identity

This rule applies across the **Experience Layer** and the sovereign registry/services it orchestrates.

## Canonical anchor

1. **Impilo ID (Health ID)** — a stable UUID issued by the national identity plane — is the **primary longitudinal human identity**. All person-scoped continuity (clinical, biometric, workforce, registry self-service) resolves to this anchor first.

2. **Provider public ID** (`VARAPI` provider token), **council registration numbers**, **licence numbers**, **employee numbers**, **national IDs**, **Moodle/Fundo user references**, **MusheX party references**, and similar values are **linked identifiers**. They must be stored and surfaced as **secondary** facts tied to the person/provider graph, never as parallel “roots” that bypass the Health ID.

## Varapi (provider registry)

- Table `varapi.provider.impilo_health_id` is the **mandatory anchor** for every provider profile (Flyway `V010__impilo_identity_anchor.sql`).
- `varapi.provider_identifiers` carries the **generic linked-identifier model**: `identifier_system`, `identifier_type`, `identifier_value`, `verification_state`, `is_primary`, effective dates, and optional `metadata` (JSONB).
- **Create provider** (`POST /v1/internal/providers`) accepts `impiloHealthId` on `CreateProviderRequest`. When `varapi.identity.require-impilo-health-id-on-provider-create` is `true` (default), the Health ID **must** be supplied by the caller after identity resolution; when `false`, Varapi generates a UUID for lab/bootstrap flows only.
- **Lookup by Health ID**: `GET /v1/internal/providers/by-health-id/{uuid}`, plus `/affiliations` and `/notices` (notices placeholder until a notices service exists).

## Facility and site relationships

- **Tuso** `practitioner_in_charge_assignment` stores both `provider_public_id` and optional `impilo_health_id` (V008). Kafka payloads from Varapi include both so mirrors never rely on opaque internal numeric IDs alone.
- **Indawo** `ind_site_assignments` adds optional `impilo_health_id` (V005) alongside `assigned_to_ref` so site work can bind to the same person anchor when the assignee is human.
- **Varapi** `provider_affiliations` already link `provider_id` → `facility_id` (Tuso-validated). When `varapi.identity.require-impilo-health-id-for-facility-affiliation` is `true`, the provider row must carry an Impilo anchor before new facility edges are accepted.

## Vito (client / biometric plane)

Vito continues to key biometric profiles and templates by **`healthId` (UUID)** — the same Impilo / Health ID. See `docs/vito/VITO_AND_IMPILO_IDENTITY.md`.

## APIs and UI

- Registry DTOs expose **`impiloHealthId`** next to `providerPublicId` so BFF and UI can label **Health ID** vs **Provider ID** vs **linked identifiers** explicitly.
- Experience UI copy should steer operators to pass **Health ID** for person resolution flows and **internal provider id** only where a registry-scoped technical key is required.

## Validation stance

New human-centric edges should **fail closed** when a requested link would orphan data off the Health ID anchor (duplicate Health ID per tenant, identifier already owned by another provider, affiliation without anchor when enforcement flags are on).
