import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DeathCasesPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

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
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("DeathCasesPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("renders real death cases from the BFF with dignified status", async () => {
    get.mockResolvedValueOnce([
      {
        caseId: "dc-1",
        patientCpid: "CPID-1",
        placeOfDeathContext: "INPATIENT",
        certificationStatus: "DRAFT",
        coronerReferralRequired: true,
        civilRegistrationStatus: "NOT_STARTED",
      },
    ]);
    render(<DeathCasesPage />);
    await waitFor(() => {
      expect(screen.getByText("CPID-1")).toBeInTheDocument();
    });
    expect(screen.getByText("Referral required")).toBeInTheDocument();
    // Real link to the case detail, not a dead button.
    expect(screen.getByRole("link", { name: "CPID-1" })).toHaveAttribute("href", "/work/clinical/death-cases/dc-1");
    expect(get).toHaveBeenCalledWith("/internal/v1/death/cases");
  });

  it("shows an honest empty state, not a fake dashboard", async () => {
    get.mockResolvedValueOnce([]);
    render(<DeathCasesPage />);
    await waitFor(() => {
      expect(screen.getByText(/No death cases recorded/)).toBeInTheDocument();
    });
  });

  it("surfaces a real error instead of hiding it", async () => {
    get.mockRejectedValueOnce({ error: { message: "Upstream death service unavailable" } });
    render(<DeathCasesPage />);
    await waitFor(() => {
      expect(screen.getByText(/Upstream death service unavailable/)).toBeInTheDocument();
    });
  });
});
