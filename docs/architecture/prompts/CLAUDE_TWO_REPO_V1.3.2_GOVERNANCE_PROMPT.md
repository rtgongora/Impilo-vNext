# Impilo vNext — Two-Repository Truth Recovery and v1.3.2 Governance Reconciliation

You are working from the clean governance worktree:

- `/opt/impilo/worktrees/Impilo-vNext-architecture-governance`

You also have access to:

- `/opt/impilo/repos/website-recovery/impilo-website-recovered`

Do not assume either repository is obsolete, complete or authoritative from its name.

## First: load and verify governance

Run:

```bash
bash scripts/architecture/verify-governance-pack.sh
```

Read, in this order:

1. `CLAUDE.md`
2. `docs/architecture/ARCHITECTURE_PRECEDENCE.md`
3. `docs/architecture/hybrid-federated-target-architecture-v1.3.2.md`
4. `docs/architecture/product-capability-architecture-v2.0.md`
5. `docs/standards/technical-standards-catalogue-v1.0.md`
6. `docs/architecture/supersession-notice-v1.0.md`
7. `docs/architecture/README.md`
8. existing plane doctrine, ADRs, nested `CLAUDE.md`/`AGENTS.md`, contribution rules, CI workflows and local quality scripts in both repositories

v1.3.2 is the latest working architecture. It is **not architecture-frozen**. Preserve its `[D]`, `[T]`, `[O]` and `[L]` classifications and obey its implementation gate. Do not describe v1.3.1 as approved or active; it is a superseded working draft.

Do not implement product features in this task.

# Phase 1 — recover the real user-facing product across both repositories

Trace how the current public, authenticated web, mobile and supporting API surfaces are actually assembled and deployed.

Inspect both repositories for:

- Git remotes, branches, worktrees and recent relevant commits;
- package manifests, monorepo workspaces and dependency links;
- frontend applications, route trees, middleware and navigation registries;
- shared components, copied source, generated source, mounted assets and symlinks;
- Dockerfiles, build scripts and exact build contexts;
- Docker Compose, Kubernetes, Helm and deployment manifests;
- local deployment, full-boot, preview and production scripts;
- reverse proxy, Traefik, ingress, BFF and API-gateway configuration;
- Next.js rewrites and build-time versus runtime environment variables;
- image repositories, image names, tags, digests and provenance records;
- public website, authenticated shell, citizen app, provider app and API boundaries;
- any deployment path that takes source or artefacts from both repositories;
- stale or duplicated surfaces no longer on a real request path.

Produce a surface-to-source matrix showing, for every current user-facing surface:

1. source repository and exact path;
2. build command and build context;
3. image or artefact produced;
4. deployment manifest and route/hostname;
5. runtime dependencies and proxy path;
6. evidence that the path is or is not used by preview and production;
7. duplicated or divergent implementation in the other repository;
8. confidence level and unresolved evidence.

Do not infer deployment truth from file names. Prove it through scripts, manifests, image metadata, running configuration or other concrete evidence.

# Phase 2 — validate v1.3.2's factual claims against current repository truth

v1.3.2 governs target doctrine, but its current-state claims remain evidence claims. For every material claim used to justify a rule, determine whether it is:

- confirmed in the current worktree;
- confirmed only at the cited recovery commit;
- changed since the recovery;
- contradicted by current code;
- present in the sibling website repository rather than the main repository;
- or not presently verifiable.

At minimum inspect the seams behind:

- browser/client-supplied authority headers;
- Envoy `ext_authz`, OPA/PDP and shadow/enforced modes;
- route registration and deny-by-default behaviour;
- the inactivity-lock exemption defect;
- notification scope coming from query parameters versus session audience;
- `EMPTY` versus `UNAVAILABLE` false-success paths;
- `GatewayIntent`, `returnTo` and post-login arbitration;
- national and node experience resolver placement;
- work-context minting and audience-bound session transitions;
- shared-workstation clearing and My Life policy;
- journey persistence, draft content, locking and actor assignment;
- Action Centre versus notification-service and existing inbox/read-state migrations;
- delivery-context, clinical-setting, care-setting and form/pathway scope vocabularies;
- web/mobile shared authority and resolver behaviour;
- node-to-national synchronous clinical dependencies;
- National Core/Hospital Node profile packaging and any undeclared fork;
- event `tenant_id`/`pod_id`, provenance and origin-node fields;
- Butano/FHIR write and read paths and any split-brain stores;
- build-time endpoint baking and runtime endpoint discovery;
- integration configuration hierarchy;
- Tshepo's actual component boundaries.

For each finding cite repository, file, symbol or manifest, current behaviour, target rule, severity and whether action is allowed before freeze.

# Phase 3 — reconcile repository governance with v1.3.2

Review the newly added governance material. Update it only after Phases 1 and 2 are evidenced.

Required outcomes:

1. v1.3.2 is the active working Target Architecture path everywhere.
2. v1.3.1 remains historical only and is not referenced as the current baseline.
3. Existing plane doctrine and architecture indexes remain intact.
4. Product Architecture and Technical Standards are subordinate successor drafts, not competing frozen constitutions.
5. Their status and source-lineage wording is corrected where v1.3.2 changes it.
6. `CLAUDE.md` remains concise and imports the governance rules without replacing existing project rules.
7. Any sibling-repository governance is based on proven product ownership, not assumption.
8. Any contradiction between v1.3.2 and repository evidence is recorded explicitly; do not silently rewrite doctrine or manufacture evidence.

# Phase 4 — design and, where safe, add enforceable repository controls

Only settled invariants and items on the **may-proceed** side of the v1.3.2 gate may become executable controls in this task.

The gate permits:

- deny-by-default for unregistered routes;
- fixing/proving the inactivity lock;
- server-derived notification scope;
- `EMPTY` versus `UNAVAILABLE` separation;
- measuring clinical composition seams;
- P5/P6/P7 preparation;
- non-controlling drafts of the seven packs.

The gate blocks:

- canonical journey-store implementation;
- Action Centre data-model implementation;
- freezing the experience-contract schema;
- administrative-authority selector implementation;
- P3 clinical-pathway surfacing implementation;
- node-local journey projection;
- declaring A87–A103 a complete acceptance suite.

For every proposed machine check provide:

1. invariant;
2. repositories and paths covered;
3. detection method;
4. false-positive and false-negative risks;
5. passing fixture;
6. deliberately broken fixture;
7. evidence that the check goes red;
8. local command;
9. CI execution point;
10. whether the rule is `[D]`, `[T]`, `[O]` or dependent on `[L]`.

Do not build a check that freezes an unresolved schema or legal decision.

# Deliverables

Return, in this order:

1. Executive verdict: how the current user-facing product is assembled from the two repositories.
2. Surface-to-source/build/deployment matrix.
3. v1.3.2 factual-claim validation table.
4. Contradiction and evidence-drift register.
5. Architecture document-status and precedence matrix.
6. Proposed repository ownership and consolidation model.
7. Governance files changed, with reasons.
8. Machine-enforceable rules added or proposed, including deliberate red-test evidence.
9. v1.3.2 implementation-gate compliance table.
10. Unresolved Product Owner, clinical-governance, legal, privacy, operational or data-protection decisions.
11. Exact commands run and files changed.

Do not commit, merge, move or delete either repository. Do not implement must-wait product features. Stop after the report and permitted governance/control changes are ready for review.
