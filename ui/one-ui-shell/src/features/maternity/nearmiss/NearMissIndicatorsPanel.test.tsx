import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { NearMissIndicatorsResponse } from "./useMaternalNearMiss";

const mutate = vi.fn();
const state: {
  mutate: typeof mutate;
  isPending: boolean;
  isError: boolean;
  error: unknown;
  data: NearMissIndicatorsResponse | null;
} = {
  mutate,
  isPending: false,
  isError: false,
  error: null,
  data: null,
};

vi.mock("./useMaternalNearMiss", async () => {
  const actual = await vi.importActual<typeof import("./useMaternalNearMiss")>("./useMaternalNearMiss");
  return {
    ...actual,
    useNearMissIndicators: () => state,
  };
});

import { NearMissIndicatorsPanel } from "./NearMissIndicatorsPanel";

function result(overrides: Partial<NearMissIndicatorsResponse["data"]> = {}): NearMissIndicatorsResponse {
  return {
    data: {
      indicator_period: "2026-Q2",
      near_miss_count: 2,
      maternal_death_count: 1,
      indeterminate_count: 1,
      severe_maternal_outcome_count: 4,
      near_miss_to_death_ratio: 2,
      near_miss_to_death_ratio_upper_bound: null,
      mortality_index: 25,
      mortality_index_lower_bound: null,
      mortality_index_direction: "HIGHER_IS_WORSE",
      note: null,
      ...overrides,
    },
    meta: {},
  };
}

describe("NearMissIndicatorsPanel", () => {
  beforeEach(() => {
    mutate.mockReset();
    state.isPending = false;
    state.isError = false;
    state.error = null;
    state.data = null;
  });

  it("computes with the entered period and one default row", async () => {
    const user = userEvent.setup();
    render(<NearMissIndicatorsPanel />);

    await user.type(screen.getByTestId("near-miss-indicators-period"), "2026-Q2");
    await user.click(screen.getByTestId("near-miss-indicators-compute"));

    expect(mutate).toHaveBeenCalledWith({ indicatorPeriod: "2026-Q2", cases: [{ outcome: "NEAR_MISS" }] });
  });

  it("adds and removes case rows, sending every configured outcome", async () => {
    const user = userEvent.setup();
    render(<NearMissIndicatorsPanel />);

    await user.click(screen.getByTestId("near-miss-indicators-add-row"));
    await user.selectOptions(screen.getByTestId("near-miss-indicators-outcome-1"), "MATERNAL_DEATH");
    await user.click(screen.getByTestId("near-miss-indicators-compute"));

    expect(mutate).toHaveBeenCalledWith({
      indicatorPeriod: "",
      cases: [{ outcome: "NEAR_MISS" }, { outcome: "MATERNAL_DEATH" }],
    });
  });

  it("never drops the indeterminate count from the counts display", () => {
    state.data = result({ indeterminate_count: 3 });
    render(<NearMissIndicatorsPanel />);
    expect(screen.getByTestId("near-miss-indicators-indeterminate-count").textContent).toBe("3");
  });

  it("renders a null ratio as 'not computable', never as 0.00", () => {
    state.data = result({ near_miss_to_death_ratio: null, near_miss_to_death_ratio_upper_bound: 2 });
    render(<NearMissIndicatorsPanel />);
    const ratio = screen.getByTestId("near-miss-indicators-ratio");
    expect(ratio.textContent).toContain("Not computable");
    expect(ratio.textContent).not.toContain("0.00");
  });

  it("renders a null mortality index as 'not computable', never as 0.00%", () => {
    state.data = result({ mortality_index: null, mortality_index_lower_bound: 10 });
    render(<NearMissIndicatorsPanel />);
    const idx = screen.getByTestId("near-miss-indicators-mortality-index");
    expect(idx.textContent).toContain("Not computable");
  });

  it("shows the note when present", () => {
    state.data = result({ note: "Interpret with caution — small denominator." });
    render(<NearMissIndicatorsPanel />);
    expect(screen.getByTestId("near-miss-indicators-note").textContent).toContain("small denominator");
  });

  it("renders a distinct 502-unavailable banner, not a zero result", () => {
    state.isError = true;
    state.error = { status: 502, error: "near_miss_unavailable", message: "down" };
    render(<NearMissIndicatorsPanel />);
    expect(screen.getByTestId("near-miss-indicators-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("near-miss-indicators-result")).not.toBeInTheDocument();
  });

  it("renders the 422 rejection with the named rejected cases from upstream_body", () => {
    state.isError = true;
    state.error = {
      status: 422,
      upstream_status: 422,
      upstream_body: JSON.stringify({
        data: {
          message: "Every case must carry a recognised outcome.",
          rejected_cases: ["case[1]: MAYBE"],
          accepted_values: ["NEAR_MISS", "MATERNAL_DEATH", "NEAR_MISS_INDETERMINATE", "NOT_SEVERE"],
        },
      }),
    };
    render(<NearMissIndicatorsPanel />);
    const rejection = screen.getByTestId("near-miss-indicators-rejected");
    expect(rejection.textContent).toContain("Every case must carry a recognised outcome");
    expect(rejection.textContent).toContain("case[1]: MAYBE");
  });
});
