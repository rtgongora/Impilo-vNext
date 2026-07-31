import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mutate = vi.fn();
const mutationState: {
  mutate: typeof mutate;
  isPending: boolean;
  isError: boolean;
  error: unknown;
  data: unknown;
} = {
  mutate,
  isPending: false,
  isError: false,
  error: null,
  data: null,
};

vi.mock("@/hooks/queries/useBirthDestination", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useBirthDestination")>(
    "@/hooks/queries/useBirthDestination",
  );
  return {
    ...actual,
    useBirthDestination: () => mutationState,
  };
});

import { BirthDestinationPanel } from "./BirthDestinationPanel";

function verdict(overrides: Record<string, unknown> = {}) {
  return {
    facility_id: 42,
    required_level: "BEMONC",
    status: "MEETS_REQUIRED_LEVEL",
    operational_gate_passed: true,
    emonc_verdict: "BEMONC",
    call_ahead: true,
    message: "This facility meets the level of care she needs (basic emergency obstetric care).",
    guidance: "Confirm by phone that it is ready to receive her.",
    ...overrides,
  };
}

describe("BirthDestinationPanel", () => {
  beforeEach(() => {
    mutate.mockReset();
    mutationState.isPending = false;
    mutationState.isError = false;
    mutationState.error = null;
    mutationState.data = null;
    sessionStorage.clear();
  });

  it("prefills the facility id from sessionStorage exp:facility_id when numeric", () => {
    sessionStorage.setItem("exp:facility_id", "1042");
    render(<BirthDestinationPanel />);
    expect(screen.getByTestId("birth-destination-facility-id")).toHaveValue("1042");
  });

  it("does not prefill when exp:facility_id is not numeric", () => {
    sessionStorage.setItem("exp:facility_id", "not-a-number");
    render(<BirthDestinationPanel />);
    expect(screen.getByTestId("birth-destination-facility-id")).toHaveValue("");
  });

  it("assesses with the entered facility id and selected level", async () => {
    const user = userEvent.setup();
    render(<BirthDestinationPanel />);

    await user.type(screen.getByTestId("birth-destination-facility-id"), "7");
    await user.selectOptions(screen.getByTestId("birth-destination-required-level"), "CEMONC");
    await user.click(screen.getByTestId("birth-destination-assess"));

    expect(mutate).toHaveBeenCalledWith({ facilityId: 7, requiredLevel: "CEMONC" });
  });

  it("renders MEETS_REQUIRED_LEVEL with the message, guidance and 'Call ahead' verbatim", () => {
    mutationState.data = { data: verdict(), meta: {} };
    render(<BirthDestinationPanel />);

    const card = screen.getByTestId("birth-destination-verdict");
    expect(card).toHaveAttribute("data-status", "MEETS_REQUIRED_LEVEL");
    expect(card.textContent).toContain("meets the level of care she needs");
    expect(card.textContent).toContain("Confirm by phone");
    expect(screen.getByTestId("birth-destination-call-ahead").textContent).toContain("Call ahead");
  });

  it("renders CAPABILITY_UNKNOWN distinctly, never as 'cannot' or 'can'", () => {
    mutationState.data = {
      data: verdict({
        status: "CAPABILITY_UNKNOWN",
        emonc_verdict: "INSUFFICIENT_EVIDENCE",
        message: "Nothing is known about this facility's emergency obstetric capability.",
        guidance:
          "This is not a statement that it has none — it has not been assessed. Call ahead to confirm.",
      }),
      meta: {},
    };
    render(<BirthDestinationPanel />);

    const card = screen.getByTestId("birth-destination-verdict");
    expect(card.textContent).toContain("not yet assessed");
    expect(card.textContent).not.toMatch(/\bcan\b|\bcannot\b/i);
    expect(screen.getByTestId("birth-destination-call-ahead")).toBeInTheDocument();
  });

  it("renders a distinct 502 UNAVAILABLE banner, never inside the verdict card", () => {
    mutationState.isError = true;
    mutationState.error = {
      status: 502,
      data: verdict({
        status: "UNAVAILABLE",
        message: "The facility's status could not be retrieved.",
        guidance: "Do not treat this as either a safe or an unsafe destination — it is unknown.",
      }),
      meta: {},
    };
    render(<BirthDestinationPanel />);

    expect(screen.getByTestId("birth-destination-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("birth-destination-verdict")).not.toBeInTheDocument();
    expect(screen.getByTestId("birth-destination-unavailable").textContent).toContain(
      "could not be retrieved",
    );
  });

  it("disables Assess until a numeric facility id is entered", () => {
    render(<BirthDestinationPanel />);
    expect(screen.getByTestId("birth-destination-assess")).toBeDisabled();
  });
});
