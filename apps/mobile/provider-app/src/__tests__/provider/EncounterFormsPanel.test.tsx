import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const formsMocks = vi.hoisted(() => ({
  resolveEncounterForms: vi.fn(),
  getFormCatalog: vi.fn(),
  listEncounterFormResponses: vi.fn(),
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
  return { container, root, queryClient };
}

async function flush() {
  for (let i = 0; i < 6; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

const triageDef = JSON.stringify({
  id: "impilo.opd.triage.v1",
  title: "OPD Triage",
  sections: [{ id: "s", title: "Triage", fields: [{ id: "reason", label: "Reason", kind: "text" }] }],
});

describe("EncounterFormsPanel (mobile)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    formsMocks.resolveEncounterForms.mockResolvedValue({
      mandatory: [{ formKey: "impilo.opd.triage.v1", formVersion: 1, name: "OPD Triage", requiresCountersign: false, obligation: "MANDATORY" }],
      recommended: [],
      optional: [],
      countersignRequired: [{ formKey: "impilo.rx.v1", formVersion: 1, name: "Prescription", requiresCountersign: true, obligation: "COUNTERSIGN_REQUIRED" }],
      prohibited: [{ formKey: "impilo.diag.v1", formVersion: 1, name: "Diagnosis", requiresCountersign: false, obligation: "PROHIBITED", reason: "Cadre NURSE is not permitted to perform workflow DIAGNOSE" }],
    });
    formsMocks.getFormCatalog.mockResolvedValue([
      { formKey: "impilo.opd.triage.v1", name: "OPD Triage", version: 1, formSchemaVersionId: "v1", requiresCountersign: false, definitionJson: triageDef },
    ]);
    formsMocks.listEncounterFormResponses.mockResolvedValue([
      { responseId: "r1", formKey: "impilo.opd.triage.v1", status: "SUBMITTED" },
    ]);
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
      mounted = undefined;
    }
  });

  it("renders resolved groups, a prohibited form with its reason, and documented responses", async () => {
    const mod = await import("../../screens/provider/EncounterFormsPanel");
    mounted = renderWithQuery(
      <mod.EncounterFormsPanel encounterId="42" resolveParams={{ cadre: "NURSE", careSetting: "OUTPATIENT" }} />,
    );
    await flush();

    const c = mounted.container;
    expect(c.querySelector('[data-testid="forms-group-MANDATORY"]')).toBeTruthy();
    expect(c.querySelector('[data-testid="forms-group-COUNTERSIGN_REQUIRED"]')).toBeTruthy();
    const prohibited = c.querySelector('[data-testid="forms-group-PROHIBITED"]');
    expect(prohibited?.textContent).toContain("not permitted");
    expect(c.querySelector('[data-testid="form-row-impilo.opd.triage.v1"]')).toBeTruthy();
    expect(c.querySelector('[data-testid="forms-submitted"]')?.textContent).toContain("SUBMITTED");
    expect(formsMocks.resolveEncounterForms).toHaveBeenCalledWith("42", { cadre: "NURSE", careSetting: "OUTPATIENT" });
  });
});
