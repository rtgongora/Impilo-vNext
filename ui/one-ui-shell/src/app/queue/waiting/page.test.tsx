import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import WaitingListPage from "./page";

const push = vi.fn();
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
            patientName: "Nyasha Dube",
            status: "WAITING",
            priority: 3,
            queuedAt: "2026-04-08T09:10:00.000Z",
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

describe("WaitingListPage", () => {
  beforeEach(() => {
    push.mockReset();
    callMutate.mockReset();
    callMutate.mockImplementation(
      (_payload: { id: string }, options?: { onSuccess?: () => void }) => options?.onSuccess?.(),
    );
  });

  it("routes triaged patients into chart handoff and flags rows that need triage first", async () => {
    const user = userEvent.setup();

    render(<WaitingListPage />);

    expect(screen.getByText("Watch pacing, spot missing triage, and hand the next patient into chart")).toBeInTheDocument();
    expect(screen.getByText("Needs triage")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open Triage" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Start encounter handoff" }));

    expect(callMutate).toHaveBeenCalledWith(
      { id: "entry-1" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(push).toHaveBeenCalledWith("/ehr/patient-1?entry=queue");
  });
});
