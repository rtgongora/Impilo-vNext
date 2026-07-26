import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ImamAssessment, ImamEpisode, ImamVisit } from "@/hooks/queries/useImam";
import {
  attendanceBanner,
  dischargeReadiness,
  overrideReasonPrompt,
  programmeLabel,
  requiresOverrideReason,
  responseLabel,
  triStateLabel,
  visitVerdict,
} from "../imam-presentation";
import { DischargeCriteriaList } from "../DischargeCriteriaList";
import { ImamVisitTimeline } from "../ImamVisitTimeline";
import { computeDueToday } from "../../workspace/due-today";
import { ageFactsFor } from "../../workspace/paediatric-age";

const NOW = new Date("2026-07-26T09:00:00Z");

function assessment(overrides: Partial<ImamAssessment> = {}): ImamAssessment {
  return {
    programme: "OTP",
    programmeName: "Outpatient therapeutic programme",
    assessable: true,
    notAssessableReason: null,
    reviewsRecorded: 2,
    noReviewRecorded: false,
    daysInProgramme: 22,
    dischargeEligible: false,
    dischargeCriteria: [],
    unmetCriteria: [],
    dischargeOutcome: null,
    dischargeDestination: null,
    attendanceStatus: "ATTENDING",
    consecutiveMissedVisits: 0,
    daysSinceLastContact: 1,
    nextReviewDue: "2026-08-02",
    daysOverdue: 0,
    tracingRequired: false,
    responseStatus: "RESPONDING",
    weightGainGPerKgPerDay: 6.1,
    escalation: null,
    escalationAction: null,
    recommendedSachetsPerWeek: 21,
    therapeuticFood: "RUTF",
    actions: [],
    contentVersion: "engineering-seed-1.0.0",
    approvalStatus: "ENGINEERING_SEED",
    note: null,
    ...overrides,
  };
}

function visit(overrides: Partial<ImamVisit> = {}): ImamVisit {
  return {
    id: "v1",
    episodeId: "ep1",
    visitNumber: 1,
    scheduledFor: null,
    attended: true,
    visitDate: "2026-07-20T09:00:00Z",
    recordedBy: "nurse-1",
    muacCm: 12.6,
    weightKg: 7.8,
    heightCm: null,
    weightForHeightZ: null,
    oedema: "ABSENT",
    appetiteTest: null,
    dangerSignsPresent: false,
    rutfProductCode: "RUTF",
    rutfSachetsIssued: 21,
    rutfSachetsRecommended: 21,
    clinicalNote: null,
    nextReviewDue: "2026-07-27",
    dischargeEligible: false,
    attendanceStatus: "ATTENDING",
    responseStatus: "RESPONDING",
    assessmentNote: null,
    assessmentContentVersion: "engineering-seed-1.0.0",
    ...overrides,
  };
}

describe("discharge readiness", () => {
  it("an unevaluated verdict is never rendered as not-ready", () => {
    // The distinction the whole feature turns on: null means the knowledge platform could not be
    // reached, and painting that as "not ready for discharge" puts a clinical finding about the
    // child on screen when the only fact was a failed network call.
    const readiness = dischargeReadiness(assessment({ dischargeEligible: null }));

    expect(readiness.state).toBe("NOT_EVALUATED");
    expect(readiness.headline).not.toMatch(/not yet dischargeable/i);
    expect(readiness.detail).toMatch(/not a finding about the child/i);
  });

  it("a met assessment reads as cured, not merely as complete", () => {
    const readiness = dischargeReadiness(
      assessment({ dischargeEligible: true, dischargeOutcome: "CURED" }),
    );

    expect(readiness.state).toBe("READY");
    expect(readiness.detail).toMatch(/discharge as cured/i);
  });

  it("leaving inpatient stabilisation reads as a transfer, never as a cure", () => {
    const readiness = dischargeReadiness(
      assessment({
        programme: "INPATIENT_STABILISATION",
        dischargeEligible: true,
        dischargeOutcome: "TRANSFERRED_OUT",
        dischargeDestination: "OTP",
      }),
    );

    expect(readiness.detail).toMatch(/not a cure/i);
    expect(readiness.detail).not.toMatch(/cured/i);
  });

  it("renders every criterion with the sentence that explains it, met and unmet alike", () => {
    render(
      <DischargeCriteriaList
        assessment={assessment({
          unmetCriteria: ["MUAC_SUSTAINED"],
          dischargeCriteria: [
            {
              code: "MUAC_SUSTAINED",
              name: "Arm circumference at or above 12.5 cm on 2 consecutive reviews",
              met: false,
              detail: "1 review(s) recorded; 2 consecutive are required.",
            },
            {
              code: "OEDEMA_FREE",
              name: "No bilateral pitting oedema for 14 days",
              met: true,
              detail: "Oedema recorded absent.",
            },
          ],
        })}
      />,
    );

    // Showing only the unmet ones would hide how close the child is to discharge.
    expect(screen.getByTestId("criterion-MUAC_SUSTAINED")).toHaveTextContent("2 consecutive are required");
    expect(screen.getByTestId("criterion-OEDEMA_FREE")).toHaveTextContent("Oedema recorded absent");
  });

  it("shows the unevaluated state instead of an empty criteria list", () => {
    render(<DischargeCriteriaList assessment={assessment({ dischargeEligible: null })} />);

    expect(screen.getByTestId("discharge-not-evaluated")).toBeInTheDocument();
    expect(screen.queryByTestId("discharge-criteria")).not.toBeInTheDocument();
  });
});

