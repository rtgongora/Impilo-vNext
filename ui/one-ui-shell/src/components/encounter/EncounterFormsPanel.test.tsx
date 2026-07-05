import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ClinicalFormPatientContext } from "@/lib/clinical-forms/types";

const submitMutate = vi.fn().mockResolvedValue({ data: { responseId: "r1", status: "SUBMITTED" } });
const countersignMutate = vi.fn().mockResolvedValue({ data: { responseId: "r2", status: "SUBMITTED" } });

const state = {
  resolution: {
    isLoading: false,
    isError: false,
    data: {
      data: {
        mandatory: [
          { formKey: "impilo.opd.triage.v1", formSchemaId: "s1", formVersion: 1, formSchemaVersionId: "v1", name: "OPD Triage", requiresCountersign: false, obligation: "MANDATORY" },
        ],
        recommended: [],
        optional: [],
        countersignRequired: [
          { formKey: "impilo.rx.v1", formSchemaId: "s2", formVersion: 1, formSchemaVersionId: "v2", name: "Prescription", requiresCountersign: true, obligation: "COUNTERSIGN_REQUIRED" },
        ],
        prohibited: [
          { formKey: "impilo.diag.v1", formSchemaId: "s3", formVersion: 1, formSchemaVersionId: "v3", name: "Diagnosis", requiresCountersign: false, obligation: "PROHIBITED", reason: "Cadre NURSE is not permitted to perform workflow DIAGNOSE" },
        ],
      },
    },
  },
};

const triageDefinition = JSON.stringify({
  id: "impilo.opd.triage.v1",
  version: "1.0.0",
  title: "OPD Triage",
  description: "",
  healthDomain: "GENERAL",
  programme: "OPD",
  encounterTypes: ["OPD"],
  sexApplicability: "ALL",
  offlineCapable: true,
  audit: { dataCustodian: "MOHCC", sensitivity: "STANDARD" },
  sections: [{ id: "s", title: "Section", fields: [{ id: "reason", linkId: "reason", label: "Reason", kind: "text" }] }],
});

const responsesState: { data: { data: Array<Record<string, unknown>> } } = { data: { data: [] } };

vi.mock("@/hooks/queries/useEncounterForms", () => ({
  useEncounterFormResolution: () => state.resolution,
  useFormCatalog: () => ({
    data: { data: [{ formKey: "impilo.opd.triage.v1", name: "OPD Triage", version: 1, formSchemaVersionId: "v1", requiresCountersign: false, definitionJson: triageDefinition }] },
  }),
  useEncounterFormResponses: () => responsesState,
  useSubmitEncounterForm: () => ({ mutateAsync: submitMutate, isPending: false }),
  useCountersignFormResponse: () => ({ mutateAsync: countersignMutate, isPending: false }),
}));

// Render the real DAK form with a deterministic submit affordance.
vi.mock("@/lib/clinical-forms/clinical-form-renderer/DakFormRenderer", () => ({
  DakFormRenderer: ({ onSubmit, submitLabel }: { onSubmit?: (v: Record<string, unknown>) => void; submitLabel?: string }) => (
    <button type="button" onClick={() => onSubmit?.({ reason: "fever" })}>
      {submitLabel ?? "Submit"}
    </button>
  ),
}));

import { EncounterFormsPanel } from "./EncounterFormsPanel";

const patient: ClinicalFormPatientContext = {
  patientId: "p1",
  ageMonths: 360,
  ageBand: "ADULT",
  sex: "FEMALE",
  pregnant: null,
  programmes: [],
  knownConditionCodes: [],
};

function renderPanel() {
  return render(
    <EncounterFormsPanel
      encounterId="42"
      patientId="p1"
      patient={patient}
      resolveParams={{ cadre: "NURSE" }}
      providerRoles={["NURSE"]}
      encounterType="OPD"
    />,
  );
}

describe("EncounterFormsPanel", () => {
  beforeEach(() => {
    submitMutate.mockClear();
    countersignMutate.mockClear();
    responsesState.data = { data: [] };
  });

  it("groups resolved forms and shows a prohibited form greyed with its reason", () => {
    renderPanel();
    expect(screen.getByTestId("forms-group-MANDATORY")).toBeInTheDocument();
    expect(screen.getByTestId("forms-group-COUNTERSIGN_REQUIRED")).toBeInTheDocument();
    const prohibited = screen.getByTestId("forms-group-PROHIBITED");
    expect(prohibited).toHaveTextContent("not permitted");
    // Prohibited form's button is disabled (no fake completions).
    const diagBtn = screen.getByRole("button", { name: /Diagnosis/ });
    expect(diagBtn).toBeDisabled();
  });

  it("selecting a mandatory form renders it and submitting persists via the mutation", async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(screen.getByRole("button", { name: /OPD Triage/ }));
    const submitBtn = await screen.findByRole("button", { name: /Submit & record/ });
    await user.click(submitBtn);
    expect(submitMutate).toHaveBeenCalledWith({ formKey: "impilo.opd.triage.v1", answers: { reason: "fever" } });
    expect(await screen.findByText(/Submitted and recorded/)).toBeInTheDocument();
  });

  it("offers a countersign action for submitted countersign-required responses and records it", async () => {
    responsesState.data = {
      data: [
        { responseId: "r2", formKey: "impilo.rx.v1", status: "SUBMITTED", countersignRequired: true },
        { responseId: "r3", formKey: "impilo.opd.triage.v1", status: "SUBMITTED", countersignRequired: false },
      ],
    };
    const user = userEvent.setup();
    renderPanel();

    // Only the countersign-required response offers the action.
    expect(screen.getByTestId("countersign-open-r2")).toBeInTheDocument();
    expect(screen.queryByTestId("countersign-open-r3")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("countersign-open-r2"));
    await user.type(screen.getByLabelText("Countersign attestation"), "Reviewed and agreed");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(countersignMutate).toHaveBeenCalledWith({
      responseId: "r2",
      attestation: "Reviewed and agreed",
    });
    expect(await screen.findByText(/countersigned/)).toBeInTheDocument();
  });

  it("shows the server rejection honestly when a countersign is refused", async () => {
    responsesState.data = {
      data: [{ responseId: "r2", formKey: "impilo.rx.v1", status: "SUBMITTED", countersignRequired: true }],
    };
    countersignMutate.mockRejectedValueOnce(new Error("Response already countersigned"));
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByTestId("countersign-open-r2"));
    await user.click(screen.getByRole("button", { name: "Confirm" }));
    expect(await screen.findByText(/already countersigned/)).toBeInTheDocument();
  });
});
