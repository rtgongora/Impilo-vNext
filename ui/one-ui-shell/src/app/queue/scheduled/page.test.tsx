import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ScheduledQueuePage from "./page";

const { push, confirmMutate, cancelMutate, checkInMutate } = vi.hoisted(() => ({
  push: vi.fn(),
  confirmMutate: vi.fn(),
  cancelMutate: vi.fn(),
  checkInMutate: vi.fn(),
}));

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

vi.mock("@/hooks/queries/useAppointments", () => ({
  useAppointments: () => ({
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
  }),
  useConfirmAppointment: () => ({ mutate: confirmMutate, isPending: false }),
  useCancelAppointment: () => ({ mutate: cancelMutate, isPending: false }),
  useCheckInAppointment: () => ({ mutate: checkInMutate, isPending: false }),
}));

describe("ScheduledQueuePage", () => {
  beforeEach(() => {
    push.mockReset();
    confirmMutate.mockReset();
    cancelMutate.mockReset();
    checkInMutate.mockReset();
    checkInMutate.mockImplementation(() => {
      push("/ehr/patient-1/encounters?journey_id=42&transaction_id=journey-42");
    });
  });

  it("loads appointments and exposes confirm actions", async () => {
    const user = userEvent.setup();

    render(<ScheduledQueuePage />);

    expect(
      screen.getByText("Keep scheduled visits visible, confirm readiness, and open the right downstream surface"),
    ).toBeInTheDocument();
    expect(screen.getByText("TELECONSULT")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(confirmMutate).toHaveBeenCalledWith("appt-1");
  });

  it("check-in bridges into the outpatient encounter spine", async () => {
    const user = userEvent.setup();

    render(<ScheduledQueuePage />);

    await user.click(screen.getByRole("button", { name: "Check in" }));

    await waitFor(() => expect(checkInMutate).toHaveBeenCalledWith("appt-1"));
  });
});
