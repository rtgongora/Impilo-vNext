import { describe, expect, it } from "vitest";
import { summarizeCertificateValidity, summarizeFundoMyLearning } from "./useFundoLms";

describe("summarizeFundoMyLearning", () => {
  it("derives KPIs from array payloads", () => {
    const kpis = summarizeFundoMyLearning({
      inProgress: [{ id: "ip-1" }, { id: "ip-2" }],
      required: [{ id: "req-1" }, { id: "req-2" }, { id: "req-3" }],
      overdue: [{ id: "od-1" }],
      cpdEligibleCompletions: [{ id: "cpd-1" }, { id: "cpd-2" }],
    });

    expect(kpis).toEqual({
      inProgress: 2,
      required: 3,
      overdue: 1,
      cpdEligible: 2,
    });
  });

  it("falls back to numeric aggregates when arrays are absent", () => {
    const kpis = summarizeFundoMyLearning({
      inProgressCount: 5,
      requiredCount: "4",
      overdueCount: 2,
      cpdEligibleCount: "3",
    });

    expect(kpis).toEqual({
      inProgress: 5,
      required: 4,
      overdue: 2,
      cpdEligible: 3,
    });
  });

  it("does not fabricate required from overdue when required is missing", () => {
    const kpis = summarizeFundoMyLearning({
      overdue: [{ id: "od-1" }, { id: "od-2" }],
    });

    expect(kpis.required).toBe(0);
    expect(kpis.overdue).toBe(2);
  });
});

describe("summarizeCertificateValidity", () => {
  const now = new Date("2026-06-26T00:00:00Z");

  it("flags an active certificate well before expiry", () => {
    const v = summarizeCertificateValidity({ status: "ISSUED", validUntil: "2027-06-26T00:00:00Z" }, now);
    expect(v.state).toBe("ACTIVE");
    expect(v.needsRenewal).toBe(false);
  });

  it("flags expiring-soon inside the refresher lead window", () => {
    const v = summarizeCertificateValidity({ status: "ISSUED", validUntil: "2026-07-10T00:00:00Z" }, now);
    expect(v.state).toBe("EXPIRING_SOON");
    expect(v.needsRenewal).toBe(true);
    expect(v.daysRemaining).toBe(14);
  });

  it("flags expired by date even if status not yet swept", () => {
    const v = summarizeCertificateValidity({ status: "ISSUED", validUntil: "2026-06-25T00:00:00Z" }, now);
    expect(v.state).toBe("EXPIRED");
    expect(v.needsRenewal).toBe(true);
  });

  it("respects an EXPIRED status from the backend sweep", () => {
    const v = summarizeCertificateValidity({ status: "EXPIRED", validUntil: "2026-06-25T00:00:00Z" }, now);
    expect(v.state).toBe("EXPIRED");
  });

  it("treats a certificate with no expiry as non-expiring", () => {
    const v = summarizeCertificateValidity({ status: "ISSUED" }, now);
    expect(v.state).toBe("NO_EXPIRY");
    expect(v.needsRenewal).toBe(false);
  });

  it("marks revoked certificates", () => {
    const v = summarizeCertificateValidity({ status: "REVOKED", validUntil: "2027-01-01T00:00:00Z" }, now);
    expect(v.state).toBe("REVOKED");
  });
});
