/**
 * RMNP W13-C mobile clinical journey workspaces — forced form keys.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const formsMocks = vi.hoisted(() => ({
  getFormCatalog: vi.fn(),
  submitEncounterForm: vi.fn(),
}));
vi.mock("../../services/encounterFormsService", () => formsMocks);

function renderWithQuery(element: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<QueryClientProvider client={queryClient}>{element}</QueryClientProvider>);
  });
  return { container, root };
}

function byTestId(container: HTMLElement, testId: string) {
  return container.querySelector(`[data-testid="${testId}"]`);
}

describe("ClinicalJourneyWorkspaces", () => {
  let root: Root | null = null;

  beforeEach(() => {
    formsMocks.getFormCatalog.mockResolvedValue([
      {
        formKey: "impilo.pnc.maternal.contact.v1",
        definitionJson: JSON.stringify({
          id: "pnc-maternal",
          title: "PNC maternal",
          sections: [
            {
              id: "completion",
              title: "Completion",
              fields: [
                {
                  id: "screeningComplete",
                  label: "Screening complete",
                  kind: "coded_single",
                  options: [{ code: "YES", display: "Yes" }, { code: "NO", display: "No" }],
                },
              ],
            },
          ],
        }),
      },
    ]);
    formsMocks.submitEncounterForm.mockResolvedValue({ responseId: "R1", status: "SUBMITTED" });
  });

  afterEach(() => {
    root?.unmount();
    root = null;
    vi.clearAllMocks();
  });

  it("FacilityPncMaternalWorkspace forces impilo.pnc.maternal.contact.v1", async () => {
    const mod = await import("../../screens/provider/ClinicalJourneyWorkspaces");
    const { container, root: r } = renderWithQuery(<mod.FacilityPncMaternalWorkspace />);
    root = r;
    expect(byTestId(container, "facility-pnc-maternal-workspace")).toBeTruthy();
    expect(container.textContent).toContain("impilo.pnc.maternal.contact.v1");
    expect(container.textContent).toContain("Distinct from CHW");
  });

  it("FacilityPncNewbornWorkspace delegates PSBI to young-infant path", async () => {
    formsMocks.getFormCatalog.mockResolvedValue([
      {
        formKey: "impilo.pnc.newborn.contact.v1",
        definitionJson: JSON.stringify({
          id: "pnc-newborn",
          title: "PNC newborn",
          sections: [{ id: "s", title: "S", fields: [] }],
        }),
      },
    ]);
    const mod = await import("../../screens/provider/ClinicalJourneyWorkspaces");
    const { container, root: r } = renderWithQuery(<mod.FacilityPncNewbornWorkspace />);
    root = r;
    expect(container.textContent).toContain("young-infant.imci.v1");
    expect(container.textContent).toContain("PSBI is not restated");
  });
});
