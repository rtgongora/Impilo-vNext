import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PatientLocationBadge } from "./PatientLocationBadge";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

const { useParams, useSearchParams } = vi.hoisted(() => ({
  useParams: vi.fn(),
  useSearchParams: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useParams, useSearchParams }));

const { useFacilityStore } = vi.hoisted(() => ({ useFacilityStore: vi.fn() }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore }));

function emptyParams() {
  return { get: () => null } as unknown as URLSearchParams;
}

function renderBadge() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PatientLocationBadge />
    </QueryClientProvider> as ReactNode,
  );
}

describe("PatientLocationBadge — G053 live inpatient location", () => {
  beforeEach(() => {
    get.mockReset();
    useParams.mockReturnValue({ patientId: "CPID-555" });
    useSearchParams.mockReturnValue(emptyParams());
    useFacilityStore.mockReturnValue({ facility: { id: "FAC-1", name: "Parirenyatwa" } });
  });

  it("renders ward + bed from the live admission (SoR), overriding URL hints", async () => {
    get.mockResolvedValue({
      data: {
        admissionRef: "ADM-1",
        status: "ADMITTED",
        wardName: "Medical Ward A",
        bedNumber: "MW-12",
      },
    });

    renderBadge();

    await waitFor(() => expect(screen.getByText("Medical Ward A")).toBeInTheDocument());
    expect(screen.getByText("MW-12")).toBeInTheDocument();
    // queried the current-location SoR route with the patient cpid
    expect(get).toHaveBeenCalledWith(
      expect.stringContaining("/internal/v1/inpatient/admissions/current-location?subject_cpid=CPID-555"),
    );
  });

  it("falls back to facility-only outpatient label when the patient is not admitted", async () => {
    get.mockResolvedValue({ data: null });
    renderBadge();
    await waitFor(() => expect(screen.getByText("Outpatient")).toBeInTheDocument());
    expect(screen.getByText("Parirenyatwa")).toBeInTheDocument();
  });
});
