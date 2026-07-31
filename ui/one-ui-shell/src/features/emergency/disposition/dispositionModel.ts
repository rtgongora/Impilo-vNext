/**
 * The fifteen episode dispositions, and which fields each one needs.
 *
 * **pct-service owns these rules, not this file.** Every requirement below mirrors a CHECK
 * constraint in pct's `V208__emergency_disposition_and_observation.sql`; the copy exists so the form
 * can ask for the right fields up front rather than letting a clinician fill in a whole record and
 * be refused. If the two ever disagree, pct wins — the form submits regardless and shows pct's own
 * refusal verbatim, so a drifted copy costs a wasted keystroke, never a wrongly accepted record.
 *
 * These are not clinical rules. Nothing here decides what disposition a patient should have; it
 * records the one a clinician chose and states what pct requires alongside it.
 *
 * The fifteen types are deliberately finer-grained than the episode FSM's seven CLOSED_* states:
 * `CLOSED_HANDED_OVER` alone covers ward, ICU, HDU, theatre, mental-health and transfer-out.
 */

export type DispositionField = "destination" | "causeOrCircumstances" | "lastSeenAt";

export interface DispositionType {
  code: string;
  label: string;
  /** Grouping for the picker only. Carries no clinical meaning. */
  group: "Discharge" | "Admission or transfer" | "Observation" | "Death" | "Unplanned departure";
  /** Fields pct's CHECK constraints require in addition to reason and actor. */
  requires: DispositionField[];
  /** What the clinician is being asked to state, in their own workflow's terms. */
  hint?: string;
}

export const DISPOSITION_TYPES: DispositionType[] = [
  { code: "DISCHARGED_HOME", label: "Discharged home", group: "Discharge", requires: [] },
  {
    code: "DISCHARGED_WITH_FOLLOWUP",
    label: "Discharged with follow-up",
    group: "Discharge",
    requires: [],
  },
  {
    code: "REFERRED_OUTPATIENT",
    label: "Referred to outpatient",
    group: "Discharge",
    requires: ["destination"],
    hint: "Name the clinic or service the patient is referred to.",
  },
  {
    code: "ADMITTED_WARD",
    label: "Admitted — ward",
    group: "Admission or transfer",
    requires: ["destination"],
    hint: "Name the ward. An admission with no stated destination is a half-recorded disposition.",
  },
  {
    code: "ADMITTED_ICU",
    label: "Admitted — ICU",
    group: "Admission or transfer",
    requires: ["destination"],
  },
  {
    code: "ADMITTED_HDU",
    label: "Admitted — HDU",
    group: "Admission or transfer",
    requires: ["destination"],
  },
  {
    code: "TO_THEATRE",
    label: "To theatre",
    group: "Admission or transfer",
    requires: ["destination"],
  },
  {
    code: "TRANSFERRED_OUT",
    label: "Transferred out",
    group: "Admission or transfer",
    requires: ["destination"],
    hint: "Name the receiving facility.",
  },
  {
    code: "TO_MENTAL_HEALTH",
    label: "To mental health service",
    group: "Admission or transfer",
    requires: ["destination"],
  },
  {
    code: "OBSERVATION",
    label: "Observation / short stay",
    group: "Observation",
    requires: [],
    hint: "Neither an admission nor a discharge. Start the observation stay separately.",
  },
  {
    code: "DIED",
    label: "Died",
    group: "Death",
    requires: ["causeOrCircumstances"],
    hint: "The circumstances of a death in the emergency department must be on the record, not inferable from the disposition type alone.",
  },
  {
    code: "LEFT_BEFORE_ASSESSMENT",
    label: "Left before assessment",
    group: "Unplanned departure",
    requires: ["lastSeenAt"],
    hint: "When the patient was last actually seen is the one fact that makes an unplanned departure auditable later.",
  },
  {
    code: "LEFT_AGAINST_ADVICE",
    label: "Left against advice",
    group: "Unplanned departure",
    requires: ["lastSeenAt"],
  },
  {
    code: "ABSCONDED",
    label: "Absconded",
    group: "Unplanned departure",
    requires: ["lastSeenAt"],
  },
  { code: "DECLINED_CARE", label: "Declined care", group: "Discharge", requires: [] },
];

export function dispositionType(code: string): DispositionType | undefined {
  return DISPOSITION_TYPES.find((type) => type.code === code);
}

export interface DispositionDraft {
  dispositionType: string;
  dispositionReason: string;
  destination?: string;
  causeOrCircumstances?: string;
  lastSeenAt?: string;
}

/**
 * Which fields are still missing. Used to label the form, never to decide whether the record is
 * acceptable — that decision is pct's, and the form submits so pct can make it.
 */
export function missingFields(draft: DispositionDraft): string[] {
  const missing: string[] = [];
  const type = dispositionType(draft.dispositionType);
  if (!type) return ["dispositionType"];
  if (!draft.dispositionReason?.trim()) missing.push("dispositionReason");
  for (const field of type.requires) {
    if (!String(draft[field] ?? "").trim()) missing.push(field);
  }
  return missing;
}

export function dispositionGroups(): { group: DispositionType["group"]; types: DispositionType[] }[] {
  const order: DispositionType["group"][] = [
    "Discharge",
    "Admission or transfer",
    "Observation",
    "Death",
    "Unplanned departure",
  ];
  return order.map((group) => ({
    group,
    types: DISPOSITION_TYPES.filter((type) => type.group === group),
  }));
}
