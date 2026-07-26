import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { DakFormRenderer } from "../clinical-form-renderer/DakFormRenderer";
import type {
  ClinicalFieldKind,
  ClinicalFormDefinition,
  ClinicalFormRuntimeContext,
} from "../types";

/**
 * The renderer declared ten field kinds and implemented six. The other four rendered the
 * words "Unsupported field kind" into the clinical form — a live defect, not a gap in a
 * roadmap. These tests pin every declared kind to something a clinician can actually use.
 */

const ALL_KINDS: ClinicalFieldKind[] = [
  "coded_single",
  "coded_multi",
  "numeric_with_unit",
  "date",
  "datetime",
  "boolean",
  "segmented",
  "terminology_bound",
  "fhir_mapped",
  "text",
  "clinical_assertion",
  "exam_finding",
];

const context: ClinicalFormRuntimeContext = {
  patient: {
    patientId: "CPID-1",
    ageMonths: 14,
    ageBand: "INFANT",
    sex: "FEMALE",
    pregnant: null,
    programmes: [],
    knownConditionCodes: [],
  },
  encounterType: "IMCI",
  providerRoles: ["NURSE"],
};

function formWith(kind: ClinicalFieldKind): ClinicalFormDefinition {
  return {
    id: `test.${kind}`,
    version: "1",
    title: "Field kind probe",
    description: "",
    healthDomain: "CHILD",
    programme: "IMCI",
    encounterTypes: ["IMCI"],
    sections: [
      {
        id: "s1",
        title: "Section",
        fields: [
          {
            id: "probe",
            linkId: "probe",
            label: "Probe field",
            kind,
            unit: kind === "numeric_with_unit" ? "kg" : undefined,
            options:
              kind === "coded_single" || kind === "coded_multi" || kind === "segmented" || kind === "terminology_bound"
                ? [
                    { code: "a", display: "Option A" },
                    { code: "b", display: "Option B" },
                  ]
                : undefined,
          },
        ],
      },
    ],
  };
}

function renderKind(kind: ClinicalFieldKind) {
  return render(
    <DakFormRenderer form={formWith(kind)} context={context} onSubmit={vi.fn()} />,
  );
}

describe("form renderer field kinds", () => {
  it.each(ALL_KINDS)("renders %s without falling through to the unsupported message", (kind) => {
    const { container } = renderKind(kind);
    expect(container.textContent).not.toContain("Unsupported field kind");
  });

  it("renders a clinical assertion as the four canonical answers", () => {
    renderKind("clinical_assertion");

    expect(screen.getByRole("radio", { name: "Yes" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "No" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Unknown" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Not assessed" })).toBeTruthy();
  });

  it("renders an examination finding with every reason a finding may be absent", () => {
    renderKind("exam_finding");

    // Seven wordy options exceed the segment budget, so the control renders as a select —
    // the fallback exists so the answers are still all reachable on a phone.
    const select = screen.getByRole("combobox");
    const labels = Array.from(select.querySelectorAll("option")).map((o) => o.textContent);
    expect(labels).toContain("Normal");
    expect(labels).toContain("Not examined");
    expect(labels).toContain("Unable to examine");
    expect(labels).toContain("Deferred");
    expect(labels).toContain("Child distressed");
    expect(labels).toContain("Uncertain");
  });

  it("renders a boolean as an explicit choice, not a checkbox", () => {
    // An unticked checkbox is indistinguishable from an unanswered question.
    renderKind("boolean");

    expect(screen.getByRole("radio", { name: "Yes" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "No" })).toBeTruthy();
    expect(screen.queryByRole("checkbox")).toBeNull();
  });

  it("renders a datetime as a real datetime input", () => {
    const { container } = renderKind("datetime");
    expect(container.querySelector('input[type="datetime-local"]')).toBeTruthy();
  });

  it("renders a terminology-bound field with its coded options", () => {
    renderKind("terminology_bound");
    const select = screen.getByRole("combobox");
    const labels = Array.from(select.querySelectorAll("option")).map((o) => o.textContent);
    expect(labels).toContain("Option A");
  });

  it("renders a FHIR-mapped field by its underlying shape, keeping the mapping as metadata", () => {
    const { container } = renderKind("fhir_mapped");
    expect(container.querySelector('input[type="text"]')).toBeTruthy();
    expect(container.textContent).not.toContain("linkId");
  });

  it("captures a clinical assertion answer", () => {
    renderKind("clinical_assertion");

    fireEvent.click(screen.getByRole("radio", { name: "Not assessed" }));
    expect(screen.getByRole("radio", { name: "Not assessed" }).getAttribute("aria-checked")).toBe("true");
  });
});
