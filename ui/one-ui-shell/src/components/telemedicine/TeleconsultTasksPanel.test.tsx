import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TeleconsultTasksPanel } from "./TeleconsultTasksPanel";
import type { ReferralTask } from "@/hooks/queries/useTelemedicine";

const { tasksState, createMutate, orderMutate } = vi.hoisted(() => ({
  tasksState: {
    current: {
      data: { data: [] as ReferralTask[] },
      isLoading: false,
      isError: false,
    },
  },
  createMutate: vi.fn(),
  orderMutate: vi.fn(),
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useReferralTasks: () => tasksState.current,
  useCreateReferralTask: () => ({ mutateAsync: createMutate, isPending: false }),
  usePlaceReferralOrder: () => ({ mutateAsync: orderMutate, isPending: false }),
}));

function task(overrides: Partial<ReferralTask>): ReferralTask {
  return {
    taskId: "t-1",
    referralId: "r-1",
    sourceRef: null,
    taskType: "FOLLOW_UP",
    assigneeId: null,
    assigneeRole: null,
    status: "OPEN",
    blocksClosure: false,
    dueAt: null,
    notes: null,
    createdBy: null,
    createdAt: null,
    completedBy: null,
    completedAt: null,
    ...overrides,
  };
}

describe("TeleconsultTasksPanel", () => {
  beforeEach(() => {
    createMutate.mockReset().mockResolvedValue({ data: task({}) });
    orderMutate.mockReset().mockResolvedValue({ data: {} });
    tasksState.current = { data: { data: [] }, isLoading: false, isError: false };
  });

  it("shows the empty state when there are no tasks", () => {
    render(<TeleconsultTasksPanel sessionId="r-1" />);
    expect(screen.getByText(/No tasks on this consultation yet/i)).toBeInTheDocument();
  });

  it("surfaces a blocking-closure badge when an open task blocks closure", () => {
    tasksState.current.data.data = [task({ blocksClosure: true, status: "OPEN", taskType: "RESULT_REVIEW" })];
    render(<TeleconsultTasksPanel sessionId="r-1" />);
    expect(screen.getByTestId ? screen.getByTestId("teleconsult-blocking-badge") : screen.getByText(/blocking closure/i)).toBeInTheDocument();
  });

  it("does not count a resolved task as blocking", () => {
    tasksState.current.data.data = [task({ blocksClosure: true, status: "COMPLETED" })];
    render(<TeleconsultTasksPanel sessionId="r-1" />);
    expect(screen.queryByTestId("teleconsult-blocking-badge")).not.toBeInTheDocument();
  });

  it("creates a follow-up task with the blocks-closure flag", async () => {
    const user = userEvent.setup();
    render(<TeleconsultTasksPanel sessionId="r-1" />);
    await user.click(screen.getByTestId("teleconsult-task-blocks-closure"));
    await user.click(screen.getByRole("button", { name: /Add task/i }));
    expect(createMutate).toHaveBeenCalledWith(
      expect.objectContaining({ taskType: "FOLLOW_UP", blocksClosure: true }),
    );
  });

  it("places a provenance-linked coded order when patientCpid is present", async () => {
    const user = userEvent.setup();
    render(<TeleconsultTasksPanel sessionId="r-1" patientCpid="CPID-1" />);
    const codeInput = screen.getByPlaceholderText(/ZIBO code/i);
    await user.type(codeInput, "ZIBO-CBC");
    await user.click(screen.getByRole("button", { name: /Place order/i }));
    expect(orderMutate).toHaveBeenCalledWith(
      expect.objectContaining({ orderType: "LAB", patientCpid: "CPID-1", ziboOrderCode: "ZIBO-CBC" }),
    );
  });
});
