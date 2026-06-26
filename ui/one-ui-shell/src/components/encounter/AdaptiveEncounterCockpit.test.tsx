import { render, screen, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdaptiveEncounterCockpit } from "./AdaptiveEncounterCockpit";
import type { CadreDecision } from "@/hooks/queries/useCadreDecision";

const { mutate, state } = vi.hoisted(() => ({
  mutate: vi.fn(),
  state: { data: undefined as CadreDecision | undefined, isPending: false, isError: false },
}));

vi.mock("@/hooks/queries/useCadreDecision", () => ({
  useResolveCadreDecision: () => ({
    mutate,
    data: state.data,
    isPending: state.isPending,
    isError: state.isError,
  }),
}));

const sampleDecision: CadreDecision = {
  permittedWorkflows: ["ASSESS", "NOTE", "ORDER_LAB"],
  cockpitSpine: [
    {
      tab: "OVERVIEW",
      enabled: true,
      actions: [{ action: "VIEW_TIMELINE", enabled: true, requiresStepUp: false }],
    },
    {
      tab: "ORDERS_RESULTS",
      enabled: true,
      actions: [
        { action: "ORDER_LAB", enabled: true, requiresStepUp: false },
        { action: "PRESCRIBE", enabled: false, requiresStepUp: true },
      ],
    },
    {
      tab: "CARE",
      enabled: false,
      actions: [{ action: "CREATE_CARE_PLAN", enabled: false, requiresStepUp: false }],
    },
  ],
  escalation: { breakGlassAvailable: false, supervisorRequiredFor: ["PRESCRIBE"] },
  auditRef: "audit-123",
};

describe("AdaptiveEncounterCockpit", () => {
  beforeEach(() => {
    mutate.mockReset();
    state.data = undefined;
    state.isPending = false;
    state.isError = false;
  });

  it("resolves the cadre decision on mount", () => {
    state.data = sampleDecision;
    render(<AdaptiveEncounterCockpit request={{ cadre: "MEDICAL_PRACTITIONER", context: "OUTPATIENT" }} />);
    expect(mutate).toHaveBeenCalledTimes(1);
  });

  it("renders only enabled tabs (disabled CARE tab is hidden)", () => {
    state.data = sampleDecision;
    render(<AdaptiveEncounterCockpit request={{ cadre: "NURSE" }} />);
    expect(screen.getAllByText("Overview").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Orders & Results").length).toBeGreaterThan(0);
    expect(screen.queryByText("Care")).toBeNull();
  });

  it("renders an enabled action as a live button and invokes onAction", () => {
    state.data = sampleDecision;
    const onAction = vi.fn();
    render(<AdaptiveEncounterCockpit request={{ cadre: "NURSE" }} onAction={onAction} />);
    const orderLab = screen.getByRole("button", { name: /Order Lab/i });
    fireEvent.click(orderLab);
    expect(onAction).toHaveBeenCalledWith("ORDER_LAB", "ORDERS_RESULTS", false);
  });

  it("renders a disabled action as inert (no dead button) with a reason", () => {
    state.data = sampleDecision;
    render(<AdaptiveEncounterCockpit request={{ cadre: "NURSE" }} />);
    // PRESCRIBE is disabled + supervisor-required: it must NOT be a button.
    expect(screen.queryByRole("button", { name: /Prescribe/i })).toBeNull();
    const inert = screen.getByTitle("Requires supervisor");
    expect(inert).toHaveAttribute("aria-disabled", "true");
  });

  it("shows an error state with no enabled actions when resolution fails", () => {
    state.isError = true;
    render(<AdaptiveEncounterCockpit request={{ cadre: "NURSE" }} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });
});
