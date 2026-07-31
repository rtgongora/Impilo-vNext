/**
 * Enforces the six behaviours in docs/clinical/rmnp/partograph-ctg-mobile-contract.md §4 at the
 * rendering layer. The service-layer test (maternityService.test.ts) proves the data contract
 * (200-vs-502 discrimination, the write path sending exactly what it is given); this file proves
 * the UI does not launder that data into something calmer than it is.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@impilo/mobile-api-client";

const maternityMocks = vi.hoisted(() => ({
  openPartograph: vi.fn(),
  getActivePartograph: vi.fn(),
  getPartograph: vi.fn(),
  addPartographPoint: vi.fn(),
  closePartograph: vi.fn(),
  openCtgSession: vi.fn(),
  getActiveCtgSession: vi.fn(),
  getCtgSession: vi.fn(),
  getCtgChunks: vi.fn(),
  addCtgAnnotation: vi.fn(),
  classifyNearMissForm: vi.fn(),
}));
vi.mock("../../services/maternityService", () => maternityMocks);

const formsMocks = vi.hoisted(() => ({ getFormCatalog: vi.fn() }));
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

function typeInto(container: HTMLElement, testId: string, value: string) {
  const input = byTestId(container, testId) as HTMLInputElement | null;
  if (!input) throw new Error(`Missing input: ${testId}`);
  act(() => {
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

async function click(container: HTMLElement, testId: string) {
  const el = byTestId(container, testId) as HTMLElement | null;
  if (!el) throw new Error(`Missing element: ${testId}`);
  await act(async () => {
    el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

async function flush() {
  for (let i = 0; i < 6; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

describe("PartographWorkspace", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    formsMocks.getFormCatalog.mockResolvedValue([]);
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
      mounted = undefined;
    }
  });

  it("renders 'no partograph open' as a plain answer, never as an error (contract §4.1)", async () => {
    maternityMocks.getActivePartograph.mockResolvedValue({ partographActive: false, patientId: "P1" });
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.PartographWorkspace />);
    await flush();
    typeInto(mounted.container, "maternity-patient-id", "P1");
    await flush();

    expect(byTestId(mounted.container, "partograph-no-session")).toBeTruthy();
    expect(mounted.container.querySelector('[accessibilityrole="alert"]')).toBeFalsy();
  });

  it("renders the unavailable banner on PCT_UNAVAILABLE, never an empty chart (contract §4.2)", async () => {
    maternityMocks.getActivePartograph.mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.PartographWorkspace />);
    await flush();
    typeInto(mounted.container, "maternity-patient-id", "P1");
    await flush();

    const banner = mounted.container.textContent ?? "";
    expect(banner).toContain("could not be read");
    expect(banner).not.toContain("No partograph is open");
    expect(byTestId(mounted.container, "partograph-no-session")).toBeFalsy();
  });

  it("renders INSUFFICIENT_DATA as the most alarming state, with every outstanding observation listed (contract §4.4)", async () => {
    maternityMocks.getActivePartograph.mockResolvedValue({
      partographActive: true,
      session: {
        session_id: "S1",
        started_at: "2026-07-26T08:00:00Z",
        status: "ACTIVE",
        progress: {
          status: "INSUFFICIENT_DATA",
          latest_dilation_cm: null,
          outstanding_observations: [
            "fetal heart rate has never been recorded in this session",
            "cervical dilation has never been recorded in this session",
          ],
          observations: ["No labour observations have been recorded in this session."],
          recommended_action: "Assess and record cervical dilation to establish progress.",
          content_version: "who-partograph-classic-1.0.0",
          content_source: "WHO",
        },
      },
    });
    maternityMocks.getPartograph.mockResolvedValue({ points: [] });

    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.PartographWorkspace />);
    await flush();
    typeInto(mounted.container, "maternity-patient-id", "P1");
    await flush();

    const progress = byTestId(mounted.container, "partograph-progress");
    expect(progress?.textContent).toContain("Insufficient data");
    expect(progress?.textContent).not.toMatch(/on track|reassuring|all clear/i);

    const outstanding = byTestId(mounted.container, "partograph-outstanding");
    expect(outstanding).toBeTruthy();
    expect(outstanding?.textContent).toContain("fetal heart rate has never been recorded");
    expect(outstanding?.textContent).toContain("cervical dilation has never been recorded");
  });

  // The no-carry-forward guarantee (contract §4.5) is proved at the service layer in
  // maternityService.test.ts, where `addPartographPoint` is shown to send exactly the answers
  // object it is given — the UI resets that object to {} on every new entry and after every
  // successful submit (see PartographWorkspace's pointMutation.onSuccess).
});

describe("CtgWorkspace", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    formsMocks.getFormCatalog.mockResolvedValue([]);
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
      mounted = undefined;
    }
  });

  it("renders a gap as a gap, never joined across it (contract §4.6)", async () => {
    maternityMocks.getActiveCtgSession.mockResolvedValue({
      ctgActive: true,
      session: { session_id: "S2", started_at: "2026-07-26T08:00:00Z", status: "ACTIVE", device_id: null },
    });
    maternityMocks.getCtgSession.mockResolvedValue({ annotations: [] });
    maternityMocks.getCtgChunks.mockResolvedValue([
      {
        chunk_id: "C1",
        channel: "FHR",
        started_at: "2026-07-26T08:00:00Z",
        sample_count: 1200,
        missing_sample_count: 600,
      },
    ]);

    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.CtgWorkspace />);
    await flush();
    typeInto(mounted.container, "maternity-patient-id", "P1");
    await flush();

    const gap = byTestId(mounted.container, "ctg-chunk-gap-C1");
    expect(gap).toBeTruthy();
    expect(gap?.textContent).toContain("600 samples not captured");
    expect(gap?.textContent).toContain("Not interpolated");
  });

  it("renders the unavailable banner on PCT_UNAVAILABLE rather than an empty trace", async () => {
    maternityMocks.getActiveCtgSession.mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.CtgWorkspace />);
    await flush();
    typeInto(mounted.container, "maternity-patient-id", "P1");
    await flush();

    expect(mounted.container.textContent ?? "").toContain("could not be read");
    expect(byTestId(mounted.container, "ctg-no-session")).toBeFalsy();
  });
});

/**
 * MaternalNearMissWorkspace answers to its own contract (RMNP W12), enforced here at the
 * rendering layer the same way the two suites above enforce theirs: blank ≠ ABSENT ≠
 * unrecognised, INDETERMINATE is never rendered as "no near-miss", and a 502 reads as an outage
 * rather than a clinical all-clear.
 */
