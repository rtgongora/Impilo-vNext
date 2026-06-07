import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { WardManagementPanel } from "./WardManagementPanel";

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: () => ({
    facility: { id: "f1000000-0000-0000-0000-000000000001", name: "Demo Hospital" },
  }),
}));

vi.mock("@/hooks/queries/useBeds", () => ({
  useBeds: () => ({
    data: {
      data: [
        {
          id: "bed-1",
          type: "bed",
          attributes: {
            bedNumber: "A1",
            status: "available",
            wardId: "ward-1",
            wardName: "Medical Ward",
          },
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
  useWards: () => ({
    data: {
      data: [
        {
          id: "ward-1",
          type: "ward",
          attributes: { name: "Medical Ward" },
        },
      ],
    },
  }),
  useDischargeBed: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock("@/hooks/queries/useInpatient", () => ({
  useDischargeAdmission: () => ({ mutateAsync: vi.fn() }),
  useTransferPatient: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn().mockResolvedValue({ data: null }) },
}));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <WardManagementPanel />
    </QueryClientProvider>,
  );
}

describe("WardManagementPanel", () => {
  it("renders ward KPI board from sovereign beds API", () => {
    renderPanel();
    expect(screen.getByText("Total Beds")).toBeInTheDocument();
    expect(screen.getByText("Medical Ward")).toBeInTheDocument();
  });
});
