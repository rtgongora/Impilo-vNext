import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import PatientSearchPage from "./page";

let workflow: string | null = null;

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) => (key === "workflow" ? workflow : null),
  }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatients: (params?: { search?: string }) => ({
    data: params?.search
      ? {
          data: [
            {
              id: "patient-1",
              attributes: {
                displayName: "Tariro Moyo",
                cpid: "CP-001",
                dateOfBirth: "1991-06-11",
                gender: "female",
              },
            },
          ],
        }
      : { data: [] },
    isLoading: false,
  }),
}));

describe("PatientSearchPage", () => {
  it("offers direct queue continuity from search results", async () => {
    const user = userEvent.setup();
    workflow = null;

    render(<PatientSearchPage />);

    expect(screen.getByText("Find the right patient, then route directly into chart or registration")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Search by name, CPID, national ID, or date of birth..."), "Tariro");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("Tariro Moyo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add to Queue" })).toHaveAttribute(
      "href",
      "/queue/walk-in?patientId=patient-1",
    );
  });

  it("routes LIMS entry through direct orders and results handoff", async () => {
    const user = userEvent.setup();
    workflow = "lims";

    render(<PatientSearchPage />);

    expect(screen.getByText("Find the right patient, then continue into laboratory orders and results")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Search by name, CPID, national ID, or date of birth..."), "Tariro");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByRole("link", { name: "Orders & Results" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/orders?entry=lims",
    );
    expect(screen.getByRole("link", { name: "Results" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/results?entry=lims",
    );
  });
});
