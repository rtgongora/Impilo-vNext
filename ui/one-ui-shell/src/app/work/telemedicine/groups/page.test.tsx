import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ClinicalGroupsPage from "./page";
import {
  CLINICAL_GROUP_TYPES,
  GROUP_CREATION_BLOCKED_REASON,
  GROUP_GOVERNANCE_REQUIREMENTS,
} from "@/lib/telemedicine/clinical-groups";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine/groups", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation(async (path: string) => {
      if (path === "/internal/v1/telemedicine/operating-model/clinical-groups") {
        return {
          data: {
            type: "clinical-group-taxonomy",
            attributes: {
              creationBlocked: true,
              creationBlockedReason: GROUP_CREATION_BLOCKED_REASON,
              governanceRequirements: GROUP_GOVERNANCE_REQUIREMENTS,
              groupTypes: CLINICAL_GROUP_TYPES,
            },
          },
        };
      }
      throw new Error(`unexpected path ${path}`);
    });
  });

  it("keeps clinical group creation fail-closed with the reason stated", async () => {
    renderWithQuery(<ClinicalGroupsPage />);
    expect(screen.getByText(/group creation is governance-blocked/i)).toBeInTheDocument();
    expect(
      await screen.findByText(/creation stays disabled until the governed model ships/i),
    ).toBeInTheDocument();
    // No create affordance exists anywhere on the page.
    expect(screen.queryByRole("button", { name: /create/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /create/i })).not.toBeInTheDocument();
  });

  it("stays fail-closed when the operating-model registry is unreachable", async () => {
    get.mockRejectedValue(new Error("registry down"));
    renderWithQuery(<ClinicalGroupsPage />);
    expect(screen.getByText(/group creation is governance-blocked/i)).toBeInTheDocument();
    expect(
      await screen.findByText(/registry is unreachable/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/group creation remains blocked/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /create/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /create/i })).not.toBeInTheDocument();
  });

  it("renders the full governed taxonomy from the registry", async () => {
    renderWithQuery(<ClinicalGroupsPage />);
    expect(await screen.findByText(CLINICAL_GROUP_TYPES[0].label)).toBeInTheDocument();
    for (const group of CLINICAL_GROUP_TYPES) {
      expect(screen.getByText(group.label)).toBeInTheDocument();
    }
    expect(get).toHaveBeenCalledWith("/internal/v1/telemedicine/operating-model/clinical-groups");
  });

  it("shows patient-data exposure limits per group type", async () => {
    const user = userEvent.setup();
    renderWithQuery(<ClinicalGroupsPage />);

    // MDT panels never get care-team-scoped data
    await screen.findByText("MDT panel");
    expect(screen.getAllByText("Case presentation only").length).toBeGreaterThan(0);

    await user.click(screen.getByText("MDT panel"));
    expect(screen.getByText("Oncology MDT")).toBeInTheDocument();
    expect(screen.getByText("MDT / Case Review Mode")).toBeInTheDocument();
  });

  it("explains why wellness social groups are not reused", () => {
    renderWithQuery(<ClinicalGroupsPage />);
    expect(screen.getByText(/why wellness social groups are not reused/i)).toBeInTheDocument();
  });
});
