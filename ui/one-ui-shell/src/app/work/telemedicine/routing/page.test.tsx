import type { ReactNode } from "react";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TelemedicineRoutingPage from "./page";

// The virtual-hospitals tab now reads the BFF registry (Wave-2 de-dup).
const VH_DIRECTORY_ENVELOPE = {
  data: {
    type: "virtual-hospital-directory",
    attributes: {
      virtualHospitals: (
        JSON.parse(
          readFileSync(
            path.resolve(
              path.dirname(fileURLToPath(import.meta.url)),
              "../../../../../../../contracts/telemedicine/virtual-hospitals.json",
            ),
            "utf8",
          ),
        ) as { virtualHospitals: unknown[] }
      ).virtualHospitals,
      source: "TUSO",
      registryDegraded: false,
    },
  },
};

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine/routing directory", () => {
  beforeEach(() => {
    get.mockReset();
    window.localStorage.clear();
  });

  it("searches the real provider routing endpoint and offers the governed request flow", async () => {
    get.mockResolvedValue({
      data: {
        content: [
          {
            providerPublicId: "prov-1",
            title: "Dr",
            givenName: "Tariro",
            familyName: "Ncube",
            profession: "MEDICAL_PRACTITIONER",
            cadre: "SPECIALIST",
            status: "ACTIVE",
          },
        ],
      },
    });

    const user = userEvent.setup();
    renderWithQuery(<TelemedicineRoutingPage />);

    await user.type(screen.getByPlaceholderText(/search verified providers/i), "ncube");
    expect(await screen.findByText("Dr Tariro Ncube")).toBeInTheDocument();
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/teleconsult/routing/providers?q=ncube");
    });
    expect(screen.getByRole("link", { name: /request consult/i })).toHaveAttribute(
      "href",
      "/telemedicine/new",
    );
  });

  it("pins a provider without bypassing policy (pin is discovery only)", async () => {
    get.mockResolvedValue({
      data: {
        content: [
          {
            providerPublicId: "prov-2",
            givenName: "Rudo",
            familyName: "Moyo",
            profession: "NURSE",
          },
        ],
      },
    });

    const user = userEvent.setup();
    renderWithQuery(<TelemedicineRoutingPage />);

    await user.type(screen.getByPlaceholderText(/search verified providers/i), "moyo");
    const pinButton = await screen.findByRole("button", { name: /pin rudo moyo/i });
    await user.click(pinButton);

    expect(screen.getByText("Pinned")).toBeInTheDocument();
    const stored = window.localStorage.getItem("impilo.telemedicine.routing-pins.v1");
    expect(stored).toContain("prov-2");
  });

  it("separates routable virtual hospitals from configured-only ones", async () => {
    get.mockImplementation(async (p: string) =>
      p === "/internal/v1/telemedicine/operating-model/virtual-hospitals"
        ? VH_DIRECTORY_ENVELOPE
        : { data: { content: [] } },
    );
    const user = userEvent.setup();
    renderWithQuery(<TelemedicineRoutingPage />);

    await user.click(screen.getByRole("button", { name: /virtual hospitals/i }));
    expect(
      await screen.findByText(/routable today \(via real teleconsult specialty-pool seams\)/i),
    ).toBeInTheDocument();
    expect((await screen.findAllByText("not routable yet")).length).toBeGreaterThan(0);
  });

  it("states the fail-closed 501 posture for pool/on-call routing", () => {
    renderWithQuery(<TelemedicineRoutingPage />);
    expect(screen.getByText(/fails closed \(501\)/i)).toBeInTheDocument();
  });
});
