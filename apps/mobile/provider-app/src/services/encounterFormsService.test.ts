import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import {
  resolveEncounterForms,
  getFormCatalog,
  createFormDraft,
  submitFormResponse,
  submitEncounterForm,
  listEncounterFormResponses,
} from "./encounterFormsService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

function ok<T>(data: T) {
  return { data: { data }, status: 200, correlationId: "c1", headers: {} };
}

describe("encounterFormsService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("resolveEncounterForms posts params and unwraps the resolution", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ mandatory: [{ formKey: "impilo.opd.triage.v1" }], recommended: [], optional: [], prohibited: [], countersignRequired: [] }),
    );
    const res = await resolveEncounterForms("42", { cadre: "NURSE", careSetting: "OUTPATIENT" });
    expect(res.mandatory[0].formKey).toBe("impilo.opd.triage.v1");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/encounters/42/forms/resolve", {
      cadre: "NURSE",
      careSetting: "OUTPATIENT",
    });
  });

  it("getFormCatalog unwraps the catalog array", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok([{ formKey: "impilo.opd.triage.v1", definitionJson: "{}" }]));
    const catalog = await getFormCatalog();
    expect(catalog).toHaveLength(1);
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/extensions/forms/catalog");
  });

  it("createFormDraft posts answers to the version-locked draft endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok({ responseId: "r1", status: "DRAFT" }));
    const draft = await createFormDraft("42", "impilo.opd.triage.v1", { triageCategory: "3" });
    expect(draft.responseId).toBe("r1");
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/encounters/42/forms/impilo.opd.triage.v1/responses/draft",
      { answers: { triageCategory: "3" } },
    );
  });

  it("submitFormResponse posts to the submit endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok({ responseId: "r1", status: "SUBMITTED" }));
    const res = await submitFormResponse("r1");
    expect(res.status).toBe("SUBMITTED");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/form-responses/r1/submit", {});
  });

  it("submitEncounterForm chains draft then submit", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce(ok({ responseId: "r9", status: "DRAFT" }))
      .mockResolvedValueOnce(ok({ responseId: "r9", status: "SUBMITTED" }));
    const res = await submitEncounterForm("42", "impilo.opd.triage.v1", { triageCategory: "2" });
    expect(res.status).toBe("SUBMITTED");
    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/internal/v1/encounters/42/forms/impilo.opd.triage.v1/responses/draft",
      { answers: { triageCategory: "2" } },
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/internal/v1/form-responses/r9/submit", {});
  });

  it("listEncounterFormResponses unwraps the array", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok([{ responseId: "r1", formKey: "x", status: "SUBMITTED" }]));
    const rows = await listEncounterFormResponses("42");
    expect(rows).toHaveLength(1);
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/encounters/42/form-responses");
  });
});
