import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MaternitySummaryResponse } from "@/hooks/queries/useMaternitySummary";

const summaryState: {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  data: MaternitySummaryResponse | undefined;
} = {
  isLoading: false,
  isError: false,
  error: null,
  data: undefined,
};

vi.mock("@/hooks/queries/useMaternitySummary", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMaternitySummary")>(
    "@/hooks/queries/useMaternitySummary",
  );
  return {
    ...actual,
    useMaternitySummary: () => summaryState,
  };
});

import { MaternitySummaryPanel } from "./MaternitySummaryPanel";

describe("MaternitySummaryPanel", () => {
  beforeEach(() => {
    summaryState.isLoading = false;
    summaryState.isError = false;
    summaryState.error = null;
    summaryState.data = undefined;
  });

  it("shows a loading state", () => {
    summaryState.isLoading = true;
    render(<MaternitySummaryPanel patientId="P1" />);
    expect(screen.getByTestId("maternity-summary-loading")).toBeInTheDocument();
  });

  it("renders partograph_active, ctg_active, observation_count and last_observed_at", () => {
    summaryState.data = {
      data: {
        patient_id: "P1",
        partograph_active: true,
        partograph_session_id: "S1",
        progress: {
          status: "LEFT_OF_ALERT",
          latest_dilation_cm: 4,
          expected_dilation_cm: 5,
          hours_behind_alert_line: null,
          outstanding_observations: [],
          observations: [],
          recommended_action: null,
          content_version: "v1",
          content_source: "WHO",
        },
        ctg_active: false,
        observation_count: 3,
        last_observed_at: "2026-07-30T10:00:00Z",
      },
      meta: {},
    };
    render(<MaternitySummaryPanel patientId="P1" />);

    expect(screen.getByTestId("maternity-summary-partograph").textContent).toContain("Active");
    expect(screen.getByTestId("maternity-summary-ctg").textContent).toContain("No open session");
    expect(screen.getByTestId("maternity-summary-observation-count").textContent).toContain("3 labour observations");
    expect(screen.getByTestId("maternity-summary-last-observed").textContent).not.toContain("No observations");
  });

  it("renders 'no active session' as a real answer when nothing is open", () => {
    summaryState.data = {
      data: {
        patient_id: "P1",
        partograph_active: false,
        ctg_active: false,
        observation_count: 0,
        last_observed_at: null,
      },
      meta: {},
    };
    render(<MaternitySummaryPanel patientId="P1" />);
    expect(screen.getByTestId("maternity-summary-partograph").textContent).toContain("No open session");
    expect(screen.getByTestId("maternity-summary-last-observed").textContent).toContain("No observations recorded yet");
  });

  it("renders a distinct outage banner on 502, never as an empty record", () => {
    summaryState.isError = true;
    summaryState.error = { status: 502, error: "maternity_summary_unavailable" };
    render(<MaternitySummaryPanel patientId="P1" />);

    const banner = screen.getByTestId("maternity-summary-unavailable");
    expect(banner).toBeInTheDocument();
    expect(banner.textContent).toContain("not the same as this patient having no maternity record");
    expect(screen.queryByTestId("maternity-summary-panel")).not.toBeInTheDocument();
  });
});
