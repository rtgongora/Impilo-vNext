import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import QueuePage from "./page";

const push = vi.fn();
const callMutate = vi.fn();
const mutationMutate = vi.fn();
const escalateMutate = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isQueueManager: true }),
}));

vi.mock("@/hooks/queries/useQueue", () => ({
  useQueueEntries: () => ({
    data: {
      data: [
        {
          id: "entry-1",
          attributes: {
            patientId: "patient-1",
            patientName: "Tariro Moyo",
            status: "WAITING",
            priority: 2,
            queuedAt: "2026-04-08T09:00:00.000Z",
            triageCategory: "ORANGE",
          },
        },
        {
          id: "entry-2",
          attributes: {
            patientId: "patient-2",
            patientName: "Simba Zhou",
            status: "CALLED",
            priority: 3,
            queuedAt: "2026-04-08T09:10:00.000Z",
            triageCategory: "GREEN",
          },
        },
        {
          id: "entry-3",
          attributes: {
            patientId: "patient-3",
            patientName: "Nyasha Dube",
            status: "PAUSED",
            priority: 4,
            queuedAt: "2026-04-08T09:20:00.000Z",
            triageCategory: "YELLOW",
          },
        },
        {
          id: "entry-4",
          attributes: {
            patientId: "patient-4",
            patientName: "Rudo Chikafu",
            status: "WAITING",
            priority: 1,
            queuedAt: "2026-04-08T09:30:00.000Z",
            triageCategory: "RED",
            escalatedAt: "2026-04-08T09:35:00.000Z",
            escalatedBy: "actor-77",
            escalationReason: "Deteriorating vitals in the waiting area",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useCallPatient: () => ({
    mutate: callMutate,
    isPending: false,
  }),
  useEscalateQueueEntry: () => ({
    mutate: escalateMutate,
    isPending: false,
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({
    invalidateQueries: vi.fn(),
  }),
  useQuery: () => ({
    data: {
      data: {
        waiting: 1,
        called: 1,
        inService: 0,
        completed: 4,
        avgWaitSeconds: 900,
      },
    },
  }),
  useMutation: () => ({
    mutate: mutationMutate,
    isPending: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe("QueuePage", () => {
  beforeEach(() => {
    push.mockReset();
    callMutate.mockReset();
    mutationMutate.mockReset();
    escalateMutate.mockReset();
    callMutate.mockImplementation(
      (_payload: { id: string }, options?: { onSuccess?: () => void }) => options?.onSuccess?.(),
    );
  });

  it("shows orchestration lanes and routes waiting patients into chart handoff", async () => {
    const user = userEvent.setup();

    render(<QueuePage />);

    expect(screen.getByText("Coordination workboard")).toBeInTheDocument();
    expect(screen.getByText("Needs action now")).toBeInTheDocument();
    expect(screen.getByText("Tracking in progress")).toBeInTheDocument();
    expect(screen.getByText("Needs attention")).toBeInTheDocument();
    expect(screen.getByText("Full queue board")).toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "Start encounter handoff" })[0]);

    expect(callMutate).toHaveBeenCalledWith(
      { id: "entry-1" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(push).toHaveBeenCalledWith("/ehr/patient-1?entry=queue");
  });

  it("shows the Escalated badge with the escalation reason for escalated entries", () => {
    render(<QueuePage />);

    const badges = screen.getAllByTitle(
      "Escalated: Deteriorating vitals in the waiting area",
    );
    expect(badges.length).toBeGreaterThan(0);
    expect(badges[0]).toHaveTextContent(/Escalated/);
  });

  it("requires a reason before escalating and submits it to the escalate mutation", async () => {
    const user = userEvent.setup();

    render(<QueuePage />);

    await user.click(screen.getAllByRole("button", { name: /Escalate/ })[0]);

    const dialog = screen.getByRole("dialog", { name: "Escalate queue entry" });
    const submit = within(dialog).getByRole("button", { name: "Escalate" });
    expect(submit).toBeDisabled();

    await user.type(
      within(dialog).getByLabelText("Reason (required)"),
      "Chest pain reported at reception",
    );
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(escalateMutate).toHaveBeenCalledWith(
      { id: "entry-1", reason: "Chest pain reported at reception" },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });
});
