import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AdminBedsPage from "./page";

const mutate = vi.fn();

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
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useFacilityOperations", () => ({
  facilityOpsKeys: {
    all: (fid: string) => ["facility-ops", fid],
    wards: (fid: string) => ["facility-ops", fid, "wards"],
    beds: (fid: string) => ["facility-ops", fid, "beds"],
    queueWaiting: (fid: string) => ["facility-ops", fid, "queue", "waiting"],
    queueStats: (fid: string) => ["facility-ops", fid, "queue", "stats"],
  },
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      data: [
        {
          id: "ward-1",
          attributes: {
            name: "Ward A",
            wardType: "MEDICAL",
            floor: "2",
            totalBeds: 12,
            availableBeds: 4,
            occupiedBeds: 8,
          },
        },
      ],
    },
    isLoading: false,
  }),
  useMutation: () => ({
    mutate,
    isPending: false,
  }),
  useQueryClient: () => ({
    invalidateQueries: vi.fn(),
  }),
}));

describe("AdminBedsPage", () => {
  it("surfaces capacity orchestration and keeps bed operations linked to encounter context", async () => {
    const user = userEvent.setup();
    render(<AdminBedsPage />);

    expect(screen.getByText("Capacity configuration")).toBeInTheDocument();
    expect(screen.getByText("Capacity loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "Bed Operations" })).toHaveAttribute(
      "href",
      "/beds?patientId=patient-1&encounterId=enc-1&source=discharge",
    );

    await user.click(screen.getByRole("button", { name: /Add Ward/i }));
    await user.type(screen.getByPlaceholderText("Ward name"), "Ward B");
    await user.click(screen.getByRole("button", { name: "Create Ward & Beds" }));

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Ward B",
        facilityId: "facility-1",
      }),
    );
  });
});
