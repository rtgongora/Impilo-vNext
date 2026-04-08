export type FacilityLike = {
  id?: string | null;
  name?: string | null;
} | null | undefined;

export type ReferralLike = {
  id: string;
  attributes: {
    referralType?: string | null;
    referral_type?: string | null;
    specialty?: string | null;
    reason?: string | null;
    clinicalSummary?: string | null;
    clinical_summary?: string | null;
    scheduledAt?: string | null;
    receivingFacilityId?: string | null;
    receivingFacilityName?: string | null;
    receiving_facility_id?: string | null;
    receiving_facility_name?: string | null;
    referredToFacility?: string | null;
    referred_to_facility?: string | null;
    referredByName?: string | null;
    response_notes?: string | null;
    responseNotes?: string | null;
    outcome?: string | null;
  };
};

export type TeleconsultFollowUpOption =
  | "NONE"
  | "REVIEW"
  | "REPEAT_TELECONSULT"
  | "ADMIT"
  | "REFER_FURTHER";

export type ReferralStageCopy = {
  title: string;
  detail: string;
  tone: "amber" | "blue" | "purple" | "green" | "slate";
};

export type ConsultationCoordinationMeta = {
  linkedReferralId: string | null;
  linkedTeleconsult: string | null;
  responseSentFromTeleconsult: boolean;
  nextWorkspaceAction: string | null;
  hasReferralLoopUpdate: boolean;
};

export const COORDINATION_COPY = {
  returnedFromTeleconsult: "Returned from teleconsult",
  closeLoopAction: "Review response and close the loop in Consults",
  needsActionNow: "Needs action now",
  trackingInProgress: "Tracking in progress",
  responseReturned: "Response returned",
  closedLoops: "Closed loops",
  needsAttention: "Needs attention",
  coordinationWorkboard: "Coordination workboard",
  startEncounterHandoff: "Start encounter handoff",
  openEncounterHandoff: "Open encounter handoff",
  resumeEncounterHandoff: "Resume encounter handoff",
  loopClosureHandoff: "Loop-closure handoff",
  noteAndReturnedResponseSaved: "Note and returned response saved",
  completionNoteSaved: "Completion note saved",
} as const;

export function toDateTimeLocalValue(value: string | null | undefined) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

export function normalizeFacilityName(value: string | null | undefined) {
  return value?.trim().toLowerCase().replace(/\s+/g, " ") ?? "";
}

export function getReferralFacilityName(referral: ReferralLike) {
  return (
    referral.attributes.receivingFacilityName ??
    referral.attributes.receiving_facility_name ??
    referral.attributes.referredToFacility ??
    referral.attributes.referred_to_facility ??
    "Pending facility"
  );
}

export function isReferralReceivingHere(referral: ReferralLike, facility: FacilityLike) {
  if (!facility) return false;

  const receivingId =
    referral.attributes.receivingFacilityId ?? referral.attributes.receiving_facility_id;
  if (receivingId && facility.id && receivingId === facility.id) {
    return true;
  }

  const normalizedFacilityName = normalizeFacilityName(facility.name);
  if (!normalizedFacilityName) return false;

  return [
    referral.attributes.receivingFacilityName,
    referral.attributes.receiving_facility_name,
    referral.attributes.referredToFacility,
    referral.attributes.referred_to_facility,
  ].some((name) => normalizeFacilityName(name) === normalizedFacilityName);
}

export function buildReferralTeleconsultBrief(referral: ReferralLike) {
  const attrs = referral.attributes;
  const summary = attrs.clinicalSummary ?? attrs.clinical_summary;
  return [attrs.specialty || attrs.referralType || attrs.referral_type, attrs.reason, summary ? `Summary: ${summary}` : ""]
    .filter(Boolean)
    .join(" | ");
}

