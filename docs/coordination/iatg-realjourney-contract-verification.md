# IATG Real-Life Journeys — Contract Verification (C / D / E / F)

**Scope.** Section-3 contract verification for the browser forms in journeys **C (facility),
D (provider access), E (facility mode), F (adjudication)**. For each submit form this maps the
**browser payload → BFF DTO → downstream service DTO**, names the durable id + follow-up read, and
records the honest failure-code handling. Governance payloads A/B were verified in the E3 stream;
this doc folds in C/D/E/F per the requirement not to limit static verification to A/B.

**Method.** Every row is read from source (controllers + DTOs + UI hooks) at the RJ stream tip in
worktree `/home/user/wt-rj`; `file:line` citations are given so a reviewer can re-confirm. No
payload shape is inferred.

**Verdict at authoring time:** all four journeys' primary submit forms have
**field-name-aligned** contracts UI→BFF→downstream, a durable id, and a follow-up read. One live
defect was found and fixed this stream (Facility-Mode `APPROVED`→`ACTIVE` gate). Residual
notes are listed per journey.

---

## Journey C — Facility legitimacy + facility claim

### C1 — Facility claim submit (`/facility/claim`)
| Layer | Contract | Source |
|---|---|---|
| Browser payload | `{ facilityUuid: string, consent: true, role?, evidenceRef? }` | `ui/one-ui-shell/src/hooks/queries/useFacilityClaim.ts:68-71,114-121` |
| BFF endpoint | `POST /api/v1/facility-claim/appoint` — requires `facilityUuid`, `consent===true` (else `400 CONSENT_REQUIRED`); claimant forced to `X-Actor-ID`, never body | `experience-bff/.../controller/FacilityClaimController.java:175-221` |
| Downstream | `POST /v1/internal/facilities/{facilityId}/admin-claim` (path = canonical UUID); body `SubmitClaimRequest{personHealthId, role, evidenceRef, validFrom, validTo, notes}` | `TusoFacilityClaimClient.java:57-64`; tuso `FacilityClaimController.java:63-70`; `FacilityClaimDtos.java:49-56` |
| Success | BFF `201` `{submitted:true, appointmentId, facilityUuid, personHealthId(masked), role, approvalState}`; row persists **PENDING** | `FacilityClaimService.java:120-164` |
| Durable id | `appointmentId` (Long) ← downstream `AppointmentView.id` | `FacilityClaimDtos.java:63-74` |
| Follow-up read | `GET /api/v1/facility-claim/appointments?facilityUuid=` → `AppointmentView[]` w/ `approvalState` | `FacilityClaimController.java:108-127` |

### C2 — Facility status-composite (legitimacy panel read)
| Layer | Contract | Source |
|---|---|---|
| Browser | `GET /internal/v1/facilities/{id}/status-composite` (id = canonical UUID) | `useFacilityStatusComposite` |
| BFF | fail-closed proxy → `502 TUSO_UNAVAILABLE` on upstream error, `404` on null | `experience-bff FacilityController.java:177-196` |
| Downstream | tuso `GET /v1/facilities/{facilityUuid}/status-composite` → `FacilityStatusCompositeResponse{sourceLegitimacy[], platformAccessAllowed, reasons[], regulatoryStatus}` | `FacilitySourceLegitimacyDtos.java:68-78` |
| Enums | source ∈ `HPA_LEGAL, MINISTRY_OPERATIONAL, PLATFORM_OPERATIONAL`; status ∈ `REGISTERED_CURRENT, EXPIRED, SUSPENDED, KNOWN_NOT_COMPLIANT, PENDING_VERIFICATION, NOT_FOUND, GOVERNMENT_OPERATIONAL_EXCEPTION` | `FacilityLegitimacySource.java:12-22`, `FacilityLegitimacyStatus.java:13-39` |
| Mandatory reason | `GOVERNMENT_OPERATIONAL_EXCEPTION` requires non-blank `reason` + explicit `allowedOnPlatform` | `FacilitySourceLegitimacyDtos.java:25-31` |

**C verification checklist:** payload shape matches ✓ · enums match ✓ · durable id (`appointmentId`)
✓ · follow-up read ✓ · no local-only terminal state (claim persists PENDING) ✓ · refresh-safe ✓ ·
502/404 honest (fail-closed notice `data-testid=facility-legitimacy-unavailable`) ✓ · `409` on
not-platform-allowed / already-active-admin propagated ✓ (`FacilityClaimService.java:131-143`).
**Residual note:** the seed must resolve the **canonical** `facility_uuid` (search→id→
`/configuration`), not the search `facilityUid` (a master-pack import string), or legitimacy rows
land on a uuid the panel does not read by — the RJ seed does this (`iatg-realjourney-seed.sh`
`resolve_facility_uuid`).

---

