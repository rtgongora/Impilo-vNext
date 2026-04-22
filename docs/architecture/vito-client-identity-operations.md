# VITO Client Identity Operations

## Intent

VITO V2 extends the client registry from a passive demographic store into an operational identity platform for Impilo vNext. The service remains the canonical owner of client identity resolution while adding governed workflows for:

- multi-channel registration initiation
- provisional identity capture
- evidence and verification
- matching and duplicate review
- merge review and survivorship
- relationship management
- Tshepo authorisation linkage
- stewardship and audit queues

## Core Model

The implementation separates identity concerns rather than collapsing them into one generic status:

- `client`: canonical client/person record and lifecycle status
- `client_registration`: front-door onboarding workflow and provenance
- `client_identifier`: provisional, canonical, and external identifiers
- `client_alias`: demographic variants and prior names
- `client_identity_evidence`: evidence dossier for proofing and later review
- `client_verification_review`: verification queue items and completed decisions
- `match_result` + `dedup_case`: probabilistic matching and duplicate review
- `client_merge_case` + existing `merge_history`: governed merge review and execution
- `client_relationship`: guardian, dependant, caregiver, next-of-kin, and proxy links
- `client_authorization_link`: identity-side linkage into Tshepo journeys and references
- `client_stewardship_action`: quality, correction, duplicate, and follow-up work queue
- `client_status_history` + `client_audit_event`: explicit lifecycle traceability

## Workflow Foundations

### Registration

Supported registration pathways:

- `SELF_INITIATED`
- `PROVIDER_ASSISTED`
- `FACILITY_REGISTRATION`
- `COMMUNITY_REGISTRATION`
- `OUTREACH_REGISTRATION`
- `VIRTUAL_REGISTRATION`
- `BULK_IMPORT`
- `INTEROPERABILITY_IMPORT`

Each registration preserves provenance including actor, channel, facility reference, provider reference, service context, and workspace context when available.

### Provisional Identity

Registrations can issue a provisional identifier immediately. This allows care and downstream workflow continuity without overstating assurance. Canonical Impilo ID issuance remains tied to verification review.

### Verification

Evidence is captured independently from the registration record. Submission into a pending verification state creates both:

- an open verification review item
- a stewardship action for governed follow-up

Verification approval can advance the client into `ACTIVE`, increase assurance, and issue the canonical Impilo ID.

### Matching and Deduplication

Matching remains delegated to the existing `MatchingEngine`. V2 adds operational handling around it:

- candidate duplicates are stored and surfaced as first-class work
- stewardship actions are created for unresolved duplicates
- confirmed duplicates can advance into merge review
- merge does not delete history; it executes through the existing `MergeService`

### Relationships and Authorisation Linkage

Relationships are identity-side references only. They support later Tshepo policy and consent decisions without moving policy logic into VITO. Authorisation links store identity-to-journey references while Tshepo remains the owner of authorisation and policy enforcement.

## Interoperability Boundaries

### TUSO

TUSO remains the source of facility legitimacy and workspace context. VITO only preserves facility provenance on registration records.

### VARAPI

VARAPI remains the source of provider legitimacy. VITO stores provider provenance as a reference on client registration, not as a professional registry.

### TSHEPO

TSHEPO remains the owner of authorisation and policy. VITO supplies identity state, relationship context, and authorisation references. The boundary is intentionally explicit.

## Constraints Preserved

- VITO remains the canonical owner of client identity truth.
- Registration initiation is not treated as full verification.
- Provisional clients are not treated as fully resolved identities.
- Matching candidates are not treated as confirmed duplicates.
- Merge remains non-destructive and continues through the existing merge guardrails.
- Trust context, internal vs external access modes, and outbox-based eventing are preserved.

## Extension Points

The current implementation intentionally leaves clean seams for future waves:

- stronger proofing adapters and evidence verification integrations
- more advanced survivorship rules and configurable match thresholds
- public/self-service registration surfaces beyond the Experience shell
- richer guardian and proxy workflows
- payment or fee checkpoints where future policy requires them
- analytics and registry quality dashboards
