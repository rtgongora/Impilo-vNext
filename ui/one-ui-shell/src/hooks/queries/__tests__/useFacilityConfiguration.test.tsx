// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const getMock = vi.fn();
const postMock = vi.fn();
const patchMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: (...a: unknown[]) => getMock(...a),
    post: (...a: unknown[]) => postMock(...a),
    patch: (...a: unknown[]) => patchMock(...a),
  },
}));

import {
  useFacilityConfiguration,
  useFacilitySetupChecklist,
  useConfigServicePoints,
  useFacilityQueueDefinitions,
  useCreateServicePoint,
  useUpdateServicePoint,
  useRetireServicePoint,
  useCreateWorkspace,
  useRetireWorkspace,
  usePublishConfiguration,
} from "../useFacilityConfiguration";

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "TestQueryClientWrapper";
  return Wrapper;
}

describe("useFacilityConfiguration hooks", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    patchMock.mockReset();
  });

  it("configuration hits the admin config route", async () => {
    getMock.mockResolvedValue({ data: { configurationState: "CONFIGURED" } });
    const { result } = renderHook(() => useFacilityConfiguration("42"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/configuration");
  });

  it("setup checklist hits the admin checklist route", async () => {
    getMock.mockResolvedValue({ data: { items: [] } });
    const { result } = renderHook(() => useFacilitySetupChecklist("42"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/setup-checklist");
  });

  it("service points hits the admin service-points route", async () => {
    getMock.mockResolvedValue({ data: [] });
    const { result } = renderHook(() => useConfigServicePoints("42"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/service-points");
  });

  it("queue definitions hits the admin queue-definitions route", async () => {
    getMock.mockResolvedValue({ data: [] });
    const { result } = renderHook(() => useFacilityQueueDefinitions("42"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/queue-definitions");
  });

  it("create service point posts to the admin route", async () => {
    postMock.mockResolvedValue({ data: { id: "sp-1" } });
    const { result } = renderHook(() => useCreateServicePoint("42"), { wrapper: wrapper() });
    await result.current.mutateAsync({ name: "Triage", servicePointType: "TRIAGE" });
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/service-points", {
      name: "Triage",
      servicePointType: "TRIAGE",
    });
  });

  it("update service point patches the admin route", async () => {
    patchMock.mockResolvedValue({ data: { id: "sp-1" } });
    const { result } = renderHook(() => useUpdateServicePoint("42"), { wrapper: wrapper() });
    await result.current.mutateAsync({ id: "sp-1", input: { name: "New" } });
    expect(patchMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/service-points/sp-1", {
      name: "New",
    });
  });

  it("retire service point posts to the retire route", async () => {
    postMock.mockResolvedValue({ data: { id: "sp-1", active: false } });
    const { result } = renderHook(() => useRetireServicePoint("42"), { wrapper: wrapper() });
    await result.current.mutateAsync("sp-1");
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/service-points/sp-1/retire");
  });

  it("create workspace posts to the admin route", async () => {
    postMock.mockResolvedValue({ data: { id: "ws-1" } });
    const { result } = renderHook(() => useCreateWorkspace("42"), { wrapper: wrapper() });
    await result.current.mutateAsync({ name: "OPD", workspaceType: "OPD" });
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/workspaces", {
      name: "OPD",
      workspaceType: "OPD",
    });
  });

  it("retire workspace posts to the retire route", async () => {
    postMock.mockResolvedValue({ data: { id: "ws-1", active: false } });
    const { result } = renderHook(() => useRetireWorkspace("42"), { wrapper: wrapper() });
    await result.current.mutateAsync("ws-1");
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/workspaces/ws-1/retire");
  });

  it("publish configuration posts to the publish route", async () => {
    postMock.mockResolvedValue({ data: { configurationState: "CONFIGURED" } });
    const { result } = renderHook(() => usePublishConfiguration("42"), { wrapper: wrapper() });
    await result.current.mutateAsync();
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/42/configuration/publish");
  });
});
