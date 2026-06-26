# Patient Safety PoC — Known limitations & what needs MCAZ refinement next

This build is an **honest** proof-of-concept. The following are deliberately incomplete or stubbed,
and are labelled as such in the product (never hidden behind a fake success).

## External submission (intentionally not live)
- **VigiFlow / E2B dispatch adapter is OFF by default.** Cases reach `EXPORT_READY` as an
  **E2B(R3)-aligned** package and are entered into VigiFlow **manually**; the reference id is recorded
  on the case. There is **no** automated transmission and no "Submitted" state in this build.
- **VigiMobile / VigiFlow eForm links are external WHO-UMC link-outs.** Impilo does not receive a copy
  of submissions made on the external eForm unless a callback adapter is configured.
- **integration-hub adapter registration is a placeholder set** (vigiflow-e2b, vigimobile-link,
  whatsapp, sms, ussd, email, voice) with enabled/disabled status. Live adapters are out of scope.

## Citizen / public path (deployment wiring)
- The citizen report posts to `/v1/public/patient-safety/reports`. `self-service/next.config.js`
  already rewrites `/v1/public/*` to the gateway; the **Envoy upstream route** to `experience-bff`
  for this path is the remaining infra line. The BFF endpoint itself is implemented and functional.
- Anonymous citizen reports land in a **configured public tenant**
  (`impilo.patient-safety.public-tenant-id`). Real deployments should map this to a national PV tenant
  and add a minimal-identity/consent step.

## Policy / authorization
- The **Tshepo policy SPEC** is authored and queued (see
  [`docs/policy/patient-safety-policy-spec.md`](../policy/patient-safety-policy-spec.md)).
  `PolicyEngine.java` is single-writer-locked to the consent/trust cluster, so enforcement is **not
  yet wired**. The service currently relies on the tech-companion trust-header contract and role
  context; MCAZ-role gating is specified but not enforced in this PoC.

## Forms
- ADR (PVF01 Rev10), AEFI and serious-AEFI investigation **field groups** are specified
  ([`docs/forms/patient-safety-form-packs.md`](../forms/patient-safety-form-packs.md)) and reports
  carry `form_pack_key` / `form_pack_version` / `form_data`. **Runtime form rendering and
  schema-validation binding via forms-service is referenced, not enforced** in this PoC.

## Clinical coding & data quality
- Reactions are free-text with optional MedDRA fields; **no MedDRA dictionary** integration.
- **Duplicate detection/merge** beyond the `DUPLICATE` status is not implemented.
- **Attachments** reference a document-service `objectId`; the upload flow lives in document-service
  and is not surfaced in the PoC report UI.

## Prefill
- BFF `/prefill` currently composes **recently-dispensed medicines** (pharmacy-service) + VigiMobile
  links + adapter posture. VITO/VARAPI/TUSO/PCT/product-registry prefill points are identified in the
  service boundary but not all wired.

## Surveillance
- surveillance-service consumes `report.serious` for signal/cluster detection via its **own**
  consumer; the signal/cluster algorithms are surveillance-service's responsibility, not this service.

## Suggested MCAZ refinement agenda
1. Confirm the **ADR (PVF01 Rev10) and AEFI field sets** and seriousness criteria wording.
2. Decide the **causality scale** surfaced to reviewers (WHO-UMC vs Naranjo) and where it is recorded.
3. Confirm the **VigiFlow operating model**: manual entry + reference capture now, and whether/when an
   E2B(R3) dispatch adapter is in scope (and its certification requirements).
4. Define **serious-AEFI investigation** form + causality classification (WHO AEFI categories).
5. Define **citizen minimal-identity + consent** for public reports and attachment handling.
6. Confirm **roles & access** (citizen/caregiver/provider/pharmacist/vaccinator/facility focal/
   district PHO/MCAZ reviewer/supervisor) for the queued Tshepo policy.
