import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import VirtualHospitalDetailPage from "./page";

// The UI's ALL_VIRTUAL_HOSPITALS constant was deleted in the Wave-2 registry
// de-dup; the detail page now comes from the BFF. Feed the mock the canonical seed.
const ALL_VIRTUAL_HOSPITALS = (
  JSON.parse(
    readFileSync(
      path.resolve(
        path.dirname(fileURLToPath(import.meta.url)),
        "../../../../../../../../contracts/telemedicine/virtual-hospitals.json",
      ),
      "utf8",
    ),
  ) as { virtualHospitals: Array<{ id: string }> }
).virtualHospitals;

const { paramsRef, get } = vi.hoisted(() => ({
  paramsRef: { current: { id: "vh-national-telemedicine" } as { id: string } },
  get: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useParams: () => paramsRef.current,
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine/virtual-hospitals/[id] detail", () => {
  beforeEach(() => {
    window.localStorage.clear();
    get.mockReset();
    get.mockImplementation(async (path: string) => {
      const prefix = "/internal/v1/telemedicine/operating-model/virtual-hospitals/";
      if (path.startsWith(prefix)) {
        const id = decodeURIComponent(path.slice(prefix.length));
        const hospital = ALL_VIRTUAL_HOSPITALS.find((vh) => vh.id === id);
        if (!hospital) throw new Error("404 VIRTUAL_HOSPITAL_UNKNOWN");
        return { data: { type: "virtual-hospital", attributes: hospital } };
      }
      throw new Error(`unexpected path ${path}`);
    });
  });

  it("answers the identity-model questions for a routable institution", async () => {
    paramsRef.current = { id: "vh-national-telemedicine" };
    renderWithQuery(<VirtualHospitalDetailPage />);

    expect(await screen.findByText("National Telemedicine Hospital")).toBeInTheDocument();
    expect(screen.getByText("vh-national-telemedicine")).toBeInTheDocument();
    expect(
      screen.getByText(/virtual service-delivery entity \(never a physical facility record\)/i),
    ).toBeInTheDocument();
    // Regulatory posture is configurable, never presumed
    expect(screen.getByText(/pending regulatory determination/i)).toBeInTheDocument();
    // Real entry path shown for routable institutions
    expect(screen.getByRole("link", { name: /create request/i })).toHaveAttribute(
      "href",
      "/telemedicine/new",
    );
    // Staffing doctrine: no implicit assignment
    expect(screen.getByText(/no provider is auto-assigned virtual duties/i)).toBeInTheDocument();
  });

  it("offers no pretend entry path for a not-yet-routable institution", async () => {
    paramsRef.current = { id: "vh-mental-health" };
    renderWithQuery(<VirtualHospitalDetailPage />);

    expect(await screen.findByText("Virtual Mental Health Unit")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /create request/i })).not.toBeInTheDocument();
    expect(screen.getByText(/not routable yet/i)).toBeInTheDocument();
    // Every queue is honestly awaiting backend
    expect(screen.getAllByText("awaiting backend").length).toBeGreaterThan(0);
  });

  it("handles unknown ids without pretending data exists", async () => {
    paramsRef.current = { id: "vh-does-not-exist" };
    renderWithQuery(<VirtualHospitalDetailPage />);
    expect(await screen.findByText(/no virtual hospital with id/i)).toBeInTheDocument();
  });
});
