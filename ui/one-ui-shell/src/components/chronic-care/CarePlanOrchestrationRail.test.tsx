import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { CarePlanOrchestrationRail } from "./CarePlanOrchestrationRail";

const mockAddGoal = vi.fn();
const mockPerform = vi.fn();

const performState = { isError: false };

vi.mock("@/hooks/queries/useCareContinuity", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useCareContinuity")>();
  return {
    ...actual,
    useAddCarePlanGoal: () => ({ mutate: mockAddGoal, isPending: false, isError: false }),
    useUpdateCarePlanGoal: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
    useAddCarePlanIntervention: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
    usePerformCarePlanIntervention: () => ({
      mutate: mockPerform,
      isPending: false,
      isError: performState.isError,
    }),
  };
});

const samplePlan = {
  id: "cp-1",
  title: "Diabetes plan",
  status: "Active" as const,
  category: "Chronic Disease",
  startDate: "2026-01-01",
  targetDate: "2026-06-01",
  author: "Dr. Ncube",
  goals: [{ id: "g-1", description: "HbA1c", progress: 40, category: "Lab", targetDate: "", createdDate: "", statusKey: "IN_PROGRESS", priorityKey: "HIGH", notes: "" }],
  interventions: [{ id: "int-1", label: "Foot exam", completed: false }],
};

describe("CarePlanOrchestrationRail", () => {
  beforeEach(() => {
    mockAddGoal.mockReset();
    mockPerform.mockReset();
    performState.isError = false;
  });

  it("prompts to create a plan when none exist", () => {
    render(<CarePlanOrchestrationRail patientId="pat-1" plans={[]} />);
    expect(screen.getByText(/Create a care plan/)).toBeInTheDocument();
  });

  it("renders orchestration controls for active plan", async () => {
    const user = userEvent.setup();
    render(<CarePlanOrchestrationRail patientId="pat-1" plans={[samplePlan]} />);
    expect(screen.getByText(/Chronic care orchestration/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Perform next intervention/i }));
    expect(mockPerform).toHaveBeenCalled();
    expect(screen.queryByTestId("care-plan-write-failures")).not.toBeInTheDocument();
  });

  /**
   * experience-bff empty-200 honesty sweep — UI half. The BFF used to answer this write with
   * {"performed": true} regardless. It now fails honestly, and a silent failure would repeat the
   * same claim: that care was delivered.
   */
  it("says so when the intervention was not recorded", () => {
    performState.isError = true;
    render(<CarePlanOrchestrationRail patientId="pat-1" plans={[samplePlan]} />);
    expect(screen.getByTestId("care-plan-write-failures")).toHaveTextContent(
      /intervention was not recorded/i,
    );
  });
});
