import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TriageQueuePage from "./page";

const push = vi.fn();
const triageMutate = vi.fn();
const callMutate = vi.fn();

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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "user-1", displayName: "Nurse Moyo", email: "moyo@example.com" },
  }),
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
            queuedAt: "2026-04-08T09:00:00.000Z",
            reason: "Shortness of breath",
          },
        },
        {
          id: "entry-2",
          attributes: {
            patientId: "patient-2",
            patientName: "Simba Zhou",
            status: "WAITING",
            queuedAt: "2026-04-08T09:10:00.000Z",
            triageCategory: "ORANGE",
            reason: "Chest pain",
          },
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
  useCallPatient: () => ({
    mutate: callMutate,
    isPending: false,
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({
    invalidateQueries: vi.fn(),
  }),
  useMutation: () => ({
    mutate: triageMutate,
    isPending: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe("TriageQueuePage", () => {
  beforeEach(() => {
    push.mockReset();
    triageMutate.mockReset();
    callMutate.mockReset();
    callMutate.mockImplementation(
      (_payload: { id: string }, options?: { onSuccess?: () => void }) => options?.onSuccess?.(),
    );
  });

  it("supports triage completion and handoff from the same queue surface", async () => {
    const user = userEvent.setup();

    render(<TriageQueuePage />);

    expect(screen.getByText("Assess acuity, document red flags, then hand the encounter into chart")).toBeInTheDocument();
    expect(screen.getByText("Awaiting triage")).toBeInTheDocument();
    expect(screen.getByText("Ready for handoff")).toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "Start Triage" })[0]);
    await user.click(screen.getByRole("button", { name: /P2/i }));
    await user.click(screen.getByRole("button", { name: "Complete Triage" }));

    expect(triageMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "entry-1",
        acuity: 2,
        triaged_by: "user-1",
        triaged_by_name: "Nurse Moyo",
      }),
    );

    await user.click(screen.getByRole("button", { name: "Start encounter handoff" }));

    expect(callMutate).toHaveBeenCalledWith(
      { id: "entry-2" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(push).toHaveBeenCalledWith("/ehr/patient-2?entry=queue");
  }, 15000);
});
