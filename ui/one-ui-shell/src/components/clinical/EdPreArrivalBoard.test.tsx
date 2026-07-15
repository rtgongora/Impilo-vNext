import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post: vi.fn() } }));

import { EdPreArrivalBoard } from "./EdPreArrivalBoard";

function renderBoard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <EdPreArrivalBoard facilityId="fac-1" />
    </QueryClientProvider>,
  );
}

describe("EdPreArrivalBoard", () => {
  beforeEach(() => get.mockReset());

  it("explains the empty state", async () => {
    get.mockResolvedValue({ data: [] });
    renderBoard();
    await waitFor(() => expect(screen.getByText(/No inbound EMS patients/i)).toBeInTheDocument());
  });

  it("renders an inbound patient with parsed prehospital vitals", async () => {
    get.mockResolvedValue({
      data: [
        {
          visit_id: "v1",
          patient_cpid: "TEMP-EMS-B",
          ems_mission_ref: "mission-9",
          pre_arrival: JSON.stringify({ missionState: "EN_ROUTE_FACILITY", snapshot: { obsCount: 3, latestVitals: { hr: 120, sbp: 92, spo2: 93, rr: 24 }, latestGcs: { gcs: 13 }, narrative: "Fall ~6m" } }),
        },
      ],
    });
    renderBoard();
    await waitFor(() => expect(screen.getByText("TEMP-EMS-B")).toBeInTheDocument());
    expect(screen.getByText("EN_ROUTE_FACILITY")).toBeInTheDocument();
    expect(screen.getByText(/HR 120 · SBP 92 · SpO₂ 93 · RR 24/)).toBeInTheDocument();
  });
});
