# Impilo vNext Architecture Precedence

**Status:** Effective repository interpretation rule.
**Date:** 2026-08-04.

## 1. Governing hierarchy

When two documents disagree, the higher layer prevails:

1. **Hybrid / Federated Target Architecture v1.3.5**
   Complete active document: `docs/architecture/hybrid-federated-target-architecture-v1.3.5.md` — the versioned file is the self-contained, historically durable architecture; there is exactly **one** complete active copy.
   Convenience pointer: `docs/architecture/vnext-hybrid-federation-target-architecture.md` — a short pointer naming the active version, status and path; never a second copy.
   Highest-precedence working architecture. It is **not architecture-frozen**; freeze requires Product Owner sign-off. Its explicit implementation gate governs what may and may not be built before freeze.
2. **Product, Capability and Plane Architecture**
   `docs/architecture/product-capability-architecture-v2.0.md`
3. **Technical Standards Catalogue**
   `docs/standards/technical-standards-catalogue-v1.0.md`
4. **Domain and Experience Completion Packs**
   `docs/experience/packs/` and other approved domain specifications.
5. **Executable implementation artefacts**
   ADRs, OpenAPI, AsyncAPI, JSON Schema, FHIR profiles, Rego policies, migrations, Helm profiles, tests and runbooks.

A lower-level artefact may refine a higher-level decision. It MUST NOT contradict it. A contradiction requires an ADR and an explicit update to the affected higher-level document before implementation.

## 2. Statement classification

The Target Architecture classifies substantive statements as:

- `[D]` doctrine — changed by a PO ruling recorded as an ADR;
- `[T]` technical design — changed by a new ADR;
- `[O]` operating-model decision — changed by the service owner and PO;
- `[L]` awaiting legal or governance determination — not to be guessed or silently resolved in code.

Claude, developers and CI rules must preserve these distinctions. Repository evidence may disprove a current-state claim, but it does not silently overturn doctrine.

## 3. Immediate supersession rule

Files under `docs/architecture/archive/` are retained for lineage only. They are not controlling architecture, they carry a supersession banner, and nothing may be implemented from them. **v1.3.1**, **v1.3.2**, **v1.3.3** and **v1.3.4** are all superseded working drafts, and **not one of them was ever frozen**: each corrected its predecessor and was then stopped at its own freeze review. **v1.3.5** preserves every settled decision from all four and is the active working architecture candidate. It is **not frozen**.

The following legacy assumptions remain withdrawn:

- client-supplied tenant, pod, facility, actor, provider, purpose or work-context values as load-bearing authority;
- a single application service representing the complete Tshepo trust layer;
- password grant as the standard interactive sign-in model;
- private nodes requesting legal or clinical authority in a registration payload;
- a central projection silently overriding the authoritative origin of a clinical fact;
- Ring 0 being interpreted as a deployment location;
- a node or pod identifier being treated as a tenant, organisation, facility or legal controller.

See `docs/architecture/supersession-notice-v1.0.md`.

## 4. v1.3.5 implementation gate

### May proceed before freeze

- deny-by-default for unregistered routes;
- repair the never-firing inactivity lock;
- derive notification scope from the server-side session;
- separate `EMPTY` from `UNAVAILABLE`;
- measure the clinical composition seams;
- prepare accessibility, design-system and usability work (P5/P6/P7);
- draft all seven Experience Completion Packs as non-controlling drafts.

### Must wait for architecture freeze

- canonical journey-store implementation;
- Action Centre data-model implementation;
- freezing the experience-contract schema;
- administrative-authority selector implementation;
- clinical pathway surfacing implementation under P3;
- node-local journey projection;
- treating A87–A103 as a complete acceptance suite.

Analysis, repository-truth recovery and non-controlling pack drafting are permitted. A must-wait item may be measured or specified, but not implemented or frozen.

## 5. Required reading by change type

| Change area | Required source |
|---|---|
| Federation, trust domains, hosting, nodes, continuity, record authority | Target Architecture v1.3.5 |
| Product ownership, planes, rings, capability boundaries | Product Architecture |
| APIs, request context, events, FHIR, reliability, finance, AI | Technical Standards Catalogue plus v1.3.5 where it amends the model |
| Navigation, journeys, UI states, self-service, clinical work experience | Target Architecture v1.3.5 plus the applicable Experience Completion Pack |
| Architectural departure | Existing ADRs plus a new or amended ADR before implementation |

## 6. Interpretation law

Repository code is evidence of current behaviour, not automatic product truth. A shipped implementation that contradicts a governing architecture decision is a defect unless an approved ADR has amended that decision. Conversely, a current-state statement in architecture that repository evidence disproves must be corrected transparently; it must not be defended merely because it appears in a document.