describe("the cure needs its evidence", () => {
  it("requires a reason when the criteria are not met", () => {
    expect(requiresOverrideReason("CURED", assessment({ dischargeEligible: false }))).toBe(true);
  });

  it("requires a reason when the criteria could not be evaluated at all", () => {
    // Certifying a cure the system could not check is the same false reassurance as certifying
    // one it checked and refused.
    expect(requiresOverrideReason("CURED", assessment({ dischargeEligible: null }))).toBe(true);
    expect(overrideReasonPrompt(assessment({ dischargeEligible: null }))).toMatch(
      /could not be evaluated/i,
    );
  });

  it("asks for nothing extra when the criteria are met", () => {
    expect(requiresOverrideReason("CURED", assessment({ dischargeEligible: true }))).toBe(false);
  });

  it("never demands discharge evidence for an outcome that is not a cure", () => {
    // A child who died or defaulted did not meet the criteria; demanding proof that they did
    // would make the honest outcome the hardest one to record.
    for (const outcome of ["DEFAULTED", "DIED", "NON_RESPONDER", "TRANSFERRED_OUT"]) {
      expect(requiresOverrideReason(outcome, assessment({ dischargeEligible: false }))).toBe(false);
    }
  });
});

describe("attendance", () => {
  it("puts the never-reviewed child above any missed-visit count", () => {
    const banner = attendanceBanner(
      assessment({ noReviewRecorded: true, attendanceStatus: "DEFAULTER", consecutiveMissedVisits: 2 }),
    );

    expect(banner?.tone).toBe("critical");
    expect(banner?.title).toMatch(/no review has ever been recorded/i);
    expect(banner?.detail).toMatch(/not a child doing well/i);
  });

  it("says tracing starts before the defaulter definition is met", () => {
    const banner = attendanceBanner(
      assessment({ attendanceStatus: "TRACE_REQUIRED", daysOverdue: 4, consecutiveMissedVisits: 1 }),
    );

    expect(banner?.tone).toBe("warning");
    expect(banner?.detail).toMatch(/before the child meets the defaulter definition/i);
  });

  it("does not report attendance as fine when it could not be determined", () => {
    const banner = attendanceBanner(assessment({ attendanceStatus: "UNKNOWN" }));

    expect(banner?.tone).toBe("unknown");
    expect(banner?.detail).toMatch(/do not read this as the child attending/i);
  });
});

describe("the review timeline", () => {
  it("an episode with no review is an alarm, not an empty list", () => {
    render(<ImamVisitTimeline visits={[]} reviewIntervalDays={7} />);

    const empty = screen.getByTestId("imam-visits-empty");
    expect(empty).toHaveTextContent("No review has been recorded");
    expect(empty).toHaveTextContent(/not an empty list/i);
  });

  it("a closed episode with no review says the outcome was never observed", () => {
    render(<ImamVisitTimeline visits={[]} reviewIntervalDays={7} episodeClosed />);

    expect(screen.getByTestId("imam-visits-empty")).toHaveTextContent(
      /closed without a single review/i,
    );
  });

  it("marks an unevaluated review as unevaluated rather than as treatment continuing", () => {
    render(<ImamVisitTimeline visits={[visit({ dischargeEligible: null })]} reviewIntervalDays={7} />);

    expect(screen.getByTestId("imam-visit-1-verdict")).toHaveTextContent("Not evaluated");
  });

  it("shows a ration that differs from the recommendation", () => {
    render(
      <ImamVisitTimeline
        visits={[visit({ rutfSachetsIssued: 14, rutfSachetsRecommended: 21 })]}
        reviewIntervalDays={7}
      />,
    );

    expect(screen.getByTestId("imam-visit-1")).toHaveTextContent("21 recommended");
  });

  it("reports danger signs nobody assessed as not assessed", () => {
    render(<ImamVisitTimeline visits={[visit({ dangerSignsPresent: null })]} reviewIntervalDays={7} />);

    expect(screen.getByTestId("imam-visit-1")).toHaveTextContent("Not assessed");
  });

  it("a missed review is shown as missed, not as a gap", () => {
    render(
      <ImamVisitTimeline
        visits={[visit({ attended: false, visitDate: null, scheduledFor: "2026-07-20" })]}
        reviewIntervalDays={7}
      />,
    );

    expect(screen.getByTestId("imam-visit-1-verdict")).toHaveTextContent("Did not attend");
  });
});

