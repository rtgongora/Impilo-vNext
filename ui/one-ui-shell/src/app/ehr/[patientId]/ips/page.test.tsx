import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import IpsPage from "./page";

const { getText } = vi.hoisted(() => ({ getText: vi.fn() }));

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => ({
    data: {
      data: {
        id: "patient-1",
        attributes: {
          cpid: "ZW-CPID-TEST-001",
        },
      },
    },
    isLoading: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    getText: (path: string) => getText(path),
  },
}));

function renderWithQuery(ui: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("IpsPage", () => {
  beforeEach(() => {
    getText.mockResolvedValue(
      JSON.stringify({
        resourceType: "Bundle",
        type: "DOCUMENT",
        timestamp: "2026-04-10T09:00:00.000Z",
        entry: [
          { resource: { resourceType: "Patient", id: "p1" } },
          { resource: { resourceType: "Composition", id: "c1" } },
          { resource: { resourceType: "AllergyIntolerance", id: "a1" } },
        ],
      }),
    );
  });

  it("loads IPS via Experience BFF GET /internal/v1/summary/ips/{cpid}", async () => {
    renderWithQuery(<IpsPage />);

    await waitFor(() =>
      expect(getText).toHaveBeenCalledWith("/internal/v1/summary/ips/ZW-CPID-TEST-001"),
    );

    expect(screen.getByRole("heading", { level: 1, name: /International Patient Summary/i })).toBeInTheDocument();
    expect(screen.getByText(/Butano summary through the Experience BFF/i)).toBeInTheDocument();
    expect(screen.getByText(/ZW-CPID-TEST-001/)).toBeInTheDocument();
    expect(await screen.findByText("Bundle contents")).toBeInTheDocument();
    expect(screen.getByText("Patient")).toBeInTheDocument();
    expect(screen.getByText("Composition")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Timeline context/i })).toHaveAttribute(
      "href",
      "/ehr/patient-1/timeline",
    );
  });

  it("surfaces load failure when the BFF returns an error", async () => {
    getText.mockRejectedValue(new Error("upstream unavailable"));

    renderWithQuery(<IpsPage />);

    await waitFor(() => expect(screen.getByText(/Unable to load the IPS bundle/i)).toBeInTheDocument());
    expect(screen.getByText(/upstream unavailable/)).toBeInTheDocument();
  });
});
