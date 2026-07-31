import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const hookState = {
  coverage: {
    isLoading: false,
    isError: false,
    error: null as unknown,
    data: [] as unknown[],
  },
  history: {
    isLoading: false,
    isError: false,
    error: null as unknown,
    data: [] as unknown[],
  },
  losses: {
    isLoading: false,
    isError: false,
    error: null as unknown,
    data: [] as unknown[],
  },
  topAuths: {
    isLoading: false,
    isError: false,
    error: null as unknown,
    data: [] as unknown[],
  },
  terminations: {
    isLoading: false,
    isError: false,
    error: null as unknown,
    data: [] as unknown[],
  },
};

vi.mock("@/hooks/queries/useConfidentialReproductive", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useConfidentialReproductive")>(
    "@/hooks/queries/useConfidentialReproductive",
  );
  return {
    ...actual,
    useContraceptionCoverage: () => hookState.coverage,
    useContraceptionHistory: () => hookState.history,
    usePregnancyLosses: () => hookState.losses,
    useTopAuthorisations: () => hookState.topAuths,
    useTerminations: () => hookState.terminations,
  };
});

import { ReproductiveConfidentialPanel } from "./ReproductiveConfidentialPanel";

describe("ReproductiveConfidentialPanel", () => {
  beforeEach(() => {
    hookState.coverage = { isLoading: false, isError: false, error: null, data: [] };
    hookState.history = { isLoading: false, isError: false, error: null, data: [] };
    hookState.losses = { isLoading: false, isError: false, error: null, data: [] };
    hookState.topAuths = { isLoading: false, isError: false, error: null, data: [] };
    hookState.terminations = { isLoading: false, isError: false, error: null, data: [] };
  });

  it("shows honest empty wording when all lanes return empty lists", () => {
    render(<ReproductiveConfidentialPanel patientCpid="CP-1" />);
    expect(screen.getByTestId("reproductive-confidential-panel")).toBeInTheDocument();
    expect(screen.getAllByText(/Nothing visible on this confidential lane/i).length).toBeGreaterThan(0);
  });

  it("renders contraception coverage when present", () => {
    hookState.coverage.data = [
      {
        contraceptive_episode_id: "C-1",
        subject_cpid: "CP-1",
        method_code: "IMPALN",
        coverage_status: "ACTIVE",
      },
    ];
    render(<ReproductiveConfidentialPanel patientCpid="CP-1" />);
    expect(screen.getByTestId("contraception-coverage-item")).toBeInTheDocument();
  });

  it("renders a distinct outage banner on 502, never as empty", () => {
    hookState.coverage.isError = true;
    hookState.coverage.error = { status: 502, error: { code: "PCT_UNAVAILABLE" } };
    render(<ReproductiveConfidentialPanel patientCpid="CP-1" />);
    const banner = screen.getByTestId("contraception-unavailable");
    expect(banner.textContent).toContain("not the same as there being nothing to see");
    expect(screen.queryByTestId("contraception-coverage-item")).not.toBeInTheDocument();
  });
});
