import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import VirtualHospitalDirectoryPage from "./page";

// The UI's ALL_VIRTUAL_HOSPITALS constant was deleted in the Wave-2 registry
// de-dup; the directory now comes from the BFF. Feed the mock the canonical seed.
const ALL_VIRTUAL_HOSPITALS = (
  JSON.parse(
    readFileSync(
      path.resolve(
        path.dirname(fileURLToPath(import.meta.url)),
        "../../../../../../../contracts/telemedicine/virtual-hospitals.json",
      ),
      "utf8",
    ),
  ) as { virtualHospitals: Array<{ id: string; name: string } & Record<string, unknown>> }
).virtualHospitals;

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine/virtual-hospitals directory", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation(async (path: string) => {
      if (path === "/internal/v1/telemedicine/operating-model/virtual-hospitals") {
        return {
          data: {
            type: "virtual-hospital-directory",
            attributes: { virtualHospitals: ALL_VIRTUAL_HOSPITALS },
          },
        };
      }
      throw new Error(`unexpected path ${path}`);
    });
  });

  it("lists every configured virtual hospital from the operating-model registry", async () => {
    renderWithQuery(<VirtualHospitalDirectoryPage />);
    expect(await screen.findByText("National Telemedicine Hospital")).toBeInTheDocument();
    for (const name of [
      "Virtual Specialist Hospital",
      "Virtual Maternal and Newborn Unit",
      "Virtual Mental Health Unit",
      "Harare Provincial Virtual Hospital",
      "Midlands Provincial Virtual Hospital",
    ]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
    expect(get).toHaveBeenCalledWith("/internal/v1/telemedicine/operating-model/virtual-hospitals");
  });

  it("filters by level", async () => {
    const user = userEvent.setup();
    renderWithQuery(<VirtualHospitalDirectoryPage />);
    await screen.findByText("National Telemedicine Hospital");
    await user.click(screen.getByRole("button", { name: "Provincial" }));
    expect(screen.getByText("Harare Provincial Virtual Hospital")).toBeInTheDocument();
    expect(screen.queryByText("National Telemedicine Hospital")).not.toBeInTheDocument();
  });

  it("shows honest substrate badges and never an operational claim", async () => {
    renderWithQuery(<VirtualHospitalDirectoryPage />);
    await screen.findByText("National Telemedicine Hospital");
    expect(screen.getAllByText("Routable today via teleconsult routing").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Configured — awaiting backend substrate").length).toBeGreaterThan(0);
    expect(screen.queryAllByText(/^Operational$/)).toHaveLength(0);
  });

  it("links each card to its detail page", async () => {
    renderWithQuery(<VirtualHospitalDirectoryPage />);
    const first = ALL_VIRTUAL_HOSPITALS[0];
    const card = (await screen.findByText(first.name)).closest("a");
    expect(card).toHaveAttribute("href", `/work/telemedicine/virtual-hospitals/${first.id}`);
  });

  it("states honestly when the registry is unreachable", async () => {
    get.mockRejectedValue(new Error("registry down"));
    renderWithQuery(<VirtualHospitalDirectoryPage />);
    expect(
      await screen.findByText(/operating-model registry is unreachable/i),
    ).toBeInTheDocument();
  });
});
