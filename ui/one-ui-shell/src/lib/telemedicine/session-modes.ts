/**
 * Clinical session-mode governance matrix (doctrine-as-data).
 *
 * Product doctrine: groups are for discovery and routing; SESSION MODES are
 * for clinical governance. The selected mode must drive UI, identity
 * visibility, documentation template, participant rights, consent and audit.
 *
 * Five governed session templates exist today (W0 doctrine-as-data, served by
 * `GET /internal/v1/session-templates`): TELEMEDICINE, MEETING, LIVE_EVENT,
 * LEARNING_LIVE, LEARNING_RECORDING. The ten clinical modes below map onto
 * those templates where one exists, and are honestly marked PLANNED where the
 * governance template does not exist yet (handoff HO-3 — template additions
 * are a `libs/session-templates` change owned by W0).
 */

import type { IdentityVisibilityLevel } from "./privacy";

/** W0 session template ids (mirrors libs/session-templates SessionMode). */
export type BackingTemplateMode =
  | "TELEMEDICINE"
  | "MEETING"
  | "LIVE_EVENT"
  | "LEARNING_LIVE"
  | "LEARNING_RECORDING";

export type SessionModeStatus =
  | "TEMPLATED" // a governed W0 session template backs this mode today
  | "TEMPLATED_PARTIAL" // usable via an existing template, but mode-specific governance fields are missing
  | "PLANNED"; // no governing template yet — blocked on HO-3/HO-4

export interface ClinicalSessionMode {
  id: string;
  name: string;
  description: string;
  /** Existing W0 template this mode runs on today, if any. */
  backingTemplate: BackingTemplateMode | null;
  status: SessionModeStatus;
  /** Identity visibility levels permitted in this mode (policy pending HO-4). */
  identityVisibility: IdentityVisibilityLevel[];
  defaultIdentityVisibility: IdentityVisibilityLevel;
  consentRequired: boolean;
  recordingDefault: "OFF" | "OFF_UNLESS_GOVERNED" | "GOVERNED_ON";
  participantRoles: string[];
  documentationTarget: string;
  auditDepth: "STANDARD" | "CLINICAL" | "STRICT";
  /** What blocks full governance, stated honestly. */
  gap?: string;
}