export function getReferralStageCopy(status: string, isReceivingContext: boolean): ReferralStageCopy {
  switch (status) {
    case "PENDING":
      return isReceivingContext
        ? {
            title: "Ready for receiving handoff",
            detail: "Accept the referral here, confirm the facility context, and set up the next response step.",
            tone: "amber",
          }
        : {
            title: "Waiting on receiving facility",
            detail: "The package is out. Monitor acceptance or launch a teleconsult if the team needs synchronous review.",
            tone: "amber",
          };
    case "ACCEPTED":
      return isReceivingContext
        ? {
            title: "Response drafting in progress",
            detail: "Specialist review is underway. Document findings or pivot straight into teleconsult support from this card.",
            tone: "blue",
          }
        : {
            title: "Receiving team has accepted",
            detail: "The handoff is acknowledged. The next expected milestone is a specialist response or virtual consult.",
            tone: "blue",
          };
    case "RESPONDED":
      return isReceivingContext
        ? {
            title: COORDINATION_COPY.responseReturned,
            detail: "The receiving side has completed its response. The referring team should now review and formally close the loop.",
            tone: "purple",
          }
        : {
            title: "Loop ready to close",
            detail: "Specialist guidance is back in chart. Review the recommendations and mark the referral complete in this workspace.",
            tone: "purple",
          };
    case "COMPLETED":
      return {
        title: "Referral loop closed",
        detail: "Both sides of the referral have been documented and the encounter now holds the final outcome.",
        tone: "green",
      };
    default:
      return {
        title: "Referral workflow in motion",
        detail: "This referral is still moving through the encounter-based coordination flow.",
        tone: "slate",
      };
  }
}

export function mapTeleconsultFollowUpToOutcome(followUp: TeleconsultFollowUpOption) {
  switch (followUp) {
    case "REVIEW":
      return "FOLLOW_UP_REQUIRED";
    case "REPEAT_TELECONSULT":
      return "FOLLOW_UP_REQUIRED";
    case "ADMIT":
      return "ADMITTED";
    case "REFER_FURTHER":
      return "FURTHER_REFERRAL";
    default:
      return "TREATED";
  }
}

export function formatTeleconsultFollowUpLabel(followUp: TeleconsultFollowUpOption) {
  return followUp === "NONE"
    ? "No follow-up needed"
    : followUp
        .toLowerCase()
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");
}

export function buildTeleconsultCompletionBody({
  findings,
  plan,
  followUpLabel,
  referralId,
  sessionType,
}: {
  findings: string;
  plan: string;
  followUpLabel: string;
  referralId?: string | null;
  sessionType?: string | null;
}) {
  const sections = [
    findings ? `Findings:\n${findings}` : "",
    plan ? `Assessment and plan:\n${plan}` : "",
    `Follow-up:\n${followUpLabel}`,
  ];

  if (referralId) {
    sections.push(
      [
        "Referral loop update:",
        `Linked referral: ${referralId}`,
        `Linked teleconsult: ${sessionType ?? "Teleconsult"} session`,
        "Response sent from teleconsult: Yes",
        `Next workspace action: ${COORDINATION_COPY.closeLoopAction}`,
      ].join("\n"),
    );
  }

  return sections.filter(Boolean).join("\n\n");
}

export function parseConsultationCoordinationMeta(
  body: string | null | undefined,
): ConsultationCoordinationMeta {
  const source = body ?? "";
  const readValue = (label: string) => {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = source.match(new RegExp(`${escaped}:\\s*(.+)`, "i"));
    return match?.[1]?.trim() ?? null;
  };

  const responseSentValue = readValue("Response sent from teleconsult");
  const linkedReferralId = readValue("Linked referral");

  return {
    linkedReferralId,
    linkedTeleconsult: readValue("Linked teleconsult"),
    responseSentFromTeleconsult: responseSentValue?.toLowerCase() === "yes",
    nextWorkspaceAction: readValue("Next workspace action"),
    hasReferralLoopUpdate:
      source.toLowerCase().includes("referral loop update:") || linkedReferralId !== null,
  };
}
