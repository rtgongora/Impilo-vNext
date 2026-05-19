# Impilo vNext Telemedicine and Virtual Care Pipeline

## Purpose
Canonical status for native vNext telemedicine capability using provider-neutral runtime abstractions while preserving sovereign ownership boundaries (`PCT`, `OROS`, `BUTANO`, `TSHEPO`, `VARAPI`, `TUSO`, `COSTA/MusheX`).

## Safety Guardrails Applied
- No replacement of `PCT`, `OROS`, `BUTANO`, `VITO`, `VARAPI`, `TUSO`, `COSTA`, or `MusheX`.
- No destructive migrations and no hard-binding to a single media vendor.
- TSHEPO-governed authorization, consent, trust headers, and audit controls remain in-path.

## Runtime Components (Current)
- `experience-bff`: teleconsult orchestration, policy checks, audit emission, writeback/notification/finance triggers.
- `pct-service`: canonical referral/session lifecycle state and telemedicine ops snapshot.
- Telemedicine provider-neutral session provisioning in `pct-service`:
  - `MANAGED_PRIMARY`
  - `ASYNC_NO_VIDEO`
  - `MANUAL_PHONE`
  - `EXTERNAL_MANAGED` (config-driven external adapter)
- Mobile BFF endpoints now pass provider-type hints through to PCT (`sessionProvider`).

## Functional Acceptance Status
1. Telemedicine core service: **Functional**  
2. Provider-neutral session abstraction: **Functional**  
3. Video/audio provider integration: **Partial** (external adapter implemented; production endpoint validation and callbacks still pending)  
4. Asynchronous e-consult: **Functional**  
5. Provider/EHR surface: **Functional**  
6. Specialist workbench: **Partial** (ops backlog/workbench present; deeper specialty UX not uniform)  
7. Citizen/patient surface: **Partial** (API + mobile paths present; limited rich web workflow)  
8. Facility coordinator view: **Partial** (ops/admin data present; dedicated coordinator UX partial)  
9. Ops monitoring: **Functional**  
10. Teleradiology: **Functional**  
11. Telepathology: **Partial**  
12. Teleoncology: **Partial**  
13. Teleophthalmology: **Partial**  
14. Teledermatology: **Partial**  
15. Telecardiology: **Partial**  
16. Telestroke: **Partial**  
17. Telepsychiatry: **Partial**  
18. Telepharmacy: **Partial**  
19. Telerehabilitation: **Partial**  
20. Tele-MNCH: **Partial**  
21. Chronic care telefollow-up: **Partial**  
22. Remote patient monitoring: **Missing**  
23. Tele-ICU: **Partial**  
24. Tele-surgery/pre-op/post-op: **Partial**  
25. Tele-dentistry: **Partial**  
26. Tele-audiology/ENT: **Partial**  
27. Tele-nutrition: **Partial**  
28. Tele-public-health: **Partial**  
29. BUTANO writeback: **Functional**  
30. COSTA/MusheX integration: **Functional**  
31. Notifications: **Functional**  
32. Audit/security/consent: **Functional**  
33. Contracts: **Partial** (core routes aligned; specialty/event payload typing still expanding)  
34. Tests/smoke checks: **Partial**

## Contracts Updated in This Refinement
- `contracts/openapi/pct.openapi.yaml`: typed telehealth session create/response schemas with provider-neutral `sessionProvider`.
- `contracts/openapi/mobile-provider.openapi.yaml`: typed session create payload including `session_provider`.
- `contracts/openapi/mobile-citizen.openapi.yaml`: typed citizen telehealth request payload including `sessionProvider`.

## Remaining Backlog
- External media provider production callback/webhook integration and resilience hardening.
- Remote patient monitoring workflows and telemetry ingestion.
- Specialty-specific UX/workflow depth beyond shared teleconsult pipeline.
- Broader deterministic integration tests across specialty and failure-mode permutations.
