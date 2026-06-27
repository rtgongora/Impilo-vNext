import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useClinicalCdsAlerts, useCdsInsight, type CdsAlert } from "../useClinicalCds";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe("useClinicalCdsAlerts", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("assembles real patient records into the rules-engine context and returns its alerts", async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith("/internal/v1/conditions"))
        return Promise.resolve({ data: [{ id: "c1", type: "condition", attributes: { conditionName: "Asthma", icdCode: "J45", clinicalStatus: "active" } }] });
      if (url.startsWith("/internal/v1/pharmacy/prescriptions"))
        return Promise.resolve({ data: [{ id: "p1", type: "prescription", attributes: { status: "active", items: [{ medication: "propranolol", dosage: "40mg", quantity: 30 }] } }] });
      if (url.startsWith("/internal/v1/vitals"))
        return Promise.resolve({ data: [{ id: "v1", type: "vitals", attributes: { systolic: 190, diastolic: 100, heartRate: 80, temperature: 37, respiratoryRate: 18 } }] });
      if (url.startsWith("/internal/v1/allergies"))
        return Promise.resolve({ data: [{ id: "a1", type: "allergy", attributes: { allergen: "penicillin", severity: "SEVERE", status: "active" } }] });
      return Promise.resolve({ data: [] });
    });
    post.mockResolvedValue({ data: { alerts: [{ code: "VITALS_HYPERTENSIVE_CRISIS", severity: "CRITICAL", message: "BP high" }] } });

    const { result } = renderHook(() => useClinicalCdsAlerts("patient-1"), { wrapper });

    await waitFor(() => expect(result.current.alerts.length).toBeGreaterThan(0));
    expect(result.current.alerts[0].code).toBe("VITALS_HYPERTENSIVE_CRISIS");

    // It POSTed a real assembled context to the governed rules engine (not a fabricated alert set).
    const [url, body] = post.mock.calls[0];
    expect(url).toBe("/internal/v1/clinical/rules/evaluate");
    expect(body.diagnoses).toEqual(["Asthma J45"]);
    expect(body.activeMedications).toEqual([{ genericName: "propranolol", displayName: "propranolol" }]);
    expect(body.vitals).toMatchObject({ systolicBp: 190, heartRate: 80, temperatureC: 37 });
    expect(body.allergies).toEqual([{ substance: "penicillin", severity: "SEVERE" }]);
  });

  it("is disabled (no evaluation) when there is no active patient", async () => {
    const { result } = renderHook(() => useClinicalCdsAlerts(undefined), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(true));
    expect(post).not.toHaveBeenCalled();
    expect(result.current.alerts).toEqual([]);
  });
});

describe("useCdsInsight", () => {
  beforeEach(() => {
    post.mockReset();
  });

  const alert: CdsAlert = { code: "DRUG_ALLERGY_INTERACTION", severity: "CRITICAL", message: "conflict" };

  it("does not call the LLM when there are no deterministic alerts", async () => {
    const { result } = renderHook(() => useCdsInsight("p1", [], "enc-1"), { wrapper });
    await waitFor(() => expect(result.current).toBeTruthy());
    expect(post).not.toHaveBeenCalled();
    expect(result.current.insight).toBeNull();
  });

  it("returns the grounded insight and sends only code/severity/message", async () => {
    post.mockResolvedValue({ data: { insight: { summary: "Review the allergy", generated_by: "AI" } } });
    const { result } = renderHook(() => useCdsInsight("p1", [alert], "enc-1"), { wrapper });
    await waitFor(() => expect(result.current.insight).not.toBeNull());
    expect(result.current.insight?.summary).toBe("Review the allergy");
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/clinical/cds/summary",
      expect.objectContaining({
        alerts: [{ code: "DRUG_ALLERGY_INTERACTION", severity: "CRITICAL", message: "conflict" }],
        encounter_id: "enc-1",
      }),
    );
  });

  it("surfaces null insight (fail-closed) without error", async () => {
    post.mockResolvedValue({ data: { insight: null } });
    const { result } = renderHook(() => useCdsInsight("p1", [alert], "enc-1"), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.insight).toBeNull();
  });
});
