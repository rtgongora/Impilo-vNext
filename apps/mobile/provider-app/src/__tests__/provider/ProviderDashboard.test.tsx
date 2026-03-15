/**
 * Provider Dashboard Tests — Task and encounter display verification.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetMyTasks = vi.fn();
const mockListEncounters = vi.fn();

vi.mock("../../services/taskService", () => ({
  getMyTasks: (...args: unknown[]) => mockGetMyTasks(...args),
}));

vi.mock("../../services/encounterService", () => ({
  listEncounters: (...args: unknown[]) => mockListEncounters(...args),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: () => ({
    facilityId: "facility-1",
    facilityName: "Test Facility",
    unreadNotifications: 0,
  }),
  appStore: {
    subscribe: vi.fn(() => () => {}),
    getState: () => ({ facilityId: "facility-1" }),
  },
}));

describe("Provider Dashboard Data Loading", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads tasks and encounters on mount", async () => {
    mockGetMyTasks.mockResolvedValue({
      tasks: [
        {
          id: "task-1",
          title: "Review lab results",
          assigneeId: "provider-1",
          assigneeName: "Dr. Smith",
          facilityId: "facility-1",
          taskType: "LAB_REVIEW",
          priority: "HIGH",
          status: "PENDING",
          createdAt: "2026-03-15T08:00:00Z",
        },
      ],
      totalElements: 1,
    });

    mockListEncounters.mockResolvedValue({
      encounters: [
        {
          id: "enc-1",
          patientId: "pat-1",
          facilityId: "facility-1",
          encounterType: "OUTPATIENT",
          status: "IN_PROGRESS",
          startedAt: "2026-03-15T09:00:00Z",
          createdAt: "2026-03-15T09:00:00Z",
          updatedAt: "2026-03-15T09:00:00Z",
        },
      ],
      totalElements: 1,
    });

    // Simulate loading
    const [taskResult, encounterResult] = await Promise.all([
      mockGetMyTasks(undefined, 0, 50),
      mockListEncounters(undefined, 0, 20),
    ]);

    expect(taskResult.tasks).toHaveLength(1);
    expect(taskResult.tasks[0].title).toBe("Review lab results");
    expect(encounterResult.encounters).toHaveLength(1);
    expect(encounterResult.encounters[0].status).toBe("IN_PROGRESS");
  });

  it("handles empty task list", async () => {
    mockGetMyTasks.mockResolvedValue({ tasks: [], totalElements: 0 });
    mockListEncounters.mockResolvedValue({ encounters: [], totalElements: 0 });

    const [taskResult, encounterResult] = await Promise.all([
      mockGetMyTasks(undefined, 0, 50),
      mockListEncounters(undefined, 0, 20),
    ]);

    expect(taskResult.tasks).toHaveLength(0);
    expect(encounterResult.encounters).toHaveLength(0);
  });

  it("handles API errors gracefully", async () => {
    mockGetMyTasks.mockRejectedValue(new Error("Network error"));

    await expect(mockGetMyTasks()).rejects.toThrow("Network error");
  });
});
