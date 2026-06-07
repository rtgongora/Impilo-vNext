import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SchedulingPage from "./page";

const { createMutateAsync } = vi.hoisted(() => ({
  createMutateAsync: vi.fn().mockResolvedValue({ data: { id: "appt-new" } }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => ({
    get: () => null,
  }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/components/experience/FacilityWorkClusterRibbon", () => ({
  FacilityWorkClusterRibbon: () => null,
}));

vi.mock("@/components/experience/OrganizationPlaneContextBar", () => ({
  OrganizationPlaneContextBar: () => null,
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: {
      id: "user-1",
      email: "test@impilo.io",
      displayName: "Test Provider",
      roles: ["CLINICIAN"],
      actorType: "PROVIDER",
    },
  }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatients: (params?: { search?: string }) => ({
    data:
      params?.search && params.search.length >= 2
        ? {
            data: [
              {
                id: "patient-1",
                attributes: {
                  displayName: "Tariro Moyo",
                  cpid: "CP-001",
                  dateOfBirth: "1991-06-11",
                  gender: "female",
                },
              },
            ],
          }
        : { data: [] },
    isLoading: false,
  }),
}));

vi.mock("@/hooks/usePrivacyDisplayStore", () => ({
  usePrivacyDisplayStore: Object.assign(vi.fn(() => ({ level: "normal" })), {
    getState: () => ({ level: "normal" }),
  }),
}));

vi.mock("@/lib/pii-mask", () => ({
  maskName: (name: string) => name,
  maskDob: (dob: string) => dob,
  displayCpid: (cpid: string) => cpid,
}));

vi.mock("@/hooks/queries/useAppointments", () => ({
  useCreateAppointment: () => ({ mutateAsync: createMutateAsync, isPending: false }),
  useConfirmAppointment: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCancelAppointment: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCheckInAppointment: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

describe("SchedulingPage", () => {
  beforeEach(() => {
    createMutateAsync.mockClear();
  });

  it("renders scheduling hub with facility context", () => {
    render(<SchedulingPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Scheduling" })).toBeInTheDocument();
    expect(screen.getByText(/Appointments at Harare Central/)).toBeInTheDocument();
  });

  it("shows scheduling cluster navigation links and view toggles", () => {
    render(<SchedulingPage />);

    expect(screen.getByText("Scheduling cluster")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Roster" })).toHaveAttribute("href", "/scheduling/roster");
    expect(screen.getByRole("link", { name: "On-call" })).toHaveAttribute("href", "/scheduling/on-call");
    expect(screen.getByText("List")).toBeInTheDocument();
    expect(screen.getByText("Calendar")).toBeInTheDocument();
    expect(screen.getByText("New Appointment")).toBeInTheDocument();
  });

  it("shows tab filters for appointment status", () => {
    render(<SchedulingPage />);

    expect(screen.getByText("All")).toBeInTheDocument();
    expect(screen.getByText("Today")).toBeInTheDocument();
    expect(screen.getByText("Pending")).toBeInTheDocument();
    expect(screen.getByText("Confirmed")).toBeInTheDocument();
  });

  it("books an appointment through the canonical scheduling hook", async () => {
    const user = userEvent.setup();

    render(<SchedulingPage />);

    await user.click(screen.getByRole("button", { name: "New Appointment" }));
    await user.type(screen.getByPlaceholderText("Search by name or CPID..."), "Tari");
    await waitFor(() => expect(screen.getByText("Tariro Moyo")).toBeInTheDocument(), { timeout: 2000 });
    await user.click(screen.getByText("Tariro Moyo"));
    const dateInput = document.querySelector('input[type="date"]');
    expect(dateInput).not.toBeNull();
    await user.type(dateInput as HTMLInputElement, "2026-06-10");
    await user.click(screen.getByRole("button", { name: "Schedule" }));

    await waitFor(() =>
      expect(createMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          patient_id: "patient-1",
          facility_id: "facility-1",
          appointment_type: "OPD",
          scheduled_at: "2026-06-10T09:00:00Z",
        }),
      ),
    );
  });
});
