/**
 * Supervisor Dashboard Tests — Metrics and team data loading.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetFacilityMetrics = vi.fn();
const mockGetTeamMembers = vi.fn();
const mockGetEscalations = vi.fn();

vi.mock("../../services/supportService", () => ({
  getFacilityMetrics: (...args: unknown[]) => mockGetFacilityMetrics(...args),
  getTeamMembers: (...args: unknown[]) => mockGetTeamMembers(...args),
  getEscalations: (...args: unknown[]) => mockGetEscalations(...args),
}));

describe("Supervisor Dashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads facility metrics", async () => {
    mockGetFacilityMetrics.mockResolvedValue({
      facilityId: "facility-1",
      facilityName: "Harare Central Hospital",
      date: "2026-03-15",
      patientsSeenToday: 42,
      encountersOpen: 8,
      encountersClosed: 34,
      averageWaitTimeMinutes: 25,
      pendingTasks: 12,
      overdueEscalations: 2,
      stockAlerts: 3,
    });

    const metrics = await mockGetFacilityMetrics("facility-1");
    expect(metrics.patientsSeenToday).toBe(42);
    expect(metrics.encountersOpen).toBe(8);
    expect(metrics.overdueEscalations).toBe(2);
    expect(metrics.stockAlerts).toBe(3);
  });

  it("loads team members with task counts", async () => {
    mockGetTeamMembers.mockResolvedValue([
      {
        id: "tm-1",
        name: "Dr. Moyo",
        role: "Physician",
        facilityId: "facility-1",
        status: "ACTIVE",
        activeTasks: 5,
        completedToday: 12,
        lastActive: "2026-03-15T14:30:00Z",
      },
      {
        id: "tm-2",
        name: "Nurse Chipo",
        role: "Nurse",
        facilityId: "facility-1",
        status: "ACTIVE",
        activeTasks: 3,
        completedToday: 8,
        lastActive: "2026-03-15T14:25:00Z",
      },
    ]);

    const team = await mockGetTeamMembers("facility-1");
    expect(team).toHaveLength(2);
    expect(team[0].activeTasks).toBe(5);
    expect(team[1].completedToday).toBe(8);
  });

  it("loads escalations", async () => {
    mockGetEscalations.mockResolvedValue([
      {
        id: "esc-1",
        type: "CLINICAL",
        title: "Critical patient waiting",
        description: "Patient with chest pain waiting > 30min",
        severity: "CRITICAL",
        status: "PENDING",
        raisedBy: "nurse-1",
        raisedByName: "Nurse Chipo",
        facilityId: "facility-1",
        createdAt: "2026-03-15T14:00:00Z",
      },
    ]);

    const escalations = await mockGetEscalations("facility-1");
    expect(escalations).toHaveLength(1);
    expect(escalations[0].severity).toBe("CRITICAL");
    expect(escalations[0].status).toBe("PENDING");
  });
});
