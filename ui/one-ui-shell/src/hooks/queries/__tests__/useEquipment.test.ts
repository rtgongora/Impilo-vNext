import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  equipmentItems,
  useEquipmentList,
  useReadiness,
  useRegisterEquipment,
  useReportFault,
} from "../useEquipment";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("equipmentItems (envelope tolerance, no fabrication)", () => {
  it("unwraps { items: [] } envelopes", () => {
    expect(equipmentItems({ items: [{ a: 1 }] })).toEqual([{ a: 1 }]);
  });
  it("returns [] for null / primitives / non-array items", () => {
    expect(equipmentItems(null)).toEqual([]);
    expect(equipmentItems(42)).toEqual([]);
    expect(equipmentItems({ items: "nope" })).toEqual([]);
    expect(equipmentItems({})).toEqual([]);
  });
});

describe("useEquipment hooks call the BFF equipment lane", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("useEquipmentList GETs /internal/v1/equipment with facility + paging", async () => {
    get.mockResolvedValue({ items: [], total_elements: 0, has_more: false });
    const { result } = renderHook(() => useEquipmentList({ facilityId: "FAC-1", page: 0, size: 50 }), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/equipment?facility_id=FAC-1&page=0&size=50");
  });

  it("useReadiness GETs the readiness endpoint with facility_ref and is gated on facility", async () => {
    get.mockResolvedValue({ facility_ref: "FAC-9", ready: false, total_gaps: 1, profiles: [] });
    const { result } = renderHook(() => useReadiness("FAC-9"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/equipment/readiness?facility_ref=FAC-9");
    expect(result.current.data?.total_gaps).toBe(1);
  });

  it("useReadiness is disabled without a facility ref (no fabricated call)", async () => {
    const { result } = renderHook(() => useReadiness(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(get).not.toHaveBeenCalled();
  });

  it("useRegisterEquipment POSTs the registration body", async () => {
    post.mockResolvedValue({ equipment_id: "E1" });
    const { result } = renderHook(() => useRegisterEquipment(), { wrapper });
    result.current.mutate({ facilityId: "FAC-1", name: "Ventilator", equipmentType: "VENTILATOR" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/equipment", { facilityId: "FAC-1", name: "Ventilator", equipmentType: "VENTILATOR" });
  });

  it("useReportFault POSTs to the equipment fault path", async () => {
    post.mockResolvedValue({ fault_id: "F1" });
    const { result } = renderHook(() => useReportFault(), { wrapper });
    result.current.mutate({ equipmentId: "E9", body: { summary: "Leak", safetyConcern: true } });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/equipment/E9/faults", { summary: "Leak", safetyConcern: true });
  });
});
