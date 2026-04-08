import { describe, expect, it } from "vitest";
import {
  buildReferralTeleconsultBrief,
  buildTeleconsultCompletionBody,
  formatTeleconsultFollowUpLabel,
  getReferralStageCopy,
  isReferralReceivingHere,
  mapTeleconsultFollowUpToOutcome,
  parseConsultationCoordinationMeta,
  toDateTimeLocalValue,
} from "../consult-workflows";

describe("consult workflows", () => {
  it("matches receiving referrals by facility id", () => {
    expect(
      isReferralReceivingHere(
        {
          id: "ref-1",
          attributes: {
            receivingFacilityId: "facility-a",
            referredToFacility: "District Hospital",
          },
        },
        { id: "facility-a", name: "District Hospital" },
      ),
    ).toBe(true);
  });

  it("matches receiving referrals by facility name when id is absent", () => {
    expect(
      isReferralReceivingHere(
        {
          id: "ref-2",
          attributes: {
            receiving_facility_name: "Parirenyatwa Group of Hospitals",
          },
        },
        { name: "parirenyatwa   group of hospitals" },
      ),
    ).toBe(true);
  });

  it("returns stage guidance for a referring team waiting to close the loop", () => {
    expect(getReferralStageCopy("RESPONDED", false)).toEqual({
      title: "Loop ready to close",
      detail: "Specialist guidance is back in chart. Review the recommendations and mark the referral complete in this workspace.",
      tone: "purple",
    });
  });

  it("builds a referral-aware teleconsult brief", () => {
    expect(
      buildReferralTeleconsultBrief({
        id: "ref-3",
        attributes: {
          specialty: "Cardiology",
          reason: "Evaluate chest pain",
          clinicalSummary: "Troponin rising overnight",
        },
      }),
    ).toBe("Cardiology | Evaluate chest pain | Summary: Troponin rising overnight");
  });

  it("formats follow-up labels and maps outcomes", () => {
    expect(formatTeleconsultFollowUpLabel("REPEAT_TELECONSULT")).toBe("Repeat Teleconsult");
    expect(mapTeleconsultFollowUpToOutcome("ADMIT")).toBe("ADMITTED");
  });

  it("builds and parses teleconsult completion handoff metadata", () => {
    const body = buildTeleconsultCompletionBody({
      findings: "Improved work of breathing.",
      plan: "Continue nebulisers and review tomorrow.",
      followUpLabel: "Review",
      referralId: "ref-tele-9",
      sessionType: "VIDEO",
    });

    expect(body).toContain("Referral loop update:");
    expect(body).toContain("Linked referral: ref-tele-9");

    expect(parseConsultationCoordinationMeta(body)).toEqual({
      linkedReferralId: "ref-tele-9",
      linkedTeleconsult: "VIDEO session",
      responseSentFromTeleconsult: true,
      nextWorkspaceAction: "Review response and close the loop in Consults",
      hasReferralLoopUpdate: true,
    });
  });

  it("keeps datetime-local formatting stable", () => {
    expect(toDateTimeLocalValue("2026-04-08T10:30:00.000Z")).toMatch(/2026-04-08T\d{2}:30/);
  });
});
