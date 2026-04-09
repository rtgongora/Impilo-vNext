import { describe, expect, it } from "vitest";
import {
  buildOperationalAlerts,
  buildQueuePerformanceByType,
  buildQueueRows,
  buildWardUtilization,
  mapQueuePriority,
  patientLabel,
  waitMinutesSince,
  type BedResource,
  type QueueEntryResource,
  type QueueStatsData,
  type WardResource,
} from "../useFacilityOperations";

describe("useFacilityOperations view models", () => {
  it("patientLabel shortens id safely", () => {
    expect(patientLabel("a1b2c3d4-e5f6-7890-abcd-ef1234567890")).toMatch(/^Patient [a-f0-9]+$/i);
    expect(patientLabel("")).toBe("Patient");
  });

  it("mapQueuePriority maps tiers", () => {
    expect(mapQueuePriority("URGENT")).toBe("Urgent");
    expect(mapQueuePriority("LOW")).toBe("Low");
    expect(mapQueuePriority(null)).toBe("Normal");
  });

  it("waitMinutesSince is non-negative", () => {
    const past = new Date(Date.now() - 120_000).toISOString();
    expect(waitMinutesSince(past)).toBeGreaterThanOrEqual(1);
  });

  it("buildQueueRows maps waiting entries", () => {
    const entries: QueueEntryResource[] = [
      {
        id: "q1",
        type: "QueueEntry",
        attributes: {
          facility_id: "f",
          patient_id: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
          queue_type: "OPD",
          priority: "URGENT",
          status: "WAITING",
          assigned_to: null,
          arrival_time: new Date(Date.now() - 30 * 60_000).toISOString(),
          reason: null,
        },
      },
    ];
    const rows = buildQueueRows(entries);
    expect(rows).toHaveLength(1);
    expect(rows[0].priority).toBe("Urgent");
    expect(rows[0].type).toBe("OPD");
  });

  it("buildWardUtilization prefers bed-level rollups when beds exist", () => {
    const wards: WardResource[] = [];
    const beds: BedResource[] = [
      {
        id: "b1",
        type: "bed",
        attributes: {
          bedNumber: "1",
          bedType: "STANDARD",
          status: "OCCUPIED",
          wardId: "w1",
          wardName: "ICU",
          acuity: null,
          patientId: null,
          patientName: null,
          admittedAt: null,
          assignedDoctor: null,
        },
      },
      {
        id: "b2",
        type: "bed",
        attributes: {
          bedNumber: "2",
          bedType: "STANDARD",
          status: "AVAILABLE",
          wardId: "w1",
          wardName: "ICU",
          acuity: null,
          patientId: null,
          patientName: null,
          admittedAt: null,
          assignedDoctor: null,
        },
      },
    ];
    const u = buildWardUtilization(wards, beds);
    expect(u).toEqual([{ ward: "ICU", total: 2, occupied: 1, available: 1, cleaning: 0 }]);
  });

  it("buildQueuePerformanceByType aggregates waiting entries", () => {
    const now = Date.now();
    const entries: QueueEntryResource[] = [
      {
        id: "1",
        type: "QueueEntry",
        attributes: {
          facility_id: "f",
          patient_id: "p1",
          queue_type: "ANC",
          priority: null,
          status: "WAITING",
          assigned_to: null,
          arrival_time: new Date(now - 10 * 60_000).toISOString(),
          reason: null,
        },
      },
      {
        id: "2",
        type: "QueueEntry",
        attributes: {
          facility_id: "f",
          patient_id: "p2",
          queue_type: "ANC",
          priority: null,
          status: "WAITING",
          assigned_to: null,
          arrival_time: new Date(now - 20 * 60_000).toISOString(),
          reason: null,
        },
      },
    ];
    const perf = buildQueuePerformanceByType(entries);
    expect(perf).toHaveLength(1);
    expect(perf[0].name).toBe("ANC");
    expect(perf[0].waiting).toBe(2);
    expect(perf[0].avgWait).toBe(15);
  });

  it("buildOperationalAlerts emits threshold messages from live-shaped inputs", () => {
    const wards: WardResource[] = [
      {
        id: "w1",
        type: "ward",
        attributes: {
          name: "ICU",
          wardType: "ICU",
          floor: "2",
          totalBeds: 10,
          availableBeds: 0,
          occupiedBeds: 10,
          status: "ACTIVE",
        },
      },
    ];
    const beds: BedResource[] = Array.from({ length: 10 }, (_, i) => ({
      id: `b${i}`,
      type: "bed",
      attributes: {
        bedNumber: String(i),
        bedType: "STANDARD",
        status: i < 9 ? "OCCUPIED" : "AVAILABLE",
        wardId: "w1",
        wardName: "ICU",
        acuity: null,
        patientId: null,
        patientName: null,
        admittedAt: null,
        assignedDoctor: null,
      },
    }));
    const queueStats: QueueStatsData = {
      waiting: 20,
      called: 0,
      inService: 0,
      completed: 0,
      noShow: 0,
      avgWaitSeconds: 40 * 60,
    };
    const longWaitEntries: QueueEntryResource[] = Array.from({ length: 4 }, (_, i) => ({
      id: `lq${i}`,
      type: "QueueEntry",
      attributes: {
        facility_id: "f",
        patient_id: `aaaaaaaa-bbbb-cccc-dddd-00000000000${i}`,
        queue_type: "OPD",
        priority: null,
        status: "WAITING",
        assigned_to: null,
        arrival_time: new Date(Date.now() - 50 * 60_000).toISOString(),
        reason: null,
      },
    }));
    const alerts = buildOperationalAlerts({
      wards,
      beds,
      queueStats,
      queueEntriesWaiting: longWaitEntries,
    });
    expect(alerts.some((a) => a.message.includes("ICU"))).toBe(true);
    expect(alerts.some((a) => a.message.includes("20 patient"))).toBe(true);
    expect(alerts.some((a) => a.message.includes("40+ minutes"))).toBe(true);
  });
});
