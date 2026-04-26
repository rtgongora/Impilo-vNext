import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import PharmacyDispensePage from "./page";

const { invalidateQueries, post } = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  post: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) =>
      ({
        patientId: "patient-1",
        encounterId: "enc-1",
        source: "discharge",
      })[key] ?? null,
  }),
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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "user-1", displayName: "Pharmacist Moyo" },
  }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isDispenser: true }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      data: [
        {
          id: "rx-1",
          medication_name: "Amoxicillin",
          dosage: "500 mg",
          frequency: "BD",
          duration: "5 days",
          quantity: 10,
          status: "PENDING",
          prescribed_by: "Dr. Moyo",
          patient_name: "Tariro Moyo",
          patient_id: "patient-1",
          created_at: "2026-04-08T09:00:00.000Z",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useQueryClient: () => ({
    invalidateQueries,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post,
  },
}));

describe("PharmacyDispensePage", () => {
  it("keeps dispense in place with encounter continuity and real user attribution", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({});

    render(<PharmacyDispensePage />);

    expect(screen.getByText("Dispense continuity")).toBeInTheDocument();
    expect(screen.getByText("Dispense loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");

    await user.click(screen.getByRole("button", { name: "Dispense" }));

    expect(post).toHaveBeenCalledWith("/internal/v1/pharmacy/dispense", {
      prescription_id: "rx-1",
      dispensed_by: "user-1",
      payment_method: "CASH",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["prescriptions", { status: "PENDING" }] });
  });
});