describe("MaternalNearMissWorkspace", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  const NEAR_MISS_CATALOG = [
    {
      formKey: "impilo.maternal.nearmiss.assessment.v1",
      definitionJson: JSON.stringify({
        id: "maternal-near-miss-v1",
        title: "Maternal near-miss",
        sections: [
          {
            id: "cardiovascular",
            title: "Cardiovascular",
            fields: [
              {
                id: "nearMiss.shock",
                label: "Shock",
                kind: "coded_single",
                options: [
                  { code: "PRESENT", display: "Yes" },
                  { code: "ABSENT", display: "No" },
                ],
              },
              { id: "nearMiss.lactateMmolL", label: "Lactate", kind: "decimal", unit: "mmol/L" },
            ],
          },
        ],
      }),
    },
  ];

  function baseOutcome(overrides: Record<string, unknown> = {}, metaOverrides: Record<string, unknown> = {}) {
    return {
      data: {
        classification_code: "NEAR_MISS_NOT_MET",
        classification_name: "WHO near-miss criteria not met",
        status: "CLASSIFIED",
        is_near_miss: false,
        provisional: false,
        unresolved_criteria: [],
        missing_inputs: [],
        rationale: "No organ-dysfunction criterion was met on the information recorded.",
        review_required: false,
        review_note: null,
        ...overrides,
      },
      meta: {
        request_id: "req-1",
        correlation_id: "corr-1",
        form_key: "impilo.maternal.nearmiss.assessment.v1",
        criteria_recorded: 1,
        criteria_left_blank: [],
        ...metaOverrides,
      },
    };
  }

  beforeEach(() => {
    vi.clearAllMocks();
    formsMocks.getFormCatalog.mockResolvedValue(NEAR_MISS_CATALOG);
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
      mounted = undefined;
    }
  });

  it("renders form-21's sections unanswered by default, and omits a blank field on submit", async () => {
    maternityMocks.classifyNearMissForm.mockResolvedValue(baseOutcome());
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.MaternalNearMissWorkspace />);
    await flush();

    expect(mounted.container.textContent ?? "").toContain("Shock");
    expect(mounted.container.textContent ?? "").toContain("Lactate");

    await click(mounted.container, "field-nearMiss.shock-opt-PRESENT");
    await flush();

    await click(mounted.container, "near-miss-submit");
    await flush();

    expect(maternityMocks.classifyNearMissForm).toHaveBeenCalledTimes(1);
    const [answers] = maternityMocks.classifyNearMissForm.mock.calls[0];
    expect(answers).toEqual({ "nearMiss.shock": "PRESENT" });
    expect(answers).not.toHaveProperty("nearMiss.lactateMmolL");
  });

  it("renders a near-miss identification with its rationale, distinctly from NOT_MET", async () => {
    maternityMocks.classifyNearMissForm.mockResolvedValue(
      baseOutcome({
        classification_code: "NEAR_MISS_CARDIOVASCULAR",
        classification_name: "Cardiovascular dysfunction",
        is_near_miss: true,
        review_required: true,
        review_note: "She met the WHO near-miss criteria and survived.",
        rationale: "Signs meet the criteria for NEAR_MISS_CARDIOVASCULAR.",
      }),
    );
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.MaternalNearMissWorkspace />);
    await flush();

    await click(mounted.container, "near-miss-submit");
    await flush();

    const outcome = byTestId(mounted.container, "near-miss-outcome");
    expect(outcome?.textContent).toContain("Near-miss identified");
    expect(outcome?.textContent).toContain("Cardiovascular dysfunction");
    expect(outcome?.textContent).toContain("She met the WHO near-miss criteria");
  });

  it("never renders INDETERMINATE as 'no near-miss', and names the missing inputs by label", async () => {
    maternityMocks.classifyNearMissForm.mockResolvedValue(
      baseOutcome({
        classification_code: null,
        classification_name: null,
        status: "INDETERMINATE",
        is_near_miss: false,
        unresolved_criteria: ["NEAR_MISS_CARDIOVASCULAR"],
        missing_inputs: ["nearMiss.lactateMmolL"],
        rationale: "Cannot classify: Cardiovascular dysfunction could not be excluded.",
      }),
    );
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.MaternalNearMissWorkspace />);
    await flush();

    await click(mounted.container, "near-miss-submit");
    await flush();

    const outcome = byTestId(mounted.container, "near-miss-indeterminate");
    expect(outcome?.textContent).toContain("Indeterminate");
    expect(outcome?.textContent).not.toMatch(/no near-miss/i);
    expect(outcome?.textContent).toContain("Cardiovascular");
    // The missing input is named by its real label, not its raw linkId.
    expect(outcome?.textContent).toContain("Lactate");
  });

  it("renders the unrecognised-answer refusal with the answers it could not read, not a generic failure", async () => {
    maternityMocks.classifyNearMissForm.mockRejectedValue(
      new ApiError({
        code: "unrecognised_form_answer",
        message: "These answers could not be read, and were not treated as negatives.",
        status: 422,
        correlationId: "corr-nm2",
        details: { unrecognised_answers: ["nearMiss.shock=MAYBE"], accepted_codes: ["PRESENT", "ABSENT"] },
      }),
    );
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.MaternalNearMissWorkspace />);
    await flush();

    await click(mounted.container, "near-miss-submit");
    await flush();

    const refusal = byTestId(mounted.container, "near-miss-refusal");
    expect(refusal?.textContent).toContain("could not be read");
    expect(refusal?.textContent).toContain("nearMiss.shock=MAYBE");
  });

  it("renders a 502 near_miss_unavailable as an outage, never as 'no near-miss found'", async () => {
    maternityMocks.classifyNearMissForm.mockRejectedValue(
      new ApiError({
        code: "near_miss_unavailable",
        message:
          "The near-miss criteria could not be evaluated. This is not a finding of 'no near-miss'. Classify clinically and resubmit when the service returns.",
        status: 502,
        correlationId: "corr-nm3",
      }),
    );
    const mod = await import("../../screens/provider/MaternityWorkspaces");
    mounted = renderWithQuery(<mod.MaternalNearMissWorkspace />);
    await flush();

    await click(mounted.container, "near-miss-submit");
    await flush();

    const refusal = byTestId(mounted.container, "near-miss-refusal");
    expect(refusal?.textContent).toContain("could not be evaluated");
    expect(refusal?.textContent).toContain("not a finding of 'no near-miss'");
    expect(byTestId(mounted.container, "near-miss-outcome")).toBeFalsy();
  });
});
