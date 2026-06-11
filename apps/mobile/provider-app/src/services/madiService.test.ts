import { describe, expect, it, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}));

import {
  fetchBloodOrders,
  createBloodOrder,
  submitBloodOrder,
  fetchTransfusionEpisodes,
  startTransfusion,
  reportTransfusionReaction,
  fetchOperationalDrives,
  recordDriveDonation,
  fetchCentralBankMetrics,
  fetchEmergencyRedistributions,
  approveEmergencyRedistribution,
  initiateEmergencyHandoff,
} from "./madiService";

const BASE = "/internal/v1/mobile/provider/madi";

describe("provider madiService", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  it("fetchBloodOrders calls orders list with filters", async () => {
    mockGet.mockResolvedValue({
      data: { data: [{ order_id: "ord-1", status: "SUBMITTED" }] },
    });

    const orders = await fetchBloodOrders({ status: "SUBMITTED", patient_cpid: "cpid-1" });

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/orders?status=SUBMITTED&patient_cpid=cpid-1`);
    expect(orders[0]?.order_id).toBe("ord-1");
  });

  it("createBloodOrder posts order payload", async () => {
    mockPost.mockResolvedValue({
      data: { data: { order_id: "ord-2", patient_cpid: "cpid-2" } },
    });

    const order = await createBloodOrder({
      patient_cpid: "cpid-2",
      blood_group: "A",
      component_type: "PACKED_RED_CELLS",
      units_requested: 2,
    });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/orders`, {
      patient_cpid: "cpid-2",
      blood_group: "A",
      component_type: "PACKED_RED_CELLS",
      units_requested: 2,
    });
    expect(order?.order_id).toBe("ord-2");
  });

  it("submitBloodOrder posts to submit sub-path", async () => {
    mockPost.mockResolvedValue({ data: { data: { order_id: "ord-3", status: "SUBMITTED" } } });

    await submitBloodOrder("ord-3");

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/orders/ord-3/submit`, {});
  });

  it("fetchTransfusionEpisodes lists transfusions", async () => {
    mockGet.mockResolvedValue({
      data: { data: [{ episode_id: "ep-1", status: "IN_PROGRESS" }] },
    });

    const episodes = await fetchTransfusionEpisodes({ status: "IN_PROGRESS" });

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/transfusions?status=IN_PROGRESS`);
    expect(episodes).toHaveLength(1);
  });

  it("startTransfusion creates episode", async () => {
    mockPost.mockResolvedValue({
      data: { data: { episode_id: "ep-2", patient_cpid: "cpid-3" } },
    });

    const episode = await startTransfusion({ patient_cpid: "cpid-3", order_id: "ord-4" });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/transfusions`, {
      patient_cpid: "cpid-3",
      order_id: "ord-4",
    });
    expect(episode?.episode_id).toBe("ep-2");
  });

  it("reportTransfusionReaction posts haemovigilance payload", async () => {
    mockPost.mockResolvedValue({
      data: { data: { reaction_id: "rx-1", episode_id: "ep-2", severity: "MODERATE" } },
    });

    const reaction = await reportTransfusionReaction({
      episode_id: "ep-2",
      reaction_type: "FEVER",
      severity: "MODERATE",
      description: "Temp spike at 15 min",
    });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/haemovigilance/reactions`, {
      episode_id: "ep-2",
      reaction_type: "FEVER",
      severity: "MODERATE",
      description: "Temp spike at 15 min",
    });
    expect(reaction?.reaction_id).toBe("rx-1");
  });

  it("fetchOperationalDrives returns empty array on error", async () => {
    mockGet.mockRejectedValue(new Error("offline"));

    const drives = await fetchOperationalDrives();

    expect(drives).toEqual([]);
  });

  it("recordDriveDonation posts donation capture", async () => {
    mockPost.mockResolvedValue({ data: {} });

    const ok = await recordDriveDonation("drive-9", {
      donor_id: "donor-9",
      bag_number: "BAG-001",
      volume_ml: 450,
    });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/drives/drive-9/donations`, {
      donor_id: "donor-9",
      bag_number: "BAG-001",
      volume_ml: 450,
    });
    expect(ok).toEqual({ ok: true });
  });

  it("fetchCentralBankMetrics calls central bank metrics endpoint", async () => {
    mockGet.mockResolvedValue({
      data: { data: { totalUnits: 120, availableUnits: 80, openHaemovigilanceCases: 3 } },
    });

    const metrics = await fetchCentralBankMetrics();

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/central-bank/metrics`);
    expect(metrics?.totalUnits).toBe(120);
    expect(metrics?.availableUnits).toBe(80);
  });

  it("fetchEmergencyRedistributions lists redistribution queue", async () => {
    mockGet.mockResolvedValue({
      data: { data: [{ redistribution_id: "red-1", status: "REQUESTED" }] },
    });

    const rows = await fetchEmergencyRedistributions();

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/central-bank/emergency-redistributions`);
    expect(rows[0]?.redistribution_id).toBe("red-1");
  });

  it("approveEmergencyRedistribution posts approve payload", async () => {
    mockPost.mockResolvedValue({
      data: { data: { redistribution_id: "red-2", status: "FULFILLED" } },
    });

    const row = await approveEmergencyRedistribution("red-2", {
      approved_by: "ops-mobile",
      units_allocated: 2,
      status: "FULFILLED",
    });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/central-bank/emergency-redistributions/red-2/approve`, {
      approved_by: "ops-mobile",
      units_allocated: 2,
      status: "FULFILLED",
    });
    expect(row?.status).toBe("FULFILLED");
  });

  it("initiateEmergencyHandoff posts handoff retry", async () => {
    mockPost.mockResolvedValue({
      data: { data: { redistribution_id: "red-3", handoff_status: "HANDOFF_PENDING" } },
    });

    const row = await initiateEmergencyHandoff("red-3", { initiated_by: "ops-mobile" });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/central-bank/emergency-redistributions/red-3/handoff`, {
      initiated_by: "ops-mobile",
    });
    expect(row?.handoff_status).toBe("HANDOFF_PENDING");
  });
});
