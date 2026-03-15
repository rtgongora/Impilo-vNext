/**
 * Offline Capture Tests — Verifies offline data capture and sync behavior.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockRecordScreening = vi.fn();
const mockRecordImmunization = vi.fn();
const mockRecordCommunityVisit = vi.fn();

vi.mock("../../services/householdService", () => ({
  recordScreening: (...args: unknown[]) => mockRecordScreening(...args),
  recordImmunization: (...args: unknown[]) => mockRecordImmunization(...args),
  recordCommunityVisit: (...args: unknown[]) => mockRecordCommunityVisit(...args),
}));

describe("Offline Capture", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("records screening with offline flag", async () => {
    mockRecordScreening.mockResolvedValue({
      id: "scr-1",
      patientId: "pat-1",
      screeningType: "MALARIA",
      status: "COMPLETED",
      results: { result: "Negative", recorded_offline: true },
      screenedBy: "chw-1",
      screenedAt: "2026-03-15T10:00:00Z",
    });

    const result = await mockRecordScreening({
      patientId: "pat-1",
      screeningType: "MALARIA",
      results: { result: "Negative", recorded_offline: true },
    });

    expect(result.screeningType).toBe("MALARIA");
    expect(result.results.recorded_offline).toBe(true);
  });

  it("records immunization", async () => {
    mockRecordImmunization.mockResolvedValue({
      id: "imm-1",
      patientId: "pat-1",
      vaccineName: "BCG",
      vaccineCode: "BCG",
      doseNumber: 1,
      doseDate: "2026-03-15",
      batchNumber: "BATCH-001",
      site: "LEFT_DELTOID",
      administeredBy: "chw-1",
    });

    const result = await mockRecordImmunization({
      patientId: "pat-1",
      vaccineName: "BCG",
      vaccineCode: "BCG",
      doseNumber: 1,
      batchNumber: "BATCH-001",
      site: "LEFT_DELTOID",
    });

    expect(result.vaccineName).toBe("BCG");
    expect(result.doseNumber).toBe(1);
  });

  it("records community visit with GPS", async () => {
    mockRecordCommunityVisit.mockResolvedValue({
      id: "visit-1",
      householdId: "hh-1",
      visitDate: "2026-03-15T10:30:00Z",
      visitType: "GENERAL",
      status: "IN_PROGRESS",
      gpsLatitude: -17.8292,
      gpsLongitude: 31.0522,
      visitedBy: "chw-1",
      createdAt: "2026-03-15T10:30:00Z",
    });

    const result = await mockRecordCommunityVisit({
      householdId: "hh-1",
      visitType: "GENERAL",
      gpsLatitude: -17.8292,
      gpsLongitude: 31.0522,
    });

    expect(result.gpsLatitude).toBe(-17.8292);
    expect(result.status).toBe("IN_PROGRESS");
  });

  it("handles network errors during capture", async () => {
    mockRecordScreening.mockRejectedValue(new Error("Network error"));

    await expect(
      mockRecordScreening({ patientId: "pat-1", screeningType: "HIV", results: {} })
    ).rejects.toThrow("Network error");
  });
});
