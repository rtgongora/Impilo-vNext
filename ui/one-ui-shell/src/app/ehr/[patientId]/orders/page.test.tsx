import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrdersPage from "./page";

const { invalidateQueries, post, mutateAsync, mockUseProductRegistrySearch } = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  post: vi.fn(),
  mutateAsync: vi.fn(),
  mockUseProductRegistrySearch: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
  useSearchParams: () => new URLSearchParams("encounterId=enc-1"),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/ehr/ClinicalReviewHeader", () => ({
  ClinicalReviewHeader: () => <div data-testid="clinical-review-header" />,
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "user-1", displayName: "Dr. Moyo", email: "moyo@example.com" },
  }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        {
          id: "enc-1",
          attributes: {
            status: "IN_PROGRESS",
            encounterType: "OUTPATIENT",
            startedAt: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => ({
    data: { data: { id: "patient-1", attributes: { cpid: "CPID-ZW-00001" } } },
  }),
}));

vi.mock("@/hooks/queries/useClinicalWorklist", () => ({
  useClinicalWorklist: () => ({
    data: { data: { items: [], summary: { total: 0, urgent: 0, overdue: 0, by_source: {} } } },
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries }),
}));

vi.mock("@/hooks/queries/useLabOrders", () => ({
  useLabOrders: () => ({
    data: {
      data: [
        {
          id: "order-1",
          attributes: {
            orderNumber: "LAB-001",
            testName: "Troponin",
            category: "LABORATORY",
            priority: "STAT",
            status: "ORDERED",
            orderedByName: "Dr. Moyo",
            createdAt: "2026-04-08T09:15:00.000Z",
          },
        },
        {
          id: "order-2",
          attributes: {
            orderNumber: "LAB-002",
            testName: "FBC",
            category: "LABORATORY",
            priority: "ROUTINE",
            status: "COLLECTED",
            orderedByName: "Dr. Moyo",
            createdAt: "2026-04-08T09:30:00.000Z",
          },
        },
        {
          id: "order-3",
          attributes: {
            orderNumber: "LAB-003",
            testName: "U&E",
            category: "LABORATORY",
            priority: "ROUTINE",
            status: "RESULTED",
            orderedByName: "Dr. Moyo",
            createdAt: "2026-04-08T10:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useCreateLabOrder: () => ({
    mutate: vi.fn(),
    mutateAsync,
    isPending: false,
    isError: false,
  }),
  useCollectLabOrder: () => ({ mutate: vi.fn(), isPending: false }),
  useCancelLabOrder: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/queries/useProductRegistry", () => ({
  useProductRegistrySearch: (params: unknown) => mockUseProductRegistrySearch(params),
}));

vi.mock("@/lib/clinical-guidance-events", () => ({
  CLINICAL_ORDER_DRAFT_EVENT: "clinical-order-draft",
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

describe("OrdersPage", () => {
  beforeEach(() => {
    post.mockReset();
    mutateAsync.mockReset();
    invalidateQueries.mockReset();
    post.mockResolvedValue({});
    mutateAsync.mockResolvedValue({ data: { id: "order-new" } });
    mockUseProductRegistrySearch.mockImplementation((params: { q?: string } | undefined) => {
      if (params?.q === "cbc") {
        return {
          data: { items: [{ id: "svc-1", name: "Complete Blood Count", code: "CBC", kind: "service" }] },
          isLoading: false,
          isError: false,
        };
      }
      return { data: { items: [] }, isLoading: false, isError: false };
    });
  });

  it("surfaces diagnostic ordering orchestration with in-place workflow actions", () => {
    render(<OrdersPage />);
    expect(screen.getByText(/Guided cross-domain order composer/i)).toBeInTheDocument();
    expect(screen.getByText("Unified order orchestration")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Order" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Collect" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enter Result" })).toBeInTheDocument();
  });

  it("allows product-registry search to fill test name/code (no fake catalog)", () => {
    render(<OrdersPage />);
    fireEvent.click(screen.getByRole("button", { name: "Add Order" }));
    fireEvent.change(screen.getByLabelText("Search term"), { target: { value: "cbc" } });

    expect(screen.getByText(/1 match/)).toBeInTheDocument();
    const lookupCard = screen.getByText("Product / service lookup (shared rail)").closest("div");
    expect(lookupCard).toBeTruthy();
    const table = within(lookupCard as HTMLElement).getByRole("table");
    expect(within(table).getByText("Complete Blood Count")).toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: "Use" }));

    expect(screen.getByDisplayValue("Complete Blood Count")).toBeInTheDocument();
    expect(screen.getByDisplayValue("CBC")).toBeInTheDocument();
  });

  it("shows explicit non-submittable lane messaging for procedure lane", () => {
    render(<OrdersPage />);
    fireEvent.click(screen.getByTestId("guided-lane-procedure"));
    expect(
      screen.getByText(/intentionally read-only until a typed Experience BFF write contract/i)
    ).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("guided-submit-action"));
    expect(post).not.toHaveBeenCalled();
  });

  it("submits imaging action through typed lab-orders BFF contract with IMAGING category", async () => {
    render(<OrdersPage />);
    fireEvent.click(screen.getByTestId("guided-lane-imaging"));
    fireEvent.change(screen.getByTestId("guided-imaging-name"), { target: { value: "Chest X-ray PA" } });
    fireEvent.click(screen.getByTestId("guided-submit-action"));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          patientId: "patient-1",
          encounterId: "enc-1",
          testName: "Chest X-ray PA",
          category: "IMAGING",
        }),
      ),
    );
  });

  it("submits lab action through typed lab-orders BFF contract", async () => {
    render(<OrdersPage />);
    fireEvent.change(screen.getByTestId("guided-lab-name"), { target: { value: "Troponin" } });
    fireEvent.change(screen.getByTestId("guided-lab-code"), { target: { value: "TROP" } });
    fireEvent.click(screen.getByTestId("guided-submit-action"));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          patientId: "patient-1",
          encounterId: "enc-1",
          testName: "Troponin",
          testCode: "TROP",
          patientCpid: "CPID-ZW-00001",
          pctEncounterRef: "enc-1",
        }),
      ),
    );
  });

  it("submits medication action through canonical pharmacy endpoint", async () => {
    render(<OrdersPage />);
    fireEvent.click(screen.getByTestId("guided-lane-medication"));
    fireEvent.change(screen.getByTestId("guided-medication-name"), { target: { value: "Amoxicillin" } });
    fireEvent.change(screen.getByTestId("guided-medication-dosage"), { target: { value: "500mg" } });
    fireEvent.click(screen.getByTestId("guided-submit-action"));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/pharmacy/prescriptions",
        expect.objectContaining({
          patient_id: "patient-1",
          medication_name: "Amoxicillin",
          dosage: "500mg",
        })
      );
    });
  });

  it("submits referral action through canonical referrals endpoint", async () => {
    render(<OrdersPage />);
    fireEvent.click(screen.getByTestId("guided-lane-referral"));
    fireEvent.change(screen.getByTestId("guided-referral-specialty"), { target: { value: "Cardiology" } });
    fireEvent.change(screen.getByTestId("guided-referral-reason"), { target: { value: "Chest pain with exertion" } });
    fireEvent.click(screen.getByTestId("guided-submit-action"));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/referrals",
        expect.objectContaining({
          patient_id: "patient-1",
          specialty: "Cardiology",
          reason: "Chest pain with exertion",
        })
      );
    });
  });

  it("submits teleconsult action through canonical teleconsult endpoint", async () => {
    render(<OrdersPage />);
    fireEvent.click(screen.getByTestId("guided-lane-telemedicine"));
    fireEvent.change(screen.getByTestId("guided-tele-mode"), { target: { value: "VIDEO" } });
    fireEvent.change(screen.getByTestId("guided-tele-notes"), { target: { value: "Follow-up video consult" } });
    fireEvent.click(screen.getByTestId("guided-submit-action"));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/teleconsult/sessions",
        expect.objectContaining({
          patient_id: "patient-1",
          facility_id: "facility-1",
          session_type: "VIDEO",
          notes: "Follow-up video consult",
        })
      );
    });
  });
});
