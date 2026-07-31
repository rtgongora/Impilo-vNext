import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { RenalDosingPanel } from "../RenalDosingPanel";

const egfrState = vi.hoisted(() => ({
  egfr: null as number | null,
  isLoading: false,
  isError: false,
}));

const adviseState = vi.hoisted(() => ({
  mutate: vi.fn(),
  isPending: false,
  isError: false,
  data: undefined as
    | {
        data: {
          level: string;
          message: string;
          computed: boolean;
          content_version: string;
        };
      }
    | undefined,
}));

vi.mock("@/hooks/queries/usePatientEgfr", () => ({
  usePatientEgfr: () => ({
    egfr: egfrState.egfr,
    source: egfrState.egfr != null ? "observation" : null,
    isLoading: egfrState.isLoading,
    isError: egfrState.isError,
  }),
}));

vi.mock("@/hooks/queries/useRenalDosing", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useRenalDosing")>();
  return {
    ...actual,
    useRenalDosingAdvise: () => adviseState,
  };
});

describe("RenalDosingPanel", () => {
  beforeEach(() => {
    egfrState.egfr = null;
    egfrState.isLoading = false;
    egfrState.isError = false;
    adviseState.mutate.mockReset();
    adviseState.isPending = false;
    adviseState.isError = false;
    adviseState.data = undefined;
  });

  it("states honestly when eGFR is unknown", () => {
    render(<RenalDosingPanel patientId="patient-1" />);

    expect(screen.getByTestId("renal-dosing-egfr-unknown")).toHaveTextContent(
      /eGFR unknown — dosing advice unavailable/i,
    );
    expect(screen.getByTestId("renal-dosing-drug-class")).toBeDisabled();
    expect(adviseState.mutate).not.toHaveBeenCalled();
  });

  it("requests advice when eGFR is available", async () => {
    egfrState.egfr = 42;
    render(<RenalDosingPanel patientId="patient-1" />);

    await waitFor(() =>
      expect(adviseState.mutate).toHaveBeenCalledWith({ egfr: 42, drugClass: "BIGUANIDE" }),
    );
    expect(screen.getByTestId("renal-dosing-egfr-value")).toHaveTextContent("42");
  });

  it("renders advice level and message", () => {
    egfrState.egfr = 25;
    adviseState.data = {
      data: {
        level: "ADJUST",
        message: "Metformin is contraindicated at this eGFR.",
        computed: true,
        content_version: "impilo-renal-dosing-1.0.0",
      },
    };

    render(<RenalDosingPanel patientId="patient-1" />);

    expect(screen.getByTestId("renal-dosing-advice-level")).toHaveTextContent("ADJUST");
    expect(screen.getByText(/contraindicated/i)).toBeInTheDocument();
  });

  it("re-requests advice when drug class changes", async () => {
    const user = userEvent.setup();
    egfrState.egfr = 55;
    render(<RenalDosingPanel patientId="patient-1" />);

    await waitFor(() => expect(adviseState.mutate).toHaveBeenCalled());
    adviseState.mutate.mockClear();

    await user.selectOptions(screen.getByTestId("renal-dosing-drug-class"), "NSAID");
    await waitFor(() =>
      expect(adviseState.mutate).toHaveBeenCalledWith({ egfr: 55, drugClass: "NSAID" }),
    );
  });
});
