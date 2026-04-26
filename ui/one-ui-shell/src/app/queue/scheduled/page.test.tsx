import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ScheduledQueuePage from "./page";

const { get, confirmMutate, cancelMutate, invalidateQueries } = vi.hoisted(() => ({
  get: vi.fn(),
  confirmMutate: vi.fn(),
  cancelMutate: vi.fn(),
  invalidateQueries: vi.fn(),
}));
let mutationCall = 0;

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
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

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get,
    post: vi.fn(),
  },
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries }),
  useQuery: ({ queryFn }: { queryFn: () => unknown }) => {
    queryFn();
    return {
      data: {
        data: [
          {
            id: "appt-1",
            type: "appointment",
            attributes: {
              patient_id: "patient-1",
              appointment_type: "TELECONSULT",
              provider_name: "Dr. Ncube",
              status: "SCHEDULED",
              scheduled_at: "2026-04-08T09:30:00.000Z",
              reason: "Cardiology review",
            },
          },
        ],
      },
      isLoading: false,
      error: null,
    };
  },
  useMutation: () => {
    mutationCall += 1;
    return mutationCall === 1
      ? { mutate: confirmMutate, isPending: false }
      : { mutate: cancelMutate, isPending: false };
  },
}));

describe("ScheduledQueuePage", () => {
  beforeEach(() => {
    get.mockReset();
    confirmMutate.mockReset();
    cancelMutate.mockReset();
    invalidateQueries.mockReset();
    mutationCall = 0;
  });

  it("loads appointments from the live scheduling endpoint and exposes confirm actions", async () => {
    const user = userEvent.setup();

    render(<ScheduledQueuePage />);

    expect(get).toHaveBeenCalledWith("/internal/v1/appointments?facility_id=facility-1");
    expect(screen.getByText("Keep scheduled visits visible, confirm readiness, and open the right downstream surface")).toBeInTheDocument();
    expect(screen.getByText("TELECONSULT")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(confirmMutate).toHaveBeenCalledWith("appt-1");
  });
});
