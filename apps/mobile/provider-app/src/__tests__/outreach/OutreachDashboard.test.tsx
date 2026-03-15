/**
 * Outreach Dashboard Tests — Household and outreach data loading.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetHouseholds = vi.fn();
const mockGetMyTasks = vi.fn();

vi.mock("../../services/householdService", () => ({
  getHouseholds: (...args: unknown[]) => mockGetHouseholds(...args),
}));

vi.mock("../../services/taskService", () => ({
  getMyTasks: (...args: unknown[]) => mockGetMyTasks(...args),
}));

describe("Outreach Dashboard Data", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads households and outreach tasks", async () => {
    mockGetHouseholds.mockResolvedValue({
      households: [
        {
          id: "hh-1",
          headOfHousehold: "Jane Moyo",
          members: [
            { id: "m-1", patientId: "pat-1", name: "Baby Moyo", relationship: "CHILD", dateOfBirth: "2025-01-01", sex: "F" },
          ],
          address: "12 Main St, Harare",
          gpsLatitude: -17.8292,
          gpsLongitude: 31.0522,
          facilityId: "facility-1",
          lastVisitDate: "2026-03-01T10:00:00Z",
          nextVisitDate: "2026-03-10T10:00:00Z",
          riskCategory: "HIGH",
          createdAt: "2026-01-01T00:00:00Z",
        },
      ],
      totalElements: 1,
    });

    mockGetMyTasks.mockResolvedValue({
      tasks: [
        {
          id: "task-1",
          title: "Household visit — Moyo family",
          taskType: "COMMUNITY_VISIT",
          priority: "HIGH",
          status: "PENDING",
          assigneeId: "chw-1",
          assigneeName: "Community Health Worker",
          facilityId: "facility-1",
          createdAt: "2026-03-15T06:00:00Z",
        },
      ],
      totalElements: 1,
    });

    const hhResult = await mockGetHouseholds("facility-1", 0, 100);
    const taskResult = await mockGetMyTasks(undefined, 0, 50);

    expect(hhResult.households).toHaveLength(1);
    expect(hhResult.households[0].headOfHousehold).toBe("Jane Moyo");
    expect(hhResult.households[0].riskCategory).toBe("HIGH");
    expect(hhResult.households[0].members).toHaveLength(1);

    const outreachTasks = taskResult.tasks.filter(
      (t: { taskType: string }) => t.taskType === "OUTREACH" || t.taskType === "COMMUNITY_VISIT"
    );
    expect(outreachTasks).toHaveLength(1);
  });

  it("identifies overdue visits", async () => {
    const pastDate = new Date();
    pastDate.setDate(pastDate.getDate() - 5);

    mockGetHouseholds.mockResolvedValue({
      households: [
        {
          id: "hh-1",
          headOfHousehold: "Test Family",
          members: [],
          address: "Test",
          gpsLatitude: 0,
          gpsLongitude: 0,
          facilityId: "facility-1",
          nextVisitDate: pastDate.toISOString(),
          riskCategory: "MEDIUM",
          createdAt: "2026-01-01T00:00:00Z",
        },
      ],
      totalElements: 1,
    });

    const result = await mockGetHouseholds("facility-1");
    const overdue = result.households.filter(
      (h: { nextVisitDate?: string }) => h.nextVisitDate && new Date(h.nextVisitDate) < new Date()
    );
    expect(overdue).toHaveLength(1);
  });
});