## Journey D — Provider trust + Request Provider Access

### D1 — Provider access-request submit (`/citizen/provider-claim`)
| Layer | Contract | Source |
|---|---|---|
| Browser payload | `{ requestType, profession?, councilCode?, councilNumber?, ecNumber?, organizationRef?, evidenceSummary? }` | `useProviderAccessRequest.ts:44-53,69-71` |
| Enum (UI) | `NEW_PROVIDER, ORG_INVITATION, COUNCIL_NUMBER, EC_NUMBER, HAVE_PROVIDER_ID, RECOVER` | `useProviderAccessRequest.ts:23-30` |
| BFF | `POST /api/v1/provider-claim/access-request` — `requestType` required (else `400 REQUEST_TYPE_REQUIRED`); re-wraps to `200 {data:view}` | `ProviderClaimController.java:348-365` |
| Downstream | varapi `POST /v1/internal/providers/access-requests` `201`; DTO `SubmitProviderAccessRequest{requestType@NotBlank, profession, councilCode, councilNumber, ecNumber, organizationRef, evidenceSummary}` | `ProviderAccessRequestController.java:31-38`; `SubmitProviderAccessRequest.java:10-18` |
| Enum (backend) | **matches UI exactly** — `ProviderAccessRequestType{NEW_PROVIDER, ORG_INVITATION, COUNCIL_NUMBER, EC_NUMBER, HAVE_PROVIDER_ID, RECOVER}` | `ProviderAccessRequestType.java:12-19` |
| Durable id | `publicId` (format `PAR-XXXXXXXX`) on `ProviderAccessRequestView` | `ProviderAccessRequestService.java:156-164`; `ProviderAccessRequestView.java:11-26` |
| Follow-up read | `GET /api/v1/provider-claim/status[/{publicId}]` → same view w/ `status`, `nextActor`, masked `councilNumberMasked`/`ecNumberMasked` | `ProviderClaimController.java:368-394` |
| Status routing | `COUNCIL_NUMBER→PENDING_COUNCIL_REVIEW`, `EC_NUMBER→PENDING_EMPLOYER_REVIEW`, `ORG_INVITATION→PENDING_ORGANIZATION_REVIEW`, `NEW_PROVIDER(already-linked)→DUPLICATE_SUSPECTED (nextActor NATIONAL_ADMINISTRATOR)`, `HAVE_PROVIDER_ID/RECOVER→SUBMITTED (defer to claim/recover lanes)` | `ProviderAccessRequestService.java:66-142` |

### D2 — Provider claim (redeem token) & recovery
| Form | BFF | Downstream | Durable / terminal |
|---|---|---|---|
| Claim | `POST /api/v1/provider-claim/claim {claimToken, consent:true}` (`ProviderClaimController.java:135-168`) | varapi `POST /bootstrap/claim {claimToken, claimantHealthId}` `200` | `{linked:true, providerPublicId(masked), lifecycleStatus=CLAIMED}`; `409` one-person-one-profile |
| Recover | `POST /api/v1/provider-claim/recover {evidence:{type,source,ref,confidence}}` (`ProviderClaimController.java:275-339`) | varapi `/recovery/initiate` then `/recovery/complete`; asserts `providerPublicId` unchanged (else `502 RECOVERY_INTEGRITY`) | `RECOVERED`/`404 MATCH_NOT_FOUND`/`409`; **never mints a new id** |

### D3 — Four-block trust profile (`/citizen/wallet/trust`)
`GET /api/v1/trust/profile/{healthId}` → four blocks each with `sourceOfRecord` + independent
`status` (a block may be `UNAVAILABLE` without poisoning others). EC is masked (`maskedEcNumber`);
CONFLICT must not expose another Health ID. (`TrustProfileComposer.java:76-81,94-247`.)

**D verification checklist:** payload shape matches ✓ · **UI enum ≡ backend enum** ✓ · required
fields surfaced (status/nextActor/reason on status page) ✓ · durable `publicId` ✓ · follow-up read
✓ · no local-only state (varapi row persisted) ✓ · refresh-safe ✓ · masked evidence (council/EC)
✓ · no duplicate id on recovery ✓ · `400/404/409/502` honest + propagated (`propagate()` never
remaps 409, `ProviderClaimController.java:452-462`) ✓.

---

## Journey E — Facility Mode after provider login

