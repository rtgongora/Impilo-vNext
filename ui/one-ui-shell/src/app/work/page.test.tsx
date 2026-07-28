import type { ReactNode } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import WorkHomePage from "./page";

const { useWorkHomeMock, useSessionExperienceContractMock, retrySection } = vi.hoisted(() => ({
  useWorkHomeMock: vi.fn(),
  useSessionExperienceContractMock: vi.fn(),
  retrySection: vi.fn(),
}));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle && <p>{subtitle}</p>}
      {children}
    </div>
  ),
}));
vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => useSessionExperienceContractMock(),
}));
vi.mock("@/hooks/queries/useWorkHome", () => ({
  useWorkHome: () => useWorkHomeMock(),
  useWorkHomeSectionRetry: () => retrySection,
}));
vi.mock("@/components/work-home/WorkOperationsPanel", () => ({
  WorkOperationsPanel: () => <div data-testid="work-operations-panel" />,
}));

afterEach(() => cleanup());

const RESOLVED_CONTEXT = {
  contextId: "ctx-1",
  contextKind: "facility" as const,
  sourceSystem: "VASHANDI" as const,
  availableModes: ["CLINICAL_CARE"],
  defaultMode: "CLINICAL_CARE",
  restrictions: [],
  label: "Parirenyatwa — Oncology Ward B",
  groupHint: "today" as const,
};

describe("WorkHomePage", () => {
  it("shows a loading state while the session contract is resolving", () => {
    useSessionExperienceContractMock.mockReturnValue({ contract: undefined, isLoading: true });
    useWorkHomeMock.mockReturnValue({ workHome: { sections: [], friendlyState: "" }, isLoading: false, isError: false });

    render(<WorkHomePage />);
    expect(screen.getByText(/Loading your work context/)).toBeTruthy();
  });

  it("renders the in-page workplace picker when no context is recommended (F3 — never redirects to /facility)", () => {
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [], recommendedContextId: null },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({ workHome: { sections: [], friendlyState: "" }, isLoading: false, isError: false });

    render(<WorkHomePage />);
    expect(screen.getByText("No work assignment found")).toBeTruthy();
  });

  it("renders the picker with real resolved contexts when several are available", () => {
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [RESOLVED_CONTEXT], recommendedContextId: null },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({ workHome: { sections: [], friendlyState: "" }, isLoading: false, isError: false });

    render(<WorkHomePage />);
    expect(screen.getByText("Parirenyatwa — Oncology Ward B")).toBeTruthy();
  });

  it("shows an honest degraded message (not a crash) when the context/mode cannot be re-proven", () => {
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [RESOLVED_CONTEXT], recommendedContextId: "ctx-1" },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({
      workHome: { sections: [], friendlyState: "work_context_unavailable" },
      isLoading: false,
      isError: false,
    });

    render(<WorkHomePage />);
    expect(screen.getByText(/Your work context isn't available right now/)).toBeTruthy();
  });

  it("renders sections from the BFF for the recommended context", () => {
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [RESOLVED_CONTEXT], recommendedContextId: "ctx-1" },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({
      workHome: {
        contextId: "ctx-1",
        family: "FACILITY_CLINICAL",
        mode: "CLINICAL_CARE",
        friendlyState: "",
        sections: [
          {
            sectionId: "clinical-worklist",
            title: "Clinical worklist",
            status: "OK",
            items: [],
            buckets: {},
            summary: {},
            generatedAt: "2026-07-28T05:00:00Z",
          },
        ],
      },
      isLoading: false,
      isError: false,
    });

    render(<WorkHomePage />);
    expect(screen.getByText("Clinical worklist")).toBeTruthy();
    expect(screen.getByText("Clinical care")).toBeTruthy(); // WORK_HOME_FAMILY_LABELS subtitle
  });

  it("shows the migrated provider-workspace operations panel for the facility-clinical family (F6)", () => {
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [RESOLVED_CONTEXT], recommendedContextId: "ctx-1" },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({
      workHome: { contextId: "ctx-1", family: "FACILITY_CLINICAL", mode: "CLINICAL_CARE", friendlyState: "", sections: [] },
      isLoading: false,
      isError: false,
    });

    render(<WorkHomePage />);

    expect(screen.getByTestId("work-operations-panel")).toBeTruthy();
  });

  it("does not show the operations panel for a non-clinical family (e.g. facility management)", () => {
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [RESOLVED_CONTEXT], recommendedContextId: "ctx-1" },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({
      workHome: { contextId: "ctx-1", family: "FACILITY_MANAGEMENT", mode: "FACILITY_MANAGEMENT", friendlyState: "", sections: [] },
      isLoading: false,
      isError: false,
    });

    render(<WorkHomePage />);

    expect(screen.queryByTestId("work-operations-panel")).toBeNull();
  });

  it("lets the person switch workplace back to the picker when more than one context is resolved", () => {
    const secondContext = { ...RESOLVED_CONTEXT, contextId: "ctx-2", label: "District office" };
    useSessionExperienceContractMock.mockReturnValue({
      contract: { resolvedWorkContexts: [RESOLVED_CONTEXT, secondContext], recommendedContextId: "ctx-1" },
      isLoading: false,
    });
    useWorkHomeMock.mockReturnValue({
      workHome: { contextId: "ctx-1", family: "FACILITY_CLINICAL", mode: "CLINICAL_CARE", friendlyState: "", sections: [] },
      isLoading: false,
      isError: false,
    });

    render(<WorkHomePage />);
    fireEvent.click(screen.getByText("Switch workplace"));

    // Back at the picker, both workplaces are offered again.
    expect(screen.getByText("Parirenyatwa — Oncology Ward B")).toBeTruthy();
    expect(screen.getByText("District office")).toBeTruthy();
  });
});
