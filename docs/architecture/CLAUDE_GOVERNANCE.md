# Impilo vNext Repository Governance for Claude

@docs/architecture/ARCHITECTURE_PRECEDENCE.md

## Mandatory operating rules

1. Recover repository and deployment truth before changing implementation. Cite concrete repository paths, symbols, build contexts, images, request paths, persistence paths and policy paths.
2. Read `hybrid-federated-target-architecture-v1.3.2.md` as the latest working architecture — the versioned file is the complete active document (`vnext-hybrid-federation-target-architecture.md` is only a pointer to it). It is not frozen. Obey its implementation gate.
3. Do not infer authority from browser headers, deployment location, operator identity or UI state.
4. Client-supplied tenant, trust-domain, facility, provider, actor, purpose, assurance or work-context assertions are never load-bearing.
5. Tshepo is the complete trust layer. Keycloak, OPA, Envoy, Mvumo, context, sessions, audit, federation trust, risk and supporting services are components within Tshepo.
6. National Core, Hospital Node and Facility Edge use one governed artefact set. Profile-specific components and behaviour must be declared, configuration-driven, compatibility-governed and separately tested; forked hospital implementations are prohibited.
7. Routine local clinical care must not synchronously depend on the National Core.
8. Personal, professional, work, organisation, regulatory, node-operations and platform sessions remain audience-bound.
9. A projection or cache never silently overwrites or becomes the authoritative origin of a clinical fact.
10. `delivery_context` and `clinical_setting` are separate. `clinical_setting` is derived from the clinical journey and never guessed from location.
11. Administrative authority is scoped. An unscoped label such as `ORG_ADMIN` grants nothing by itself.
12. Freshness and landing precedence are capability-scoped. A failure relevant to node Work must not hijack unrelated My Life or My Professional journeys.
13. Entry intent declares the required audience, assurance and work context. An eligible user is offered the correct audience transition; tokens are never silently upgraded across audiences.
14. A node stores, counts and resolves only node-allow-listed Work, facility, organisation and node journeys. Personal and professional journey references do not leak to an employer node, even as labels or counts.
15. During disconnection, a node may rely on recognised national signed truth, institutionally authoritative local truth granted by the federation agreement, and approved offline instruments. It may not invent an authority class.
16. Journey persistence must preserve actual draft work, versioning, attachment references, actor assignment and surfaced conflicts. Position-only resumption is not completion.
17. Experience projection TTL, action due date, draft expiry, domain-case lifecycle and retention are separate lifecycles. A projection never closes or expires its source case.
18. Work receives only the minimum safety consequence required from professional standing; professional case detail and correspondence remain in My Professional.
19. `EMPTY`, `UNAVAILABLE`, `DEGRADED`, `DENIED`, `STALE` and `LOADING` are distinct product states.
20. Web and mobile may differ in presentation. They may not differ in authority, policy, journey state or refusal decisions.
21. On `MANAGED_SHARED` devices, My Life defaults to `BLOCK`; an approved isolated step-up may be offered. Fast switching clears patient, personal, search, draft, clipboard and decrypted context and preserves only a safe operational location.
22. Nompilo may explain, recommend, navigate, summarise and visibly prefill. It must not submit, consent, disclose, prescribe, dispense, order, approve, countersign, change authority or claim unobserved success.
23. P5 accessibility/content, P6 design system and P7 usability are cross-cutting and begin immediately; they are not late polish.
24. Do not claim completion because a route, component, enum, migration or service exists. Prove the real end-to-end user, request, persistence, policy, failure and recovery paths.
25. A conformance check is not proven until it has deliberately gone red when the protected condition is broken.

## Before editing

For every non-trivial task:

1. Read the precedence file and task-relevant controlling documents.
2. Inspect all repositories that may contribute source, assets, images, routes or deployment configuration to the affected surface.
3. Find existing `CLAUDE.md`, `AGENTS.md`, ADRs, architecture indexes, CI checks and local quality scripts that apply.
4. State current behaviour with evidence and distinguish it from target doctrine.
5. Identify contradictions, evidence drift and unresolved PO, clinical, legal or data-protection decisions.
6. Check the v1.3.2 implementation gate before touching code.
7. Only then change permitted code or documents.

## Architecture change discipline

Do not silently place doctrine in code comments, prompts or `CLAUDE.md`. A genuine architecture change requires the correct `[D]`, `[T]`, `[O]` or `[L]` treatment, an ADR where required, and an update to the governing document. Instruction files operationalise architecture; they do not replace architecture governance.