| Layer | Contract | Source |
|---|---|---|
| Eligibility read | `GET /api/v1/facility-claim/appointments?facilityUuid=` → `{facilityUuid, appointments:[AppointmentView]}` | `FacilityClaimController.java:108-127`; `useFacilityMode.ts:38-50` |
| Eligibility gate | `administersFacility()` true iff a row's `approvalState === "ACTIVE"` for the person | `useFacilityMode.ts:53-61` |
| Persisted action | Facility-mode approve → `POST /api/v1/facility-claim/approve {appointmentId}` → tuso `POST /v1/internal/facility-admin-appointments/{id}/approve` sets `ACTIVE` | `FacilityClaimController.java:230-272`; `FacilityClaimService.java:172-199` |
| Backend state enum | `PENDING, ACTIVE, REJECTED, REVOKED` (approve sets **ACTIVE**, never "APPROVED") | `FacilityAdminAppointmentEntity.java:22-24` |

**DEFECT FOUND + FIXED (this stream):** `administersFacility()` previously gated on
`approvalState === "APPROVED"`, a literal tuso **never emits** — so Facility Mode could never
activate for a legitimately-approved admin. Corrected to `ACTIVE` with a regression-guard test
asserting `"APPROVED"` does **not** grant. (`useFacilityMode.ts`, `useFacilityMode.test.ts`.)

**E verification checklist:** context read backs eligibility (not frontend-only) ✓ · gate matches
backend literal ✓ (post-fix) · switching facility carries facilityUuid to the backend ✓ · a
provider cannot administer a facility with no appointment ✓ · persisted approve action + follow-up
read ✓ · empty-array = honest ineligible state (no fabricated eligibility) ✓.

---

## Journey F — Adjudication + decision completion

| Capability | Endpoint | Success / durable id | Source |
|---|---|---|---|
| Start adjudication instance | `POST /internal/v1/workflows/instances {definitionId, initiatorRef, subjectRef, context}` (needs `X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID`) | `201`, `instanceId` | `WorkflowInstanceController.java:31-47`; `StartInstanceRequest.java:7-11` |
| Definitions | identity `ad1d0000-…-0001`, provider `…-0002`, facility `…-0003` (PUBLISHED) | — | `workflow-service V003__iatg_adjudication_definitions.sql` |
| Record decision (append-only) | `POST …/workforce-governance/adjudications/decisions {subjectType, subjectRef, decision, reason, …}` | `201`, `id` | `AdjudicationDecisionController.java:93-113,50-64` |
| Query decisions | `GET …/adjudications/decisions?subjectType=&subjectRef=` → `{latestEffective, history[]}` | `200` | `AdjudicationDecisionController.java:115-131` |
| WS-D producer | employment CONFLICT → `WgvWorkflowServiceClient.startAdjudication` (def `…-0001`, gate `impilo.governance.adjudication.enabled`) | best-effort | `EmploymentConflictCaseService` |
| WS-F producer | provider-claim CONFLICT → `ProviderWorkflowServiceClient.startAdjudication` (def `…-0002`, gate `impilo.varapi.adjudication.enabled`, default off) | best-effort | `ProviderClaimAdjudicationService.java:61-94` |
| Channel-C resolve | org-claim `escalate` → decision → Kafka feedback → claim `ACCEPTED` | polled | harness step 9; `ClaimSubmissionController.java:52-55` |

**F verification checklist:** workflow instance created ✓ · decision is **append-only** (every POST
inserts a row; query returns `history[]`) ✓ · consumer resolves source claim (harness step 9 polls
to `ACCEPTED`) ✓ · rejected/needs-info path exists (seed scenario 21 `DECIDED_DENIED`;
`DUPLICATE_SUSPECTED` routing) ✓ · producers config-gated default-off (claim path byte-identical
when off) ✓.
**Residual note:** the WS-F **resolve write-back** (what an APPROVED/DENIED decision mechanically
does to the varapi provider on a self-claim conflict — merge/reject/reissue) is a doctrine decision
recorded append-only in workforce-governance, not auto-mapped in varapi; documented as a follow-up.
varapi exposes **no** direct REST to start/read an adjudication (internal side-effect only) — seed
scenario 20 starts the pending case against workflow-service directly.

---

## Failure-code handling matrix (Section 3 #10)

| Code | Journey C | Journey D | Journey E | Journey F |
|---|---|---|---|---|
| 400 | consent/facilityUuid required | requestType/claimToken/consent required | facilityUuid required | definitionId required |
| 403 | (Envoy/PDP) | claimant≠actor defence-in-depth (`bootstrap/claim`) | — | — |
| 404 | facility null → BFF 404 | recovery no-profile; status unknown publicId | — | definition/instance not found |
| 409 | not-platform-allowed / already-active-admin | one-person-one-profile; token burned | — | — |
| 422 | — | — | — | definition not PUBLISHED |
| 502 | TUSO_UNAVAILABLE (fail-closed notice) | RECOVERY_INTEGRITY; UPSTREAM_502 | upstream propagate | — |

All BFF error paths return `{error:{code,message}, meta}` and the UI surfaces the reason + next
action (no silent empty panels, no endless spinners). `propagate()` never remaps a downstream
`409` to a fake success.