describe("labels", () => {
  it("expands programme codes rather than showing the acronym alone", () => {
    expect(programmeLabel("OTP")).toBe("Outpatient therapeutic programme");
    expect(programmeLabel("SFP")).toBe("Supplementary feeding programme");
    expect(programmeLabel(null)).toBe("Unknown programme");
  });

  it("treats an unmeasurable response as unknown rather than as steady", () => {
    expect(responseLabel(null)).toMatch(/not yet measurable/i);
    expect(responseLabel("STATIC")).toMatch(/below target/i);
  });

  it("keeps not-assessed as its own answer", () => {
    expect(triStateLabel(null, "Present", "Absent")).toBe("Not assessed");
    expect(triStateLabel(false, "Present", "Absent")).toBe("Absent");
    expect(triStateLabel(true, "Present", "Absent")).toBe("Present");
  });

  it("does not call an unattended review a verdict about the child", () => {
    expect(visitVerdict(visit({ attended: false })).label).toBe("Did not attend");
  });
});

describe("what is due today", () => {
  const base = {
    patientId: "p1",
    age: ageFactsFor(new Date(NOW.getTime() - 400 * 86_400_000).toISOString()),
    hasAnyGrowthMeasurement: true,
    lastMeasuredAt: NOW.toISOString(),
    immunisationCount: 3,
    now: NOW,
  };

  it("raises an overdue nutrition review for an enrolled child", () => {
    const items = computeDueToday({
      ...base,
      imam: { enrolled: true, programme: "Outpatient therapeutic programme", nextReviewDue: "2026-07-19", unavailable: false },
    });

    const item = items.find((i) => i.id === "nutrition-treatment-overdue");
    expect(item?.urgency).toBe("overdue");
    expect(item?.detail).toMatch(/7 days ago/);
    expect(item?.href).toBe("/ehr/p1/imam");
  });

  it("treats an enrolled child with no scheduled review as overdue, not as fine", () => {
    const items = computeDueToday({
      ...base,
      imam: { enrolled: true, programme: "OTP", nextReviewDue: null, unavailable: false },
    });

    expect(items.find((i) => i.id === "nutrition-treatment-unscheduled")?.detail).toMatch(
      /nobody is expecting back/i,
    );
  });

  it("says nothing about nutrition when an enrolled child is up to date", () => {
    const items = computeDueToday({
      ...base,
      imam: { enrolled: true, programme: "OTP", nextReviewDue: "2026-08-02", unavailable: false },
    });

    expect(items.filter((i) => i.id.startsWith("nutrition"))).toHaveLength(0);
  });

  it("prompts enrolment for a malnourished child in no programme", () => {
    const items = computeDueToday({
      ...base,
      latestWeightForAgeZ: -2.9,
      imam: { enrolled: false, programme: null, nextReviewDue: null, unavailable: false },
    });

    const item = items.find((i) => i.id === "nutrition-followup");
    expect(item?.detail).toMatch(/in no nutrition programme/i);
    expect(item?.href).toBe("/ehr/p1/imam");
  });

  it("never implies a child is unenrolled when the record could not be read", () => {
    const items = computeDueToday({
      ...base,
      latestWeightForAgeZ: -2.9,
      imam: { enrolled: false, programme: null, nextReviewDue: null, unavailable: true },
    });

    const item = items.find((i) => i.id === "nutrition-unknown");
    expect(item?.urgency).toBe("unknown");
    expect(item?.detail).toMatch(/do not assume they are not/i);
    // And the "enrol them" prompt must not fire off an unread record.
    expect(items.find((i) => i.id === "nutrition-followup")).toBeUndefined();
  });
});