export const CLINICAL_SESSION_MODES: ClinicalSessionMode[] = [
  {
    id: "direct-clinical-encounter",
    name: "Direct Clinical Encounter Mode",
    description:
      "Patient-specific consultation. Full encounter menu by role; orders/prescribing/documentation where permitted. Equivalent to a physical encounter.",
    backingTemplate: "TELEMEDICINE",
    status: "TEMPLATED",
    identityVisibility: ["FULL", "CARE_TEAM_LIMITED"],
    defaultIdentityVisibility: "FULL",
    consentRequired: true,
    recordingDefault: "OFF_UNLESS_GOVERNED",
    participantRoles: ["PROVIDER", "PATIENT", "CAREGIVER", "INTERPRETER", "SUPERVISOR", "OBSERVER"],
    documentationTarget: "PCT encounter (referral response + FHIR writeback)",
    auditDepth: "CLINICAL",
  },
  {
    id: "hybrid-encounter-support",
    name: "Hybrid Physical Encounter Support Mode",
    description:
      "Patient physically at a facility; remote provider joins to support the local team. Local encounter remains primary; remote contribution is attributed.",
    backingTemplate: "TELEMEDICINE",
    status: "TEMPLATED_PARTIAL",
    identityVisibility: ["FULL", "CARE_TEAM_LIMITED"],
    defaultIdentityVisibility: "CARE_TEAM_LIMITED",
    consentRequired: true,
    recordingDefault: "OFF",
    participantRoles: ["LOCAL_PROVIDER", "REMOTE_PROVIDER", "PATIENT", "SUPERVISOR"],
    documentationTarget: "Host physical encounter (linked child teleconsult)",
    auditDepth: "CLINICAL",
    gap: "Runs on the TELEMEDICINE template; remote-contribution attribution and co-signature semantics are not yet template fields.",
  },
  {
    id: "provider-to-provider-advice",
    name: "Provider-to-Provider Advice Mode",
    description:
      "Clinician seeks advice; patient may not be present. Advice is documented back into the encounter via the structured referral response.",
    backingTemplate: "TELEMEDICINE",
    status: "TEMPLATED_PARTIAL",
    identityVisibility: ["CARE_TEAM_LIMITED", "PSEUDONYMISED"],
    defaultIdentityVisibility: "CARE_TEAM_LIMITED",
    consentRequired: false,
    recordingDefault: "OFF",
    participantRoles: ["REQUESTING_PROVIDER", "ADVISING_PROVIDER", "SUPERVISOR"],
    documentationTarget: "PCT referral structured response (real today)",
    auditDepth: "CLINICAL",
    gap: "No dedicated advice template; patient-absent identity minimisation not enforced by template.",
  },
  {
    id: "mdt-case-review",
    name: "MDT / Case Review Mode",
    description:
      "Multiple clinicians review one or more cases. Case-presentation format; case notes only for authorised participants; decisions and actions captured.",
    backingTemplate: null,
    status: "PLANNED",
    identityVisibility: ["CARE_TEAM_LIMITED", "PSEUDONYMISED", "ANONYMISED"],
    defaultIdentityVisibility: "PSEUDONYMISED",
    consentRequired: true,
    recordingDefault: "OFF_UNLESS_GOVERNED",
    participantRoles: ["CHAIR", "PRESENTER", "PANEL_MEMBER", "OBSERVER"],
    documentationTarget: "MDT decision record + per-case action items (backend absent)",
    auditDepth: "STRICT",
    gap: "No MDT session template and no case-presentation/decision backend (HO-3, HO-5).",
  },
  {
    id: "case-presentation",
    name: "Case Presentation Mode",
    description:
      "Structured clinical brief — identified, pseudonymised, anonymised or de-identified — for MDT, specialist briefing, teaching, audit, referral or handover.",
    backingTemplate: null,
    status: "PLANNED",
    identityVisibility: ["FULL", "CARE_TEAM_LIMITED", "PSEUDONYMISED", "ANONYMISED"],
    defaultIdentityVisibility: "PSEUDONYMISED",
    consentRequired: true,
    recordingDefault: "OFF",
    participantRoles: ["PRESENTER", "AUDIENCE"],
    documentationTarget: "CasePresentation record (backend absent)",
    auditDepth: "STRICT",
    gap: "Case-presentation model does not exist in any service (HO-5).",
  },
  {
    id: "case-notes-review",
    name: "Case Notes Review Mode",
    description:
      "Direct access to the patient's clinical record. Stricter permissions: care-team membership or authorised review role, minimum-necessary visibility, full audit.",
    backingTemplate: null,
    status: "PLANNED",
    identityVisibility: ["FULL", "CARE_TEAM_LIMITED"],
    defaultIdentityVisibility: "CARE_TEAM_LIMITED",
    consentRequired: true,
    recordingDefault: "OFF",
    participantRoles: ["CARE_TEAM_MEMBER", "AUTHORISED_REVIEWER"],
    documentationTarget: "Existing clinical record (access-grant model absent)",
    auditDepth: "STRICT",
    gap: "Session-scoped case-notes access grants require Tshepo/OPA policy work (HO-4).",
  },
  {
    id: "audit-mortality-review",
    name: "Audit / Mortality Review Mode",
    description:
      "Structured audit: maternal/perinatal/neonatal death review, adverse events, near-miss, surgical audit — with no-name/no-blame configuration and action tracking.",
    backingTemplate: null,
    status: "PLANNED",
    identityVisibility: ["PSEUDONYMISED", "ANONYMISED"],
    defaultIdentityVisibility: "ANONYMISED",
    consentRequired: false,
    recordingDefault: "OFF",
    participantRoles: ["CHAIR", "PRESENTER", "PANEL_MEMBER", "SECRETARIAT"],
    documentationTarget: "Separate audit record with action plan (backend absent; PCT death pathway exists separately)",
    auditDepth: "STRICT",
    gap: "No audit-session model; audit outputs must stay separate from patient-facing notes (HO-3, HO-5).",
  },
  {
    id: "teaching-supervision",
    name: "Teaching / Supervision Mode",
    description:
      "Learner, observer and supervisor roles with restricted permissions, consent and de-identification rules; Fundo artefact only when approved.",
    backingTemplate: "LEARNING_LIVE",
    status: "TEMPLATED_PARTIAL",
    identityVisibility: ["PSEUDONYMISED", "ANONYMISED"],
    defaultIdentityVisibility: "ANONYMISED",
    consentRequired: true,
    recordingDefault: "GOVERNED_ON",
    participantRoles: ["FACILITATOR", "COFACILITATOR", "LEARNER", "SUPERVISOR", "OBSERVER"],
    documentationTarget: "Teaching notes separate from clinical record; Fundo artefact via governed approval",
    auditDepth: "STRICT",
    gap: "LEARNING_LIVE template exists (W0); patient de-identification enforcement and recording→artefact path are W0 W1 scope.",
  },
  {
    id: "emergency-advisory",
    name: "Emergency Advisory Mode",
    description:
      "Rapid join, minimal friction, high-acuity context, strict audit, fallback communication. Reduced friction never reduces accountability.",
    backingTemplate: null,
    status: "PLANNED",
    identityVisibility: ["FULL", "EMERGENCY_ACCESS"],
    defaultIdentityVisibility: "EMERGENCY_ACCESS",
    consentRequired: false,
    recordingDefault: "OFF",
    participantRoles: ["REQUESTING_PROVIDER", "EMERGENCY_ADVISER"],
    documentationTarget: "Encounter/referral with emergency flag; break-glass audit",
    auditDepth: "STRICT",
    gap: "No emergency-advisory template (rapid-join grants, SLA timers) — HO-3; break-glass display policy — HO-4.",
  },
  {
    id: "diagnostics-review",
    name: "Diagnostics Review Mode",
    description:
      "Imaging/lab context panel, DICOM/PACS-linked review, diagnostic recommendations and urgent-finding escalation.",
    backingTemplate: null,
    status: "PLANNED",
    identityVisibility: ["CARE_TEAM_LIMITED", "PSEUDONYMISED", "ANONYMISED"],
    defaultIdentityVisibility: "CARE_TEAM_LIMITED",
    consentRequired: false,
    recordingDefault: "OFF",
    participantRoles: ["REQUESTING_PROVIDER", "DIAGNOSTICS_REVIEWER"],
    documentationTarget: "Recommendation written back to encounter (imaging lifecycle exists in OROS/PACS)",
    auditDepth: "CLINICAL",
    gap: "No diagnostics-review session template; DICOM de-identification checks for teaching/audit reuse absent.",
  },
];

export function getSessionMode(id: string): ClinicalSessionMode | undefined {
  return CLINICAL_SESSION_MODES.find((mode) => mode.id === id);
}

export const SESSION_MODE_STATUS_LABELS: Record<SessionModeStatus, string> = {
  TEMPLATED: "Governed template live",
  TEMPLATED_PARTIAL: "Runs on existing template — governance gaps recorded",
  PLANNED: "Planned — template/policy pending",
};
