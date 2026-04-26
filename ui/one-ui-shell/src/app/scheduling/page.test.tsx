import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SchedulingPage from "./page";

vi.mock("next/navigation", () => ({
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
  usePatients: () => ({
    data: { data: [] },
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

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

describe("SchedulingPage", () => {
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
});
