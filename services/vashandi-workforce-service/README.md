# Vashandi — Operational Workforce Service

Vashandi is the **operational workforce system-of-record**: it owns *where and how a
health worker actually works*, distinct from professional standing (Varapi) and
identity/tokens (Tshepo). Core entities: workforce **profiles**, org **memberships**,
and **assignments/postings** (the operative grant of work at a facility / workspace /
department). Around those it owns attendance, leave, rosters & shifts, virtual (on-call)
pools, theatre-case teams, training requirements, access-risk review, and analytics.

Schema `vashandi` (tables prefixed `vsh_`). Port **8087**. All controllers are internal
(`/v1/internal/vashandi/...`), reached behind the Envoy ext_authz → TSHEPO gate.

## Identity-program integration (Provider/Place ID program)

The Provider ID program (D-P3 / D-P7) makes Vashandi the operational half of a single
governed identity chain: **identity → assignment → work session → expiry teardown**.

- **Engagement + validity on assignments** (`V008__assignment_engagement_expiry.sql`) —
  `vsh_workforce_assignment.engagement_type` ∈ `PERMANENT | ROTATION | LOCUM | OUTREACH |
  TELEMED | SPECIALIST_POOL | SUPERVISORY | TRAINING`, with a CHECK that any non-PERMANENT
  engagement is end-dated, and a partial index on `end_date WHERE status='active'`.
  `engagement_type` is accepted on assignment create (`VashandiDtos.CreateAssignmentRequest`)
  and already serialized on the read path (the API returns the entity directly), so the
  shell surfaces engagement / validity / expiry on `/work/vashandi/assignments`.
- **Assignment materialisation from a provider access request**
  (`FacilityAccessApprovedConsumer`) — an APPROVED varapi `FACILITY_ACCESS` decision
  materialises a Vashandi assignment (`assignmentType=facility_access`, engagement + validity
  from the payload, `source_authority = VARAPI_ACCESS_REQUEST:{publicId}`). Fail-closed and
  idempotent; it never invents a missing profile. The shell shows a "from a provider access
  request" provenance chip when `source_authority` carries that prefix.
- **Expiry sweep → token teardown** (`AssignmentExpirySweep`) — a scheduled sweep flips
  expired active assignments to `ended` and emits `impilo.vashandi.assignment.ended.v1`
  (person anchor + facility + workspace + engagement). **tshepo-identity**'s
  `AssignmentEndedTokenConsumer` consumes it and revokes the matching `WORK_CONTEXT` scoped
  tokens — so an expired posting cannot keep a live work session.
- **Work-context read model** (`VashandiWorkContextController`,
  `GET /v1/internal/vashandi/work-context?actorId={healthId}`) — the anchor the shell's
  work-session hub (`/provider/workplace`) proves against before minting a `WORK_CONTEXT`
  token, and the source of the WHERE/WHAT context picker. Anti-enumeration: an unknown actor
  returns an empty context, never a 404.

See `docs/design/provider-clinical-place/provider-place-identity-program.md` (D-P3, D-P7)
and `docs/architecture/place-journey-doctrine.md`.
