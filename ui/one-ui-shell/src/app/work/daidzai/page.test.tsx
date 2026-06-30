import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DaidzaiCommandPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo" />,
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

describe("DaidzaiCommandPage", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("renders real incidents from the BFF with triage category", async () => {
    get.mockResolvedValueOnce([
      {
        id: "inc-1",
        incidentReference: "INC-20260630-AAA",
        incidentType: "INDIVIDUAL",
        emergencyCategory: "CARDIAC",
        severity: "CRITICAL",
        triageCategory: "RED",
        status: "TRIAGED",
      },
    ]);
    render(<DaidzaiCommandPage />);
    await waitFor(() => {
      expect(screen.getByText("INC-20260630-AAA")).toBeInTheDocument();
    });
    expect(screen.getByText("RED")).toBeInTheDocument();
    // Real nav links, not dead buttons
    expect(screen.getByRole("link", { name: "Dispatch" })).toHaveAttribute(
      "href",
      "/work/daidzai/dispatch"
    );
    expect(screen.getByRole("link", { name: "Disasters" })).toHaveAttribute(
      "href",
      "/work/daidzai/disasters"
    );
    expect(get).toHaveBeenCalledWith("/internal/v1/daidzai/incidents");
  });

  it("shows an honest empty state, not a fake dashboard", async () => {
    get.mockResolvedValueOnce([]);
    render(<DaidzaiCommandPage />);
    await waitFor(() => {
      expect(screen.getByText(/No active emergency incidents/)).toBeInTheDocument();
    });
  });

  it("surfaces a real error instead of hiding it", async () => {
    get.mockRejectedValueOnce({ status: 500 });
    render(<DaidzaiCommandPage />);
    await waitFor(() => {
      expect(screen.getByText(/Could not load incidents/)).toBeInTheDocument();
    });
  });
});
