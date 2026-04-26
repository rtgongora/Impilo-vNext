import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import QueuePage from "./page";

const push = vi.fn();
const callMutate = vi.fn();
const mutationMutate = vi.fn();

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
      ],
    },
    isLoading: false,
  }),
  useCallPatient: () => ({
    mutate: callMutate,
    isPending: false,
  }),
  useTransferQueueEntry: () => ({ mutate: vi.fn(), isPending: false }),
  useAbandonQueueEntry: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQueryClient: () => ({
      invalidateQueries: vi.fn(),
    }),
    useQuery: (opts: { queryKey: unknown[] }) => {
      if (opts.queryKey[0] === "queue-definitions") {
        return {
          data: {
            data: [{ queueId: "550e8400-e29b-41d4-a716-446655440000", queueType: "TRIAGE", name: "Triage desk" }],
          },
          isLoading: false,
        };
      }
      return {
        data: {
          data: {
            waiting: 1,
            called: 1,
            inService: 0,
            completed: 4,
            avgWaitSeconds: 900,
          },
        },
        isLoading: false,
      };
    },
    useMutation: () => ({
      mutate: mutationMutate,
      isPending: false,
    }),
  };
});

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

    await user.click(screen.getByRole("button", { name: "Start encounter handoff" }));

    expect(callMutate).toHaveBeenCalledWith(
      { id: "entry-1" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(push).toHaveBeenCalledWith("/ehr/patient-1?entry=queue");
  });
});
