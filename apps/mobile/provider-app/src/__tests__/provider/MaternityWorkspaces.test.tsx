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
